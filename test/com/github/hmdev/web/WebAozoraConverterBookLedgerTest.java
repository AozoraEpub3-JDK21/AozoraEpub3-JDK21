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

import com.github.hmdev.info.BookLedger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Web から取った作品に台帳ができ、掲載先で題が変わっても txt の名前と identifier が変わらないことのテスト（internal #11）。
 *
 * 修正前は、txt・EPUB の名前と identifier を題と作者から作っていたので、「【書籍化】」が付くと別の本として増えていた。
 */
public class WebAozoraConverterBookLedgerTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;
	private volatile String title = "題";

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
				respond(exchange, 200, "<html><body><h1>" + title + "</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ "<li><a href=\"/ep/1/\">第1話</a></li>"
					+ "</ul></body></html>");
			} else if (path.equals("/ep/1/")) {
				respond(exchange, 200, "<html><body><h2>第1話</h2><div class=\"body\"><p>一話目</p></div></body></html>");
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
			"CONTENT_SUBTITLE\th2:0",
			"CONTENT_ARTICLE\t.body:0",
			"").getBytes(StandardCharsets.UTF_8));
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(base + "/novel/", web);
		assertNotNull("サイト定義を読めない", converter);
		registeredFqdn = fqdn;
		return converter;
	}

	@Test
	public void theFirstDownloadWritesTheLedger() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");

		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals("[著者] 題.txt", txt.getName());

		BookLedger ledger = BookLedger.load(txt.getParentFile());
		assertNotNull("txt の隣に台帳ができる", ledger);
		assertEquals(base + "/novel/", ledger.sourceUrl);
		assertEquals(BookLedger.identifierFor(base + "/novel/"), ledger.identifier);
		assertEquals("[著者] 題", ledger.outputBaseName);
	}

	@Test
	public void aRenamedWorkKeepsItsFileNameAndIdentifier() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");

		File first = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(first);
		BookLedger before = BookLedger.load(first.getParentFile());

		// 掲載先で題が変わった
		title = "【書籍化】題";
		File second = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(second);
		assertEquals("題が変わっても txt は同じ名前", first.getAbsolutePath(), second.getAbsolutePath());
		BookLedger after = BookLedger.load(second.getParentFile());
		assertEquals(before.identifier, after.identifier);
		assertEquals("[著者] 題", after.outputBaseName);
		// 本の中の題は掲載先に合わせる
		String text = new String(Files.readAllBytes(second.toPath()), StandardCharsets.UTF_8);
		assertTrue("新しい題が本文の表題に入る: " + text, text.split("\n", 2)[0].endsWith("【書籍化】題"));
		// 古い名前の txt が別に増えていない
		File[] txts = second.getParentFile().listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"));
		assertEquals(1, txts.length);
	}
}
