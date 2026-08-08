package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** セッションの寿命管理と、並行するプレビューを壊さない掃除 */
public class PreviewSessionTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void extractionIsDeferred() throws IOException
	{
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("book.epub"));
		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			assertNull("登録だけでは展開しない", session.getBook(bookId).getOpf());

			PreviewSession.Book book = session.ensureExtracted(bookId);
			assertNotNull(book.getOpf());
			assertNotNull(book.getToc());
			assertEquals("テスト書籍", book.getDisplayName());

			// 二度目は再展開しない。Book インスタンスは再展開しても同一なので、
			// 世代ディレクトリが変わらないことで判定する
			assertEquals(book.getDir(), session.ensureExtracted(bookId).getDir());
		}
	}

	@Test
	public void cleanupKeepsDirectoriesOwnedByALiveSession() throws IOException
	{
		try (PreviewSession live = new PreviewSession()) {
			Path liveRoot = live.getRoot();
			Files.writeString(liveRoot.resolve("marker.txt"), "alive");

			// 別のプレビューが起動したときと同じ掃除を走らせる
			PreviewSession.cleanupOrphans();

			assertTrue("実行中セッションの展開先を消してはならない",
				Files.isRegularFile(liveRoot.resolve("marker.txt")));
		}
	}

	@Test
	public void cleanupRemovesDirectoriesWithoutOwner() throws IOException
	{
		// 持ち主の居ない残骸 (ロックファイルが無い旧版の残り) は回収する。
		// 猶予期間があるので、十分に古いものとして扱わせる
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		Path orphan = Files.createTempDirectory(tmp, PreviewSession.TEMP_PREFIX + "-");
		Files.writeString(orphan.resolve("stale.txt"), "orphan");
		makeOld(orphan);

		PreviewSession.cleanupOrphans();

		assertFalse("持ち主の居ない残骸は削除する", Files.exists(orphan));
	}

	@Test
	public void cleanupSparesNewlyCreatedDirectoryThatAlreadyHasAnUnlockedLockFile() throws IOException
	{
		// .lock は作られたがまだ tryLock していない一瞬。
		// 「.lock があるなら猶予なしで tryLock 判定」にすると、この状態を削除してしまう
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		Path fresh = Files.createTempDirectory(tmp, PreviewSession.TEMP_PREFIX + "-");
		try {
			Files.writeString(fresh.resolve("marker.txt"), "starting up");
			Files.createFile(fresh.resolve(PreviewSession.LOCK_FILE));

			PreviewSession.deleteIfNotInUse(fresh);

			assertTrue("ロック取得前のディレクトリを消してはならない",
				Files.isRegularFile(fresh.resolve("marker.txt")));
		} finally {
			PreviewSession.deleteRecursively(fresh);
		}
	}

	@Test
	public void cleanupSparesNewlyCreatedDirectoryWithoutLock() throws IOException
	{
		// ディレクトリ作成と .lock 生成は原子的でない。
		// 別プロセスが「作った直後でまだロックを張っていない」状態を消してはならない
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		Path fresh = Files.createTempDirectory(tmp, PreviewSession.TEMP_PREFIX + "-");
		try {
			Files.writeString(fresh.resolve("marker.txt"), "starting up");

			PreviewSession.cleanupOrphans();

			assertTrue("作成直後のディレクトリを消してはならない",
				Files.isRegularFile(fresh.resolve("marker.txt")));
		} finally {
			PreviewSession.deleteRecursively(fresh);
		}
	}

	@Test
	public void cleanupSkipsDirectoryWhoseLockCannotBeAcquired() throws IOException
	{
		// LIVE_ROOTS の早期 return を通らない経路で、.lock が掴まれていたら削除しないこと。
		// 同一 JVM から tryLock すると OverlappingFileLockException になるため、
		// 検証できるのは「判定できない場合は残す」経路。
		// 別プロセスがロック中の acquired == null 経路は in-process では再現できない
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		Path dir = Files.createTempDirectory(tmp, PreviewSession.TEMP_PREFIX + "-");
		Path lockFile = dir.resolve(PreviewSession.LOCK_FILE);
		try (FileChannel channel = FileChannel.open(lockFile,
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			 FileLock held = channel.tryLock()) {
			assertNotNull("前提: ロックを取得できること", held);
			Files.writeString(dir.resolve("marker.txt"), "in use");
			makeOld(dir);

			PreviewSession.deleteIfNotInUse(dir);

			assertTrue("ロックされているディレクトリを消してはならない",
				Files.isRegularFile(dir.resolve("marker.txt")));
		} finally {
			PreviewSession.deleteRecursively(dir);
		}
	}

	/** 猶予期間の判定を通すため、更新時刻を十分過去にする */
	private static void makeOld(Path dir) throws IOException
	{
		Files.setLastModifiedTime(dir, FileTime.fromMillis(
			System.currentTimeMillis() - PreviewSession.ORPHAN_GRACE_MILLIS - 10_000));
	}

	@Test
	public void closeReleasesLockAndRemovesDirectory() throws IOException
	{
		PreviewSession session = new PreviewSession();
		Path root = session.getRoot();
		assertTrue(Files.isDirectory(root));
		session.close();
		assertFalse(Files.exists(root));
	}

	@Test
	public void bookIdsAreOpaqueAndTheSameFileIsNotDuplicated() throws IOException
	{
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("book.epub"));
		Path other = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("other.epub"));
		try (PreviewSession session = new PreviewSession()) {
			assertEquals("b1", session.addBook(epub));
			// 同じ EPUB を繰り返し登録しても展開先を増やさない
			assertEquals("b1", session.addBook(epub));
			assertEquals("b2", session.addBook(other));
			assertEquals("b1", session.getDefaultBookId());
		}
	}

	@Test
	public void reconvertedEpubAtTheSamePathIsExtractedAgain() throws IOException
	{
		// この機能の中心的な使い方:
		// 変換 → プレビュー → 設定を変えて同じパスへ再変換 → プレビュー。
		// 展開済みを使い回すと古いレイアウトを見続けることになる
		Path epub = temp.getRoot().toPath().resolve("book.epub");
		EpubFixture.standard().writeTo(epub);

		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			assertEquals("テスト書籍", session.ensureExtracted(bookId).getOpf().getTitle());

			// 同じパスへ別内容の EPUB を書き直す (再変換に相当)
			EpubFixture.standard()
				.put("OPS/package.opf",
					EpubFixture.packageOpf().replace("テスト書籍", "改訂版テスト書籍"))
				.writeTo(epub);
			Files.setLastModifiedTime(epub, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

			// 同じパスなので bookId は変わらない (展開先を増やさない設計)
			assertEquals(bookId, session.addBook(epub));
			assertEquals("再変換後の内容が反映されること",
				"改訂版テスト書籍", session.ensureExtracted(bookId).getOpf().getTitle());
		}
	}

	@Test
	public void unchangedEpubIsNotExtractedTwice() throws IOException
	{
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("book.epub"));
		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			OpfPackage first = session.ensureExtracted(bookId).getOpf();
			// 変わっていなければ解析結果を使い回す (同じインスタンスが返る)
			assertTrue(first == session.ensureExtracted(bookId).getOpf());
		}
	}

	@Test
	public void extractedBookStaysUsableAfterTheSourceEpubDisappears() throws IOException
	{
		// kindlegen 経路は変換後に .epub を消す。
		// 表示中のプレビューが丸ごと 500 にならないよう、展開済みは配り続ける
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("gone.epub"));
		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			session.ensureExtracted(bookId);

			Files.delete(epub);

			PreviewSession.Book book = session.ensureExtracted(bookId);
			assertEquals("テスト書籍", book.getOpf().getTitle());
			assertTrue(Files.isRegularFile(book.getDir().resolve("OPS/xhtml/text00001.xhtml")));
		}
	}

	@Test
	public void failedReExtractionKeepsTheWorkingCopy() throws IOException
	{
		// 変換で書き込み途中の .epub を掴むと展開に失敗する。
		// そのときに直前まで正常だった展開結果まで失ってはならない
		Path epub = temp.getRoot().toPath().resolve("book.epub");
		EpubFixture.standard().writeTo(epub);

		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			Path firstDir = session.ensureExtracted(bookId).getDir();
			assertEquals("テスト書籍", session.getBook(bookId).getOpf().getTitle());

			// 壊れた内容で上書きする (書き込み途中に相当)
			Files.writeString(epub, "not a zip at all");
			Files.setLastModifiedTime(epub, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

			try {
				session.ensureExtracted(bookId);
				org.junit.Assert.fail("壊れた EPUB では失敗すること");
			} catch (IOException expected) {
				/* 期待どおり */
			}

			// 直前の展開結果はそのまま配れる状態で残っていること
			PreviewSession.Book book = session.getBook(bookId);
			assertEquals("テスト書籍", book.getOpf().getTitle());
			assertEquals(firstDir, book.getDir());
			assertTrue(Files.isRegularFile(firstDir.resolve("OPS/xhtml/text00001.xhtml")));
		}
	}

	@Test
	public void reExtractionRemovesThePreviousDirectory() throws IOException
	{
		Path epub = temp.getRoot().toPath().resolve("book.epub");
		EpubFixture.standard().writeTo(epub);

		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(epub);
			Path firstDir = session.ensureExtracted(bookId).getDir();

			EpubFixture.standard()
				.put("OPS/package.opf", EpubFixture.packageOpf().replace("テスト書籍", "改訂版"))
				.writeTo(epub);
			Files.setLastModifiedTime(epub, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

			Path secondDir = session.ensureExtracted(bookId).getDir();

			assertNotEquals("新しいディレクトリへ展開すること", firstDir, secondDir);
			assertFalse("古い展開先は削除すること", Files.exists(firstDir));
			assertEquals("改訂版", session.getBook(bookId).getOpf().getTitle());
		}
	}

	@Test
	public void ensureExtractedFailsForAnInvalidEpub() throws IOException
	{
		// container.xml が無い ZIP は EPUB として扱えない。
		// CLI が「開けた」と報告しないよう、ここで失敗する必要がある
		Path broken = EpubFixture.standard()
			.remove("META-INF/container.xml")
			.writeTo(temp.getRoot().toPath().resolve("broken.epub"));
		try (PreviewSession session = new PreviewSession()) {
			String bookId = session.addBook(broken);
			try {
				session.ensureExtracted(bookId);
				org.junit.Assert.fail("不正な EPUB は失敗すること");
			} catch (IOException expected) {
				assertTrue(expected.getMessage().contains("container.xml"));
			}
			assertNull("失敗後は解析結果を残さないこと", session.getBook(bookId).getOpf());
		}
	}
}
