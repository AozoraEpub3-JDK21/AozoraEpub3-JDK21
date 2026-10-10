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
		File siteDir = new File(root, "web/" + fqdn);
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
		File siteDir = new File(root, "web/" + fqdn);
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
			held.set(Thread.holdsLock(com.github.hmdev.web.WebAozoraConverter.WEB_LOCK));
			return new Properties();
		}, "");
		updater.conversions = (props, cache) -> { throw new IllegalStateException("ここまで来れば十分"); };
		try {
			updater.update("https://ncode.syosetu.com/n1234ab/", tempFolder.newFile("b.epub").toPath());
		} catch (IllegalStateException e) {
			/* 意図的: 変換は作らない */
		}
		assertTrue(held.get());
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
}
