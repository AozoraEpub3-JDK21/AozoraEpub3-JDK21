import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;

import com.github.hmdev.web.WebAozoraConverter;

/**
 * カクヨム章サポート — buildEpisodeChapterMapFromNextData のユニットテスト
 *
 * <p>"TableOfContentsChapter" は章題を持たず "Chapter:ID" を参照する。直後に次の章の
 * "Episode:ID" が並んでいても、その話の題を章題にしないことを確かめる。</p>
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterKakuyomuChapterTest
 */
public class WebAozoraConverterKakuyomuChapterTest {

	private Method buildMethod;
	private WebAozoraConverter converter;

	@Before
	public void setUp() throws Exception {
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://kakuyomu.jp/works/100", new File("web"));
		buildMethod = WebAozoraConverter.class.getDeclaredMethod(
			"buildEpisodeChapterMapFromNextData", String.class, String.class);
		buildMethod.setAccessible(true);
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, String> invoke(String json) throws Exception {
		return (HashMap<String, String>) buildMethod.invoke(converter, json, "100");
	}

	private static String tocChapter(String chId, String... epIds) {
		StringBuilder sb = new StringBuilder();
		sb.append("\"TableOfContentsChapter:").append(chId).append("\":{\"__typename\":\"TableOfContentsChapter\",\"id\":\"")
			.append(chId).append("\",\"episodeUnions\":[");
		for (int i = 0; i < epIds.length; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"__ref\":\"Episode:").append(epIds[i]).append("\"}");
		}
		sb.append("],\"chapter\":{\"__ref\":\"Chapter:").append(chId).append("\"}}");
		return sb.toString();
	}

	private static String episode(String epId, String title) {
		return "\"Episode:" + epId + "\":{\"__typename\":\"Episode\",\"id\":\"" + epId
			+ "\",\"title\":\"" + title + "\",\"publishedAt\":\"2026-01-01T00:00:00Z\"}";
	}

	private static String chapter(String chId, String title) {
		return "\"Chapter:" + chId + "\":{\"__typename\":\"Chapter\",\"id\":\"" + chId
			+ "\",\"level\":1,\"title\":\"" + title + "\"}";
	}

	// ---------------------------------------------------------------
	// 章の項目の直後に次の章の話が来る並び (2026-10 に実作品で発生)
	// ---------------------------------------------------------------
	@Test
	public void episodeEntryRightAfterTocChapter_usesChapterTitle() throws Exception {
		String json = "{\"props\":{\"pageProps\":{\"__APOLLO_STATE__\":{"
			+ tocChapter("10", "1", "2") + ","
			+ episode("3", "第3話　次の章の最初") + ","
			+ tocChapter("20", "3") + ","
			+ episode("1", "第1話　はじまり") + ","
			+ episode("2", "第2話　つづき") + ","
			+ chapter("10", "第一章　出会い編") + ","
			+ chapter("20", "第二章　旅立ち編")
			+ "}}}}";
		HashMap<String, String> map = invoke(json);
		assertEquals(3, map.size());
		assertEquals("第一章　出会い編", map.get("1"));
		assertEquals("第一章　出会い編", map.get("2"));
		assertEquals("第二章　旅立ち編", map.get("3"));
	}

	// ---------------------------------------------------------------
	// 章の項目の直後に章本体が来る並び (従来から正しく読めていた形)
	// ---------------------------------------------------------------
	@Test
	public void chapterEntryRightAfterTocChapter_usesChapterTitle() throws Exception {
		String json = "{\"__APOLLO_STATE__\":{"
			+ tocChapter("10", "1") + ","
			+ chapter("10", "第一章") + ","
			+ tocChapter("20", "2") + ","
			+ chapter("20", "第二章") + ","
			+ episode("1", "第1話") + ","
			+ episode("2", "第2話")
			+ "}}";
		HashMap<String, String> map = invoke(json);
		assertEquals("第一章", map.get("1"));
		assertEquals("第二章", map.get("2"));
	}

	// ---------------------------------------------------------------
	// 章本体が見つからない章は、別の題で埋めずに章なし扱い
	// ---------------------------------------------------------------
	@Test
	public void missingChapterEntry_isSkipped() throws Exception {
		String json = "{"
			+ tocChapter("10", "1") + ","
			+ episode("1", "第1話") + ","
			+ tocChapter("20", "2") + ","
			+ chapter("20", "第二章")
			+ "}";
		HashMap<String, String> map = invoke(json);
		assertNull(map.get("1"));
		assertEquals("第二章", map.get("2"));
	}

	// ---------------------------------------------------------------
	// 章に入らない話 (ID 空・"chapter":null の項目) は、最後の章の後ろに
	// その話への参照 (firstPublicEpisodeUnion) があっても章なしのまま
	// ---------------------------------------------------------------
	@Test
	public void episodeOutsideChapters_hasNoChapter() throws Exception {
		String json = "{"
			+ "\"TableOfContentsChapter:\":{\"__typename\":\"TableOfContentsChapter\",\"id\":\"\","
			+ "\"episodeUnions\":[{\"__ref\":\"Episode:1\"}],\"chapter\":null},"
			+ tocChapter("20", "2") + ","
			+ "\"Work:100\":{\"firstPublicEpisodeUnion\":{\"__ref\":\"Episode:1\"}},"
			+ chapter("20", "第一章")
			+ "}";
		HashMap<String, String> map = invoke(json);
		assertNull(map.get("1"));
		assertEquals("第一章", map.get("2"));
	}

	// ---------------------------------------------------------------
	// 章題末尾のタブ (エスケープ "\t") は落とす
	// ---------------------------------------------------------------
	@Test
	public void trailingEscapedTab_isStripped() throws Exception {
		String json = "{"
			+ tocChapter("10", "1") + ","
			+ chapter("10", "最終章　白き王\\t")
			+ "}";
		assertEquals("最終章　白き王", invoke(json).get("1"));
	}

	// ---------------------------------------------------------------
	// 章題の括弧・エスケープした引用符・\\uXXXX で、項目の範囲も章題も切れない
	// ---------------------------------------------------------------
	@Test
	public void titlesWithBracesQuotesAndUnicodeEscapes() throws Exception {
		String json = "{"
			+ "\"TableOfContentsChapter:10\":{\"__typename\":\"TableOfContentsChapter\",\"id\":\"10\","
			+ "\"note\":\"} {\\\"\","
			+ "\"episodeUnions\":[{\"__ref\":\"Episode:1\"}],\"chapter\":{\"__ref\":\"Chapter:10\"}},"
			+ episode("2", "第2話") + ","
			+ tocChapter("20", "2") + ","
			+ chapter("10", "第一章 {序} \\\"再会\\\" A\\u0026B \\\\n") + ","
			+ chapter("20", "第二章")
			+ "}";
		HashMap<String, String> map = invoke(json);
		assertEquals("第一章 {序} \"再会\" A&B \\n", map.get("1"));
		assertEquals("第二章", map.get("2"));
	}

	// ---------------------------------------------------------------
	// 4 桁の 16 進でない \\u はそのまま残す
	// ---------------------------------------------------------------
	@Test
	public void invalidUnicodeEscape_isKept() throws Exception {
		String json = "{"
			+ tocChapter("10", "1") + ","
			+ chapter("10", "章\\u+041\\u12")
			+ "}";
		assertEquals("章\\u+041\\u12", invoke(json).get("1"));
	}

	// ---------------------------------------------------------------
	// 章なし作品は空マップ
	// ---------------------------------------------------------------
	@Test
	public void noTocChapter_returnsEmpty() throws Exception {
		String json = "{" + episode("1", "第1話") + "}";
		assertTrue(invoke(json).isEmpty());
	}
}
