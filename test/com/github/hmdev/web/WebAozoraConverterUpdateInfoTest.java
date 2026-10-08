package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 改稿された話を取り損ねても、次の変換で取り直すことのテスト（internal #13）。
 *
 * 修正前は、更新情報（update.txt）を話を取る前に新しい日付で書いていたので、改稿された話の取得が失敗すると、
 * 次の変換では「更新なし」と判定されて取り直されず、古い本文のまま固定されていた。
 */
public class WebAozoraConverterUpdateInfoTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;
	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
	/** 2 話目の更新日（目次）と本文 */
	private volatile String ep2Date = "2026/01/02";
	private volatile String ep2Body = "二話目の旧版";
	private volatile boolean ep2Fails = false;

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (registeredFqdn != null) WebAozoraConverter.converters.remove(registeredFqdn);
	}

	private void respond(HttpExchange exchange, int status, String html) throws IOException {
		requests.computeIfAbsent(exchange.getRequestURI().getPath(), k -> new AtomicInteger()).incrementAndGet();
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private String serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/novel/")) {
				respond(exchange, 200, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ "<li><a href=\"/ep/1/\">第1話</a><span class=\"up\">2026/01/01</span></li>"
					+ "<li><a href=\"/ep/2/\">第2話</a><span class=\"up\">" + ep2Date + "</span></li>"
					+ "</ul></body></html>");
			} else if (path.equals("/ep/1/")) {
				respond(exchange, 200, "<html><body><h2>第1話</h2><div class=\"body\"><p>一話目</p></div></body></html>");
			} else if (path.equals("/ep/2/")) {
				if (ep2Fails) respond(exchange, 500, "error");
				else respond(exchange, 200, "<html><body><h2>第2話</h2><div class=\"body\"><p>" + ep2Body + "</p></div></body></html>");
			} else {
				respond(exchange, 404, "not found");
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
			"SUB_UPDATE\t.list .up",
			"CONTENT_SUBTITLE\th2:0",
			"CONTENT_ARTICLE\t.body:0",
			"").getBytes(StandardCharsets.UTF_8));
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(base + "/novel/", web);
		assertNotNull("サイト定義を読めない", converter);
		registeredFqdn = fqdn;
		return converter;
	}

	private int count(String path) {
		AtomicInteger n = requests.get(path);
		return n == null ? 0 : n.get();
	}

	@Test
	public void aRevisionThatFailedToDownloadIsFetchedNextTime() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");

		// 1 回目: 全話を取る
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals(1, count("/ep/2/"));

		// 2 回目: 2 話目が改稿された（目次の更新日が変わる）が、取得に失敗する
		ep2Date = "2026/02/02";
		ep2Body = "二話目の改稿版";
		ep2Fails = true;
		converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertEquals("2 回目に改稿を取りに行く", 2, count("/ep/2/"));

		// 3 回目: 取れるようになった。前回取り損ねた改稿をもう一度取りに行き、新しい本文が入る
		ep2Fails = false;
		txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals("取り損ねた改稿を、次の変換でもう一度取りに行く", 3, count("/ep/2/"));
		String text = new String(Files.readAllBytes(txt.toPath()), StandardCharsets.UTF_8);
		assertTrue("改稿した本文が入る: " + text, text.contains("二話目の改稿版"));
		// 改稿していない 1 話目は取り直さない
		assertEquals(1, count("/ep/1/"));
	}
}
