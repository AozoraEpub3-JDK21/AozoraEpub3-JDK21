package com.github.hmdev.preview;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本文を読む前に答える POST の道（他オリジンの 403、Web 本棚の無いときの 503）の後に、同じ接続で次の要求を送っても、
 * 次の要求が正しく読まれること（docs/code-audit-followups.md 項目 49・internal #29）。
 *
 * <p>読まなかった本文が接続に残ると、次の要求の頭に付いて 405・404 になる（ブラウザは同じ接続を使い回すので、本棚の操作が
 * 時々失敗する形で利用者に当たる）。サーバ（JDK の HttpServer）は exchange を閉じるときに本文を読み捨てる。それを固定する。
 * 本文は答えの前に送る形と、答えを読んでから送る形の両方を見る。ソケットで送るので、HttpClient の振る舞いに依らない</p>
 */
public class PreviewServerKeepAliveTest
{
	@Rule public TemporaryFolder temp = new TemporaryFolder();
	private PreviewSession session;
	private PreviewServer server;

	@Before
	public void setUp() throws IOException
	{
		this.session = new PreviewSession();
		// ユーザーのホームを汚さないよう、設定の保存先はテンポラリに向ける
		this.server = new PreviewServer(this.session, new PreviewSettingsStore(temp.getRoot().toPath().resolve("settings.json")));
		this.server.start();
	}

	@After
	public void tearDown()
	{
		if (this.server != null) this.server.close();
		if (this.session != null) this.session.close();
	}

	private String path(String rest)
	{
		String url = this.server.getUrl();
		return url.substring(url.indexOf("/p/")) + rest;
	}

	/** 応答を 1 つ読み、状態の行と本文を返す（Content-Length まで） */
	private static String readResponse(InputStream in) throws IOException
	{
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		int b;
		int state = 0;
		while (state < 4 && (b = in.read()) >= 0) {
			head.write(b);
			boolean step = b == '\r' ? (state == 0 || state == 2) : b == '\n' && (state == 1 || state == 3);
			state = step ? state + 1 : (b == '\r' ? 1 : 0);
		}
		String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
		int length = 0;
		for (String line : lines) {
			if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring("content-length:".length()).trim());
		}
		return lines[0] + " | " + new String(in.readNBytes(length), StandardCharsets.UTF_8);
	}

	/**
	 * 本文つきの POST を送り、答えを読み、同じ接続で次の要求（本の一覧）を送って、その答えを返す
	 * @param extraHeaders POST に足すヘッダ（末尾に \r\n を付ける）
	 * @param bodyAfterAnswer 本文を答えを読んでから送る
	 */
	private String[] postThenNext(String extraHeaders, boolean bodyAfterAnswer) throws Exception
	{
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), this.server.getPort())) {
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			byte[] body = "C:\\Users\\x\\Fresh\\x".getBytes(StandardCharsets.UTF_8);
			out.write(("POST " + path("api/webshelf") + " HTTP/1.1\r\nHost: 127.0.0.1\r\n" + extraHeaders
				+ "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			if (!bodyAfterAnswer) out.write(body);
			out.flush();
			String first = readResponse(in);
			if (bodyAfterAnswer) {
				out.write(body);
				out.flush();
			}
			out.write(("GET " + path("api/library") + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			return new String[] { first, readResponse(in) };
		}
	}

	@Test
	public void theNextRequestIsReadAfterA503()
	{
		//Web 本棚を渡していないので 503（本文を読む前に答える）
		check("", "HTTP/1.1 503");
	}

	@Test
	public void theNextRequestIsReadAfterA403()
	{
		//他オリジンの POST は 403（本文を読む前に答える）
		check("Origin: http://evil.example\r\n", "HTTP/1.1 403");
	}

	private void check(String extraHeaders, String expectedFirst)
	{
		for (boolean bodyAfterAnswer : new boolean[] { false, true }) {
			String[] r;
			try {
				r = postThenNext(extraHeaders, bodyAfterAnswer);
			} catch (Exception e) {
				throw new AssertionError("本文を答えの" + (bodyAfterAnswer ? "後" : "前") + "に送った回: " + e, e);
			}
			assertTrue("先に答える道に来ていない（本文を答えの" + (bodyAfterAnswer ? "後" : "前") + "）: " + r[0], r[0].startsWith(expectedFirst));
			assertTrue("同じ接続の次の要求が正しく読まれない（本文を答えの" + (bodyAfterAnswer ? "後" : "前") + "）: " + r[1], r[1].startsWith("HTTP/1.1 200"));
		}
	}
}
