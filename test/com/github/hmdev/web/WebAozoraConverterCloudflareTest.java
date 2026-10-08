package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Cloudflare のボット確認画面（403 + cf-mitigated: challenge）で止められたときのテスト
 * （ハーメルンが 2026-10 にこうなった）。
 * - 理由の分かる文言で失敗し、確認画面をキャッシュに書かない
 * - コードだけの 403 とは区別する
 * - 止められたと分かったら、残りの話を取りに行き続けない。1 話も取れなければ本を作らない
 */
public class WebAozoraConverterCloudflareTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String registeredFqdn;
	private final AtomicInteger episodeRequests = new AtomicInteger();
	/** 第1話だけ 200 のエラーページ（本文なし）を返す */
	private boolean firstEpisodeIsErrorPage = false;
	private WebAozoraConverter shared;
	private String sharedBaseUri;

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		//既存の定義の変換器は static に共有されるので、印と baseUri を戻す
		if (shared != null) {
			shared.blockedByChallenge = false;
			shared.baseUri = sharedBaseUri;
		}
		//createWebAozoraConverter が静的な表に登録した手元のサーバ用の変換器を外す
		if (registeredFqdn != null) WebAozoraConverter.converters.remove(registeredFqdn);
	}

	private static void respond(HttpExchange exchange, int status, String html, boolean challenge) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		if (challenge) exchange.getResponseHeaders().add("cf-mitigated", "challenge");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	/** 一覧は 200、各話は 403（challenge なら確認画面）を返すサーバ。戻り値は "http://127.0.0.1:port" */
	private String serve(boolean challenge) throws IOException {
		//IPv6 を優先する環境でも URL と食い違わないよう、127.0.0.1 に明示して待ち受ける
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/novel/", exchange -> respond(exchange, 200,
			"<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">"
			+ "<li><a href=\"/ep/1.html\">第1話</a></li><li><a href=\"/ep/2.html\">第2話</a></li>"
			+ "<li><a href=\"/ep/3.html\">第3話</a></li></ul></body></html>", false));
		server.createContext("/ep/", exchange -> {
			episodeRequests.incrementAndGet();
			if (firstEpisodeIsErrorPage && exchange.getRequestURI().getPath().endsWith("/1.html")) {
				respond(exchange, 200, "<html><body><p>エラーが発生しました</p></body></html>", false);
				return;
			}
			respond(exchange, 403, "<html><title>Just a moment...</title></html>", challenge);
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** cacheFile を呼ぶだけの升は、既存のサイト定義の変換器で足りる（どの OS でも走る） */
	private WebAozoraConverter anyConverter() throws IOException {
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(
			"https://ncode.syosetu.com/n0000xx/", new File("web"));
		assertNotNull(converter);
		shared = converter;
		sharedBaseUri = converter.baseUri;
		return converter;
	}

	/** 手元のサーバ用のサイト定義（web/<ホスト:ポート>/extract.txt）を作って変換器を得る */
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

	private String failureMessage(WebAozoraConverter converter, String url, File cache) throws Exception {
		Method cacheFile = WebAozoraConverter.class.getDeclaredMethod("cacheFile", String.class, File.class, String.class);
		cacheFile.setAccessible(true);
		try {
			cacheFile.invoke(converter, url, cache, null);
		} catch (InvocationTargetException e) {
			assertTrue(e.getCause() instanceof IOException);
			return (e.getCause() instanceof CloudflareChallengeException ? "[challenge] " : "") + e.getCause().getMessage();
		}
		throw new AssertionError("403 なのに失敗しなかった");
	}

	@Test
	public void cloudflareChallengeIsExplainedAndNotCached() throws Exception {
		String base = serve(true);
		File cache = new File(tempFolder.getRoot(), "page.html");
		String message = failureMessage(anyConverter(), base + "/ep/1.html", cache);
		assertTrue(message, message.startsWith("[challenge] "));
		assertTrue(message, message.contains("Cloudflare の確認画面"));
		assertTrue(message, message.contains("HTTP 403"));
		assertFalse("確認画面をキャッシュに書かない", cache.exists());
	}

	@Test
	public void plain403KeepsTheOldMessage() throws Exception {
		String base = serve(false);
		String message = failureMessage(anyConverter(), base + "/ep/1.html", new File(tempFolder.getRoot(), "page.html"));
		assertFalse(message, message.startsWith("[challenge] "));
		assertTrue(message, message.contains("Server returned HTTP response code: 403"));
	}

	/** 止められたら残りの話（と再ダウンロード）を取りに行かず、1 話も取れなければ本を作らない */
	@Test
	public void stopsRequestingEpisodesAndFailsWhenNothingWasFetched() throws Exception {
		String base = serve(true);
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertEquals("話のページへのリクエストは最初の 1 回だけ", 1, episodeRequests.get());
		assertNull("1 話も取れなければ本を作らない（失敗にする）", txt);
	}

	/** 別のホスト（CDN の表紙・挿絵）で止められても、サイトへの取得は止めない。同じホストなら止める */
	@Test
	public void onlyASameSiteChallengeBlocksTheSite() throws Exception {
		String base = serve(true);
		WebAozoraConverter converter = anyConverter();
		converter.baseUri = "https://ncode.syosetu.com";
		failureMessage(converter, base + "/ep/1.html", new File(tempFolder.getRoot(), "a.html"));
		assertFalse("別のホストの確認画面では止めない", converter.blockedByChallenge);
		converter.baseUri = base;
		failureMessage(converter, base + "/ep/2.html", new File(tempFolder.getRoot(), "b.html"));
		assertTrue("サイトのホストの確認画面では止める", converter.blockedByChallenge);
	}

	/** 「最新 1 話」だけを変換する設定で、その話が取れなければ失敗にする（目次全体と比べない） */
	@Test
	public void failsWhenTheSelectedLatestEpisodeWasNotFetched() throws Exception {
		String base = serve(true);
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 1);
		assertNull(txt);
	}

	/** 止められた後、本文の無いキャッシュ（エラーページ）は話として数えない */
	@Test
	public void anErrorPageInTheCacheIsNotAnEpisode() throws Exception {
		firstEpisodeIsErrorPage = true;
		String base = serve(true);
		WebAozoraConverter converter = siteConverterFor(base);
		File cache = tempFolder.newFolder("cache");
		File txt = converter.convertToAozoraText(base + "/novel/", cache, 0, 0f, false, false, false, 0);
		assertNull(txt);
	}
}
