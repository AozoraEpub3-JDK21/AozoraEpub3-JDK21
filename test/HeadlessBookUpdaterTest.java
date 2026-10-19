import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.preview.BookUpdater;
import com.github.hmdev.util.VelocityTestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 本棚の「続きを取る」のテスト（internal #11）。手元・内部の宛先を取りに行かないことと、本棚の本を上書きして 1 つ前の版を残すこと
 */
public class HeadlessBookUpdaterTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String fqdn;
	private volatile String episodes = "<li><a href=\"/ep/1/\">第1話</a></li>";

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (fqdn != null) com.github.hmdev.web.WebAozoraConverterTestAccess.forget(fqdn);
		System.clearProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY);
	}

	@Test
	public void localAndPrivateHostsAreRecognised() {
		for (String url : new String[]{ "http://127.0.0.1/x", "http://127.0.0.1:8080/x", "http://localhost/x", "http://a.localhost/x",
				"http://[::1]:8080/x", "http://10.0.0.1/x", "http://192.168.10.109/x", "http://172.16.0.1/x", "http://169.254.1.1/x",
				"http://0.0.0.0/x", "http://[fd00::1]/x", "http://[fe80::1]/x",
				// PR #118 のゲート2
				"http://a@127.0.0.1:8080/x", "http://user@[::1]/x", "http://100.64.0.1/x", "http://100.127.255.255/x", "http://0.1.2.3/x" }) {
			assertTrue(url, HeadlessBookUpdater.isLocalOrPrivate(url));
		}
		for (String url : new String[]{ "https://ncode.syosetu.com/n1234ab/", "https://kakuyomu.jp/works/1", "http://8.8.8.8/x",
				"https://[2001:db8::1]/x", "http://100.128.0.1/x" }) {
			assertFalse(url, HeadlessBookUpdater.isLocalOrPrivate(url));
		}
	}

	@Test
	public void aLocalSourceIsNotFetched() throws Exception {
		File epub = tempFolder.newFile("book.epub");
		BookUpdater.Result r = new HeadlessBookUpdater(Properties::new, "").update("http://127.0.0.1:9/novel/", epub.toPath());
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("手元"));
	}

	private void respond(HttpExchange exchange, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	/** 本棚の本を、取り直した内容で上書きし、前の本を作品のフォルダに 1 つ前の版（本ごと）として残す */
	@Test
	public void theShelfBookIsOverwrittenAndThePreviousVersionKept() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/novel/")) {
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">" + episodes + "</ul></body></html>");
			} else {
				respond(exchange, "<html><body><h2>" + path + "</h2><div class=\"body\"><p>" + path + "の本文</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		//サイト定義はホスト名で探す（ポート番号は見ない。internal #29）ので、Windows でも置ける名前で作る
		File siteDir = new File(root, "web/" + fqdn.substring(0, fqdn.lastIndexOf(':')));
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
			.getBytes(StandardCharsets.UTF_8));
		File repo = VelocityTestUtils.templateDir().getParent().toFile();
		for (File f : repo.listFiles((d, n) -> n.startsWith("chuki_") && n.endsWith(".txt"))) {
			Files.copy(f.toPath(), new File(root, f.getName()).toPath());
		}
		String basePath = root.getAbsolutePath() + File.separator;
		Properties props = new Properties();
		props.load(Files.newInputStream(new File(repo, "test_data/gui_default_settings.ini").toPath()));
		props.setProperty("CachePath", new File(root, "cache").getAbsolutePath());
		System.setProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY, "true");
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);
		//静的な Velocity は先に走った試験の初期化が残るので、専用の VelocityEngine を渡した書き出しで作る
		updater.conversions = (p, cache) -> conversion(p, basePath, cache);

		//最初の本（1 話）を作る
		File shelf = tempFolder.newFolder("shelf");
		HeadlessWebConversion.Result first = conversion(props, basePath, new File(root, "cache"))
			.convert(base + "/novel/", shelf, null, true);
		assertTrue(first.message(), first.ok());
		byte[] before = Files.readAllBytes(first.epub().toPath());

		//2 話目が出たので続きを取る
		String oneEpisode = episodes;
		episodes += "<li><a href=\"/ep/2/\">第2話</a></li>";
		BookUpdater.Result r = updater.update(base + "/novel/", first.epub().toPath());
		assertTrue(r.message(), r.ok());
		assertFalse("本棚の本が新しくなる", java.util.Arrays.equals(before, Files.readAllBytes(first.epub().toPath())));
		File previous = new File(root, "cache/" + fqdn.replace(':', '_') + "/novel/" + HeadlessWebConversion.previousEpubName(new File(root, "cache/" + fqdn.replace(':', '_') + "/novel"), first.epub()));
		assertTrue("1 つ前の版が残る: " + previous, previous.isFile());
		assertArrayEquals(before, Files.readAllBytes(previous.toPath()));
		assertTrue("ほかの名前の本が増えない", shelf.list().length == 1);

		//掲載先で 2 話目が消えた: 本棚の更新は守りを付けて変換するので、書かずに止める（internal #11）
		episodes = oneEpisode;
		byte[] twoEpisodes = Files.readAllBytes(first.epub().toPath());
		BookUpdater.Result shrunk = updater.update(base + "/novel/", first.epub().toPath());
		assertFalse(shrunk.ok());
		org.junit.Assert.assertEquals("shrunk", shrunk.stop());
		assertArrayEquals(twoEpisodes, Files.readAllBytes(first.epub().toPath()));
		//利用者が「減ったまま更新する」を選んだら取り直す
		BookUpdater.Result accepted = updater.update(base + "/novel/", first.epub().toPath(), true);
		assertTrue(accepted.message(), accepted.ok());
	}

	/**
	 * 作り直す（internal #54）: 掲載元へは 1 回も取りに行かず、キャッシュの話から今の設定で本を書き直す。
	 * 更新情報（update.txt）は変えない。キャッシュに無い話があれば、本も txt も前のまま止める
	 */
	@Test
	public void regeneratingUsesOnlyTheCacheAndTheCurrentSettings() throws Exception {
		java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicReference<String> body = new java.util.concurrent.atomic.AtomicReference<>("はじめの本文");
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			//末尾に / の無い作品（カクヨムの形）。/ を付けた URL は無い
			if (path.equals("/work/")) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			if (path.equals("/novel/") || path.equals("/work")) {
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><div class=\"cover\"><img src=\"/cover.png\"></div>"
					+ "<ul class=\"list\">" + episodes + "</ul></body></html>");
			} else if (path.equals("/cover.png")) {
				byte[] png = java.util.Base64.getDecoder().decode(
					"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
				exchange.getResponseHeaders().set("Content-Type", "image/png");
				exchange.sendResponseHeaders(200, png.length);
				exchange.getResponseBody().write(png);
				exchange.close();
			} else {
				respond(exchange, "<html><body><h2>" + path + "</h2><div class=\"body\"><p>" + body.get() + "</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		File siteDir = new File(root, "web/" + fqdn.substring(0, fqdn.lastIndexOf(':')));
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a", "SUB_UPDATE\t.list .up", "COVER_IMG\t.cover img:0", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
			.getBytes(StandardCharsets.UTF_8));
		File repo = VelocityTestUtils.templateDir().getParent().toFile();
		for (File f : repo.listFiles((d, n) -> n.startsWith("chuki_") && n.endsWith(".txt"))) {
			Files.copy(f.toPath(), new File(root, f.getName()).toPath());
		}
		String basePath = root.getAbsolutePath() + File.separator;
		Properties props = new Properties();
		props.load(Files.newInputStream(new File(repo, "test_data/gui_default_settings.ini").toPath()));
		props.setProperty("CachePath", new File(root, "cache").getAbsolutePath());
		System.setProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY, "true");
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);
		updater.conversions = (p, cache) -> conversion(p, basePath, cache);

		//話ごとの更新日を出す（update.txt が書かれる形。作り直しがそれを変えないことを確かめるため）
		episodes = "<li><a href=\"/ep/1/\">第1話</a><span class=\"up\">2026/01/01</span></li>"
			+ "<li><a href=\"/ep/2/\">第2話</a><span class=\"up\">2026/01/01</span></li>";
		File shelf = tempFolder.newFolder("shelf");
		HeadlessWebConversion.Result first = conversion(props, basePath, new File(root, "cache"))
			.convert(base + "/novel/", shelf, null, true);
		assertTrue(first.message(), first.ok());
		File work = new File(root, "cache/" + fqdn.replace(':', '_') + "/novel");
		File updateInfo = new File(work, "update.txt");
		File cover = new File(work, "cover.jpg");
		assertTrue("表紙をキャッシュした: " + cover, cover.isFile());
		byte[] coverBefore = Files.readAllBytes(cover.toPath());
		//改稿されたのに取り直せていない話（更新情報の日付が目次と違う）を作る。作り直しはそれも取りに行かない
		assertTrue("このサイトの形では更新情報が書かれる（無いと、変えないことを確かめられない）", updateInfo.isFile());
		String updateText = new String(Files.readAllBytes(updateInfo.toPath()), StandardCharsets.UTF_8);
		assertTrue(updateText, updateText.contains("2026/01/01"));
		Files.write(updateInfo.toPath(), updateText.replace("2026/01/01", "2025/12/31").getBytes(StandardCharsets.UTF_8));
		byte[] updateBefore = Files.readAllBytes(updateInfo.toPath());
		org.junit.Assert.assertTrue("はじめは縦書き", opfOf(first.epub()).contains("page-progression-direction=\"rtl\""));

		//掲載元の本文が変わり、設定を横書きにした。作り直しは取りに行かないので、本文は前のまま・横書きになる
		body.set("あとで変わった本文");
		props.setProperty("Vertical", "0");
		//「更新分のみ」の設定でも、作り直しは全話で本を作る（何も取らないので、見ると「更新なし」になる）
		props.setProperty("WebConvertUpdated", "1");
		props.setProperty("WebModifiedOnly", "1");
		int before = requests.get();
		BookUpdater.Result r = updater.regenerate(base + "/novel/", first.epub().toPath());
		assertTrue(r.message(), r.ok());
		org.junit.Assert.assertEquals("掲載元へは取りに行かない", before, requests.get());
		String opf = opfOf(first.epub());
		assertTrue("今の設定（横書き）で作り直す: " + opf, opf.contains("page-progression-direction=\"ltr\""));
		String text = textOf(first.epub());
		assertTrue("本文はキャッシュのまま", text.contains("はじめの本文"));
		assertFalse(text.contains("あとで変わった本文"));
		assertArrayEquals("表紙は消さずに使う", coverBefore, Files.readAllBytes(cover.toPath()));
		assertArrayEquals("更新情報は変えない", updateBefore, Files.readAllBytes(updateInfo.toPath()));
		File previous = new File(work, HeadlessWebConversion.previousEpubName(work, first.epub()));
		assertTrue("1 つ前の版が残る: " + previous, previous.isFile());
		//もう一度作り直しても、1 つ前の版は回さない（取り直す前の版を、作り直しで失わない）
		byte[] previousBytes = Files.readAllBytes(previous.toPath());
		props.setProperty("Vertical", "1");
		BookUpdater.Result again = updater.regenerate(base + "/novel/", first.epub().toPath());
		assertTrue(again.message(), again.ok());
		assertArrayEquals("1 つ前の版はそのまま", previousBytes, Files.readAllBytes(previous.toPath()));
		org.junit.Assert.assertEquals(before, requests.get());

		//キャッシュの 2 話目を消した: 何も書かずに止める（話の欠けた本で上書きしない）
		File[] txts = work.listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"));
		org.junit.Assert.assertEquals(1, txts.length);
		byte[] txtBefore = Files.readAllBytes(txts[0].toPath());
		byte[] bookBefore = Files.readAllBytes(first.epub().toPath());
		File ep2 = new File(root, "cache/" + fqdn.replace(':', '_') + "/ep/2/index.html");
		assertTrue("2 話目のキャッシュ: " + ep2, ep2.isFile());
		//2 話目のキャッシュが本文の無いページ（200 で返ったエラーページ）になった: 取り直せないので、同じく何も書かずに止める（codex P1）
		byte[] ep2Before = Files.readAllBytes(ep2.toPath());
		Files.write(ep2.toPath(), "<html><body><h2>メンテナンス中</h2></body></html>".getBytes(StandardCharsets.UTF_8));
		BookUpdater.Result bodyless = updater.regenerate(base + "/novel/", first.epub().toPath());
		assertFalse(bodyless.message(), bodyless.ok());
		assertTrue(bodyless.message(), bodyless.message().contains("本文の無い"));
		org.junit.Assert.assertEquals("本文が無くても取りに行かない", before, requests.get());
		assertArrayEquals("本は前のまま", bookBefore, Files.readAllBytes(first.epub().toPath()));
		assertArrayEquals("txt も前のまま", txtBefore, Files.readAllBytes(txts[0].toPath()));
		Files.write(ep2.toPath(), ep2Before);
		assertTrue(ep2.delete());
		props.setProperty("Vertical", "1");
		BookUpdater.Result missing = updater.regenerate(base + "/novel/", first.epub().toPath());
		assertFalse(missing.ok());
		assertTrue(missing.message(), missing.message().contains("キャッシュに無い（または本文の無い）目次・話"));
		org.junit.Assert.assertEquals("欠けていても取りに行かない", before, requests.get());
		assertArrayEquals("本は前のまま", bookBefore, Files.readAllBytes(first.epub().toPath()));
		assertArrayEquals("txt も前のまま", txtBefore, Files.readAllBytes(txts[0].toPath()));
		assertFalse("取りに行かない", ep2.exists());

		//末尾に / の無い URL の作品も、作り直しでは 1 回も問い合わせない（/ を補う確かめも送らない）
		HeadlessWebConversion.Result noSlash = conversion(props, basePath, new File(root, "cache"))
			.convert(base + "/work", tempFolder.newFolder("shelf2"), null, true);
		assertTrue(noSlash.message(), noSlash.ok());
		int beforeNoSlash = requests.get();
		BookUpdater.Result r2 = updater.regenerate(base + "/work", noSlash.epub().toPath());
		assertTrue(r2.message(), r2.ok());
		org.junit.Assert.assertEquals("/ を補う確かめも送らない", beforeNoSlash, requests.get());
		//この作品は本文を変えた後に取ったので、その本文のまま作り直る
		assertTrue(textOf(noSlash.epub()).contains("あとで変わった本文"));

		//取ったときに / を補った作品（/novel → /novel/）を、/ の無い URL で作り直しても、取ったときの作業フォルダに書く
		File novelConverted = new File(root, "cache/" + fqdn.replace(':', '_') + "/novel_converted");
		BookUpdater.Result r3 = updater.regenerate(base + "/novel", first.epub().toPath());
		assertTrue(r3.message(), r3.ok());
		org.junit.Assert.assertEquals(beforeNoSlash, requests.get());
		assertFalse("別の作業フォルダを作らない: " + novelConverted, novelConverted.exists());
	}

	/**
	 * 全話を取り直す（internal #54）: 目次に改稿の印が無くても、キャッシュにある話もすべて取り直す。
	 * 取れなかった話は前のキャッシュで本を作り、その数を知らせる。話数が減っていたら更新と同じく止める
	 */
	@Test
	public void refetchingFetchesEveryEpisodeAgain() throws Exception {
		java.util.Map<String, Integer> hits = new java.util.concurrent.ConcurrentHashMap<>();
		java.util.concurrent.atomic.AtomicReference<String> body = new java.util.concurrent.atomic.AtomicReference<>("はじめの本文");
		java.util.Set<String> broken = java.util.concurrent.ConcurrentHashMap.newKeySet();
		//200 で本文の無いページ（メンテナンス・年齢確認など）を返す話
		java.util.Set<String> maintenance = java.util.concurrent.ConcurrentHashMap.newKeySet();
		//次の 1 回だけ取れない話
		java.util.Set<String> failOnce = java.util.concurrent.ConcurrentHashMap.newKeySet();
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			hits.merge(path, 1, Integer::sum);
			if (broken.contains(path) || failOnce.remove(path)) {
				exchange.sendResponseHeaders(500, -1);
				exchange.close();
			} else if (maintenance.contains(path)) {
				respond(exchange, "<html><body><h2>メンテナンス中</h2><p>ただいまメンテナンス中です</p></body></html>");
			} else if (path.equals("/novel/")) {
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">" + episodes + "</ul></body></html>");
			} else if (path.equals("/single/")) {
				//話の一覧の無い 1 ページの作品
				respond(exchange, "<html><body><h1>短編</h1><p class=\"author\">著者</p><div class=\"body\"><p>丙" + body.get() + "</p></div></body></html>");
			} else {
				respond(exchange, "<html><body><h2>" + path + "</h2><div class=\"body\"><p>" + (path.contains("/1/") ? "甲" : "乙") + body.get() + "</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		File siteDir = new File(root, "web/" + fqdn.substring(0, fqdn.lastIndexOf(':')));
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a", "SUB_UPDATE\t.list .up", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
			.getBytes(StandardCharsets.UTF_8));
		File repo = VelocityTestUtils.templateDir().getParent().toFile();
		for (File f : repo.listFiles((d, n) -> n.startsWith("chuki_") && n.endsWith(".txt"))) {
			Files.copy(f.toPath(), new File(root, f.getName()).toPath());
		}
		String basePath = root.getAbsolutePath() + File.separator;
		Properties props = new Properties();
		props.load(Files.newInputStream(new File(repo, "test_data/gui_default_settings.ini").toPath()));
		props.setProperty("CachePath", new File(root, "cache").getAbsolutePath());
		System.setProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY, "true");
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);
		updater.conversions = (p, cache) -> conversion(p, basePath, cache);

		//本文の印（甲・乙）は話ごと。パスは本の中で全角・漢数字に変わるので印にしない
		//目次の更新日は変わらない（改稿の印が無い）
		episodes = "<li><a href=\"/ep/1/\">第1話</a><span class=\"up\">2026/01/01</span></li>"
			+ "<li><a href=\"/ep/2/\">第2話</a><span class=\"up\">2026/01/01</span></li>";
		File shelf = tempFolder.newFolder("shelf");
		HeadlessWebConversion.Result first = conversion(props, basePath, new File(root, "cache"))
			.convert(base + "/novel/", shelf, null, true);
		assertTrue(first.message(), first.ok());

		//掲載元が日付を変えずに本文を直した。「更新分のみ」の設定で続きを取っても、取り直さない（対照）
		body.set("直した本文");
		props.setProperty("WebConvertUpdated", "1");
		//取ったばかりのキャッシュを「更新分」と見なさない（既定の 24 時間だと、さっき取った話が更新扱いになる）
		props.setProperty("WebModifiedExpire", "0");
		BookUpdater.Result plain = updater.update(base + "/novel/", first.epub().toPath());
		assertTrue(plain.message(), plain.noUpdate());
		org.junit.Assert.assertEquals("続きを取るは話を取り直さない", Integer.valueOf(1), hits.get("/ep/1/"));
		String plainText = textOf(first.epub());
		assertTrue(plainText, plainText.contains("甲はじめの本文"));

		//全話を取り直すと、印が無くても両方の話を取り直す（「更新分のみ」の設定でも本を作る）
		BookUpdater.Result r = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertTrue(r.message(), r.ok());
		org.junit.Assert.assertEquals("すべて取れたら、カードに足す文は無い（「変換しました」を重ねない）", "", r.message());
		org.junit.Assert.assertEquals(Integer.valueOf(2), hits.get("/ep/1/"));
		org.junit.Assert.assertEquals(Integer.valueOf(2), hits.get("/ep/2/"));
		String text = textOf(first.epub());
		assertTrue(text, text.contains("甲直した本文") && text.contains("乙直した本文"));
		assertFalse(text.contains("はじめの本文"));

		//2 話目が取れない: 取れた話で本を作り、2 話目は前のキャッシュのまま。その数を知らせる
		body.set("三度目の本文");
		broken.add("/ep/2/");
		BookUpdater.Result partial = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertTrue(partial.message(), partial.ok());
		assertTrue(partial.message(), partial.message().contains("1 話は取り直せませんでした"));
		text = textOf(first.epub());
		assertTrue(text, text.contains("甲三度目の本文") && text.contains("乙直した本文"));

		//2 話目が 200 で本文の無いページを返す: よいキャッシュを上書きせず、取り直せなかった話に数える
		broken.clear();
		maintenance.add("/ep/2/");
		File ep2 = new File(root, "cache/" + fqdn.replace(':', '_') + "/ep/2/index.html");
		byte[] ep2Before = Files.readAllBytes(ep2.toPath());
		BookUpdater.Result gate = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertTrue(gate.message(), gate.ok());
		assertTrue(gate.message(), gate.message().contains("1 話は取り直せませんでした"));
		assertArrayEquals("キャッシュは前のまま", ep2Before, Files.readAllBytes(ep2.toPath()));
		text = textOf(first.epub());
		assertTrue(text, text.contains("乙直した本文"));
		assertFalse(text.contains("メンテナンス"));
		//キャッシュがもう壊れていて（本文の無いページ）、掲載元もまだ本文の無いページを返す: 取れたと数えない
		Files.write(ep2.toPath(), "<html><body><h2>メンテナンス中</h2></body></html>".getBytes(StandardCharsets.UTF_8));
		BookUpdater.Result stillBroken = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertTrue(stillBroken.message(), stillBroken.message().contains("1 話は取り直せませんでした"));
		Files.write(ep2.toPath(), ep2Before);
		maintenance.clear();

		//目次の周で取れなかった話も、書き出しの周の取り直しで取れたら、取り直せなかったとは言わない
		assertTrue(ep2.delete());
		failOnce.add("/ep/2/");
		BookUpdater.Result retried = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertTrue(retried.message(), retried.ok());
		org.junit.Assert.assertEquals("", retried.message());
		assertTrue(textOf(first.epub()).contains("乙三度目の本文"));

		//掲載元が落ちていて 1 話も取れない: 「取り直しました」と言わず、本も txt も書き換えない
		body.set("四度目の本文");
		broken.add("/ep/1/");
		broken.add("/ep/2/");
		File work = new File(root, "cache/" + fqdn.replace(':', '_') + "/novel");
		File[] txts = work.listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"));
		org.junit.Assert.assertEquals(1, txts.length);
		byte[] txtBeforeDown = Files.readAllBytes(txts[0].toPath());
		byte[] beforeDown = Files.readAllBytes(first.epub().toPath());
		BookUpdater.Result down = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertFalse(down.message(), down.ok());
		assertTrue(down.message(), down.message().contains("1 話も取り直せませんでした"));
		assertArrayEquals("本は前のまま", beforeDown, Files.readAllBytes(first.epub().toPath()));
		assertArrayEquals("txt も前のまま", txtBeforeDown, Files.readAllBytes(txts[0].toPath()));

		//話の一覧の無い 1 ページの作品も、「更新分のみ」の設定のまま取り直して本を作る（更新なしで止めない）
		HeadlessWebConversion.Result single = conversion(props, basePath, new File(root, "cache"))
			.convert(base + "/single/", tempFolder.newFolder("shelf2"), null, true);
		assertTrue(single.message(), single.ok());
		BookUpdater.Result singleRefetch = updater.refetch(base + "/single/", single.epub().toPath(), false);
		assertTrue(singleRefetch.message(), singleRefetch.ok());

		//掲載元で 2 話目が消えた: 書かずに止める。「減ったまま」を選べば取り直す
		broken.clear();
		episodes = "<li><a href=\"/ep/1/\">第1話</a><span class=\"up\">2026/01/01</span></li>";
		byte[] bookBefore = Files.readAllBytes(first.epub().toPath());
		BookUpdater.Result shrunk = updater.refetch(base + "/novel/", first.epub().toPath(), false);
		assertFalse(shrunk.ok());
		org.junit.Assert.assertEquals("shrunk", shrunk.stop());
		assertArrayEquals(bookBefore, Files.readAllBytes(first.epub().toPath()));
		BookUpdater.Result accepted = updater.refetch(base + "/novel/", first.epub().toPath(), true);
		assertTrue(accepted.message(), accepted.ok());
		assertFalse(textOf(first.epub()).contains("乙"));
	}

	private static String opfOf(File epub) throws IOException {
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(epub)) {
			java.util.zip.ZipEntry e = zip.stream().filter(z -> z.getName().endsWith(".opf")).findFirst().orElseThrow();
			return new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String textOf(File epub) throws IOException {
		StringBuilder buf = new StringBuilder();
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(epub)) {
			for (java.util.zip.ZipEntry e : java.util.Collections.list(zip.entries())) {
				if (e.getName().endsWith(".xhtml")) buf.append(new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return buf.toString();
	}

	private static HeadlessWebConversion conversion(Properties props, String basePath, File cache) {
		try {
			com.github.hmdev.writer.Epub3Writer writer = new com.github.hmdev.writer.Epub3Writer(
				VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
			com.github.hmdev.writer.Epub3Writer imageWriter = new com.github.hmdev.writer.Epub3Writer(
				VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
			return new HeadlessWebConversion(props, basePath, cache, writer, imageWriter);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Web 本棚に落とすと、本は Web 本棚の直下、記録とキャッシュは Web 本棚の .aozora に置く。
	 * その本の続きを取るときも .aozora を使う（設定のキャッシュの場所に依らない。internal #11 の案 A）
	 */
	@Test
	public void aDownloadKeepsItsRecordsInTheWebShelf() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/novel/")) {
				//長い題（短い名前と違う名前になるように）
				respond(exchange, "<html><body><h1>【書籍化】題～副題がある～</h1><p class=\"author\">著者</p><ul class=\"list\">" + episodes + "</ul></body></html>");
			} else {
				respond(exchange, "<html><body><h2>" + path + "</h2><div class=\"body\"><p>" + path + "の本文</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		//サイト定義はホスト名で探す（ポート番号は見ない。internal #29）ので、Windows でも置ける名前で作る
		File siteDir = new File(root, "web/" + fqdn.substring(0, fqdn.lastIndexOf(':')));
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
			.getBytes(StandardCharsets.UTF_8));
		File repo = VelocityTestUtils.templateDir().getParent().toFile();
		for (File f : repo.listFiles((d, n) -> n.startsWith("chuki_") && n.endsWith(".txt"))) {
			Files.copy(f.toPath(), new File(root, f.getName()).toPath());
		}
		String basePath = root.getAbsolutePath() + File.separator;
		Properties props = new Properties();
		props.load(Files.newInputStream(new File(repo, "test_data/gui_default_settings.ini").toPath()));
		File settingsCache = new File(root, "settings-cache");
		props.setProperty("CachePath", settingsCache.getAbsolutePath());
		//「出力ファイル名に表題利用」を切っていても、落とした本は短い名前で、その名前のまま続きを取れる（PR の手元の codex）
		props.setProperty("AutoFileName", "");
		System.setProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY, "true");
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);
		updater.conversions = (p, cache) -> conversion(p, basePath, cache);

		File shelf = tempFolder.newFolder("webshelf");
		BookUpdater.Result r = updater.download(base + "/novel/", shelf.toPath());
		assertTrue(r.message(), r.ok());
		File[] books = shelf.listFiles((d, n) -> n.endsWith(".epub"));
		org.junit.Assert.assertEquals("本は Web 本棚の直下", 1, books.length);
		org.junit.Assert.assertEquals("[著者] 題.epub", books[0].getName());
		File work = new File(shelf, ".aozora/" + fqdn.replace(':', '_') + "/novel");
		assertTrue("記録は .aozora に", new File(work, com.github.hmdev.info.BookLedger.FILE_NAME).isFile());
		assertFalse("設定のキャッシュは使わない", settingsCache.exists());

		episodes += "<li><a href=\"/ep/2/\">第2話</a></li>";
		BookUpdater.Result updated = updater.update(base + "/novel/", books[0].toPath());
		assertTrue(updated.message(), updated.ok());
		assertTrue("続きも .aozora で（1 つ前の版がそこにできる）",
			work.listFiles((d, n) -> n.startsWith("previous ")).length == 1);
		assertFalse(settingsCache.exists());

		//Web 本棚の下のフォルダに整理しても、上の .aozora を使う（PR のゲート2）
		File sub = new File(shelf, "整理");
		assertTrue(sub.mkdir());
		File moved = new File(sub, books[0].getName());
		Files.move(books[0].toPath(), moved.toPath());
		episodes += "<li><a href=\"/ep/3/\">第3話</a></li>";
		BookUpdater.Result again = updater.update(base + "/novel/", moved.toPath());
		assertTrue(again.message(), again.ok());
		assertFalse(settingsCache.exists());

		//整理した本と同じ作品をもう一度落とそうとしても、2 冊目を作らない（取りに行く前に断る。PR の codex）
		BookUpdater.Result twice = updater.download(base + "/novel/", shelf.toPath());
		assertFalse(twice.ok());
		assertTrue(twice.message(), twice.message().contains("もう Web 本棚にある本です") && twice.message().contains("整理"));
		org.junit.Assert.assertEquals(0, shelf.listFiles((d, n) -> n.endsWith(".epub")).length);

		//名前を変える: 本・本ごとの話数・1 つ前の版・作品の名前が一緒に動き、そのまま続きを取れる（internal #11 の案 A）
		File previousBefore = new File(work, HeadlessWebConversion.previousEpubName(work, moved));
		assertTrue(previousBefore.isFile());
		int ownBefore = com.github.hmdev.info.BookLedger.load(work).ownEpisodesFor(moved);
		org.junit.Assert.assertEquals(3, ownBefore);
		BookUpdater.Result renamed = updater.rename(base + "/novel/", moved.toPath(), "新しい名前");
		assertTrue(renamed.message(), renamed.ok());
		File renamedBook = new File(sub, "新しい名前.epub");
		assertTrue(renamedBook.isFile());
		assertFalse(moved.exists());
		com.github.hmdev.info.BookLedger after = com.github.hmdev.info.BookLedger.load(work);
		org.junit.Assert.assertEquals("新しい名前", after.outputBaseName);
		org.junit.Assert.assertEquals(3, after.ownEpisodesFor(renamedBook));
		org.junit.Assert.assertEquals(-1, after.ownEpisodesFor(moved));
		assertFalse(previousBefore.exists());
		assertTrue(new File(work, HeadlessWebConversion.previousEpubName(work, renamedBook)).isFile());
		episodes += "<li><a href=\"/ep/4/\">第4話</a></li>";
		BookUpdater.Result afterRename = updater.update(base + "/novel/", renamedBook.toPath());
		assertTrue(afterRename.message(), afterRename.ok());
		assertTrue("名前を変えた本を見つける", updater.download(base + "/novel/", shelf.toPath()).message().contains("新しい名前"));

		//同じ名前の本がもうあれば変えない
		File other = new File(sub, "ほかの本.epub");
		Files.write(other.toPath(), new byte[]{1});
		BookUpdater.Result clash = updater.rename(base + "/novel/", renamedBook.toPath(), "ほかの本");
		assertFalse(clash.ok());
		assertTrue(renamedBook.isFile());
		org.junit.Assert.assertArrayEquals(new byte[]{1}, Files.readAllBytes(other.toPath()));
		assertFalse("使えない名前", updater.rename(base + "/novel/", renamedBook.toPath(), "a/b").ok());
	}

	/** キャッシュの場所の相対パスは、基のフォルダから（CLI を別のフォルダから起こしても GUI と同じキャッシュ。PR #118 のゲート2） */
	@Test
	public void aRelativeCachePathIsUnderTheBaseFolder() {
		Properties p = new Properties();
		p.setProperty("CachePath", ".cache");
		org.junit.Assert.assertEquals(new File("/opt/aozora/.cache"), HeadlessBookUpdater.cachePathOf(p, "/opt/aozora/"));
		org.junit.Assert.assertEquals("GUI の基は '' ＝カレント", new File(".cache"), HeadlessBookUpdater.cachePathOf(p, ""));
		//絶対パスは、その OS の本物の絶対パスで（Windows ではドライブ名の無い /abs/cache は絶対パスにならない。win2 の確認）
		String absolute = new File(System.getProperty("java.io.tmpdir"), "abs-cache").getAbsolutePath();
		p.setProperty("CachePath", absolute);
		org.junit.Assert.assertEquals(new File(absolute), HeadlessBookUpdater.cachePathOf(p, "/opt/aozora/"));
		org.junit.Assert.assertEquals("空なら .cache", new File("/opt/aozora/.cache"), HeadlessBookUpdater.cachePathOf(new Properties(), "/opt/aozora/"));
	}

	/** 設定は Web 変換の鍵を取ってから写す（GUI が部品を一時的に書き換えている間の値を写さない。PR #118 のゲート2） */
	@Test
	public void settingsAreTakenWhileHoldingTheWebLock() throws Exception {
		java.util.concurrent.atomic.AtomicBoolean held = new java.util.concurrent.atomic.AtomicBoolean(false);
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> {
			held.set(com.github.hmdev.web.WebAozoraConverter.WEB_LOCK.isHeldByCurrentThread());
			return new Properties();
		}, "");
		updater.conversions = (props, cache) -> { throw new IllegalStateException("ここまで来れば十分"); };
		try {
			updater.update("https://ncode.syosetu.com/n1234ab/", tempFolder.newFile("b.epub").toPath());
		} catch (IllegalStateException e) {
			/* 意図的: 変換は作らない */
		}
		assertTrue(held.get());
		assertFalse("鍵を外し忘れた", com.github.hmdev.web.WebAozoraConverter.WEB_LOCK.isLocked());
	}

	/** どの入口も、変換が投げても鍵を外して返る（試験は同じスレッドなので、外し忘れは再入では見えない。isLocked で見る。internal #22） */
	@Test
	public void everyEntryReleasesTheWebLock() throws Exception {
		HeadlessBookUpdater updater = new HeadlessBookUpdater(Properties::new, "");
		updater.conversions = (props, cache) -> { throw new IllegalStateException("変換は作らない"); };
		String url = "https://ncode.syosetu.com/n1234ab/";
		java.nio.file.Path book = tempFolder.newFile("c.epub").toPath();
		java.nio.file.Path shelf = tempFolder.newFolder("shelf").toPath();
		for (java.util.concurrent.Callable<?> call : java.util.List.<java.util.concurrent.Callable<?>>of(
				() -> updater.update(url, book),
				() -> updater.download(url, shelf),
				() -> updater.check(url, book),
				() -> updater.regenerate(url, book),
				() -> updater.refetch(url, book, false),
				() -> updater.rename(url, book, "新しい名前"))) {
			try { call.call(); } catch (Exception e) { /* 意図的: 投げても鍵を外すかだけを見る */ }
			assertFalse("鍵を外し忘れた", com.github.hmdev.web.WebAozoraConverter.WEB_LOCK.isLocked());
		}
	}

	/**
	 * .aozora を使うのは、その中にこの作品の台帳があるときだけ（前から本のあるフォルダを Web 本棚にしても、
	 * 前からの本は設定のキャッシュのまま＝話数の記録と名前を失わない。PR の手元の codex）
	 */
	@Test
	public void theShelfCacheIsUsedOnlyForItsOwnWorks() throws Exception {
		File shelf = tempFolder.newFolder("mixed");
		File work = new File(shelf, ".aozora/example.com/novel");
		assertTrue(work.mkdirs());
		com.github.hmdev.info.BookLedger.create("https://example.com/novel/", "[a] 題").save(work);
		Properties props = new Properties();
		File settingsCache = tempFolder.newFolder("settings-cache");
		props.setProperty("CachePath", settingsCache.getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		File book = new File(shelf, "sub/book.epub");
		org.junit.Assert.assertEquals(new File(shelf, ".aozora"), updater.cacheFor(props, book.toPath(), "https://example.com/novel/"));
		org.junit.Assert.assertEquals("前からの別の作品は設定のキャッシュ", settingsCache,
			updater.cacheFor(props, book.toPath(), "https://ncode.syosetu.com/n0000aa/"));
	}

	/** 深いフォルダで、更新のときに縮められる長さの名前には変えない（変えると続きを取れなくなる。PR の手元の codex） */
	@Test
	public void aNameTooLongForTheFolderIsRefused() throws Exception {
		File dir = tempFolder.newFolder("deep");
		while (dir.toPath().toRealPath().toString().length() < 120) {
			dir = new File(dir, "d".repeat(20));
			assertTrue(dir.mkdirs() || dir.isDirectory());
		}
		File book = new File(dir, "book.epub");
		Files.write(book.toPath(), new byte[]{1});
		Properties props = new Properties();
		props.setProperty("CachePath", tempFolder.newFolder("c").getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		BookUpdater.Result r = updater.rename("https://example.com/novel/", book.toPath(), "x".repeat(180));
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("長すぎます"));
		assertTrue(book.isFile());
		assertTrue(updater.rename("https://example.com/novel/", book.toPath(), "short").ok());
		org.junit.Assert.assertFalse(".epub の本を .kepub.epub に見せない", updater.rename("https://example.com/novel/", new File(dir, "short.epub").toPath(), "short.kepub").ok());
	}

	/** 名前を変えても本の形（.fxl.kepub.epub）を保つ。大文字と小文字だけの変更もできる。消えた本は更新しない（PR のゲート2） */
	@Test
	public void renamingKeepsTheFormatAndHandlesCaseOnlyChanges() throws Exception {
		File dir = tempFolder.newFolder("books");
		File book = new File(dir, "abc.fxl.kepub.epub");
		Files.write(book.toPath(), new byte[]{1});
		Properties props = new Properties();
		props.setProperty("CachePath", tempFolder.newFolder("c").getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		//名前の末尾で本の形が変わって見える名前は断る（Kobo の本に .fxl を足すと固定レイアウトに見える）
		File kobo = new File(dir, "kobo.kepub.epub");
		Files.write(kobo.toPath(), new byte[]{2});
		org.junit.Assert.assertFalse(updater.rename("https://example.com/novel/", kobo.toPath(), "kobo.fxl").ok());
		assertTrue("同じ形のままなら付けてよい", updater.rename("https://example.com/novel/", book.toPath(), "abc.fxl").ok());
		book = new File(dir, "abc.fxl.fxl.kepub.epub");
		Files.delete(kobo.toPath());
		assertTrue(updater.rename("https://example.com/novel/", book.toPath(), "def").ok());
		File def = new File(dir, "def.fxl.kepub.epub");
		assertTrue(def.isFile());
		BookUpdater.Result cased = updater.rename("https://example.com/novel/", def.toPath(), "DEF");
		assertTrue(cased.message(), cased.ok());
		org.junit.Assert.assertEquals(java.util.List.of("DEF.fxl.kepub.epub"), java.util.Arrays.asList(dir.list()));
		//消えた本（名前を変えた前の名前）は更新しない（更新の待ちの間に名前を変えたとき、前の名前の本を作り直さない）
		BookUpdater.Result gone = updater.update("https://example.com/novel/", book.toPath());
		assertFalse(gone.ok());
		assertTrue(gone.message(), gone.message().contains("見つかりません"));
	}

	/** 設定のキャッシュの台帳（GUI で変換した作品）では、本の名前を変えても作品の名前は変えない（GUI の次の変換が 2 冊目を作らない） */
	@Test
	public void renamingAGuiBookKeepsTheWorkName() throws Exception {
		//設定のキャッシュが .aozora という名前のフォルダの下にあっても、Web 本棚の記録と見なさない（PR の codex）
		File cache = new File(tempFolder.newFolder("home"), ".aozora/cache");
		assertTrue(cache.mkdirs());
		File work = new File(cache, "example.com/novel");
		assertTrue(work.mkdirs());
		com.github.hmdev.info.BookLedger.create("https://example.com/novel/", "[a] 題").withOutputBaseName("[a] 題").save(work);
		File dir = tempFolder.newFolder("gui-out");
		File book = new File(dir, "[a] 題.epub");
		Files.write(book.toPath(), new byte[]{1});
		Properties props = new Properties();
		props.setProperty("CachePath", cache.getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		assertTrue(updater.rename("https://example.com/novel/", book.toPath(), "好きな名前").ok());
		org.junit.Assert.assertEquals("[a] 題", com.github.hmdev.info.BookLedger.load(work).outputBaseName);
	}

	/**
	 * 大文字小文字だけを変えても、本ごとの話数と 1 つ前の版が新しい名前に動く（古い印は動かす前に取る。
	 * 動かした後では古い名前も新しい綴りに正規化される。win2 が Windows で実測。mac の APFS でも同じ）
	 */
	/**
	 * 名前を変えると、残っている控えの印も新しい本の印になる。動かさないと、次に片付けるときに新しい印で探すので残り続ける
	 * （internal #26 の PR のゲート2）。大文字小文字だけの変更でも、古い印は動かす前に取る
	 */
	@Test
	public void aRenameMovesTheLeftoverBackups() throws Exception {
		for (String newName : new String[]{ "xyz", "ABC" }) {
			File shelf = tempFolder.newFolder("backupshelf-" + newName);
			File work = new File(shelf, ".aozora/example.com/novel");
			assertTrue(work.mkdirs());
			File abc = new File(shelf, "abc.epub");
			Files.write(abc.toPath(), new byte[]{1});
			com.github.hmdev.info.BookLedger.create("https://example.com/novel/", "[a] 題").withOutputBaseName("abc")
				.withBookEpisodes(abc, 7).save(work);
			File leftover = new File(work, HeadlessWebConversion.backupPrefix(abc) + "123.tmp");
			Files.write(leftover.toPath(), new byte[]{3});
			Properties props = new Properties();
			props.setProperty("CachePath", tempFolder.newFolder("c-" + newName).getAbsolutePath());
			HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
			String oldPrefix = HeadlessWebConversion.backupPrefix(abc);
			assertTrue(updater.rename("https://example.com/novel/", abc.toPath(), newName).ok());
			File renamed = new File(shelf, newName + ".epub");
			File moved = new File(work, HeadlessWebConversion.backupPrefix(renamed) + "123.tmp");
			assertTrue(newName + ": 控えは新しい印の名前になる", moved.isFile());
			org.junit.Assert.assertArrayEquals(new byte[]{3}, Files.readAllBytes(moved.toPath()));
			org.junit.Assert.assertEquals(newName + ": 古い印の控えは残らない", 0, work.listFiles((d, n) -> n.startsWith(oldPrefix)).length);
		}
	}

	@Test
	public void aCaseOnlyRenameMovesTheRecords() throws Exception {
		File shelf = tempFolder.newFolder("caseshelf");
		File work = new File(shelf, ".aozora/example.com/novel");
		assertTrue(work.mkdirs());
		File abc = new File(shelf, "abc.epub");
		Files.write(abc.toPath(), new byte[]{1});
		com.github.hmdev.info.BookLedger.create("https://example.com/novel/", "[a] 題").withOutputBaseName("abc")
			.withBookEpisodes(abc, 7).save(work);
		File previous = new File(work, HeadlessWebConversion.previousEpubName(work, abc));
		Files.write(previous.toPath(), new byte[]{2});
		Properties props = new Properties();
		props.setProperty("CachePath", tempFolder.newFolder("c").getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		assertTrue(updater.rename("https://example.com/novel/", abc.toPath(), "ABC").ok());
		File upper = new File(shelf, "ABC.epub");
		org.junit.Assert.assertEquals(java.util.List.of("ABC.epub"),
			java.util.Arrays.stream(shelf.list()).filter(n -> n.endsWith(".epub")).toList());
		com.github.hmdev.info.BookLedger ledger = com.github.hmdev.info.BookLedger.load(work);
		org.junit.Assert.assertEquals(7, ledger.ownEpisodesFor(upper));
		org.junit.Assert.assertEquals("ABC", ledger.outputBaseName);
		long keys = Files.readAllLines(new File(work, com.github.hmdev.info.BookLedger.FILE_NAME).toPath()).stream()
			.filter(l -> l.startsWith("episodes.")).count();
		org.junit.Assert.assertEquals("古い印の記録を残さない", 1, keys);
		File[] previousFiles = work.listFiles((d, n) -> n.startsWith("previous "));
		org.junit.Assert.assertEquals(1, previousFiles.length);
		org.junit.Assert.assertEquals(HeadlessWebConversion.previousEpubName(work, upper), previousFiles[0].getName());
	}

	/** 元の本を指すリンクの名前には変えない（大文字小文字の変更と見なして本を一時ファイルに残さない。PR の codex） */
	@Test
	public void aLinkToTheBookIsNotACaseRename() throws Exception {
		File dir = tempFolder.newFolder("links");
		File book = new File(dir, "book.epub");
		Files.write(book.toPath(), new byte[]{1});
		java.nio.file.Path alias = dir.toPath().resolve("alias.epub");
		try {
			Files.createSymbolicLink(alias, book.toPath());
		} catch (UnsupportedOperationException | java.io.IOException e) {
			org.junit.Assume.assumeNoException("シンボリックリンクを作れない環境", e);
		}
		Properties props = new Properties();
		props.setProperty("CachePath", tempFolder.newFolder("c").getAbsolutePath());
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, "");
		BookUpdater.Result r = updater.rename("https://example.com/novel/", book.toPath(), "alias");
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("同じ名前"));
		assertTrue(book.isFile());
		org.junit.Assert.assertEquals(0, dir.listFiles((d, n) -> n.startsWith(".rename.")).length);
	}
}
