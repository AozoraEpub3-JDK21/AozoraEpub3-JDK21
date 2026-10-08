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
	/** 2 話目の取得を、最初の 1 回だけ失敗させる */
	private volatile boolean ep2FailsOnce = false;
	/** 2 話目を取りに来たら、変換をキャンセルする */
	private volatile WebAozoraConverter cancelOnEp2 = null;

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
				if (cancelOnEp2 != null) cancelOnEp2.canceled = true;
				if (ep2FailsOnce) { ep2FailsOnce = false; respond(exchange, 500, "error"); return; }
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

	/** 途中で止まっても（キャンセル・例外）、取れた話までは更新情報に残し、次の変換で取り直さない */
	@Test
	public void episodesFetchedBeforeACancelAreNotFetchedAgain() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");

		// 初めての変換で、1 話目を取ったあと 2 話目でキャンセルされる
		cancelOnEp2 = converter;
		converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		cancelOnEp2 = null;
		assertEquals(1, count("/ep/1/"));

		// 次の変換: 1 話目は取り直さない（取れた話は記録に残っている）
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals("止まる前に取れた 1 話目は取り直さない", 1, count("/ep/1/"));
	}

	/** 最初の取得で失敗して、後の再試行で取れた話は、次の変換で取り直さない */
	@Test
	public void anEpisodeFetchedOnRetryIsNotFetchedAgain() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");

		ep2FailsOnce = true;
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNotNull(txt);
		assertEquals("最初の取得で失敗し、変換の再試行で取れる", 2, count("/ep/2/"));

		converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertEquals("再試行で取れた話は、次の変換で取り直さない", 2, count("/ep/2/"));
	}

	/** 「更新分のみ変換」で改稿を取り損ねたら、「更新なし」（スキップ）ではなく失敗として返す */
	@Test
	public void aFailedRevisionIsNotReportedAsNoUpdate() throws Exception {
		String base = serve();
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");
		assertNotNull(converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0));

		ep2Date = "2026/02/02";
		ep2Fails = true;
		// 取ったばかりのキャッシュを「指定時間内に取った＝更新あり」と数えないよう、時間の窓は 0 にする
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, true, false, false, 0);
		assertEquals(null, txt);
		assertTrue("取り損ねたのを「更新なし」にしない（GUI がスキップと出さない）", converter.isUpdated());
	}
}
