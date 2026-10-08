import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.github.hmdev.web.NarouFormatSettings;
import com.github.hmdev.web.WebAozoraConverter;

/**
 * 章が変わったときの章中表紙と、続く第1話の改ページのテスト
 *
 * 左右中央の章中表紙の後に改ページが無いと、続く第1話まで左右中央の節に入る
 * （narou.rb は章題の直後に［＃改ページ］を出す）。
 * 改ページは第1話の側で書くので、本文が取れない話では改ページも出ない。
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterChapterHeaderTest
 */
public class WebAozoraConverterChapterHeaderTest {

	private static final String EPISODE_HTML = "<html><body>"
		+ "<h1 class=\"p-novel__title p-novel__title--rensai\">第1話 はじまり</h1>"
		+ "<div class=\"js-novel-text p-novel__text\"><p id=\"L1\">本文の一行目。</p></div>"
		+ "</body></html>";
	private static final String EMPTY_EPISODE_HTML = "<html><body><p>エラー</p></body></html>";

	private WebAozoraConverter converter;
	private NarouFormatSettings settings;
	private Field bookTitleField;
	private boolean savedCenterPage;
	private boolean savedHashira;
	private Object savedBookTitle;
	private Method printChapterHeader;
	private Method episodeSharesChapterHeaderPage;
	private Method docToAozoraText;

	@Before
	public void setUp() throws Exception {
		// converter はサイトごとに static に共有されるので、変えた設定は @After で戻す
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://ncode.syosetu.com/n0000xx/", new File("web"));
		assertNotNull(converter);
		settings = converter.getFormatSettings();
		savedCenterPage = settings.isChapterUseCenterPage();
		savedHashira = settings.isChapterUseHashira();

		bookTitleField = WebAozoraConverter.class.getDeclaredField("bookTitle");
		bookTitleField.setAccessible(true);
		savedBookTitle = bookTitleField.get(converter);
		bookTitleField.set(converter, "本の題");

		printChapterHeader = WebAozoraConverter.class.getDeclaredMethod(
			"printChapterHeader", BufferedWriter.class, String.class);
		printChapterHeader.setAccessible(true);
		episodeSharesChapterHeaderPage = WebAozoraConverter.class.getDeclaredMethod(
			"episodeSharesChapterHeaderPage", boolean.class);
		episodeSharesChapterHeaderPage.setAccessible(true);
		docToAozoraText = WebAozoraConverter.class.getDeclaredMethod(
			"docToAozoraText", BufferedWriter.class, Document.class, boolean.class,
			String.class, String.class, String.class);
		docToAozoraText.setAccessible(true);
	}

	@After
	public void tearDown() throws Exception {
		settings.setChapterUseCenterPage(savedCenterPage);
		settings.setChapterUseHashira(savedHashira);
		bookTitleField.set(converter, savedBookTitle);
	}

	/** 章が変わった話を、変換の本体と同じ順で出力する（章中表紙 → 第1話） */
	private String chapterStart(String chapterTitle, String episodeHtml) throws Exception {
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			printChapterHeader.invoke(converter, bw, chapterTitle);
			boolean shares = (Boolean) episodeSharesChapterHeaderPage.invoke(converter, true);
			Document doc = Jsoup.parse(episodeHtml, "https://ncode.syosetu.com/n0000xx/1/");
			docToAozoraText.invoke(converter, bw, doc, shares, null, null, null);
		}
		return sw.toString();
	}

	private static int count(String s, String sub) {
		int n = 0;
		for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
		return n;
	}

	@Test
	public void centerPageChapterTitleIsFollowedByPageBreakBeforeFirstEpisode() throws Exception {
		settings.setChapterUseCenterPage(true);
		String out = chapterStart("第一章", EPISODE_HTML);

		assertEquals("改ページは章中表紙の前と第1話の前の 2 つ: " + out, 2, count(out, "［＃改ページ］"));
		int center = out.indexOf("［＃ページの左右中央］");
		int chapterEnd = out.indexOf("［＃大見出し終わり］");
		int secondBreak = out.lastIndexOf("［＃改ページ］");
		int episodeTitle = out.indexOf("第1話 はじまり");
		int body = out.indexOf("本文の一行目。");
		assertTrue("左右中央の注記がある: " + out, center > 0);
		assertTrue("左右中央 → 章題 → 改ページ → 第1話の題 → 本文の順: " + out,
			center < chapterEnd && chapterEnd < secondBreak && secondBreak < episodeTitle && episodeTitle < body);
	}

	@Test
	public void withoutCenterPageFirstEpisodeFollowsChapterTitleOnSamePage() throws Exception {
		settings.setChapterUseCenterPage(false);
		String out = chapterStart("第一章", EPISODE_HTML);

		assertFalse("左右中央の注記は無い: " + out, out.contains("［＃ページの左右中央］"));
		assertEquals("改ページは章題の前の 1 つだけ: " + out, 1, count(out, "［＃改ページ］"));
		assertTrue("章題 → 第1話の題の順: " + out,
			out.indexOf("［＃大見出し終わり］") < out.indexOf("第1話 はじまり"));
	}

	@Test
	public void emptyFirstEpisodeLeavesNoDanglingPageBreak() throws Exception {
		settings.setChapterUseCenterPage(true);
		String out = chapterStart("第一章", EMPTY_EPISODE_HTML);

		assertEquals("本文が取れない第1話は改ページも出さない: " + out, 1, count(out, "［＃改ページ］"));
		assertTrue("章中表紙の改ページは先頭: " + out, out.startsWith("\n［＃改ページ］\n"));
	}

	@Test
	public void hashiraAndTitleAreWrittenOnTheCenterPage() throws Exception {
		settings.setChapterUseCenterPage(true);
		settings.setChapterUseHashira(true);
		String out = chapterStart("第二章 展開", EPISODE_HTML);

		int hashira = out.indexOf("［＃ここから柱］");
		int title = out.indexOf("第二章");
		int secondBreak = out.lastIndexOf("［＃改ページ］");
		assertTrue("柱がある: " + out, hashira > 0);
		assertTrue("本の題が柱に出る: " + out, out.contains("本の題"));
		assertTrue("柱 → 章題 → 改ページの順: " + out, hashira < title && title < secondBreak);
	}
}
