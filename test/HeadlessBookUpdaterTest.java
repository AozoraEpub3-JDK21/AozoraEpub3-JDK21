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
				"http://0.0.0.0/x", "http://[fd00::1]/x", "http://[fe80::1]/x" }) {
			assertTrue(url, HeadlessBookUpdater.isLocalOrPrivate(url));
		}
		for (String url : new String[]{ "https://ncode.syosetu.com/n1234ab/", "https://kakuyomu.jp/works/1", "http://8.8.8.8/x",
				"https://[2001:db8::1]/x" }) {
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

	/** 本棚の本を、取り直した内容で上書きし、前の本を作品のフォルダに previous.epub として残す */
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
		Files.createSymbolicLink(new File(root, "template").toPath(), VelocityTestUtils.templateDir());
		String basePath = root.getAbsolutePath() + File.separator;
		Properties props = new Properties();
		props.load(Files.newInputStream(new File(repo, "test_data/gui_default_settings.ini").toPath()));
		props.setProperty("CachePath", new File(root, "cache").getAbsolutePath());
		System.setProperty(HeadlessBookUpdater.ALLOW_LOCAL_PROPERTY, "true");
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);

		//最初の本（1 話）を作る
		File shelf = tempFolder.newFolder("shelf");
		HeadlessWebConversion.Result first = new HeadlessWebConversion(props, basePath, new File(root, "cache"))
			.convert(base + "/novel/", shelf, null, true);
		assertTrue(first.message(), first.ok());
		byte[] before = Files.readAllBytes(first.epub().toPath());

		//2 話目が出たので続きを取る
		episodes += "<li><a href=\"/ep/2/\">第2話</a></li>";
		BookUpdater.Result r = updater.update(base + "/novel/", first.epub().toPath());
		assertTrue(r.message(), r.ok());
		assertFalse("本棚の本が新しくなる", java.util.Arrays.equals(before, Files.readAllBytes(first.epub().toPath())));
		File previous = new File(root, "cache/" + fqdn.replace(':', '_') + "/novel/" + HeadlessWebConversion.PREVIOUS_EPUB);
		assertTrue("1 つ前の版が残る: " + previous, previous.isFile());
		assertArrayEquals(before, Files.readAllBytes(previous.toPath()));
		assertTrue("ほかの名前の本が増えない", shelf.list().length == 1);
	}
}
