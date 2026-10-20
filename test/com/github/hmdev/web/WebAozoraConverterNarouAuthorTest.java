package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;

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
 * なろう・ノクターンの目次のページから著者を取る規則（AUTHOR）のテスト（internal #61）。
 *
 * 著者にマイページがあれば .p-novel__author の中の a に名前があり、無ければ「作者：名前」が地の文で入る（2026-10 の作り）。
 * ダウンロード・更新は著者をなろう API から取るので、規則が拾わなくても困らない。作り直すときは API に問い合わせず
 * この規則で読むので、リンクの無い著者を拾わないと著者の行が空になり、表題と著者がずれた。
 */
public class WebAozoraConverterNarouAuthorTest {

	private static final String[] SITES = { "ncode.syosetu.com", "novel18.syosetu.com" };

	/** web/ を探す（作業ディレクトリに依らない）。見つからなければスキップ */
	private static File webConfigPath() {
		Path here = Paths.get(".").toAbsolutePath().normalize();
		while (here != null && !Files.isDirectory(here.resolve("web"))) here = here.getParent();
		Assume.assumeTrue("web/ が見つからない", here != null);
		return here.resolve("web").toFile();
	}

	/** 目次のページの頭（2026-10 の実物の形を縮めたもの） */
	private static Document toc(String authorHtml) {
		return Jsoup.parse("<html><body>"
			+ "<div class=\"p-novel__series\"><a class=\"p-novel__series-link\" href=\"/s0000x/\">シリーズ</a></div>"
			+ "<h1 class=\"p-novel__title\">作品の題</h1>\n\n"
			+ "<div class=\"p-novel__author\">" + authorHtml + "</div>\n"
			+ "<div class=\"p-novel__date-published\">最終エピソード掲載日：2022/08/07</div>"
			+ "</body></html>", "https://ncode.syosetu.com/n0000xx/");
	}

	/** 静的な表（createWebAozoraConverter）を通さず、web/ の規則を読んだ変換器で当てる */
	private static String authorOf(String fqdn, Document doc) throws Exception {
		WebAozoraConverter converter = new WebAozoraConverter(fqdn, webConfigPath());
		return converter.getExtractText(doc, converter.queryMap.get(ExtractId.AUTHOR));
	}

	@Test
	public void anAuthorWithoutAMyPageLinkIsTaken() throws Exception {
		for (String site : SITES) assertEquals(site, "メアリー＝ドゥ", authorOf(site, toc("作者：メアリー＝ドゥ\n")).trim());
	}

	@Test
	public void aLinkedAuthorIsStillTakenFromTheLink() throws Exception {
		for (String site : SITES) {
			assertEquals(site, "作者名", authorOf(site, toc("作者：<a href=\"https://mypage.syosetu.com/1/\">作者名</a>\n")).trim());
		}
	}
}
