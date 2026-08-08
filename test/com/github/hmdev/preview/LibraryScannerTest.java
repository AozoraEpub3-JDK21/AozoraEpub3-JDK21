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
