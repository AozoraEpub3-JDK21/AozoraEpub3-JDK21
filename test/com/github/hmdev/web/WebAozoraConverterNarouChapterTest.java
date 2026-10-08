package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Assume;
import org.junit.Test;

import com.github.hmdev.web.ExtractInfo.ExtractId;

/**
 * なろう・ノクターンの各話のページから章名を取る規則（CONTENT_CHAPTER）のテスト。
 *
 * 章名は目次のページの .p-eplist__chapter-title にしか無く、各話のページでは、
 * お知らせの欄（.c-announce--note）でない .c-announce の直下の、クラスの無い span にある（2026-10 の作り）。
 * ノクターンでは同じ所に &lt;span class="c-announce__emphasis"&gt;＜R18＞&lt;/span&gt; があり、それは章名ではない。
 *
 * ページには、ゆるい規則（.c-announce span・span:not([class]) など）なら拾ってしまう飾りの無い span を
 * わざと置き（お知らせの欄の中・.c-announce の中の入れ子・ページの頭）、規則がそれらを拾わないことも確かめる。
 */
public class WebAozoraConverterNarouChapterTest {

	private static final String[] SITES = { "ncode.syosetu.com", "novel18.syosetu.com" };

	/** web/ を探す（作業ディレクトリに依らない）。見つからなければスキップ */
	private static File webConfigPath() {
		Path here = Paths.get(".").toAbsolutePath().normalize();
		while (here != null && !Files.isDirectory(here.resolve("web"))) here = here.getParent();
		Assume.assumeTrue("web/ が見つからない", here != null);
		return here.resolve("web").toFile();
	}

	/** 各話のページの頭（2026-10 の実物の形を縮めたもの） */
	private static Document episode(String emphasis, String chapter) {
		return Jsoup.parse("<html><body>"
			+ "<header><span>飾りの無い span（ページの頭）</span></header>"
			+ "<div class=\"c-announce-box\">"
			+ "<div class=\"c-announce c-announce--note\"><span>お知らせの欄の span</span>"
			+ "ブックマーク機能を使うには<a class=\"c-announce__emphasis c-announce__emphasis--strong\">ログイン</a>してください。</div>"
			+ "<div class=\"c-announce\">"
			+ (emphasis == null ? "" : "<span class=\"c-announce__emphasis\">" + emphasis + "</span>&nbsp;&nbsp;")
			+ "<a href=\"/n0000xx/\">作品名</a>&nbsp;&nbsp;作者：<a href=\"/x/\">作者</a>"
			+ "<p><span>入れ子の span</span></p><br>"
			+ (chapter == null ? "" : "<span>" + chapter + "</span>")
			+ "<br><br></div></div>"
			+ "<h1 class=\"p-novel__title p-novel__title--rensai\">第1話</h1>"
			+ "<div class=\"js-novel-text p-novel__text\"><p>本文</p></div></body></html>",
			"https://ncode.syosetu.com/n0000xx/1/");
	}

	/** 静的な表（createWebAozoraConverter）を通さず、web/ の規則を読んだ変換器で当てる */
	private static String chapterOf(String fqdn, Document doc) throws Exception {
		WebAozoraConverter converter = new WebAozoraConverter(fqdn, webConfigPath());
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
			// 章の無い作品では、お知らせの欄・入れ子・ページの頭の飾りの無い span を拾わない
			assertNull(site, chapterOf(site, episode(null, null)));
			// ノクターンの ＜R18＞ を章名として拾わない
			assertNull(site, chapterOf(site, episode("＜R18＞", null)));
		}
	}
}
