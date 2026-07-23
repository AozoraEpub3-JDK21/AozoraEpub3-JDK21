package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * NetUtils のタイムアウト設定テスト（監査 #5）。
 *
 * URL.openStream() は接続・読み込みともタイムアウト無制限のため、
 * 応答しないサーバに当たると変換スレッドが永久にブロックしていた。
 */
public class NetUtilsTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** HttpClient 移行済み経路（connect 10s / request 30s）と値が揃っていること */
	@Test
	public void timeoutDefaultsMatchHttpClientPaths() {
		assertEquals(10_000, NetUtils.CONNECT_TIMEOUT_MILLIS);
		assertEquals(30_000, NetUtils.READ_TIMEOUT_MILLIS);
	}

	/** 接続はできるが応答を返さないサーバで、読み込みがタイムアウトすること。
	 * accept しない ServerSocket に対して接続だけ backlog で成立させる */
	@Test
	public void readTimesOutOnSilentServer() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1)) {
			URL url = new URI("http://127.0.0.1:" + server.getLocalPort() + "/never-answers").toURL();
			long start = System.nanoTime();
			assertThrows(SocketTimeoutException.class,
				() -> NetUtils.openStream(url, 1000, 200));
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
			//無制限だった頃はここで永久にブロックしていた
			assertTrue("タイムアウトが効いていない: " + elapsedMillis + "ms", elapsedMillis < 10_000);
		}
	}

	/** file: スキームでも従来どおり読めること。
	 * .url ショートカット経由で file:// が渡り得るため、HttpClient には寄せられない */
	@Test
	public void fileUrlStillReadable() throws Exception {
		Path file = tempFolder.newFile("sample.txt").toPath();
		Files.write(file, "本文です。".getBytes(StandardCharsets.UTF_8));
		try (InputStream is = NetUtils.openStream(file.toUri().toURL())) {
			assertEquals("本文です。", new String(is.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
