import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.zip.ZipFile;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 画面なしの Web 変換のテスト（internal #11 の H2）。手元のサーバの作品を、GUI の ini の値で EPUB にする。
 * GUI と同じ EPUB になることは、本物の GUI と比べて確かめた（PR に記録）。ここでは道筋と、断る場合を固定する。
 */
public class HeadlessWebConversionTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpServer server;
	private String fqdn;
	private final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
	/** 手元のサーバの目次に並べる話数と、目次の HTTP の状態（試験の途中で変える） */
	private volatile int episodes = 1;
	private volatile int listStatus = 200;

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		if (fqdn != null) com.github.hmdev.web.WebAozoraConverterTestAccess.forget(fqdn);
	}

	private void respond(HttpExchange exchange, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	/** 手元のサーバと、そのサイト定義・注記の辞書を置いた基のフォルダを作る。基のフォルダのパス（末尾に区切り）を返す */
	private String serveAndBase() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/novel/")) {
				if (listStatus != 200) {
					exchange.sendResponseHeaders(listStatus, -1);
					exchange.close();
					return;
				}
				StringBuilder list = new StringBuilder();
				for (int i = 1; i <= episodes; i++) {
					list.append("<li><a href=\"/ep/").append(i).append("/\">第").append(i).append("話</a><span class=\"up\">2026/01/01</span></li>");
				}
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">" + list + "</ul></body></html>");
			} else {
				String n = path.replaceAll("\\D", "");
				respond(exchange, "<html><body><h2>第" + n + "話</h2><div class=\"body\"><p>" + n + "話目</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		File siteDir = new File(root, "web/" + fqdn);
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a", "SUB_UPDATE\t.list .up", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
			.getBytes(StandardCharsets.UTF_8));
		//変換器は基のフォルダの注記の辞書（chuki_*.txt）を読む
		File repo = VelocityTestUtils.templateDir().getParent().toFile();
		for (File f : repo.listFiles((d, n) -> n.startsWith("chuki_") && n.endsWith(".txt"))) {
			Files.copy(f.toPath(), new File(root, f.getName()).toPath());
		}
		lastBasePath = root.getAbsolutePath() + File.separator;
		return lastBasePath;
	}

	private HeadlessWebConversion conversion(Properties props, String basePath) throws Exception {
		Epub3Writer writer = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		Epub3Writer imageWriter = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		if (lastCache == null) lastCache = tempFolder.newFolder("cache");
		return new HeadlessWebConversion(props, basePath, lastCache, writer, imageWriter);
	}

	private Properties guiDefaults() throws Exception {
		Properties p = new Properties();
		p.load(Files.newInputStream(new File(VelocityTestUtils.templateDir().getParent().toFile(), "test_data/gui_default_settings.ini").toPath()));
		return p;
	}

	@Test
	public void aWebWorkBecomesAnEpubWithTheGuiSettings() throws Exception {
		String basePath = serveAndBase();
		Properties props = guiDefaults();
		props.setProperty("Ext", ".kepub.epub");
		File dst = tempFolder.newFolder("out");
		HeadlessWebConversion.Result r = conversion(props, basePath).convert("http://" + fqdn + "/novel/", dst, null, true);
		assertTrue(r.message(), r.ok());
		assertNotNull(r.epub());
		assertEquals("[著者] 題.kepub.epub", r.epub().getName());
		try (ZipFile zip = new ZipFile(r.epub())) {
			String opf = new String(zip.getInputStream(zip.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
			assertTrue("掲載元の URL を書く: " + opf, opf.contains("<dc:source>http://" + fqdn + "/novel/</dc:source>"));
		}
	}

	@Test
	public void kindleIsRefused() throws Exception {
		String basePath = serveAndBase();
		Properties props = guiDefaults();
		props.setProperty("Ext", ".mobi");
		File dst = tempFolder.newFolder("out");
		HeadlessWebConversion.Result r = conversion(props, basePath).convert("http://" + fqdn + "/novel/", dst, null, true);
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("kindle"));
		assertEquals(0, dst.list().length);
		assertEquals("取りに行く前に断る（取ってから断ると、次の更新で更新なしに見える）", 0, requests.get());
	}

	/** 本棚の本と違う名前に出力されるときは書かない（別の本として増えないように） */
	@Test
	public void aDifferentNameFromTheBookIsNotWritten() throws Exception {
		String basePath = serveAndBase();
		File dst = tempFolder.newFolder("out");
		File book = new File(dst, "別の名前.epub");
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", dst, book, true);
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("違う名前"));
		assertEquals(0, dst.list().length);
		//違う名前を台帳に記録しない（記録すると、以後の変換がその名前になって本が 2 冊になる。#116 のゲート2）
		com.github.hmdev.info.BookLedger ledger = ledgerOf();
		assertNotNull(ledger);
		assertEquals(null, ledger.outputBaseName);

		//名前が合えば記録する
		HeadlessWebConversion.Result ok = conversion(guiDefaults(), basePathOf()).convert("http://" + fqdn + "/novel/", dst, new File(dst, "[著者] 題.epub"), true);
		assertTrue(ok.message(), ok.ok());
		assertEquals("[著者] 題", ledgerOf().outputBaseName);
	}

	private String lastBasePath;
	private File lastCache;

	private String basePathOf() { return lastBasePath; }

	private com.github.hmdev.info.BookLedger ledgerOf() {
		return com.github.hmdev.info.BookLedger.load(new File(lastCache, fqdn.replace(':', '_') + "/novel"));
	}

	@Test
	public void anExistingBookIsNotOverwrittenUnlessAsked() throws Exception {
		String basePath = serveAndBase();
		File dst = tempFolder.newFolder("out");
		Files.write(new File(dst, "[著者] 題.epub").toPath(), new byte[]{1});
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", dst, null, false);
		assertFalse(r.ok());
		assertEquals(1, Files.size(new File(dst, "[著者] 題.epub").toPath()));
	}

	/** 旧形式の ini は表紙の選択肢の表示名をそのまま書いていた。GUI と同じく選択肢として読む（PR #116 の codex） */
	@Test
	public void oldCoverLabelsAreReadAsTheChoices() {
		assertEquals("", HeadlessWebConversion.normalizeCover("[先頭の挿絵]"));
		assertEquals(HeadlessWebConversion.COVER_SAME_FILE, HeadlessWebConversion.normalizeCover("[入力ファイル名と同じ画像(png,jpg)]"));
		assertEquals(HeadlessWebConversion.COVER_NONE, HeadlessWebConversion.normalizeCover("[表紙無し]"));
		assertEquals(HeadlessWebConversion.COVER_SAME_FILE, HeadlessWebConversion.normalizeCover("[Same name as input (png,jpg)]"));
		assertEquals(HeadlessWebConversion.COVER_NONE, HeadlessWebConversion.normalizeCover("[No cover]"));
		assertEquals("今の形はそのまま", HeadlessWebConversion.COVER_NONE, HeadlessWebConversion.normalizeCover(HeadlessWebConversion.COVER_NONE));
		assertEquals("直接の指定はそのまま", "/x/cover.jpg", HeadlessWebConversion.normalizeCover("/x/cover.jpg"));
	}

	/**
	 * EPUB を書いている間も Web 変換の鍵を持つ（PR #118 の codex）。手放すと、同じ作品を GUI が変換したときに、
	 * 読んでいる途中の txt（キャッシュ）が書き直される
	 */
	@Test
	public void theWebLockIsHeldUntilTheEpubIsWritten() throws Exception {
		String basePath = serveAndBase();
		java.util.concurrent.atomic.AtomicBoolean heldWhileWriting = new java.util.concurrent.atomic.AtomicBoolean(false);
		Epub3Writer writer = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath("")) {
			@Override
			public void write(com.github.hmdev.converter.AozoraEpub3Converter converter, java.io.BufferedReader src, File srcFile, String srcExt,
					File epubFile, com.github.hmdev.info.BookInfo bookInfo, com.github.hmdev.image.ImageInfoReader imageInfoReader) throws Exception {
				heldWhileWriting.set(Thread.holdsLock(com.github.hmdev.web.WebAozoraConverter.WEB_LOCK));
				super.write(converter, src, srcFile, srcExt, epubFile, bookInfo, imageInfoReader);
			}
		};
		Epub3Writer imageWriter = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		HeadlessWebConversion.Result r = new HeadlessWebConversion(guiDefaults(), basePath, tempFolder.newFolder("cache2"), writer, imageWriter)
			.convert("http://" + fqdn + "/novel/", tempFolder.newFolder("out"), null, true);
		assertTrue(r.message(), r.ok());
		assertTrue("EPUB を書いている間、鍵を持っている", heldWhileWriting.get());
	}

	/**
	 * 変換が途中で失敗しても、本棚の本は消えずに元のまま（PR #118 のゲート2）。書き出しは失敗すると出力を消すので、
	 * 一時ファイルに書いてから置き換える。一時ファイルも残さない
	 */
	@Test
	public void aFailedConversionLeavesTheShelfBookAlone() throws Exception {
		String basePath = serveAndBase();
		File dst = tempFolder.newFolder("out");
		File book = new File(dst, "[著者] 題.epub");
		Files.write(book.toPath(), "元の本".getBytes(StandardCharsets.UTF_8));
		Epub3Writer failing = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath("")) {
			@Override
			public void write(com.github.hmdev.converter.AozoraEpub3Converter converter, java.io.BufferedReader src, File srcFile, String srcExt,
					File epubFile, com.github.hmdev.info.BookInfo bookInfo, com.github.hmdev.image.ImageInfoReader imageInfoReader) throws Exception {
				Files.write(epubFile.toPath(), new byte[]{ 1, 2, 3 });
				throw new IOException("書いている途中で失敗");
			}
		};
		Epub3Writer imageWriter = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		HeadlessWebConversion.Result r = new HeadlessWebConversion(guiDefaults(), basePath, tempFolder.newFolder("cache3"), failing, imageWriter)
			.convert("http://" + fqdn + "/novel/", dst, book, true);
		assertFalse(r.ok());
		assertEquals("本棚の本は元のまま", "元の本", new String(Files.readAllBytes(book.toPath()), StandardCharsets.UTF_8));
		assertEquals("一時ファイルを残さない", java.util.List.of("[著者] 題.epub"), java.util.Arrays.asList(dst.list()));
	}

	/** 置き換えた本は、前の本の権限を引き継ぐ（PR #118 の codex） */
	@Test
	public void theReplacedBookKeepsItsPermissions() throws Exception {
		String basePath = serveAndBase();
		File dst = tempFolder.newFolder("out");
		File book = new File(dst, "[著者] 題.epub");
		Files.write(book.toPath(), "元の本".getBytes(StandardCharsets.UTF_8));
		java.util.Set<java.nio.file.attribute.PosixFilePermission> perms;
		try {
			perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
			Files.setPosixFilePermissions(book.toPath(), perms);
		} catch (UnsupportedOperationException e) {
			Assume.assumeNoException("POSIX の権限が無い環境", e);
			return;
		}
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", dst, book, true);
		assertTrue(r.message(), r.ok());
		assertEquals(perms, Files.getPosixFilePermissions(book.toPath()));
	}

	// ---- 本棚の更新の守り（internal #11）: 目次が取れない・話数が減ったときは上書きしない ----

	/** 1 回変換して本棚の本を作り、その本を返す */
	private File shelfBook(String basePath, File dst) throws Exception {
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", dst, null, true);
		assertTrue(r.message(), r.ok());
		return r.epub();
	}

	private HeadlessWebConversion.Result guardedUpdate(String basePath, File book, boolean allowFewer) throws Exception {
		return conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, allowFewer);
	}

	/** 変換のたびに、目次の話数を台帳に書く（GUI の変換でも。次の本棚の更新が比べる元になる） */
	@Test
	public void theEpisodeCountIsRecordedInTheLedger() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		shelfBook(basePath, tempFolder.newFolder("out"));
		assertEquals(3, ledgerOf().episodes);
		episodes = 4;
		shelfBook(basePath, tempFolder.newFolder("out2"));
		assertEquals("増えたら書き直す", 4, ledgerOf().episodes);
	}

	/** 話数が減ったら、本棚の本も台帳の話数も変えずに止める */
	@Test
	public void fewerEpisodesStopTheUpdate() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] before = Files.readAllBytes(book.toPath());
		episodes = 2;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.ok());
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, r.stop());
		assertTrue(r.message(), r.message().contains("前 3 話 → 今 2 話"));
		org.junit.Assert.assertArrayEquals("本棚の本はそのまま", before, Files.readAllBytes(book.toPath()));
		assertEquals("台帳の話数を下げない（下げると、もう一度押すだけで通ってしまう）", 3, ledgerOf().episodes);
		assertFalse("前の版も作らない", new File(ledgerDir(), HeadlessWebConversion.PREVIOUS_EPUB).exists());
	}

	/** 利用者が「減ったまま更新する」を選んだら取り直し、台帳の話数も今の数にする */
	@Test
	public void fewerEpisodesCanBeAccepted() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 2;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, true);
		assertTrue(r.message(), r.ok());
		assertEquals(2, ledgerOf().episodes);
		assertTrue("前の版を残す", new File(ledgerDir(), HeadlessWebConversion.PREVIOUS_EPUB).exists());
	}

	/** 守りを付けない変換（GUI・CLI）は、話数が減っても今までどおり作る */
	@Test
	public void conversionsWithoutTheGuardStillRunWithFewerEpisodes() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 2;
		shelfBook(basePath, tempFolder.newFolder("out2"));
		assertEquals(2, ledgerOf().episodes);
	}

	/** 目次が 404 なら「作品が見つからない」で止め、キャッシュの古い目次で作り直さない */
	@Test
	public void aMissingWorkStopsTheUpdate() throws Exception {
		String basePath = serveAndBase();
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] before = Files.readAllBytes(book.toPath());
		listStatus = 404;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.ok());
		assertEquals(HeadlessWebConversion.STOP_GONE, r.stop());
		assertTrue(r.message(), r.message().contains("404"));
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(book.toPath()));
	}

	/** 404 以外で目次が取れないときも止める。理由は「失敗」で、作品が消えたとは言わない */
	@Test
	public void anUnreachableListFailsTheUpdateWithoutCallingTheWorkGone() throws Exception {
		String basePath = serveAndBase();
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		listStatus = 503;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.ok());
		assertEquals(null, r.stop());
		assertTrue(r.message(), r.message().contains("503"));
	}

	/** 守りを付けない変換は、目次が取れなくても今までどおりキャッシュの目次で作る */
	@Test
	public void conversionsWithoutTheGuardStillUseTheCachedList() throws Exception {
		String basePath = serveAndBase();
		shelfBook(basePath, tempFolder.newFolder("out"));
		listStatus = 404;
		shelfBook(basePath, tempFolder.newFolder("out2"));
	}

	/** 変換器は GUI と使い回すので、本棚の更新の後に守りを残さない（残ると GUI の変換までキャッシュの目次を使わなくなる） */
	@Test
	public void theGuardIsOffAgainAfterAnUpdate() throws Exception {
		String basePath = serveAndBase();
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		assertTrue(guardedUpdate(basePath, book, true).ok());
		//GUI は守りを立て直さずに同じ変換器を使うので、使い回される変換器そのものを見る
		com.github.hmdev.web.WebAozoraConverter shared = com.github.hmdev.web.WebAozoraConverter.createWebAozoraConverter(
			"http://" + fqdn + "/novel/", new File(basePath + "web"));
		assertFalse(shared.updateGuard);
		assertFalse(shared.allowFewerEpisodes);
	}

	private File ledgerDir() {
		return new File(lastCache, fqdn.replace(':', '_') + "/novel");
	}
}
