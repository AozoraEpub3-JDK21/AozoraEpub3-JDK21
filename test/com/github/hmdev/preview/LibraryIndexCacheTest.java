package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本棚インデックスの永続化。
 * キャッシュは再生成できるので、壊れていたら「捨てる」のが正しい振る舞い。
 */
public class LibraryIndexCacheTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path cacheFile() { return temp.getRoot().toPath().resolve("index.tsv"); }

	private static LibraryEntry entry(Path file, String title, String creator, String cover)
	{
		return new LibraryEntry(file, 123L, 456L, title, creator, cover, null);
	}

	@Test
	public void savesAndLoadsEntries()
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(entry(book, "書名", "著者", "OPS/images/cover.png")));

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		LibraryEntry got = reloaded.get(book);
		assertNotNull(got);
		assertEquals("書名", got.title());
		assertEquals("著者", got.creator());
		assertEquals("OPS/images/cover.png", got.coverEntry());
		assertEquals(123L, got.size());
		assertEquals(456L, got.modifiedMillis());
		assertTrue(got.matches(123L, 456L));
		assertTrue(!got.matches(124L, 456L));
	}

	@Test
	public void nullAndEmptyStringsStayDistinct()
	{
		// 表紙なし (null) と、書名が空文字の EPUB を混同すると
		// 「毎回サムネイルを取りに行って 404」あるいは逆の誤動作になる
		Path book = temp.getRoot().toPath().resolve("a.epub");
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(entry(book, "", null, null)));

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		LibraryEntry got = reloaded.get(book);
		assertEquals("", got.title());
		assertNull(got.creator());
		assertNull(got.coverEntry());
	}

	@Test
	public void separatorsInsideValuesDoNotBreakTheFormat()
	{
		// 書名は EPUB 由来なので何でも入りうる。タブや改行で列がずれると
		// 隣の本の情報として復元されてしまう
		Path book = temp.getRoot().toPath().resolve("a.epub");
		String nasty = "タブ\tと改行\nと\\バックスラッシュ\r";
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(entry(book, nasty, "著者", null)));

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		assertEquals(nasty, reloaded.get(book).title());
		assertEquals("著者", reloaded.get(book).creator());
	}

	@Test
	public void escapeRoundTripsIncludingTheNullMarker()
	{
		assertNull(LibraryIndexCache.unescape(LibraryIndexCache.escape(null)));
		assertEquals("", LibraryIndexCache.unescape(LibraryIndexCache.escape("")));
		// null マーカーと紛らわしい文字列そのものは壊れないこと
		assertEquals("\\0", LibraryIndexCache.unescape(LibraryIndexCache.escape("\\0")));
		assertEquals("a\tb", LibraryIndexCache.unescape(LibraryIndexCache.escape("a\tb")));
	}

	@Test
	public void brokenLinesAreDroppedIndividually() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		Files.writeString(cacheFile(),
			LibraryIndexCache.HEADER + "\n"
			+ "列が足りない行\n"
			+ LibraryIndexCache.formatLine(entry(book, "生き残る", "著者", null)) + "\n"
			+ "a\tb\tc\td\te\tf\tg\n",   // サイズが数値でない
			StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertEquals("生き残る", cache.get(book).title());
	}

	@Test
	public void aTrailingEmptyColumnStillCountsAsAColumn()
	{
		// String.split は既定で末尾の空文字列を落とす。-1 を忘れると
		// 最後の列が空の行だけが「列数不足」として捨てられる
		LibraryEntry parsed = LibraryIndexCache.parseLine("C:\\x\\a.epub\t1\t2\t書名\t著者\t\t");
		assertNotNull("列数不足として捨てられている", parsed);
		assertEquals("書名", parsed.title());
		// 空の表紙は「表紙なし」に、空の掲載元は「掲載元なし」に正規化される
		assertNull(parsed.coverEntry());
		assertNull(parsed.source());
	}

	@Test
	public void restoredValuesGoThroughTheSameConstraintsAsAFreshScan()
	{
		// キャッシュはホーム配下の平文で手で書き換えられる。復元した値を
		// そのまま信じると、512 文字上限も「表紙はルート相対で .. を含まない」も
		// キャッシュ経由だけすり抜ける
		LibraryEntry tampered = LibraryIndexCache.parseLine(String.join("\t",
			"C:\\x\\a.epub", "1", "2",
			"あ".repeat(LibraryScanner.MAX_FIELD_CHARS + 50),
			"著者",
			"../../../etc/passwd",
			"javascript:alert(1)"));
		assertNotNull(tampered);
		assertEquals(LibraryScanner.MAX_FIELD_CHARS, tampered.title().length());
		assertNull("展開先の外を指す表紙は捨てる", tampered.coverEntry());
		// 掲載元は「続きを取る」で取りに行く先。http・https 以外は捨てる
		assertNull("http・https でない掲載元は捨てる", tampered.source());
		LibraryEntry longSource = LibraryIndexCache.parseLine(String.join("\t",
			"C:\\x\\a.epub", "1", "2", "書名", "著者", "",
			"https://example.com/" + "a".repeat(LibraryScanner.MAX_FIELD_CHARS)));
		assertNull("切ると別の URL になるので、長すぎる掲載元は捨てる", longSource.source());
	}

	@Test
	public void theSourceSurvivesSaveAndLoad() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(new LibraryEntry(book, 1L, 2L, "書名", "著者", null, "https://ncode.syosetu.com/n1234ab/")));
		cache.save();

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		assertEquals("https://ncode.syosetu.com/n1234ab/", reloaded.get(book).source());
	}

	/**
	 * 1 世代目（掲載元の列が無い）の記録は使わない。使えば、掲載元のある本も「掲載元なし」のまま残ってしまう。
	 * 世代（HEADER）と列数の両方で落ちるので、どちらか片方を外してもこの升は通る（世代を上げたのは念のため）
	 */
	@Test
	public void aFirstGenerationFileWithoutTheSourceColumnIsDiscarded() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		Files.writeString(cacheFile(),
			"#aozoraepub3-preview-library\t1\n"
			+ String.join("\t", book.toAbsolutePath().normalize().toString(), "1", "2", "旧形式", "著者", "\\0") + "\n",
			StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertNull(cache.get(book));
	}

	/** 書くときは一時ファイルから置き換え、一時ファイルを残さない（途中で切れた行を読み戻さないように。internal #19） */
	@Test
	public void saveReplacesTheFileAndLeavesNoTemporary() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(new LibraryEntry(book, 1L, 2L, "書名", "著者", null, "https://ncode.syosetu.com/n1234ab/")));
		cache.save();
		cache.save();
		try (java.util.stream.Stream<Path> files = Files.list(cacheFile().getParent())) {
			assertEquals(List.of(cacheFile().getFileName().toString()),
				files.map(f -> f.getFileName().toString()).filter(n -> n.startsWith(cacheFile().getFileName().toString())).toList());
		}
	}

	/** 列数が合っていても、世代 1 の見出しなら読まない（世代を上げたことだけで落ちる形） */
	@Test
	public void aFirstGenerationHeaderAloneDiscardsTheFile() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		Files.writeString(cacheFile(),
			"#aozoraepub3-preview-library\t1\n"
			+ LibraryIndexCache.formatLine(new LibraryEntry(book, 1L, 2L, "書名", "著者", null, null)) + "\n",
			StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertNull(cache.get(book));
	}

	@Test
	public void aForeignOrOldFormatIsDiscardedWholesale() throws Exception
	{
		Path book = temp.getRoot().toPath().resolve("a.epub");
		Files.writeString(cacheFile(),
			"#aozoraepub3-preview-library\t0\n"
			+ LibraryIndexCache.formatLine(entry(book, "旧形式", "著者", null)) + "\n",
			StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertNull("世代が違うキャッシュは読まない", cache.get(book));
	}

	@Test
	public void missingCacheFileIsNotAnError()
	{
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertNull(cache.get(temp.getRoot().toPath().resolve("a.epub")));
	}

	@Test
	public void entriesFromOtherFoldersSurviveAnUpdate()
	{
		// 本棚を 2 つ切り替えるたびに互いのキャッシュを捨て合うと、
		// 常に片方が全冊再パースになる
		Path first = temp.getRoot().toPath().resolve("shelf1").resolve("a.epub");
		Path second = temp.getRoot().toPath().resolve("shelf2").resolve("b.epub");
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.update(List.of(entry(first, "一冊目", null, null)));
		cache.update(List.of(entry(second, "二冊目", null, null)));

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		assertNotNull(reloaded.get(first));
		assertNotNull(reloaded.get(second));
	}

	@Test
	public void theCacheIsCappedAndDropsTheLeastRecentlySeen()
	{
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		List<LibraryEntry> many = new ArrayList<>();
		for (int i = 0; i < LibraryIndexCache.MAX_ENTRIES + 10; i++) {
			many.add(entry(temp.getRoot().toPath().resolve("b" + i + ".epub"), "t" + i, null, null));
		}
		cache.update(many);

		// 書き出す分だけでなくメモリ上も切り詰めること。片方だけだと、GUI を
		// 起動したまま本棚を渡り歩いたときにマップが際限なく育ち、
		// 「捨てたはずの記録」が再利用され続ける
		assertEquals(LibraryIndexCache.MAX_ENTRIES, cache.size());
		assertNull(cache.get(temp.getRoot().toPath().resolve("b0.epub")));

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		assertNull("溢れた分は古い方から捨てる",
			reloaded.get(temp.getRoot().toPath().resolve("b0.epub")));
		assertNotNull("直近のものは残る",
			reloaded.get(temp.getRoot().toPath().resolve(
				"b" + (LibraryIndexCache.MAX_ENTRIES + 9) + ".epub")));
	}

	@Test
	public void loadingAlsoRespectsTheEntryLimit() throws Exception
	{
		// 上限を超える行数のファイルを手で置かれても、メモリ上の件数は守る
		StringBuilder buf = new StringBuilder();
		buf.append(LibraryIndexCache.HEADER).append('\n');
		for (int i = 0; i < LibraryIndexCache.MAX_ENTRIES + 25; i++) {
			buf.append(LibraryIndexCache.formatLine(
				entry(temp.getRoot().toPath().resolve("b" + i + ".epub"), "t" + i, null, null)))
				.append('\n');
		}
		Files.writeString(cacheFile(), buf.toString(), StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertEquals(LibraryIndexCache.MAX_ENTRIES, cache.size());
		assertNull(cache.get(temp.getRoot().toPath().resolve("b0.epub")));
		assertNotNull(cache.get(temp.getRoot().toPath().resolve(
			"b" + (LibraryIndexCache.MAX_ENTRIES + 24) + ".epub")));
	}

	@Test
	public void whatIsWrittenCanAlwaysBeReadBack() throws Exception
	{
		// 件数だけで縛ると、長い書名が並んだときに MAX_ENTRIES × MAX_FIELD_CHARS で
		// ファイルサイズ上限を超え、書いた直後の自分のファイルを読み捨てることになる
		String longTitle = "あ".repeat(LibraryScanner.MAX_FIELD_CHARS);
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		List<LibraryEntry> many = new ArrayList<>();
		for (int i = 0; i < LibraryIndexCache.MAX_ENTRIES; i++) {
			many.add(entry(temp.getRoot().toPath().resolve("b" + i + ".epub"),
				longTitle, longTitle, null));
		}
		cache.update(many);

		assertTrue("上限を超えるサイズで書き出している",
			Files.size(cacheFile()) <= LibraryIndexCache.MAX_FILE_BYTES);

		LibraryIndexCache reloaded = new LibraryIndexCache(cacheFile());
		reloaded.load();
		assertTrue("書いた内容が 1 件も読み戻せていない", reloaded.size() > 0);
		assertEquals("メモリとファイルの件数が食い違っている", cache.size(), reloaded.size());
	}

	@Test
	public void anOversizedCacheFileIsDiscardedWithoutReadingIt() throws Exception
	{
		// 読んでから件数で切るのでは、その前に巨大なファイルを全部メモリに載せてしまう
		Path book = temp.getRoot().toPath().resolve("a.epub");
		StringBuilder buf = new StringBuilder();
		buf.append(LibraryIndexCache.HEADER).append('\n');
		buf.append(LibraryIndexCache.formatLine(entry(book, "書名", null, null))).append('\n');
		buf.append("#").append("x".repeat((int)LibraryIndexCache.MAX_FILE_BYTES)).append('\n');
		Files.writeString(cacheFile(), buf.toString(), StandardCharsets.UTF_8);

		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertEquals(0, cache.size());
		assertNull(cache.get(book));
	}

	@Test
	public void aCacheFileWithInvalidUtf8IsDiscarded() throws Exception
	{
		// readAllLines は不正な UTF-8 で MalformedInputException を投げる。
		// キャッシュが壊れているだけでプレビューが起動しなくなってはいけない
		Files.write(cacheFile(), new byte[] {(byte)0xFF, (byte)0xFE, (byte)0xFF, '\n'});
		LibraryIndexCache cache = new LibraryIndexCache(cacheFile());
		cache.load();
		assertEquals(0, cache.size());
		assertNull(cache.get(temp.getRoot().toPath().resolve("a.epub")));
	}

	/** symlink の索引は、リンクのまま、たどった先を書き換える（#117 のゲート2） */
	@Test
	public void aLinkedIndexStaysALink() throws Exception
	{
		Path real = temp.getRoot().toPath().resolve("real.tsv");
		Files.writeString(real, "x", StandardCharsets.UTF_8);
		Path link = temp.getRoot().toPath().resolve("link.tsv");
		try {
			Files.createSymbolicLink(link, real);
		} catch (UnsupportedOperationException | java.io.IOException e) {
			org.junit.Assume.assumeNoException("リンクを作れない環境", e);
		}
		LibraryIndexCache cache = new LibraryIndexCache(link);
		cache.update(List.of(new LibraryEntry(temp.getRoot().toPath().resolve("a.epub"), 1L, 2L, "書名", "著者", null, null)));
		cache.save();
		assertTrue("リンクのまま", Files.isSymbolicLink(link));
		assertTrue("たどった先が書き換わる", Files.readString(real, StandardCharsets.UTF_8).startsWith(LibraryIndexCache.HEADER));
	}

	/** 前のファイルの権限を引き継ぐ（一時ファイルは 0600 で作られる。#117 のゲート2） */
	@Test
	public void saveKeepsThePermissions() throws Exception
	{
		Path file = cacheFile();
		Files.createDirectories(file.getParent());
		Files.writeString(file, "x", StandardCharsets.UTF_8);
		java.util.Set<java.nio.file.attribute.PosixFilePermission> perms;
		try {
			perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
			Files.setPosixFilePermissions(file, perms);
		} catch (UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("POSIX の権限が無い環境", e);
			return;
		}
		LibraryIndexCache cache = new LibraryIndexCache(file);
		cache.update(List.of(new LibraryEntry(temp.getRoot().toPath().resolve("a.epub"), 1L, 2L, "書名", "著者", null, null)));
		cache.save();
		assertEquals(perms, Files.getPosixFilePermissions(file));
	}

	/** 落ちたプロセスが残した古い一時ファイルは、読むときに消す（#117 のゲート2） */
	@Test
	public void staleTemporariesAreSweptOnLoad() throws Exception
	{
		Path file = cacheFile();
		Files.createDirectories(file.getParent());
		Path stale = file.getParent().resolve(file.getFileName() + ".123" + LibraryIndexCache.TMP_SUFFIX);
		Path fresh = file.getParent().resolve(file.getFileName() + ".456" + LibraryIndexCache.TMP_SUFFIX);
		Files.writeString(stale, "x", StandardCharsets.UTF_8);
		Files.writeString(fresh, "x", StandardCharsets.UTF_8);
		Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2L * 60 * 60 * 1000));
		new LibraryIndexCache(file).load();
		assertFalse("古いものは消す", Files.exists(stale));
		assertTrue("書いている最中かもしれない新しいものは残す", Files.exists(fresh));
	}

	/** 前のファイルが無ければ権限を広げない（一時ファイルの 0600 のまま。PR #117 の codex） */
	@Test
	public void aNewIndexIsNotMadeWiderThanTheTemporary() throws Exception
	{
		Path file = cacheFile();
		LibraryIndexCache cache = new LibraryIndexCache(file);
		cache.update(List.of(new LibraryEntry(temp.getRoot().toPath().resolve("a.epub"), 1L, 2L, "書名", "著者", null, null)));
		cache.save();
		try {
			assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
		} catch (UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("POSIX の権限が無い環境", e);
		}
	}

	/** symlink の索引の一時ファイルは、たどった先の隣にできるので、そこを掃除する（PR #117 の codex） */
	@Test
	public void staleTemporariesBesideALinkTargetAreSwept() throws Exception
	{
		Path realDir = temp.newFolder("realdir").toPath();
		Path real = realDir.resolve("real.tsv");
		Files.writeString(real, LibraryIndexCache.HEADER + "\n", StandardCharsets.UTF_8);
		Path link = temp.getRoot().toPath().resolve("link.tsv");
		try {
			Files.createSymbolicLink(link, real);
		} catch (UnsupportedOperationException | java.io.IOException e) {
			org.junit.Assume.assumeNoException("リンクを作れない環境", e);
		}
		Path stale = realDir.resolve("real.tsv.123" + LibraryIndexCache.TMP_SUFFIX);
		Files.writeString(stale, "x", StandardCharsets.UTF_8);
		Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2L * 60 * 60 * 1000));
		new LibraryIndexCache(link).load();
		assertFalse(Files.exists(stale));
	}
}
