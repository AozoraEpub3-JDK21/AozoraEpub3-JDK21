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

import com.github.hmdev.preview.BookUpdater;
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
	/** 目次を 2 ページに分ける（1 ページ目に 2 話、残りを 2 ページ目に）と、2 ページ目の HTTP の状態 */
	private volatile boolean paged = false;
	private volatile int page2Status = 200;
	/** 目次に出す各話の更新日（改稿を作るときに変える） */
	private volatile String upDate = "2026/01/01";
	/** 作品の更新日（null なら一覧に出さない。update.txt の 1 行目になる） */
	private volatile String workUpdate = null;
	/** 作品の題 */
	private volatile String workTitle = "題";
	/** 話のページを頼まれた回数（確かめるだけのときは 0 のまま） */
	private final java.util.concurrent.atomic.AtomicInteger episodeRequests = new java.util.concurrent.atomic.AtomicInteger();
	/** 0 話のとき、一覧のページに本文（告知）を置くか */
	private volatile boolean noticeWhenEmpty = true;

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
			if (path.equals("/novel/") || path.equals("/novel2/")) {
				if (listStatus != 200) {
					exchange.sendResponseHeaders(listStatus, -1);
					exchange.close();
					return;
				}
				boolean secondPage = "p=2".equals(exchange.getRequestURI().getQuery());
				if (secondPage && page2Status != 200) {
					exchange.sendResponseHeaders(page2Status, -1);
					exchange.close();
					return;
				}
				int from = paged && secondPage ? 3 : 1;
				int to = paged && !secondPage ? Math.min(2, episodes) : episodes;
				StringBuilder list = new StringBuilder();
				if (paged && !secondPage) list.append("<li><a class=\"pager\" href=\"/novel/?p=2\">最後</a></li>");
				for (int i = from; i <= to; i++) {
					list.append("<li><a href=\"/ep/").append(i).append("/\">第").append(i).append("話</a><span class=\"up\">").append(upDate).append("</span></li>");
				}
				//0 話のときは、一覧のページに本文だけがある（告知だけ残して話を消した、など。変換器は 1 ページの作品として読む）
				String notice = episodes == 0 && noticeWhenEmpty ? "<div class=\"body\"><p>お知らせ</p></div>" : "";
				String work = workUpdate != null ? "<p class=\"workup\">" + workUpdate + "</p>" : "";
				respond(exchange, "<html><body><h1>" + workTitle + "</h1><p class=\"author\">著者</p>" + work + "<ul class=\"list\">" + list + "</ul>" + notice + "</body></html>");
			} else {
				episodeRequests.incrementAndGet();
				String n = path.replaceAll("\\D", "");
				//本文は話ごとに違う仮名の印（数字は縦中横などで書き換わるので、本文から探せる印にする）
				respond(exchange, "<html><body><h2>第" + n + "話</h2><div class=\"body\"><p>" + marker(n) + "</p></div></body></html>");
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		fqdn = base.substring(base.indexOf("//") + 2);
		File root = tempFolder.newFolder("base");
		File siteDir = new File(root, "web/" + fqdn);
		Assume.assumeTrue("サイト定義のフォルダ（" + fqdn + "）を作れない環境のためスキップ", siteDir.mkdirs());
		Files.write(new File(siteDir, "extract.txt").toPath(), String.join("\n",
			"TITLE\th1:0", "AUTHOR\t.author:0", "HREF\t.list a:not(.pager)", "SUB_UPDATE\t.list .up",
			"UPDATE\t.workup:0", "PAGE_URL\ta.pager:-1\t(\\?p=)\\d+\\t(\\d+)\t$1$2", "CONTENT_SUBTITLE\th2:0", "CONTENT_ARTICLE\t.body:0", "")
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

	/**
	 * 本棚の本は、その本の名前で書き換える（本棚で名前を変えた本・同じ作品の別の名前の本も更新できる）。
	 * 本の名前は台帳に記録しない（#116 のゲート2）。拡張子が設定と違えば、名前が合わずに書かない
	 */
	@Test
	public void aShelfBookIsWrittenUnderItsOwnName() throws Exception {
		String basePath = serveAndBase();
		File dst = tempFolder.newFolder("out");
		File book = new File(dst, "別の名前.epub");
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).convert("http://" + fqdn + "/novel/", dst, book, true);
		assertTrue(r.message(), r.ok());
		assertEquals(book.getCanonicalFile(), r.epub().getCanonicalFile());
		assertEquals(java.util.List.of("別の名前.epub"), java.util.Arrays.asList(dst.list()));
		com.github.hmdev.info.BookLedger ledger = ledgerOf();
		assertNotNull(ledger);
		assertEquals("本の名前を記録しない", null, ledger.outputBaseName);

		//設定が .epub で、本棚の本が .kepub.epub（本の形が違う。.epub で終わっていても上書きしない。PR のゲート2）
		File kepub = new File(dst, "別の名前.kepub.epub");
		Files.write(kepub.toPath(), new byte[]{9});
		HeadlessWebConversion.Result kobo1 = conversion(guiDefaults(), basePathOf()).convert("http://" + fqdn + "/novel/", dst, kepub, true);
		assertFalse(kobo1.ok());
		org.junit.Assert.assertArrayEquals(new byte[]{9}, Files.readAllBytes(kepub.toPath()));
		//設定が .kepub.epub で、本棚の本が .epub（拡張子が合わない）
		Properties kobo = guiDefaults();
		kobo.setProperty("Ext", ".kepub.epub");
		HeadlessWebConversion.Result other = conversion(kobo, basePathOf()).convert("http://" + fqdn + "/novel/", dst, book, true);
		assertFalse(other.ok());
		assertTrue(other.message(), other.message().contains("違う名前"));
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
		assertTrue(r.message(), r.message().contains("3 → 2 話"));
		org.junit.Assert.assertArrayEquals("本棚の本はそのまま", before, Files.readAllBytes(book.toPath()));
		assertEquals("台帳の話数を下げない（下げると、もう一度押すだけで通ってしまう）", 3, ledgerOf().episodesFor(book));
		assertEquals("前の版も作らない（どの名前でも）", 0, ledgerDir().listFiles((d, n) -> n.startsWith("previous")).length);
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
		assertEquals("その本の話数", 2, ledgerOf().episodesFor(book));
		assertEquals("作品の話数は下げない", 3, ledgerOf().episodes);
		assertTrue("前の版を残す", new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), book)).exists());
	}

	/**
	 * 守りを付けない変換（GUI・CLI）は、話数が減っても今までどおり作る。ただし台帳の話数は下げない
	 * （下げると、同じ作品を GUI で別のフォルダに変換しただけで、本棚の本の守りが外れる。PR のゲート2）
	 */
	@Test
	public void conversionsWithoutTheGuardStillRunWithFewerEpisodes() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 2;
		shelfBook(basePath, tempFolder.newFolder("out2"));
		assertEquals(3, ledgerOf().episodes);
		assertEquals("本棚の本はまだ守られる", HeadlessWebConversion.STOP_SHRUNK, guardedUpdate(basePath, book, false).stop());
	}

	/** 「更新分のみ」でも、減ったまま更新すると選んだら作り直す（残った話が変わっていなくても、話が消えたことが更新。PR の手元の codex） */
	@Test
	public void acceptingFewerEpisodesRebuildsEvenWhenOnlyUpdatesAreConverted() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] before = Files.readAllBytes(book.toPath());
		episodes = 2;
		Properties props = guiDefaults();
		props.setProperty("WebConvertUpdated", "1");
		//既定（24 時間）だと、取ったばかりの話は「追加更新」とみなされて、減っていなくても作り直す
		props.setProperty("WebModifiedExpire", "0");
		HeadlessWebConversion.Result r = conversion(props, basePath)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, true);
		assertTrue(r.message(), r.ok());
		assertFalse("本が作り直される", java.util.Arrays.equals(before, Files.readAllBytes(book.toPath())));
		assertEquals("その本の話数", 2, ledgerOf().episodesFor(book));
		assertEquals("作品の話数は下げない", 3, ledgerOf().episodes);
		//減っていなければ、今までどおり「更新はありません」
		HeadlessWebConversion.Result again = conversion(props, basePath)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, false);
		assertTrue(again.message(), again.noUpdate());
	}

	/**
	 * 本棚の更新は、「最新 N 話」「追加更新分のみ」の設定でも作品の全部で本を作る（一部の話だけの本で上書きしない）。
	 * 減ったまま更新を選んだときも、残った話がどれも新しくなくても作り直す（PR の手元の codex）
	 */
	@Test
	public void libraryUpdatesAlwaysBuildTheWholeWork() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		Properties props = guiDefaults();
		props.setProperty("WebModifiedOnly", "1");
		props.setProperty("WebModifiedExpire", "0");
		props.setProperty("WebBeforeChapter", "1");
		props.setProperty("WebBeforeChapterCount", "1");
		episodes = 4;
		HeadlessWebConversion.Result r = conversion(props, basePath)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, false);
		assertTrue(r.message(), r.ok());
		String text = epubText(book);
		for (int i = 1; i <= 4; i++) assertTrue(i + " 話目がある", text.contains(marker(String.valueOf(i))));
		episodes = 2;
		HeadlessWebConversion.Result accepted = conversion(props, basePath)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, true);
		assertTrue(accepted.message(), accepted.ok());
		String after = epubText(book);
		assertTrue(after.contains(marker("1")) && after.contains(marker("2")));
		assertFalse(after.contains(marker("3")));
		assertEquals("その本の話数", 2, ledgerOf().episodesFor(book));
		assertEquals("作品の話数は下げない", 4, ledgerOf().episodes);
	}

	/** n 話目の本文の印 */
	private static String marker(String n) {
		return n.isEmpty() ? "ほんぶん" : "ほんぶん" + "あいうえおかきくけこ".charAt(Integer.parseInt(n) % 10) + "の話";
	}

	/** EPUB の本文（xhtml）をつなげて返す */
	private static String epubText(File epub) throws IOException {
		StringBuilder text = new StringBuilder();
		try (ZipFile zip = new ZipFile(epub)) {
			for (java.util.zip.ZipEntry e : java.util.Collections.list(zip.entries())) {
				if (e.getName().endsWith(".xhtml")) text.append(new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return text.toString();
	}

	/** 守りで止めたら、キャッシュの txt を前のまま残す（そこから作り直しても前の本になる。PR のゲート2） */
	@Test
	public void aStoppedUpdateLeavesTheCachedTextAlone() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		workUpdate = "2026/01/01 更新";
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		File txt = java.util.Arrays.stream(ledgerDir().listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"))).findFirst().orElseThrow();
		byte[] before = Files.readAllBytes(txt.toPath());
		File update = new File(ledgerDir(), "update.txt");
		assertTrue("この治具は update.txt を作る", update.isFile());
		byte[] updateBefore = Files.readAllBytes(update.toPath());
		episodes = 2;
		//作品も残った話も更新された（止めずに進めば、update.txt に新しい日付が書かれる）
		upDate = "2026/02/02";
		workUpdate = "2026/02/02 更新";
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, guardedUpdate(basePath, book, false).stop());
		org.junit.Assert.assertArrayEquals("txt は前のまま", before, Files.readAllBytes(txt.toPath()));
		org.junit.Assert.assertArrayEquals("update.txt も前のまま", updateBefore, Files.readAllBytes(update.toPath()));
		assertEquals("控えを残さない", 0, ledgerDir().listFiles((d, n) -> n.startsWith(".guard.")).length);
	}

	/** 目次の 2 ページ目以降が取れなければ止める。話数が減ったとも、作品が消えたとも言わない（PR のゲート2） */
	@Test
	public void aMissingLaterTocPageFailsTheUpdate() throws Exception {
		String basePath = serveAndBase();
		paged = true;
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		assertEquals(3, ledgerOf().episodes);
		byte[] before = Files.readAllBytes(book.toPath());
		page2Status = 404;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.ok());
		assertEquals(null, r.stop());
		assertTrue(r.message(), r.message().contains("目次"));
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(book.toPath()));
		//前の話数が記録に無くても（台帳より前の本）、欠けた目次では作らない
		ledgerOf().withEpisodes(-1).save(ledgerDir());
		HeadlessWebConversion.Result unknown = guardedUpdate(basePath, book, false);
		assertFalse(unknown.ok());
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(book.toPath()));
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

	/** 目次の話が 0 になって一覧のページが 1 ページの作品に見えるとき（話を消して告知だけ、など）も止める（PR の手元の codex） */
	@Test
	public void anEmptyListStopsTheUpdate() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] before = Files.readAllBytes(book.toPath());
		episodes = 0;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.ok());
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, r.stop());
		assertTrue(r.message(), r.message().contains("3 → 0 話"));
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(book.toPath()));
	}

	/** 一覧のページが話も本文も無い空のページになったときも、話数が減ったとして止め、txt を戻す（PR の手元の codex） */
	@Test
	public void anEmptyPageStopsTheUpdateAsFewerEpisodes() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		File txt = java.util.Arrays.stream(ledgerDir().listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"))).findFirst().orElseThrow();
		byte[] before = Files.readAllBytes(txt.toPath());
		episodes = 0;
		noticeWhenEmpty = false;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, r.stop());
		assertTrue(r.message(), r.message().contains("3 → 0 話"));
		org.junit.Assert.assertArrayEquals("txt は前のまま", before, Files.readAllBytes(txt.toPath()));
	}

	/** 同じ作品の本が 2 冊あっても、片方で減ったまま更新したのが、もう片方の守りを外さない（PR #120 の codex） */
	@Test
	public void acceptingFewerEpisodesOnOneBookKeepsTheOtherProtected() throws Exception {
		String basePath = serveAndBase();
		episodes = 5;
		File a = shelfBook(basePath, tempFolder.newFolder("a"));
		File b = shelfBook(basePath, tempFolder.newFolder("b"));
		episodes = 3;
		assertTrue(guardedUpdate(basePath, a, true).ok());
		episodes = 4;
		HeadlessWebConversion.Result other = guardedUpdate(basePath, b, false);
		assertEquals("5 話の本は 4 話で上書きしない", HeadlessWebConversion.STOP_SHRUNK, other.stop());
		assertTrue(other.message(), other.message().contains("5 → 4 話"));
		assertTrue("3 話にした本は 4 話で更新できる", guardedUpdate(basePath, a, false).ok());
	}

	/** 違う時期に作った本は、それぞれ作ったときの話数と比べる（作品の最大と比べると、少ない本が誤って止まる。PR #120 の codex） */
	@Test
	public void eachBookIsComparedWithItsOwnCountFromWhenItWasMade() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File a = shelfBook(basePath, tempFolder.newFolder("a"));
		episodes = 5;
		File b = shelfBook(basePath, tempFolder.newFolder("b"));
		episodes = 4;
		assertTrue("3 話の本は 4 話で更新できる", guardedUpdate(basePath, a, false).ok());
		assertEquals("5 話の本は 4 話で上書きしない", HeadlessWebConversion.STOP_SHRUNK, guardedUpdate(basePath, b, false).stop());
		assertEquals(4, ledgerOf().episodesFor(a));
		assertEquals(5, ledgerOf().episodesFor(b));
	}

	/** 1 つ前の版は本ごとに残す。同じ作品のもう 1 冊を更新しても、こちらの 1 つ前の版は消えない（PR #120 の win2 の確認） */
	@Test
	public void eachBookKeepsItsOwnPreviousVersion() throws Exception {
		String basePath = serveAndBase();
		//2 冊の中身を変える（同じ中身だと、どちらの 1 つ前の版かを見分けられない）
		episodes = 1;
		File a = shelfBook(basePath, tempFolder.newFolder("a"));
		episodes = 2;
		File b = shelfBook(basePath, tempFolder.newFolder("b"));
		episodes = 3;
		byte[] aBefore = Files.readAllBytes(a.toPath());
		assertFalse(java.util.Arrays.equals(aBefore, Files.readAllBytes(b.toPath())));
		assertTrue(guardedUpdate(basePath, a, false).ok());
		byte[] bBefore = Files.readAllBytes(b.toPath());
		assertTrue(guardedUpdate(basePath, b, false).ok());
		org.junit.Assert.assertArrayEquals("a の 1 つ前の版が残る", aBefore,
			Files.readAllBytes(new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), a)).toPath()));
		org.junit.Assert.assertArrayEquals(bBefore,
			Files.readAllBytes(new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), b)).toPath()));
	}

	/** 更新に失敗しても、1 つ前の版は書き換えない（先に写すと、もっと前の版を失う。PR のゲート2） */
	@Test
	public void aFailedUpdateKeepsTheOlderPreviousVersion() throws Exception {
		String basePath = serveAndBase();
		episodes = 1;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] v1 = Files.readAllBytes(book.toPath());
		episodes = 2;
		assertTrue(guardedUpdate(basePath, book, false).ok());
		File previous = new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), book));
		org.junit.Assert.assertArrayEquals(v1, Files.readAllBytes(previous.toPath()));
		assertTrue("名前で本が分かる: " + previous.getName(), previous.getName().contains(book.getName().replace(".epub", "")));
		episodes = 3;
		Epub3Writer failing = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath("")) {
			@Override
			public void write(com.github.hmdev.converter.AozoraEpub3Converter converter, java.io.BufferedReader src, File srcFile, String srcExt,
					File epubFile, com.github.hmdev.info.BookInfo bookInfo, com.github.hmdev.image.ImageInfoReader imageInfoReader) throws Exception {
				throw new IOException("書いている途中で失敗");
			}
		};
		Epub3Writer imageWriter = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		assertFalse(new HeadlessWebConversion(guiDefaults(), basePath, lastCache, failing, imageWriter)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, false).ok());
		org.junit.Assert.assertArrayEquals("1 つ前の版は v1 のまま", v1, Files.readAllBytes(previous.toPath()));
	}

	/** 本を置き換えられなかったら（Windows で本が開かれているなど）、1 つ前の版も書き換えない（PR #121 の codex） */
	@Test
	public void aFailedReplaceKeepsTheOlderPreviousVersion() throws Exception {
		String basePath = serveAndBase();
		episodes = 1;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] v1 = Files.readAllBytes(book.toPath());
		episodes = 2;
		assertTrue(guardedUpdate(basePath, book, false).ok());
		File previous = new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), book));
		episodes = 3;
		//置き換え先をフォルダにして、置き換えを失敗させる（本の中身はもう無いが、1 つ前の版が v1 のままかだけを見る）
		byte[] v2 = Files.readAllBytes(book.toPath());
		Files.delete(book.toPath());
		assertTrue(book.mkdir());
		Files.write(new File(book, "x").toPath(), v2);
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertFalse(r.message(), r.ok());
		org.junit.Assert.assertArrayEquals("1 つ前の版は v1 のまま", v1, Files.readAllBytes(previous.toPath()));
		assertEquals("控えを残さない", 0, ledgerDir().listFiles((d, n) -> n.startsWith(".previous.")).length);
	}

	/** 本を置き換えた後で 1 つ前の版を残せなくても、更新は成功のまま（本と台帳が食い違わない）。前の本は控えに残る（#121 の codex） */
	@Test
	public void aPreviousVersionThatCannotBeKeptDoesNotFailTheUpdate() throws Exception {
		String basePath = serveAndBase();
		episodes = 1;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] v1 = Files.readAllBytes(book.toPath());
		//1 つ前の版の名前に、置き換えられないもの（中身のあるフォルダ）を置く
		File previous = new File(ledgerDir(), HeadlessWebConversion.previousEpubName(ledgerDir(), book));
		assertTrue(previous.mkdir());
		Files.write(new File(previous, "x").toPath(), new byte[]{1});
		episodes = 2;
		HeadlessWebConversion.Result r = guardedUpdate(basePath, book, false);
		assertTrue(r.message(), r.ok());
		assertTrue(r.message(), r.message().contains("残せませんでした"));
		assertEquals("台帳は新しい本の話数", 2, ledgerOf().episodesFor(book));
		File[] kept = ledgerDir().listFiles((d, n) -> n.startsWith(".previous."));
		assertEquals("前の本は控えに残る", 1, kept.length);
		org.junit.Assert.assertArrayEquals(v1, Files.readAllBytes(kept[0].toPath()));
	}

	/** 新着を確かめる: 目次だけを読み、新しい話を数える。話は取らず、本も台帳も txt も書かない（internal #11） */
	@Test
	public void checkingCountsNewEpisodesWithoutWriting() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		byte[] before = Files.readAllBytes(book.toPath());
		File txt = java.util.Arrays.stream(ledgerDir().listFiles((d, n) -> n.endsWith(".txt") && !n.equals("update.txt"))).findFirst().orElseThrow();
		byte[] txtBefore = Files.readAllBytes(txt.toPath());
		String ledgerBefore = Files.readString(new File(ledgerDir(), com.github.hmdev.info.BookLedger.FILE_NAME).toPath());
		episodes = 5;
		episodeRequests.set(0);
		HeadlessWebConversion conv = conversion(guiDefaults(), basePath);
		HeadlessWebConversion.Result r = conv.check("http://" + fqdn + "/novel/", book);
		assertTrue(r.message(), r.ok());
		assertEquals(HeadlessWebConversion.STOP_CHECKED, r.stop());
		assertEquals(5, conv.checkedEpisodes);
		assertEquals(2, conv.checkedNew);
		assertEquals(0, conv.checkedRevised);
		assertTrue(r.message(), r.message().contains("2 話新着"));
		assertEquals("話は取らない", 0, episodeRequests.get());
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(book.toPath()));
		org.junit.Assert.assertArrayEquals(txtBefore, Files.readAllBytes(txt.toPath()));
		assertEquals("台帳を書かない", ledgerBefore, Files.readString(new File(ledgerDir(), com.github.hmdev.info.BookLedger.FILE_NAME).toPath()));
		assertEquals("前の版も作らない", 0, ledgerDir().listFiles((d, n) -> n.startsWith("previous")).length);
	}

	/** 目次の更新日が変わった話は改稿として数える（narou.rb と同じく、新しい話と改稿の両方） */
	@Test
	public void checkingCountsRevisedEpisodes() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		HeadlessWebConversion none = conversion(guiDefaults(), basePath);
		HeadlessWebConversion.Result same = none.check("http://" + fqdn + "/novel/", book);
		assertTrue(same.message(), same.message().contains("新着はありません"));
		upDate = "2026/03/03";
		episodes = 4;
		HeadlessWebConversion conv = conversion(guiDefaults(), basePath);
		HeadlessWebConversion.Result r = conv.check("http://" + fqdn + "/novel/", book);
		assertEquals(1, conv.checkedNew);
		assertEquals(3, conv.checkedRevised);
		assertTrue(r.message(), r.message().contains("1 話新着・3 話改稿"));
	}

	/** 話がキャッシュにあっても、その本に入っていなければ新着として数える（更新で話は取れたが本を書けなかった、など。PR の手元の codex） */
	@Test
	public void episodesCachedButNotInTheBookAreNew() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 5;
		//同じ作品を別の場所に変換して、話をキャッシュに入れる（本棚の本は 3 話のまま）
		shelfBook(basePath, tempFolder.newFolder("other"));
		HeadlessWebConversion conv = conversion(guiDefaults(), basePath);
		conv.check("http://" + fqdn + "/novel/", book);
		assertEquals(2, conv.checkedNew);
	}

	/** 1 ページの作品は確かめられない（本文も取りに行かない） */
	@Test
	public void aOnePageWorkCannotBeChecked() throws Exception {
		String basePath = serveAndBase();
		episodes = 0;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodeRequests.set(0);
		HeadlessWebConversion.Result r = conversion(guiDefaults(), basePath).check("http://" + fqdn + "/novel/", book);
		assertFalse(r.ok());
		assertTrue(r.message(), r.message().contains("確かめられません"));
	}

	/** 前の更新日の記録（update.txt）が無ければ、改稿は数えない（全部が改稿に見えるので。PR のゲート2）。kindle の設定でも確かめられる */
	@Test
	public void noUpdateRecordMeansNoRevisionsAndKindleCanCheck() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		Files.delete(new File(ledgerDir(), "update.txt").toPath());
		upDate = "2026/04/04";
		Properties kindle = guiDefaults();
		kindle.setProperty("Ext", ".mobi");
		HeadlessWebConversion conv = conversion(kindle, basePath);
		HeadlessWebConversion.Result r = conv.check("http://" + fqdn + "/novel/", book);
		assertTrue(r.message(), r.ok());
		assertEquals(0, conv.checkedRevised);
	}

	/** 確かめるときも、話数が減った・作品が無いは更新と同じく知らせる */
	@Test
	public void checkingReportsFewerEpisodesAndMissingWorks() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 2;
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, conversion(guiDefaults(), basePath).check("http://" + fqdn + "/novel/", book).stop());
		listStatus = 404;
		assertEquals(HeadlessWebConversion.STOP_GONE, conversion(guiDefaults(), basePath).check("http://" + fqdn + "/novel/", book).stop());
	}

	/** EPUB を作れなかったら、台帳の話数を前に戻す（本は前のままなので、次の更新は前の話数と比べる。PR の手元の codex） */
	@Test
	public void aFailedUpdateKeepsThePreviousEpisodeCount() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		File book = shelfBook(basePath, tempFolder.newFolder("out"));
		episodes = 2;
		Epub3Writer failing = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath("")) {
			@Override
			public void write(com.github.hmdev.converter.AozoraEpub3Converter converter, java.io.BufferedReader src, File srcFile, String srcExt,
					File epubFile, com.github.hmdev.info.BookInfo bookInfo, com.github.hmdev.image.ImageInfoReader imageInfoReader) throws Exception {
				throw new IOException("書いている途中で失敗");
			}
		};
		Epub3Writer imageWriter = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		HeadlessWebConversion.Result r = new HeadlessWebConversion(guiDefaults(), basePath, lastCache, failing, imageWriter)
			.convert("http://" + fqdn + "/novel/", book.getParentFile(), book, true, true, true);
		assertFalse(r.ok());
		assertEquals(3, ledgerOf().episodesFor(book));
		//次のふつうの更新は、前の話数（3）と比べて止まる
		assertEquals(HeadlessWebConversion.STOP_SHRUNK, guardedUpdate(basePath, book, false).stop());
	}

	/** 新しく落とす本は、「最新 N 話」「追加更新分のみ」「更新分のみ」でも作品の全部で作る（落とし直しもできる。PR のゲート2） */
	@Test
	public void aNewBookIsTheWholeWorkWhateverTheSettings() throws Exception {
		String basePath = serveAndBase();
		episodes = 3;
		//「最新 1 話」でも全話（「追加更新分のみ」と一緒だと「最新 N 話」は効かないので、別に見る）
		Properties latest = guiDefaults();
		latest.setProperty("WebBeforeChapter", "1");
		latest.setProperty("WebBeforeChapterCount", "1");
		File shelf = tempFolder.newFolder("webshelf");
		HeadlessWebConversion.Result r = conversion(latest, basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		assertTrue(r.message(), r.ok());
		String text = epubText(r.epub());
		for (int i = 1; i <= 3; i++) assertTrue(i + " 話目がある", text.contains(marker(String.valueOf(i))));
		//消してから落とし直せる（キャッシュが同じでも「追加更新分はありません」「更新はありません」にしない）
		Files.delete(r.epub().toPath());
		Properties updatesOnly = guiDefaults();
		updatesOnly.setProperty("WebModifiedOnly", "1");
		updatesOnly.setProperty("WebModifiedExpire", "0");
		updatesOnly.setProperty("WebConvertUpdated", "1");
		HeadlessWebConversion.Result again = conversion(updatesOnly, basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		assertTrue(again.message(), again.ok());
		String againText = epubText(again.epub());
		for (int i = 1; i <= 3; i++) assertTrue(i + " 話目がある（落とし直し）", againText.contains(marker(String.valueOf(i))));
	}

	/** 違う作品が同じ短い名前になるときは、空いている名前にする（同じ名前の本があると落とせない。PR のゲート2） */
	@Test
	public void differentWorksWithTheSameShortNameDoNotCollide() throws Exception {
		String basePath = serveAndBase();
		File shelf = tempFolder.newFolder("webshelf");
		workTitle = "【書籍化】題～一部～";
		HeadlessWebConversion.Result a = conversion(guiDefaults(), basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		workTitle = "【書籍化】題～二部～";
		HeadlessWebConversion.Result b = conversion(guiDefaults(), basePath).convertNewBook("http://" + fqdn + "/novel2/", shelf);
		assertTrue(a.message(), a.ok());
		assertTrue(b.message(), b.ok());
		assertEquals("[著者] 題.epub", a.epub().getName());
		assertEquals("[著者] 題 (2).epub", b.epub().getName());
	}

	/** 深い Web 本棚でさらに縮められる名前でも、同じ短い名前の別の作品は別の名前になる（PR の手元の codex） */
	@Test
	public void deepShelvesStillKeepSameShortNamesApart() throws Exception {
		String basePath = serveAndBase();
		File shelf = tempFolder.newFolder("deep");
		//本の名前（35 文字ほど）が、フルパスの上限（250 文字）を超えて縮められる深さにする
		String realRoot = shelf.toPath().toRealPath().toString();
		while (realRoot.length() + 1 + 220 - realRoot.length() > 0 && shelf.toPath().toRealPath().toString().length() < 225) {
			shelf = new File(shelf, "d".repeat(Math.min(40, 225 - shelf.toPath().toRealPath().toString().length())));
			assertTrue(shelf.mkdirs() || shelf.isDirectory());
		}
		//本棚の更新と同じく、記録は Web 本棚の .aozora に（もうある作品かを、そこの台帳で見る）
		lastCache = new File(shelf, ".aozora");
		String longTitle = "あ".repeat(30);
		workTitle = "【書籍化】" + longTitle + "～一部～";
		HeadlessWebConversion.Result a = conversion(guiDefaults(), basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		workTitle = "【書籍化】" + longTitle + "～二部～";
		HeadlessWebConversion.Result b = conversion(guiDefaults(), basePath).convertNewBook("http://" + fqdn + "/novel2/", shelf);
		assertTrue(a.message(), a.ok());
		assertTrue(b.message(), b.ok());
		assertFalse("名前は縮められている: " + a.epub().getName(), a.epub().getName().startsWith("[著者] " + longTitle + ".epub"));
		assertFalse(a.epub().getName().equals(b.epub().getName()));
		//縮められた名前でも、もう落としてある作品だと分かる（PR の codex）
		assertTrue(HeadlessBookUpdater.existingBook(shelf.toPath(), "http://" + fqdn + "/novel/") != null);
		//縮められた名前の本の名前を変えても、作品の名前が変わり、もう一度落とすときに見つけられる（PR の手元の codex）
		Properties props = guiDefaults();
		HeadlessBookUpdater updater = new HeadlessBookUpdater(() -> props, basePath);
		BookUpdater.Result renamed = updater.rename("http://" + fqdn + "/novel/", a.epub().toPath(), "改名");
		assertTrue(renamed.message(), renamed.ok());
		java.nio.file.Path found = HeadlessBookUpdater.existingBook(shelf.toPath(), "http://" + fqdn + "/novel/");
		assertNotNull(found);
		assertEquals("改名.epub", found.getFileName().toString());
	}

	/** Web 本棚に新しく落とす本は、短い名前（「出力ファイル名に表題利用」が切れていても）。同じ本はもう一度は書かない */
	@Test
	public void aNewBookGetsAShortNameAndIsNotOverwritten() throws Exception {
		String basePath = serveAndBase();
		workTitle = "【書籍化】長い題～副題がとても長い";
		Properties props = guiDefaults();
		props.setProperty("AutoFileName", "");
		File shelf = tempFolder.newFolder("webshelf");
		HeadlessWebConversion.Result r = conversion(props, basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		assertTrue(r.message(), r.ok());
		assertEquals("[著者] 長い題.epub", r.epub().getName());
		assertEquals("台帳に記録する（続きを取っても同じ名前）", "[著者] 長い題", ledgerOf().outputBaseName);
		byte[] before = Files.readAllBytes(r.epub().toPath());
		HeadlessWebConversion.Result again = conversion(props, basePath).convertNewBook("http://" + fqdn + "/novel/", shelf);
		assertFalse(again.ok());
		assertTrue(again.message(), again.message().contains("もう Web 本棚にある"));
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(r.epub().toPath()));
	}

	private File ledgerDir() {
		return new File(lastCache, fqdn.replace(':', '_') + "/novel");
	}
}
