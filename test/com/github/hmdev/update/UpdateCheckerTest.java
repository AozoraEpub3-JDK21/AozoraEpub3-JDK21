package com.github.hmdev.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** {@link UpdateChecker} のバージョン比較・正規化のテスト（ネットワークは使わない） */
public class UpdateCheckerTest
{
	@Test
	public void normalizeStripsPrefixAndBuildSuffix()
	{
		assertEquals("1.5.2", UpdateChecker.normalize("v1.5.2-jdk21"));
		assertEquals("1.5.2", UpdateChecker.normalize("1.5.2"));
		assertEquals("1.5.2", UpdateChecker.normalize("  V1.5.2-JDK21  "));
		assertEquals("1.6", UpdateChecker.normalize("v1.6-beta1"));
	}

	@Test
	public void normalizeFallsBackToZero()
	{
		assertEquals("0", UpdateChecker.normalize(null));
		assertEquals("0", UpdateChecker.normalize(""));
		//"v" だけ / "-jdk21" だけ でも空文字にせず 0 として比較できるようにする
		assertEquals("0", UpdateChecker.normalize("v"));
		assertEquals("0", UpdateChecker.normalize("-jdk21"));
	}

	@Test
	public void newerTagIsGreater()
	{
		assertTrue(UpdateChecker.compareVersions("v1.5.3-jdk21", "1.5.2-jdk21") > 0);
		assertTrue(UpdateChecker.compareVersions("v1.6.0-jdk21", "1.5.9-jdk21") > 0);
		assertTrue(UpdateChecker.compareVersions("v2.0.0-jdk21", "1.99.99-jdk21") > 0);
	}

	@Test
	public void olderTagIsSmaller()
	{
		assertTrue(UpdateChecker.compareVersions("v1.5.1-jdk21", "1.5.2-jdk21") < 0);
		assertTrue(UpdateChecker.compareVersions("v1.4.9-jdk21", "1.5.0-jdk21") < 0);
	}

	@Test
	public void sameVersionIsEqualRegardlessOfBuildSuffix()
	{
		assertEquals(0, UpdateChecker.compareVersions("v1.5.2-jdk21", "1.5.2-jdk21"));
		assertEquals(0, UpdateChecker.compareVersions("v1.5.2", "1.5.2-jdk25"));
	}

	@Test
	public void missingSegmentsCountAsZero()
	{
		//"1.5" と "1.5.0" は同じ。"1.5.1" の方が新しい
		assertEquals(0, UpdateChecker.compareVersions("v1.5", "1.5.0"));
		assertTrue(UpdateChecker.compareVersions("v1.5.1", "1.5") > 0);
		assertTrue(UpdateChecker.compareVersions("v1.5", "1.5.1") < 0);
	}

	@Test
	public void nonNumericSegmentsDoNotThrow()
	{
		//数字で始まらない要素は 0 扱い。例外を投げずに比較できることだけを保証する
		assertEquals(0, UpdateChecker.compareVersions("v1.x.y", "1.0.0"));
		assertTrue(UpdateChecker.compareVersions("v1.5.2b1", "1.5.1") > 0);
	}

	@Test
	public void parseTagNameReadsGitHubResponseShape()
	{
		//実レスポンスは空白なしの詰まった JSON
		assertEquals("v1.5.2-jdk21",
			UpdateChecker.parseTagName("{\"url\":\"https://api.github.com/x\",\"tag_name\":\"v1.5.2-jdk21\",\"name\":\"x\"}"));
		//整形済み JSON (空白・改行あり) でも拾えること
		assertEquals("v1.6.0-jdk21",
			UpdateChecker.parseTagName("{\n  \"tag_name\" : \"v1.6.0-jdk21\",\n  \"draft\": false\n}"));
	}

	@Test
	public void parseTagNameReturnsNullWhenAbsent()
	{
		assertNull(UpdateChecker.parseTagName(null));
		assertNull(UpdateChecker.parseTagName(""));
		assertNull(UpdateChecker.parseTagName("{\"message\":\"Not Found\"}"));
	}

	@Test
	public void successfulResponseYieldsUpdateAvailable() throws Exception
	{
		try (LocalServer server = LocalServer.start(200,
				"{\"tag_name\":\"v1.6.0-jdk21\",\"html_url\":\"https://example.invalid/r\"}")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertTrue(result.isSuccess());
			assertTrue(result.updateAvailable());
			assertEquals("v1.6.0-jdk21", result.latestVersion());
			assertEquals(UpdateChecker.DOWNLOAD_PAGE_URL, result.downloadPageUrl());
		}
	}

	@Test
	public void sameVersionIsNotReportedAsUpdate() throws Exception
	{
		try (LocalServer server = LocalServer.start(200, "{\"tag_name\":\"v1.5.2-jdk21\"}")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertTrue(result.isSuccess());
			assertFalse(result.updateAvailable());
		}
	}

	@Test
	public void nonOkStatusIsReportedAsFailure() throws Exception
	{
		try (LocalServer server = LocalServer.start(500, "boom")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertFalse(result.isSuccess());
			assertEquals("HTTP 500", result.error());
			assertNull(result.latestVersion());
			//失敗しても案内先は返す
			assertEquals(UpdateChecker.DOWNLOAD_PAGE_URL, result.downloadPageUrl());
		}
	}

	@Test
	public void rateLimitIsDistinguishedFromOtherFailures() throws Exception
	{
		try (LocalServer server = LocalServer.start(403, "{\"message\":\"API rate limit exceeded\"}",
				"x-ratelimit-remaining", "0")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertFalse(result.isSuccess());
			assertTrue("レート制限だと分かる文言にする: " + result.error(),
				result.error().contains("rate limit"));
		}
	}

	@Test
	public void forbiddenWithoutRateLimitHeaderIsPlainHttpError() throws Exception
	{
		try (LocalServer server = LocalServer.start(403, "nope")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertEquals("HTTP 403", result.error());
		}
	}

	@Test
	public void bodyWithoutTagNameIsReportedAsFailure() throws Exception
	{
		try (LocalServer server = LocalServer.start(200, "{\"message\":\"Not Found\"}")) {
			UpdateChecker.Result result = UpdateChecker.check("1.5.2-jdk21", server.url());
			assertFalse(result.isSuccess());
			assertEquals("tag_name not found", result.error());
		}
	}

	/** ループバックに立てる使い捨ての HTTP サーバ。外部ネットワークには出ない */
	private static final class LocalServer implements AutoCloseable
	{
		private final HttpServer server;

		private LocalServer(HttpServer server) { this.server = server; }

		static LocalServer start(int status, String body, String... headers) throws Exception
		{
			HttpServer server = HttpServer.create(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			server.createContext("/latest", exchange -> {
				for (int i = 0; i < headers.length; i += 2) {
					exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
				}
				exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
				exchange.sendResponseHeaders(status, bytes.length);
				exchange.getResponseBody().write(bytes);
				exchange.close();
			});
			server.start();
			return new LocalServer(server);
		}

		String url()
		{
			return "http://" + this.server.getAddress().getHostString()
				+ ":" + this.server.getAddress().getPort() + "/latest";
		}

		@Override
		public void close() { this.server.stop(0); }
	}
}
