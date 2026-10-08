package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 目次が複数ページ（101 話以上）のとき、各話の日付が全ページから、その話の位置に付くことを、変換の入口から確かめる。
 *
 * 修正前は 1 ページ目の分しか日付を取らず、2 ページ目以降の話に日付が付かなかった（internal #14）。
 * また日付は目次の要素の数で並べて、話の URL の一覧の番号で読むので、リンクの空の要素があると後ろの話の日付がずれる。
 * この升の 1 ページ目には、日付だけあってリンクの空の要素を置いてある。
 */
public class WebAozoraConverterTocPageDatesTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (registeredFqdn != null) WebAozoraConverter.converters.remove(registeredFqdn);
	}

	private static void respond(HttpExchange exchange, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static String li(String href, String title, String date) {
		return "<li><a href=\"" + href + "\">" + title + "</a><span class=\"date\">" + date + "</span></li>";
	}

	/** 目次 2 ページ（1 ページ目: 1 話・リンクの空の要素・2 話、2 ページ目: 3 話・4 話）と 4 話 */
	private String serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			String query = exchange.getRequestURI().getQuery();
			if (path.equals("/novel/") && "p=2".equals(query)) {
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ li("/ep/3/", "第3話", "2026/01/03") + li("/ep/4/", "第4話", "2026/01/04")
					+ "</ul><a class=\"pager\" href=\"/novel/?p=2\">最後</a></body></html>");
			} else if (path.equals("/novel/")) {
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ li("/ep/1/", "第1話", "2026/01/01") + li("", "（リンクの無い行）", "2026/09/99") + li("/ep/2/", "第2話", "2026/01/02")
					+ "</ul><a class=\"pager\" href=\"/novel/?p=2\">最後</a></body></html>");
			} else if (path.startsWith("/ep/")) {
				String n = path.replaceAll("\\D", "");
				respond(exchange, "<html><body><h2>第" + n + "話</h2><div class=\"body\"><p>本文" + n + "</p></div></body></html>");
			} else {
				respond(exchange, "<html></html>");
			}
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private WebAozoraConverter siteConverterFor(String base) throws IOException {
		String fqdn = base.substring(base.indexOf("//") + 2);
		File web = tempFolder.newFolder("web");
		File siteDir = new File(web, fqdn);
		//Windows はフォルダ名に : を使えない
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0",
			"AUTHOR\t.author:0",
			"HREF\t.list a",
			"PAGE_URL\ta.pager:-1\t(\\?p=)\\d+\\t(\\d+)\t$1$2",
			"CONTENT_UPDATE_LIST\t.list .date",
			"CONTENT_SUBTITLE\th2:0",
			"CONTENT_ARTICLE\t.body:0",
			"").getBytes(StandardCharsets.UTF_8));
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(base + "/novel/", web);
		assertNotNull("サイト定義を読めない", converter);
		registeredFqdn = fqdn;
		return converter;
	}

	@Test
	public void everyEpisodeGetsItsOwnDateAcrossTocPages() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		converter.getFormatSettings().setShowPostDate(true);
		File txt = converter.convertToAozoraText(base + "/novel/", tempFolder.newFolder("cache"), 0, 0f, false, false, false, 0);
		assertNotNull(txt);

		// 話ごとに「本文 n の前にある日付」を拾う（数字は漢数字に直されて出る）
		String text = new String(Files.readAllBytes(txt.toPath()), StandardCharsets.UTF_8);
		Matcher m = Pattern.compile("([〇一二三四五六七八九/]+) 更新[\\s\\S]*?本文([一二三四])").matcher(text);
		List<String> found = new ArrayList<>();
		while (m.find()) found.add(m.group(2) + ":" + m.group(1));
		assertEquals(text, Arrays.asList("一:二〇二六/〇一/〇一", "二:二〇二六/〇一/〇二", "三:二〇二六/〇一/〇三", "四:二〇二六/〇一/〇四"), found);
	}

	@Test
	public void alignedToElementsPadsAndCuts() {
		assertEquals(Arrays.asList("a", "b", null), WebAozoraConverter.alignedToElements(new String[] {"a", "b"}, 3));
		assertEquals(Arrays.asList("a"), WebAozoraConverter.alignedToElements(new String[] {"a", "b"}, 1));
		assertEquals(Arrays.asList(null, null), WebAozoraConverter.alignedToElements(null, 2));
	}
}
