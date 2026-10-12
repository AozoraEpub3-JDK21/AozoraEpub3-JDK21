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
 * 本文を読む前に答える POST の道（他オリジンの 403、Web 本棚の無いときの 503、トークン違いの 404、末尾の / が無いときの 308）の後に、
 * 同じ接続で次の要求を送っても、次の要求が正しく読まれること（docs/code-audit-followups.md 項目 49・internal #29）。
 *
 * <p>読まなかった本文が接続に残ると、次の要求の頭に付いて 405・404 になる（ブラウザは同じ接続を使い回すので、本棚の操作が
 * 時々失敗する形で利用者に当たる）。読み捨てるのはサーバ（JDK の HttpServer）が exchange を閉じるとき。それを固定する
 * （小さな本文で。JDK は 64 KiB までを読み捨て、それより多ければ接続を閉じる）。
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

	/** サーバの道の頭（/p/<トークン>） */
	private String basePath()
	{
		String url = this.server.getUrl();
		return url.substring(url.indexOf("/p/"), url.length() - 1);
	}

	/** Host ヘッダの値（サーバが案内する URL と同じ。IPv6 の台では [::1]:port） */
	private String host()
	{
		String url = this.server.getUrl();
		return url.substring("http://".length(), url.indexOf("/p/"));
	}

	private static String readLine(InputStream in) throws IOException
	{
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) >= 0 && b != '\n') {
			if (b != '\r') line.write(b);
		}
		return line.toString(StandardCharsets.ISO_8859_1);
	}

	/** 応答を 1 つ読み、状態の行と本文を返す（Content-Length と chunked のどちらでも、終わりまで読む） */
	private static String readResponse(InputStream in) throws IOException
	{
		String status = readLine(in);
		int length = 0;
		boolean chunked = false;
		for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
			String lower = line.toLowerCase(java.util.Locale.ROOT);
			if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring("content-length:".length()).trim());
			if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) chunked = true;
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		if (chunked) {
			for (int size = Integer.parseInt(readLine(in).trim(), 16); size > 0; size = Integer.parseInt(readLine(in).trim(), 16)) {
				body.write(in.readNBytes(size));
				readLine(in);
			}
			//終わりの chunk の後の空の行（trailer は使っていない）
			readLine(in);
		} else {
			body.write(in.readNBytes(length));
		}
		return status + " | " + body.toString(StandardCharsets.UTF_8);
	}

	/**
	 * 本文つきの POST を送り、答えを読み、同じ接続で次の要求（本の一覧）を送って、その答えを返す
	 * @param target POST の道
	 * @param extraHeaders POST に足すヘッダ（末尾に \r\n を付ける）
	 * @param bodyAfterAnswer 本文を答えを読んでから送る
	 */
	private String[] postThenNext(String target, String extraHeaders, boolean bodyAfterAnswer) throws IOException
	{
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), this.server.getPort())) {
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			byte[] body = "C:\\Users\\x\\Fresh\\x".getBytes(StandardCharsets.UTF_8);
			out.write(("POST " + target + " HTTP/1.1\r\nHost: " + host() + "\r\n" + extraHeaders
				+ "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			if (!bodyAfterAnswer) out.write(body);
			out.flush();
			String first = readResponse(in);
			if (bodyAfterAnswer) {
				out.write(body);
				out.flush();
			}
			out.write(("GET " + basePath() + "/api/library HTTP/1.1\r\nHost: " + host() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			return new String[] { first, readResponse(in) };
		}
	}

	private void check(String target, String extraHeaders, boolean bodyAfterAnswer, String expectedFirst) throws IOException
	{
		String[] r = postThenNext(target, extraHeaders, bodyAfterAnswer);
		assertTrue("先に答える道に来ていない: " + r[0], r[0].startsWith(expectedFirst));
		assertTrue("同じ接続の次の要求が正しく読まれない: " + r[1], r[1].startsWith("HTTP/1.1 200"));
	}

	//Web 本棚を渡していないので 503（本文を読む前に答える）
	@Test public void after503WithTheBodyFirst() throws IOException { check(basePath() + "/api/webshelf", "", false, "HTTP/1.1 503"); }
	@Test public void after503WithTheBodyAfterTheAnswer() throws IOException { check(basePath() + "/api/webshelf", "", true, "HTTP/1.1 503"); }

	//他オリジンの POST は 403（本文を読む前に答える）
	@Test public void after403WithTheBodyFirst() throws IOException { check(basePath() + "/api/webshelf", "Origin: http://evil.example\r\n", false, "HTTP/1.1 403"); }
	@Test public void after403WithTheBodyAfterTheAnswer() throws IOException { check(basePath() + "/api/webshelf", "Origin: http://evil.example\r\n", true, "HTTP/1.1 403"); }

	//トークン違いは 404（存在を伏せる。本文を読む前に答える）
	@Test public void after404WithTheBodyFirst() throws IOException { check("/p/deadbeef/api/webshelf", "", false, "HTTP/1.1 404"); }
	@Test public void after404WithTheBodyAfterTheAnswer() throws IOException { check("/p/deadbeef/api/webshelf", "", true, "HTTP/1.1 404"); }

	//末尾の / が無い道は 308（本文の無い答え。sendResponseHeaders(…, -1) の道）
	@Test public void after308WithTheBodyFirst() throws IOException { check(basePath(), "", false, "HTTP/1.1 308"); }
	@Test public void after308WithTheBodyAfterTheAnswer() throws IOException { check(basePath(), "", true, "HTTP/1.1 308"); }
}
