package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import com.github.hmdev.web.ExtractInfo.ExtractId;

/**
 * なろう・ノクターンの各話のページから章名を取る規則（CONTENT_CHAPTER）のテスト。
 *
 * 章名は目次のページの .p-eplist__chapter-title にしか無く、各話のページでは
 * .c-announce の直下の、クラスの無い span にある（2026-10 の作り）。
 * ノクターンでは同じ所に &lt;span class="c-announce__emphasis"&gt;＜R18＞&lt;/span&gt; があり、
 * それを章名として拾わないこと。
 */
public class WebAozoraConverterNarouChapterTest {

	private static final String[] SITES = {
		"https://ncode.syosetu.com/n0000xx/",
		"https://novel18.syosetu.com/n0000xx/",
	};

	/** 各話のページの .c-announce の部分（2026-10 の実物の形を縮めたもの） */
	private static Document episode(String emphasis, String chapter) {
		return Jsoup.parse("<html><body><div class=\"c-announce-box\">"
			+ "<div class=\"c-announce c-announce--note\">ブックマーク機能を使うには<a class=\"c-announce__emphasis c-announce__emphasis--strong\">ログイン</a>してください。</div>"
			+ "<div class=\"c-announce\">"
			+ (emphasis == null ? "" : "<span class=\"c-announce__emphasis\">" + emphasis + "</span>&nbsp;&nbsp;")
			+ "<a href=\"/n0000xx/\">作品名</a>&nbsp;&nbsp;作者：<a href=\"/x/\">作者</a><br>"
			+ (chapter == null ? "" : "<span>" + chapter + "</span>")
			+ "<br><br></div></div>"
			+ "<h1 class=\"p-novel__title p-novel__title--rensai\">第1話</h1>"
			+ "<div class=\"js-novel-text p-novel__text\"><p>本文</p></div></body></html>",
			"https://ncode.syosetu.com/n0000xx/1/");
	}

	private static String chapterOf(String site, Document doc) throws Exception {
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(site, new File("web"));
		assertNotNull(site, converter);
		return converter.getExtractText(doc, converter.queryMap.get(ExtractId.CONTENT_CHAPTER));
	}

	@Test
	public void chapterNameIsTakenFromTheEpisodePage() throws Exception {
		for (String site : SITES) {
			assertEquals(site, "本編", chapterOf(site, episode(null, "本編")));
			assertEquals(site, "番外編", chapterOf(site, episode("＜R18＞", "番外編")));
		}
	}

	@Test
	public void noChapterWhenTheWorkHasNone() throws Exception {
		for (String site : SITES) {
			assertNull(site, chapterOf(site, episode(null, null)));
			// ノクターンの ＜R18＞ を章名として拾わない
			assertNull(site, chapterOf(site, episode("＜R18＞", null)));
		}
	}
}
