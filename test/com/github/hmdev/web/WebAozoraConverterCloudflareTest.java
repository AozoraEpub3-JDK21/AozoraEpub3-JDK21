package com.github.hmdev.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

/**
 * Cloudflare のボット確認画面（403 + cf-mitigated: challenge）で止められたとき、
 * 理由の分かる文言で失敗することのテスト（ハーメルンが 2026-10 にこうなった）。
 * コードだけの 403 と区別できることも確かめる。
 */
public class WebAozoraConverterCloudflareTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
	}

	private String serve(boolean challenge) throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			byte[] body = "<html><title>Just a moment...</title></html>".getBytes(StandardCharsets.UTF_8);
			if (challenge) exchange.getResponseHeaders().add("cf-mitigated", "challenge");
			exchange.sendResponseHeaders(403, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/novel/1/";
	}

	private String failureMessage(String url) throws Exception {
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(
			"https://ncode.syosetu.com/n0000xx/", new File("web"));
		assertNotNull(converter);
		Method cacheFile = WebAozoraConverter.class.getDeclaredMethod("cacheFile", String.class, File.class, String.class);
		cacheFile.setAccessible(true);
		try {
			cacheFile.invoke(converter, url, new File(tempFolder.getRoot(), "page.html"), null);
		} catch (InvocationTargetException e) {
			assertTrue(e.getCause() instanceof IOException);
			return e.getCause().getMessage();
		}
		throw new AssertionError("403 なのに失敗しなかった");
	}

	@Test
	public void cloudflareChallengeIsExplained() throws Exception {
		String message = failureMessage(serve(true));
		assertTrue(message, message.contains("Cloudflare の確認画面"));
		assertTrue(message, message.contains("HTTP 403"));
	}

	@Test
	public void plain403KeepsTheOldMessage() throws Exception {
		String message = failureMessage(serve(false));
		assertFalse(message, message.contains("Cloudflare"));
		assertTrue(message, message.contains("Server returned HTTP response code: 403"));
	}
}
