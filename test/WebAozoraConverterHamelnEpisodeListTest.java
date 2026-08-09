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
 * ハーメルン新形式の話一覧 (&lt;ul class="episode-list__items"&gt;) からの章マッピング。
 *
 * <p>ハーメルンは 2026-08 頃に一覧を &lt;table&gt; から &lt;ul&gt; へ作り替えた。
 * 旧構造のテストは {@link WebAozoraConverterHamelnChapterTest} が持っており、
 * ここでは新構造と「新旧が混在しない」ことだけを見る。</p>
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterHamelnEpisodeListTest
 */
public class WebAozoraConverterHamelnEpisodeListTest {

	private static final String BASE_URI  = "https://novel.syosetu.org";
	private static final String LIST_BASE = "https://novel.syosetu.org/novel/12345/";

	private Method buildMethod;
	private WebAozoraConverter converter;

	@Before
	public void setUp() throws Exception {
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://novel.syosetu.org/12345/", new File("web"));
		var baseUriField = WebAozoraConverter.class.getDeclaredField("baseUri");
		baseUriField.setAccessible(true);
		baseUriField.set(converter, BASE_URI);

		//新旧どちらの構造も受ける入口をテストする
		buildMethod = WebAozoraConverter.class.getDeclaredMethod(
			"buildEpisodeChapterMapFromToc", Document.class, String.class);
		buildMethod.setAccessible(true);
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, String> invoke(Document doc) throws Exception {
		return (HashMap<String, String>) buildMethod.invoke(converter, doc, LIST_BASE);
	}

	/**
	 * 一覧の href からエピソードのフル URL を作る規則を<b>意図的に鏡写しにしたもの</b>。
	 *
	 * <p>本体側 (WebAozoraConverter の「一覧のhrefをすべて取得」ループ、ページネーション、
	 * createNoUpdateUrls、章マッピング) はいずれもこの単純連結でフル URL を作り、
	 * <b>できた文字列同士が一致すること</b>を契約にしている
	 * (章マッピングの引き当てと「更新なし URL」の判定がどちらも文字列一致)。
	 * どれか 1 か所だけ正規化すると、章が付かない・毎回全話を取り直す、が無音で起きる。</p>
	 *
	 * <p>そのためハーメルンの {@code href="./1.html"} は {@code .../12345/./1.html} のまま扱う。
	 * 実サイトはこの URL を 200 で返し (2026-08-09 実測)、キャッシュの実ファイルも
	 * {@code safeResolve} の normalize で正しい場所に落ちるため実害はない。</p>
	 */
	private static String fullUrl(String href)
	{
		if (href.startsWith("http")) return href;
		if (href.charAt(0) == '/') return BASE_URI + href;
		return LIST_BASE + href;
	}

	/** 実サイト (2026-08-09 時点) と同じ形の話一覧 */
	private static String episodeList(String inner)
	{
		return "<html><body><div id=\"maind\"><div class=\"ss\">"
			+ "<section class=\"episode-list\"><ul class=\"episode-list__items\">"
			+ inner
			+ "</ul></section></div></div></body></html>";
	}

	private static String chapter(String title)
	{
		return "<li class=\"episode-list__chapter\">"
			+ "<div class=\"episode-list__chapter-title\">" + title + "</div></li>";
	}

	private static String episode(String href, String title)
	{
		return "<li class=\"episode-list__item\">"
			+ "<a href=\"" + href + "\" class=\"episode-list__link\">"
			+ "<span class=\"episode-list__mark\"></span>"
			+ "<span class=\"episode-list__title\">" + title + "</span>"
			+ "<time class=\"episode-list__date\">2026/08/09 12:00</time>"
			+ "</a></li>";
	}

	@Test
	public void chaptersAreAssignedToTheEpisodesThatFollowThem() throws Exception
	{
		Document doc = Jsoup.parse(episodeList(
			chapter("日本編")
			+ episode("./1.html", "高校～プロ入り")
			+ episode("./2.html", "開幕一軍")
			+ chapter("メジャー編")
			+ episode("./3.html", "海を渡る")), BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("エピソード3件", 3, map.size());
		assertEquals("日本編", map.get(fullUrl("./1.html")));
		assertEquals("日本編", map.get(fullUrl("./2.html")));
		assertEquals("メジャー編", map.get(fullUrl("./3.html")));
	}

	@Test
	public void aWorkWithoutChaptersYieldsAnEmptyMap() throws Exception
	{
		Document doc = Jsoup.parse(episodeList(
			episode("./1.html", "未来への第一歩")
			+ episode("./2.html", "篝火を持つ者")), BASE_URI);

		assertTrue("章なし → 空マップ", invoke(doc).isEmpty());
	}

	@Test
	public void episodesBeforeTheFirstChapterAreNotMapped() throws Exception
	{
		Document doc = Jsoup.parse(episodeList(
			episode("./0.html", "プロローグ")
			+ chapter("第一章")
			+ episode("./1.html", "第1話")), BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("章ありエピソード1件のみ", 1, map.size());
		assertFalse("プロローグは含まれない", map.containsKey(fullUrl("./0.html")));
		assertEquals("第一章", map.get(fullUrl("./1.html")));
	}

	/**
	 * 章マップのキーが、本体の一覧ループと同じ規則で作られていること。
	 * ここが崩れると章の引き当てが無音で外れる (キーは文字列一致でしか照合されない)。
	 */
	@Test
	public void keysFollowTheSameUrlRuleAsTheEpisodeListLoop() throws Exception
	{
		Document doc = Jsoup.parse(episodeList(
			chapter("第一章")
			+ episode("./1.html", "相対")
			+ episode("/novel/12345/2.html", "ルート相対")
			+ episode("https://novel.syosetu.org/novel/12345/3.html", "絶対")), BASE_URI);
		HashMap<String, String> map = invoke(doc);

		for (String href : new String[]{"./1.html", "/novel/12345/2.html",
				"https://novel.syosetu.org/novel/12345/3.html"}) {
			assertTrue("本体と同じ規則のキーで引ける: "+href, map.containsKey(fullUrl(href)));
		}
		//ハーメルンの実際の href は "./N.html"。連結したままなので "/./" が残る
		assertTrue("相対 href は正規化せずに連結する",
			map.containsKey(LIST_BASE + "./1.html"));
	}

	@Test
	public void absoluteAndRootRelativeHrefsBothResolve() throws Exception
	{
		Document doc = Jsoup.parse(episodeList(
			chapter("第一章")
			+ episode("/novel/12345/1.html", "ルート相対")
			+ episode("https://novel.syosetu.org/novel/12345/2.html", "絶対URL")), BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("第一章", map.get(BASE_URI + "/novel/12345/1.html"));
		assertEquals("第一章", map.get(BASE_URI + "/novel/12345/2.html"));
	}

	/**
	 * 旧構造のキャッシュが残っていても読めること。
	 * 新構造が無いときだけ table 側にフォールバックする。
	 */
	@Test
	public void theOldTableMarkupStillWorks() throws Exception
	{
		Document doc = Jsoup.parse("<html><body>"
			+ "<div id=\"maind\"><div class=\"ss\"><table>"
			+ "<tr><td colspan=\"2\"><strong>第一章 始まり</strong></td></tr>"
			+ "<tr><td><a href=\"/novel/12345/1.html\">第1話</a></td><td><nobr>2026/01/01</nobr></td></tr>"
			+ "</table></div></div></body></html>", BASE_URI);
		HashMap<String, String> map = invoke(doc);

		assertEquals("第一章 始まり", map.get(BASE_URI + "/novel/12345/1.html"));
	}
}
