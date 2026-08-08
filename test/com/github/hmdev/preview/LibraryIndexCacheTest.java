package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
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
		return new LibraryEntry(file, 123L, 456L, title, creator, cover);
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
			+ "a\tb\tc\td\te\tf\n",   // サイズが数値でない
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
		LibraryEntry parsed = LibraryIndexCache.parseLine("C:\\x\\a.epub\t1\t2\t書名\t著者\t");
		assertNotNull(parsed);
		assertEquals("", parsed.coverEntry());
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
}
