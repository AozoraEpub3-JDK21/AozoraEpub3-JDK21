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
				respond(exchange, "<html><body><h1>題</h1><p class=\"author\">著者</p><ul class=\"list\">"
					+ "<li><a href=\"/ep/1/\">第1話</a><span class=\"up\">2026/01/01</span></li></ul></body></html>");
			} else {
				respond(exchange, "<html><body><h2>第1話</h2><div class=\"body\"><p>一話目</p></div></body></html>");
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
}
