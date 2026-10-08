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

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 題の長い作品の txt を、Linux でも作れることのテスト（internal #16）。Linux では 255 バイトに切り、mac・Windows では名前を変えない。
 * 修正前は ubuntu で「File name too long」になっていた（mac・Windows は文字数で数えるので通る）。
 */
public class WebAozoraConverterLongFileNameTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;

	private static final String LONG_TITLE = "【書籍化】" + "長い題".repeat(35);

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (registeredFqdn != null) WebAozoraConverter.converters.remove(registeredFqdn);
	}

	private void respond(HttpExchange exchange, int status, String html) throws IOException {
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
				respond(exchange, 200, "<html><body><h1>" + LONG_TITLE + "</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ "<li><a href=\"/ep/1/\">第1話</a></li></ul></body></html>");
			} else if (path.equals("/ep/1/")) {
				respond(exchange, 200, "<html><body><h2>第1話</h2><div class=\"body\"><p>一話目</p></div></body></html>");
			} else {
				respond(exchange, 404, "not found");
			}
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Test
	public void aLongTitleMakesATxtWithin255Bytes() throws Exception {
		String base = serve();
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
		assertNotNull(converter);
		registeredFqdn = fqdn;

		File txt = converter.convertToAozoraText(base + "/novel/", tempFolder.newFolder("cache"), 0, 0f, false, false, false, 0);
		assertNotNull("変換できる", txt);
		assertTrue(txt.isFile());
		if (com.github.hmdev.util.PathUtilsFitFileNameTest.acceptsLongNames(tempFolder.getRoot())) {
			assertEquals("受け付ける場所（mac・Windows）では今までの名前のまま", "[著者] " + LONG_TITLE + ".txt", txt.getName());
		} else {
			int bytes = txt.getName().getBytes(StandardCharsets.UTF_8).length;
			assertTrue("名前は 255 バイト以内: " + bytes, bytes <= 255);
			assertTrue(txt.getName(), txt.getName().matches("\\[著者\\] 【書籍化】長い題.*~[0-9a-f]{6}\\.txt"));
		}
	}
}
