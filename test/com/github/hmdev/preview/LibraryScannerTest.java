package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本棚のフォルダスキャン。EPUB を展開せず ZIP エントリだけを読むことと、
 * 壊れた 1 冊が全体を落とさないことを検証する。
 */
public class LibraryScannerTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path root() { return temp.getRoot().toPath(); }

	@Test
	public void scansRecursivelyAndReadsMetadata() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("a.epub"));
		EpubFixture.standard().writeTo(root().resolve("sub").resolve("nested").resolve("b.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);

		assertEquals(2, entries.size());
		for (LibraryEntry entry : entries) {
			assertEquals("テスト書籍", entry.title());
			assertEquals("テスト著者", entry.creator());
			assertTrue(entry.size() > 0);
		}
		// パスの昇順で安定して並ぶ (UI 側の並べ替えの土台になる)
		assertTrue(entries.get(0).file().toString().endsWith("a.epub"));
		assertTrue(entries.get(1).file().toString().endsWith("b.epub"));
	}

	@Test
	public void extractsNothingToDisk() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("a.epub"));
		LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);

		// 本棚で数百冊を並べても展開コストが出ないことがこの機能の前提。
		// スキャンがディレクトリを作っていたら遅延展開の設計が崩れている
		try (java.util.stream.Stream<Path> children = Files.list(root())) {
			assertEquals("スキャンが余計なファイルを作っている",
				List.of("a.epub"), children.map(p -> p.getFileName().toString()).sorted().toList());
		}
	}

	@Test
	public void ignoresFilesThatAreNotEpub() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("book.epub"));
		Files.writeString(root().resolve("memo.txt"), "not an epub", StandardCharsets.UTF_8);
		Files.writeString(root().resolve("archive.zip"), "not an epub", StandardCharsets.UTF_8);
		// 拡張子だけの名前は本ではない
		Files.writeString(root().resolve(".epub"), "not an epub", StandardCharsets.UTF_8);

		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).file().toString().endsWith("book.epub"));
	}

	@Test
	public void kepubIsTreatedAsEpub()
	{
		assertTrue(LibraryScanner.isEpub(Path.of("x", "book.kepub.epub")));
		assertTrue(LibraryScanner.isEpub(Path.of("x", "BOOK.EPUB")));
		assertTrue("拡張子だけのファイルは本ではない", !LibraryScanner.isEpub(Path.of("x", ".epub")));
		assertTrue(!LibraryScanner.isEpub(Path.of("x", "book.mobi")));
	}

	@Test
	public void brokenBookIsSkippedWithoutFailingTheWholeScan() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("good.epub"));
		// ZIP ですらないファイル
		Files.writeString(root().resolve("garbage.epub"), "not a zip at all", StandardCharsets.UTF_8);
		// ZIP だが container.xml が無い
		EpubFixture.standard().remove("META-INF/container.xml").writeTo(root().resolve("nocontainer.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);

		assertEquals("壊れた本があっても読める本は出す", 1, entries.size());
		assertTrue(entries.get(0).file().toString().endsWith("good.epub"));
	}

	@Test
	public void findsCoverImageDeclaredByEpub3Property() throws Exception
	{
		EpubFixture.withEpub3Cover().writeTo(root().resolve("a.epub"));
		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals("OPS/images/cover.png", entries.get(0).coverEntry());
	}

	@Test
	public void findsCoverImageDeclaredByEpub2MetaName() throws Exception
	{
		EpubFixture.withEpub2Cover("pic").writeTo(root().resolve("a.epub"));
		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals("OPS/images/pic.png", entries.get(0).coverEntry());
	}

	@Test
	public void epub2CoverPointingAtXhtmlIsNotUsedAsImage() throws Exception
	{
		// meta[name=cover] が表紙ページ (XHTML) を指している EPUB が実在する。
		// そのまま画像として配ると壊れるので media-type を確かめる。
		// この EPUB の画像は images/pic.png で id / href に cover を含まないため、
		// 推測フォールバックも効かず「表紙なし」が正しい
		EpubFixture.withEpub2Cover("cover-page").writeTo(root().resolve("a.epub"));
		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertNull(entries.get(0).coverEntry());
	}

	@Test
	public void mediaTypeIsMatchedCaseInsensitively() throws Exception
	{
		// MIME のタイプ / サブタイプは大小文字を区別しない。
		// IMAGE/PNG を画像でないと判定すると、宣言された表紙を取り落とす
		EpubFixture fixture = EpubFixture.standard();
		fixture.putBytes("OPS/images/pic.png", EpubFixture.PNG_1PX);
		fixture.put("OPS/package.opf", EpubFixture.packageOpf()
			.replace("    <item id=\"ncx\"",
				"    <item id=\"pic\" href=\"images/pic.png\" media-type=\"IMAGE/PNG\"/>\n"
				+ "    <item id=\"ncx\"")
			.replace("  </metadata>", "    <meta name=\"cover\" content=\"pic\"/>\n  </metadata>"));
		fixture.writeTo(root().resolve("a.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals("OPS/images/pic.png", entries.get(0).coverEntry());
	}

	@Test
	public void coverIsGuessedFromTheNameWhenNothingIsDeclared() throws Exception
	{
		// 表紙の宣言が無い EPUB でも、名前で分かるものは本棚に絵を出したい。
		// 外しても「表紙なし」になるだけなので推測してよい
		EpubFixture fixture = EpubFixture.standard();
		fixture.putBytes("OPS/images/cover.png", EpubFixture.PNG_1PX);
		fixture.put("OPS/package.opf", EpubFixture.packageOpf().replace(
			"    <item id=\"ncx\"",
			"    <item id=\"img1\" href=\"images/cover.png\" media-type=\"image/png\"/>\n"
			+ "    <item id=\"ncx\""));
		fixture.writeTo(root().resolve("a.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals("OPS/images/cover.png", entries.get(0).coverEntry());
	}

	@Test
	public void bookWithoutAnyCoverImageHasNoCoverEntry() throws Exception
	{
		// standard() の表紙は XHTML だけで画像が無い
		EpubFixture.standard().writeTo(root().resolve("a.epub"));
		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertNull(entries.get(0).coverEntry());
	}

	@Test
	public void coverDeclaredButMissingFromTheZipIsDropped() throws Exception
	{
		// manifest にあっても ZIP に無い EPUB がある。持たせたままだと
		// サムネイル要求のたびに 404 になる
		EpubFixture.withEpub3Cover().remove("OPS/images/cover.png").writeTo(root().resolve("a.epub"));
		List<LibraryEntry> entries = LibraryScanner.scan(root(), LibraryScanner.DEFAULT_MAX_DEPTH, null);
		assertEquals(1, entries.size());
		assertNull(entries.get(0).coverEntry());
	}

	@Test
	public void depthLimitStopsTheWalk() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("top.epub"));
		EpubFixture.standard().writeTo(root().resolve("a").resolve("b").resolve("deep.epub"));

		// maxDepth=1 は起点直下のみ
		List<LibraryEntry> shallow = LibraryScanner.scan(root(), 1, null);
		assertEquals(1, shallow.size());
		assertTrue(shallow.get(0).file().toString().endsWith("top.epub"));

		assertEquals(2, LibraryScanner.scan(root(), 3, null).size());
	}

	@Test
	public void theBookLimitCountsBooksThatCouldBeRead() throws Exception
	{
		// 上限を「候補」で数えると、壊れた .epub が先に並んでいるだけで
		// 後ろの正常な本が丸ごと落ちる
		Files.writeString(root().resolve("1-broken.epub"), "not a zip", StandardCharsets.UTF_8);
		Files.writeString(root().resolve("2-broken.epub"), "not a zip", StandardCharsets.UTF_8);
		EpubFixture.standard().writeTo(root().resolve("3-good.epub"));
		EpubFixture.standard().writeTo(root().resolve("4-good.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), 3, null, 2);
		assertEquals(2, entries.size());
		assertTrue(entries.get(0).file().toString().endsWith("3-good.epub"));
		assertTrue(entries.get(1).file().toString().endsWith("4-good.epub"));
	}

	@Test
	public void theBookLimitTruncatesTheList() throws Exception
	{
		for (int i = 1; i <= 4; i++) {
			EpubFixture.standard().writeTo(root().resolve("book" + i + ".epub"));
		}
		assertEquals(2, LibraryScanner.scan(root(), 3, null, 2).size());
		assertEquals(4, LibraryScanner.scan(root(), 3, null, 10).size());
	}

	@Test
	public void aFailedDirectoryListingDoesNotAbortTheWalk() throws Exception
	{
		// SimpleFileVisitor の既定 postVisitDirectory / visitFileFailed は、
		// 列挙が I/O エラーで中断した場合その例外を再スローする。握り潰していないと
		// ネットワークドライブや走査中に消えたフォルダで本棚全体が落ちる。
		// walkFileTree にエラーを起こさせるのは環境依存なので visitor を直接叩く
		LibraryScanner.EpubCollector collector =
			new LibraryScanner.EpubCollector(root(), LibraryScanner.MAX_BOOKS);

		assertEquals(java.nio.file.FileVisitResult.CONTINUE,
			collector.postVisitDirectory(root().resolve("gone"), new IOException("列挙に失敗")));
		assertEquals(java.nio.file.FileVisitResult.CONTINUE,
			collector.postVisitDirectory(root().resolve("ok"), null));
		assertEquals(java.nio.file.FileVisitResult.CONTINUE,
			collector.visitFileFailed(root().resolve("denied.epub"), new IOException("権限なし")));
	}

	@Test
	public void theCandidateCeilingStopsTheWalk() throws Exception
	{
		LibraryScanner.EpubCollector collector = new LibraryScanner.EpubCollector(root(), 2);
		java.nio.file.attribute.BasicFileAttributes attrs =
			Files.readAttributes(EpubFixture.standard().writeTo(root().resolve("a.epub")),
				java.nio.file.attribute.BasicFileAttributes.class);

		assertEquals(java.nio.file.FileVisitResult.CONTINUE,
			collector.visitFile(root().resolve("1.epub"), attrs));
		assertEquals("上限に達したら走査を打ち切る", java.nio.file.FileVisitResult.TERMINATE,
			collector.visitFile(root().resolve("2.epub"), attrs));
		assertEquals(2, collector.files.size());
	}

	@Test
	public void metadataLargerThanTheLimitIsRejected() throws Exception
	{
		// 高圧縮の悪意ある EPUB で巨大な OPF を掴まされないこと。
		// 宣言サイズと実読み込み量の両方で上限を掛けている
		StringBuilder huge = new StringBuilder((int)XmlUtils.MAX_METADATA_BYTES + 1024);
		huge.append("<?xml version=\"1.0\"?><package><metadata><title>x</title></metadata>");
		while (huge.length() <= XmlUtils.MAX_METADATA_BYTES) huge.append("<!-- padding -->");
		huge.append("</package>");
		EpubFixture.standard().put("OPS/package.opf", huge.toString())
			.writeTo(root().resolve("huge.epub"));
		EpubFixture.standard().writeTo(root().resolve("normal.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), 3, null);
		assertEquals("上限超過の本だけを落とす", 1, entries.size());
		assertTrue(entries.get(0).file().toString().endsWith("normal.epub"));
	}

	@Test
	public void hugeMetadataFieldsAreTruncated() throws Exception
	{
		// 1 冊ごとのメタデータ上限は合計を縛らない。上限すれすれの OPF を持つ本が
		// 2000 冊あれば、一覧を保持しているだけでヒープを食い潰せる
		String longTitle = "あ".repeat(LibraryScanner.MAX_FIELD_CHARS + 100);
		EpubFixture.standard()
			.put("OPS/package.opf",
				EpubFixture.packageOpf().replace("テスト書籍", longTitle))
			.writeTo(root().resolve("a.epub"));

		List<LibraryEntry> entries = LibraryScanner.scan(root(), 3, null);
		assertEquals(LibraryScanner.MAX_FIELD_CHARS, entries.get(0).title().length());
		assertEquals("テスト著者", entries.get(0).creator());
	}

	@Test
	public void missingFolderIsAnError()
	{
		try {
			LibraryScanner.scan(root().resolve("nosuchdir"), 3, null);
			org.junit.Assert.fail("存在しないフォルダなのに例外にならなかった");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("nosuchdir"));
		}
	}

	@Test
	public void cacheIsReusedWhileTheFileIsUnchangedAndDroppedWhenItChanges() throws Exception
	{
		Path epub = EpubFixture.standard().writeTo(root().resolve("a.epub"));
		LibraryIndexCache cache = new LibraryIndexCache(root().resolve("index.tsv"));
		cache.update(LibraryScanner.scan(root(), 3, null));

		// キャッシュ側の書名を書き換えておくと、再パースされたかどうかが見分けられる
		LibraryEntry cached = cache.get(epub);
		assertNotNull(cached);
		cache.update(List.of(new LibraryEntry(
			cached.file(), cached.size(), cached.modifiedMillis(), "キャッシュ由来", "x", null)));

		assertEquals("サイズも更新時刻も同じなら再パースしない",
			"キャッシュ由来", LibraryScanner.scan(root(), 3, cache).get(0).title());

		// 変換し直して EPUB が差し替わったら読み直す
		Files.setLastModifiedTime(epub,
			java.nio.file.attribute.FileTime.fromMillis(cached.modifiedMillis() + 5000));
		assertEquals("更新時刻が変わったら読み直す",
			"テスト書籍", LibraryScanner.scan(root(), 3, cache).get(0).title());
	}
}
