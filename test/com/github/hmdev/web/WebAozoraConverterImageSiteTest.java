package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
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
 * 挿絵の相対 src を、その挿絵のある話のページの URL で解決することを、変換の入口から確かめる。
 *
 * 修正前は、基準の URL を話の URL から誤って切り出したうえ、変換のループでは最後に取った話の基準が
 * 全話に使われていた。各ページを、そのページの URL を基準として Jsoup.parse に渡して読む形に直した。
 * その読み込みの箇所を戻すと、この升は赤になる。
 */
public class WebAozoraConverterImageSiteTest {

	/** 1×1 の PNG */
	private static final byte[] PNG = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;
	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (registeredFqdn != null) WebAozoraConverter.converters.remove(registeredFqdn);
	}

	private void respond(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
		requests.computeIfAbsent(exchange.getRequestURI().getPath(), k -> new AtomicInteger()).incrementAndGet();
		exchange.getResponseHeaders().add("Content-Type", type);
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static byte[] html(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/** 一覧は /novel/、2 話は別々のディレクトリ（/a/1/・/b/2/）。どちらの本文も相対の img.png を持つ */
	private String serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/novel/")) {
				respond(exchange, 200, "text/html; charset=UTF-8", html("<html><body><h1>題</h1><p class=\"author\">著者</p>"
					+ "<ul class=\"list\"><li><a href=\"/a/1/\">第1話</a></li><li><a href=\"/b/2/\">第2話</a></li></ul></body></html>"));
			} else if (path.equals("/a/1/")) {
				respond(exchange, 200, "text/html; charset=UTF-8", html("<html><body><h2>第1話</h2>"
					+ "<div class=\"body\"><p>一話目。</p><img src=\"img.png\"></div></body></html>"));
			} else if (path.equals("/b/2/")) {
				respond(exchange, 200, "text/html; charset=UTF-8", html("<html><body><h2>第2話</h2>"
					+ "<div class=\"body\"><p>二話目。</p><img src=\"img.png#x\"></div></body></html>"));
			} else if (path.endsWith("/img.png")) {
				respond(exchange, 200, "image/png", PNG);
			} else {
				respond(exchange, 404, "text/plain", html("not found"));
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
	public void eachEpisodeResolvesItsImageAgainstItsOwnPage() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File txt = converter.convertToAozoraText(base + "/novel/", tempFolder.newFolder("cache"), 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals("1 話目の挿絵は 1 話目のディレクトリから", 1, count("/a/1/img.png"));
		assertEquals("2 話目の挿絵は 2 話目のディレクトリから（#x は外す）", 1, count("/b/2/img.png"));
		assertEquals("一覧や別の話のディレクトリを基準にしない", 0, count("/novel/img.png"));
		assertEquals(0, count("/a/1/img.png#x"));
	}
}
