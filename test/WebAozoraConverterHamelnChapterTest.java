import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.github.hmdev.web.WebAozoraConverter;

/**
 * ハーメルン章サポート — buildEpisodeChapterMapFromTocTable のユニットテスト
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterHamelnChapterTest
 */
public class WebAozoraConverterHamelnChapterTest {

	private static final String BASE_URI  = "https://novel.syosetu.org";
	private static final String LIST_BASE = "https://novel.syosetu.org/novel/12345/";

	private Method buildMethod;
	private WebAozoraConverter converter;

	@Before
	public void setUp() throws Exception {
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://novel.syosetu.org/12345/", new File("web"));
		// baseUri は convertToAozoraText 内で設定されるため、テスト用にリフレクションでセット
		var baseUriField = WebAozoraConverter.class.getDeclaredField("baseUri");
		baseUriField.setAccessible(true);
		baseUriField.set(converter, BASE_URI);

		buildMethod = WebAozoraConverter.class.getDeclaredMethod(
			"buildEpisodeChapterMapFromTocTable", Document.class, String.class);
		buildMethod.setAccessible(true);
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, String> invoke(Document doc) throws Exception {
		return (HashMap<String, String>) buildMethod.invoke(converter, doc, LIST_BASE);
	}

	// ---------------------------------------------------------------
	// テスト1: 2章3話の正常ケース
	// ---------------------------------------------------------------
	@Test
	public void testBuildChapterMap_withChapters() throws Exception {
		String html = "<html><body>"
			+ "<div id=\"maind\"><div class=\"ss\"><table>"
			+ "<tr><td colspan=\"2\"><strong>第一章 始まり</strong></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/1.html\">第1話</a></td><td><nobr>2026/01/01</nobr></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/2.html\">第2話</a></td><td><nobr>2026/01/02</nobr></td></tr>"
			+ "<tr><td colspan=\"2\"><strong>第二章 展開</strong></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/3.html\">第3話</a></td><td><nobr>2026/01/03</nobr></td></tr>"
			+ "</table></div></div>"
			+ "</body></html>";
		Document doc = Jsoup.parse(html, BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("マップにエピソード3件", 3, map.size());
		assertEquals("第一章 始まり", map.get(BASE_URI + "/novel/12345/1.html"));
		assertEquals("第一章 始まり", map.get(BASE_URI + "/novel/12345/2.html"));
		assertEquals("第二章 展開",   map.get(BASE_URI + "/novel/12345/3.html"));
	}

	// ---------------------------------------------------------------
	// テスト2: 章区切りなし → 空マップ
	// ---------------------------------------------------------------
	@Test
	public void testBuildChapterMap_noChapters() throws Exception {
		String html = "<html><body>"
			+ "<div id=\"maind\"><div class=\"ss\"><table>"
			+ "<tr><td><a href=\"/novel/12345/1.html\">第1話</a></td><td><nobr>2026/01/01</nobr></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/2.html\">第2話</a></td><td><nobr>2026/01/02</nobr></td></tr>"
			+ "</table></div></div>"
			+ "</body></html>";
		Document doc = Jsoup.parse(html, BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertTrue("章なし → 空マップ", map.isEmpty());
	}

	// ---------------------------------------------------------------
	// テスト3: 章より前にエピソードあり（プロローグ）→ マップに含まれない
	// ---------------------------------------------------------------
	@Test
	public void testBuildChapterMap_prologueBeforeChapter() throws Exception {
		String html = "<html><body>"
			+ "<div id=\"maind\"><div class=\"ss\"><table>"
			+ "<tr><td><a href=\"/novel/12345/0.html\">プロローグ</a></td><td></td></tr>"
			+ "<tr><td colspan=\"2\"><strong>第一章</strong></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/1.html\">第1話</a></td><td></td></tr>"
			+ "</table></div></div>"
			+ "</body></html>";
		Document doc = Jsoup.parse(html, BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("章ありエピソード1件のみ", 1, map.size());
		assertFalse("プロローグはマップに含まれない", map.containsKey(BASE_URI + "/novel/12345/0.html"));
		assertEquals("第一章", map.get(BASE_URI + "/novel/12345/1.html"));
	}

	// ---------------------------------------------------------------
	// テスト4: 相対パス → baseUri + href でフルURL構築
	// ---------------------------------------------------------------
	@Test
	public void testBuildChapterMap_relativeUrls() throws Exception {
		String html = "<html><body>"
			+ "<div id=\"maind\"><div class=\"ss\"><table>"
			+ "<tr><td colspan=\"2\"><strong>第一章</strong></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/1.html\">第1話</a></td><td></td></tr>"
			+ "</table></div></div>"
			+ "</body></html>";
		Document doc = Jsoup.parse(html, BASE_URI);
		HashMap<String, String> map = invoke(doc);

		String expected = BASE_URI + "/novel/12345/1.html";
		assertTrue("フルURLがキーになる", map.containsKey(expected));
		assertEquals("第一章", map.get(expected));
	}

	// ---------------------------------------------------------------
	// テスト5: nextDataEpisodeChapterMap フィールドのリフレクション確認
	// 注: line 1006-1007 のフォールバック動作自体は統合テスト (HamelnE2ETest) で検証する
	// ---------------------------------------------------------------
	@Test
	public void testMapFieldCanBeSetViaReflection() throws Exception {
		// nextDataEpisodeChapterMap フィールドに直接セット
		var field = WebAozoraConverter.class.getDeclaredField("nextDataEpisodeChapterMap");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		HashMap<String, String> map = new HashMap<>();
		map.put("https://novel.syosetu.org/novel/12345/1.html", "テスト章");
		field.set(converter, map);

		// フィールドが正しくセットされたことを確認
		@SuppressWarnings("unchecked")
		HashMap<String, String> retrieved = (HashMap<String, String>) field.get(converter);
		assertNotNull("マップがセットされている", retrieved);
		assertEquals("テスト章", retrieved.get("https://novel.syosetu.org/novel/12345/1.html"));
	}
}
