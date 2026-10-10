package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * ローカル HTTP サーバのセキュリティ設計とレスポンス。
 * ブラウザ起動は伴わないため CI でも実行できる。
 */
public class PreviewServerTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private PreviewSession session;
	private PreviewServer server;
	private String bookId;
	private HttpClient client;

	@Before
	public void setUp() throws IOException
	{
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("book.epub"));
		this.session = new PreviewSession();
		// ユーザーのホームを汚さないよう、設定の保存先はテンポラリに向ける
		this.server = new PreviewServer(this.session,
			new PreviewSettingsStore(temp.getRoot().toPath().resolve("settings.json")));
		this.bookId = this.session.addBook(epub);
		this.server.start();
		this.client = HttpClient.newHttpClient();
	}

	@After
	public void tearDown()
	{
		if (this.server != null) this.server.close();
		if (this.session != null) this.session.close();
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(path)).GET().build();
		return this.client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String base()
	{
		return this.server.getUrl();
	}

	/** bind 先に依存しない "http://host:port" を返す */
	private String origin()
	{
		String url = this.server.getUrl();
		return url.substring(0, url.indexOf("/p/"));
	}

	@Test
	public void bindsToLoopbackOnly() throws Exception
	{
		String url = this.server.getUrl();
		// URL は実際に bind したアドレスから作る。127.0.0.1 の決め打ちは
		// IPv6 を優先する環境で「待ち受けと違うアドレス」を案内してしまう
		String expectedHost = PreviewServer.urlHost(java.net.InetAddress.getLoopbackAddress());
		assertTrue("案内する URL が bind 先と一致しない: " + url,
			url.startsWith("http://" + expectedHost + ":"));
		assertTrue(java.net.InetAddress.getLoopbackAddress().isLoopbackAddress());
		assertTrue(this.server.getPort() > 0);
	}

	@Test
	public void ipv6LoopbackIsBracketedInUrl() throws Exception
	{
		assertEquals("127.0.0.1", PreviewServer.urlHost(java.net.InetAddress.getByName("127.0.0.1")));
		assertEquals("[0:0:0:0:0:0:0:1]", PreviewServer.urlHost(java.net.InetAddress.getByName("::1")));
	}

	@Test
	public void servesViewerShell() throws Exception
	{
		HttpResponse<String> response = get(base());
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("AozoraEpub3"));
	}

	@Test
	public void baseWithoutTrailingSlashRedirects() throws Exception
	{
		// 末尾スラッシュが無いとページ内の相対 URL が壊れるのでリダイレクトする
		String noSlash = base().substring(0, base().length() - 1);
		HttpClient noRedirect = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER).build();
		HttpResponse<String> response = noRedirect.send(
			HttpRequest.newBuilder(URI.create(noSlash)).GET().build(),
			HttpResponse.BodyHandlers.ofString());

		// メソッドを変えない 308 を使う
		assertEquals(308, response.statusCode());
		assertEquals(java.net.URI.create(base()).getPath(),
			response.headers().firstValue("Location").orElse(""));
	}

	@Test
	public void redirectKeepsTheQueryString() throws Exception
	{
		// ?book=... を落とすと既定の本が開いてしまう
		String noSlash = base().substring(0, base().length() - 1) + "?book=" + this.bookId;
		HttpClient noRedirect = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER).build();
		HttpResponse<String> response = noRedirect.send(
			HttpRequest.newBuilder(URI.create(noSlash)).GET().build(),
			HttpResponse.BodyHandlers.ofString());

		assertEquals(308, response.statusCode());
		assertTrue(response.headers().firstValue("Location").orElse("")
			.endsWith("/?book=" + this.bookId));
	}

	@Test
	public void rejectsWrongToken() throws Exception
	{
		String wrong = origin() + "/p/deadbeef/";
		assertEquals(404, get(wrong).statusCode());
	}

	@Test
	public void rejectsPathOutsideTokenScope() throws Exception
	{
		String outside = origin() + "/etc/passwd";
		assertEquals(404, get(outside).statusCode());
	}

	@Test
	public void rejectsPathTraversalInBookFiles() throws Exception
	{
		// URL エンコードされた ".." も正規化後に弾く
		String traversal = base() + "book/" + this.bookId + "/OPS/%2e%2e/%2e%2e/%2e%2e/etc/passwd";
		assertEquals(404, get(traversal).statusCode());
	}

	@Test
	public void rejectsUnknownBookId() throws Exception
	{
		assertEquals(404, get(base() + "book/zzz/OPS/package.opf").statusCode());
		assertEquals(404, get(base() + "api/book/zzz").statusCode());
	}

	@Test
	public void servesXhtmlWithCorrectContentType() throws Exception
	{
		HttpResponse<String> response = get(base() + "book/" + this.bookId + "/OPS/xhtml/text00001.xhtml");
		assertEquals(200, response.statusCode());
		String contentType = response.headers().firstValue("Content-Type").orElse("");
		// text/html で返すとブラウザの解釈が変わり、縦書きの確認にならない
		assertEquals("XHTML は application/xhtml+xml で返すこと", "application/xhtml+xml", contentType);
		// charset を付けると BOM / XML 宣言より優先され、UTF-16 の EPUB を壊す
		assertFalse("XML 系に charset を付けてはならない: " + contentType,
			contentType.contains("charset"));
		assertTrue(response.body().contains("第一章"));
	}

	@Test
	public void servesEmbeddedFontWithFontContentType() throws Exception
	{
		HttpResponse<String> response = get(base() + "book/" + this.bookId + "/OPS/gaiji/u3042-u3099.ttf");
		assertEquals(200, response.statusCode());
		assertEquals("font/ttf", response.headers().firstValue("Content-Type").orElse(""));
	}

	@Test
	public void bookApiReturnsSpineAndToc() throws Exception
	{
		HttpResponse<String> response = get(base() + "api/book/" + this.bookId);
		assertEquals(200, response.statusCode());
		String json = response.body();
		assertTrue(json.contains("\"title\":\"テスト書籍\""));
		assertTrue(json.contains("OPS/xhtml/text00001.xhtml"));
		assertTrue(json.contains("\"fragment\":\"chapter1\""));
		// linear="no" の表紙は spine に載らない。
		// spine 配列だけを取り出して、cover.xhtml が含まれないことを確かめる
		int start = json.indexOf("\"spine\":[");
		assertTrue(start >= 0);
		String spine = json.substring(start, json.indexOf(']', start));
		assertFalse("linear=no の cover が spine に混ざっている: " + spine, spine.contains("cover.xhtml"));
		assertTrue(spine.contains("text00001.xhtml"));
	}

	@Test
	public void inspectApiReturnsStructure() throws Exception
	{
		HttpResponse<String> response = get(base() + "api/book/" + this.bookId + "/inspect");
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"packageVersion\":\"3.0\""));
	}

	@Test
	public void sessionApiExposesFontCatalog() throws Exception
	{
		HttpResponse<String> response = get(base() + "api/session");
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"fonts\":"));
		assertTrue(response.body().contains("\"defaultBookId\":\"" + this.bookId + "\""));
	}

	@Test
	public void assetsAreWhitelisted() throws Exception
	{
		// 分割した viewer スクリプトは全て配信できること。
		// ALLOWED_ASSETS の更新漏れはビューアーが動かない事故になるので、全ファイルを検証する
		for (String name : new String[] {"viewer-core.js", "viewer-util.js", "viewer-settings.js",
			"viewer-toc.js", "viewer-frame.js", "viewer-events.js", "viewer-inspector.js",
			"viewer-library.js"}) {
			assertEquals(name, 200, get(base() + "asset/" + name).statusCode());
		}
		assertEquals(200, get(base() + "asset/viewer.css").statusCode());
		// ホワイトリスト外はクラスパスにあっても配信しない
		assertEquals(404, get(base() + "asset/viewer.html").statusCode());
	}

	@Test
	public void everyAssetReferencedByTheShellIsServable() throws Exception
	{
		// viewer.html の参照を実際に走査して検証する。
		// アセットを増やしたとき ALLOWED_ASSETS の更新を忘れると
		// ビューアーが動かなくなるが、名前を直書きしたテストでは気付けないため
		String shell;
		try (java.io.InputStream in =
				 PreviewServer.class.getResourceAsStream("assets/viewer.html")) {
			assertNotNull("viewer.html がクラスパスにない", in);
			shell = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		java.util.regex.Matcher matcher =
			java.util.regex.Pattern.compile("(?:src|href)=[\"']asset/([^\"']+)[\"']").matcher(shell);
		int found = 0;
		while (matcher.find()) {
			String name = matcher.group(1);
			found++;
			assertEquals("viewer.html が参照しているが配信できない: " + name,
				200, get(base() + "asset/" + name).statusCode());
		}
		// 正規表現が拾い漏らすと「1 件も検証していないのに緑」になる。
		// "asset/" の出現回数と突き合わせて、取りこぼしを検出する
		int references = shell.split("asset/", -1).length - 1;
		assertEquals("asset/ 参照の一部を正規表現が拾えていない", references, found);
		assertTrue("viewer.html からアセット参照を 1 件も抽出できていない", found > 0);
	}

	@Test
	public void extractionIsDeferredUntilFirstAccess() throws Exception
	{
		PreviewSession.Book book = this.session.getBook(this.bookId);
		assertNotNull(book);
		// 登録しただけでは OPF は解析されていない
		assertEquals(null, book.getOpf());
		get(base() + "api/book/" + this.bookId);
		assertNotNull("初回アクセスで展開されること", this.session.getBook(this.bookId).getOpf());
	}

	@Test
	public void bookFilesCarryScriptBlockingCsp() throws Exception
	{
		// iframe の sandbox と二重に、EPUB 由来のスクリプト実行を防ぐ
		HttpResponse<String> response = get(base() + "book/" + this.bookId + "/OPS/xhtml/text00001.xhtml");
		String csp = response.headers().firstValue("Content-Security-Policy").orElse("");
		assertTrue("script-src 'none' が付いていない: " + csp, csp.contains("script-src 'none'"));
	}

	@Test
	public void viewerShellSandboxesTheContentFrame() throws Exception
	{
		String html = get(base()).body();
		// コメント文中の語に反応しないよう iframe タグだけを取り出して検査する
		int start = html.indexOf("<iframe");
		assertTrue("iframe が無い", start >= 0);
		String tag = html.substring(start, html.indexOf('>', start));
		// allow-scripts を与えないことが要件。allow-same-origin は CSS 注入に必要
		assertTrue("sandbox 属性が無い: " + tag, tag.contains("sandbox=\"allow-same-origin\""));
		assertFalse("allow-scripts を与えてはならない: " + tag, tag.contains("allow-scripts"));
	}

	@Test
	public void sessionApiDoesNotLeakAbsolutePaths() throws Exception
	{
		String json = get(base() + "api/session").body();
		assertFalse("EPUB の絶対パスを公開してはならない", json.contains(temp.getRoot().getAbsolutePath()));
		assertTrue(json.contains("\"fileName\":\"book.epub\""));
	}

	@Test
	public void plusInFileNameIsNotDecodedAsSpace() throws Exception
	{
		// URLDecoder(form 用) を使うと "a+b.png" が "a b.png" になり 404 になる
		Path epub = EpubFixture.standard()
			.put("OPS/images/a+b.txt", "plus-name")
			.writeTo(temp.getRoot().toPath().resolve("plus.epub"));
		String plusBookId = this.session.addBook(epub);

		HttpResponse<String> response = get(base() + "book/" + plusBookId + "/OPS/images/a+b.txt");
		assertEquals(200, response.statusCode());
		assertEquals("plus-name", response.body());
	}

	@Test
	public void malformedPercentEscapeReturnsBadRequest() throws Exception
	{
		// 壊れたエスケープは java.net.URI が構築を拒否するため、生ソケットで送る。
		// 検証したいのは「接続断や無応答にならず必ずレスポンスを返す」こと
		String path = "/p/" + this.session.getToken() + "/book/" + this.bookId + "/OPS/%zz";
		String statusLine = rawRequest("GET " + path + " HTTP/1.1");
		assertTrue("400 を返すこと: " + statusLine, statusLine.startsWith("HTTP/1.1 400"));
	}

	/** HttpClient を通さず生の HTTP リクエストを送り、ステータス行を返す */
	private String rawRequest(String requestLine) throws IOException
	{
		try (java.net.Socket socket = new java.net.Socket(
				java.net.InetAddress.getLoopbackAddress(), this.server.getPort())) {
			socket.setSoTimeout(5000);
			java.io.OutputStream out = socket.getOutputStream();
			out.write((requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n")
				.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			out.flush();
			java.io.BufferedReader reader = new java.io.BufferedReader(
				new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
			String line = reader.readLine();
			return (line == null) ? "(応答なし)" : line;
		}
	}

	// ------------------------------------------------------------------
	// 本棚 (Phase 2)
	// ------------------------------------------------------------------

	/** 本棚用のフォルダを作って走査し、セッションに取り込む */
	private Path shelfWith(String... names) throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("shelf");
		for (String name : names) {
			EpubFixture fixture = name.startsWith("cover-")
				? EpubFixture.withEpub3Cover() : EpubFixture.standard();
			fixture.writeTo(shelf.resolve(name + ".epub"));
		}
		this.session.setLibrary(shelf, LibraryScanner.scan(shelf, 3, null));
		return shelf;
	}

	@Test
	public void libraryApiListsTheScannedBooks() throws Exception
	{
		shelfWith("cover-a", "plain-b");

		HttpResponse<String> response = get(base() + "api/library");
		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

		String json = response.body();
		assertTrue(json, json.contains("\"count\":2"));
		assertTrue(json, json.contains("\"folderName\":\"shelf\""));
		assertTrue(json, json.contains("\"fileName\":\"cover-a.epub\""));
		assertTrue(json, json.contains("\"title\":\"テスト書籍\""));
		assertTrue(json, json.contains("\"creator\":\"テスト著者\""));
		// 表紙が無い本にサムネイルを取りに行かせない (全冊ぶんの 404 になる)
		assertTrue(json, json.contains("\"hasCover\":true"));
		assertTrue(json, json.contains("\"hasCover\":false"));
	}

	@Test
	public void theLibraryListsTheSourceOfBooksFromTheWeb() throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("web");
		EpubFixture.withSource("https://ncode.syosetu.com/n1234ab/").writeTo(shelf.resolve("web.epub"));
		EpubFixture.standard().writeTo(shelf.resolve("local.epub"));
		this.session.setLibrary(List.of(new LibraryShelf(shelf, LibraryScanner.scan(shelf, 3, null))));

		String json = get(base() + "api/library").body();
		assertTrue(json, json.contains("\"source\":\"https://ncode.syosetu.com/n1234ab/\""));
		assertTrue("掲載元の無い本は null: " + json, json.contains("\"source\":null"));
	}

	/** 名前を指定して棚を作り、走査結果を返す (セッションには取り込まない) */
	private LibraryShelf makeShelf(String folderName, String... names) throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve(folderName);
		for (String name : names) {
			EpubFixture fixture = name.startsWith("cover-")
				? EpubFixture.withEpub3Cover() : EpubFixture.standard();
			fixture.writeTo(shelf.resolve(name + ".epub"));
		}
		return new LibraryShelf(shelf, LibraryScanner.scan(shelf, 3, null));
	}

	@Test
	public void libraryApiListsEveryShelfWithItsOwnBooks() throws Exception
	{
		LibraryShelf first = makeShelf("novels", "a", "b");
		LibraryShelf second = makeShelf("comics", "c");
		this.session.setLibrary(List.of(first, second));

		String json = get(base() + "api/library").body();
		assertTrue(json, json.contains("\"count\":3"));
		// 棚が 2 つ以上あるときは名前を選べないので folderName は出さない
		assertTrue(json, json.contains("\"folderName\":null"));
		assertTrue(json, json.contains("\"name\":\"novels\",\"count\":2"));
		assertTrue(json, json.contains("\"name\":\"comics\",\"count\":1"));
		// 本は棚の添字を持つ (名前で引くと、同じ名前のフォルダを登録したときに取り違える)
		assertTrue(json, json.contains("\"shelf\":0"));
		assertTrue(json, json.contains("\"shelf\":1"));
		assertFalse("ホスト上の絶対パスを公開してはならない",
			json.contains(temp.getRoot().getAbsolutePath()));
	}

	@Test
	public void aBookOnTwoShelvesIsListedOnce() throws Exception
	{
		// 「出力先フォルダ」と「その親」を両方登録するのは普通に起きる。
		// 素直に登録すると同じ本が二重に並び、冊数の上限も二重に消費する
		Path parent = temp.getRoot().toPath().resolve("outer");
		EpubFixture.standard().writeTo(parent.resolve("inner").resolve("shared.epub"));
		Path child = parent.resolve("inner");

		this.session.setLibrary(List.of(
			new LibraryShelf(parent, LibraryScanner.scan(parent, 3, null)),
			new LibraryShelf(child, LibraryScanner.scan(child, 3, null))));

		String json = get(base() + "api/library").body();
		assertEquals("同じ本が 2 回並んでいる", 1,
			json.split("\"fileName\":\"shared.epub\"", -1).length - 1);
		assertTrue(json, json.contains("\"count\":1"));
		// 位置は「その本が属する棚」から見た相対にする
		assertTrue(json, json.contains("\"shelf\":0"));
		assertTrue(json, json.contains("\"subFolder\":\"inner\""));
	}

	@Test
	public void nestedShelfFoldersAreFoldedIntoTheParent()
	{
		// 親を走査すれば子も含まれるので、子を別の棚として走査するのは二度手間
		Path parent = temp.getRoot().toPath().resolve("outer");
		List<Path> roots = PreviewLauncher.normalizeShelfFolders(
			List.of(parent, parent.resolve("inner"), parent));
		assertEquals(List.of(parent.toAbsolutePath().normalize()), roots);

		// 後から親を指定された場合は、先に入れた子を畳む
		List<Path> reversed = PreviewLauncher.normalizeShelfFolders(
			List.of(parent.resolve("inner"), parent));
		assertEquals(List.of(parent.toAbsolutePath().normalize()), reversed);
	}

	@Test
	public void theNumberOfShelvesIsCapped()
	{
		List<Path> many = new java.util.ArrayList<>();
		for (int i = 0; i < LibraryScanner.MAX_SHELVES + 4; i++) {
			many.add(temp.getRoot().toPath().resolve("shelf" + i));
		}
		assertEquals(LibraryScanner.MAX_SHELVES, PreviewLauncher.normalizeShelfFolders(many).size());
	}

	@Test
	public void foldingHappensBeforeTheShelfLimitIsApplied()
	{
		// 上限を「畳む前」に掛けると、後ろに来た親で畳めるはずの子が残ったまま数だけ埋まる。
		// 子を MAX_SHELVES 個並べた後に共通の親を渡すと、結果は親 1 個でなければならない
		Path parent = temp.getRoot().toPath().resolve("outer");
		List<Path> folders = new java.util.ArrayList<>();
		for (int i = 0; i < LibraryScanner.MAX_SHELVES + 2; i++) {
			folders.add(parent.resolve("child" + i));
		}
		folders.add(parent);
		assertEquals(List.of(parent.toAbsolutePath().normalize()),
			PreviewLauncher.normalizeShelfFolders(folders));
	}

	@Test
	public void sessionApiTellsWhetherAShelfIsLoaded() throws Exception
	{
		// ビューアーは本棚ボタンを出すかどうかをここで決める。
		// 一覧まで載せると、本文を読むだけのセッション情報に最大 2000 件が付いて回る
		String before = get(base() + "api/session").body();
		assertTrue(before, before.contains("\"libraryFolder\":null"));
		assertTrue(before, before.contains("\"libraryCount\":0"));
		assertFalse("セッション情報に本棚の一覧を載せてはならない", before.contains("cover-a.epub"));

		shelfWith("cover-a", "plain-b");

		String after = get(base() + "api/session").body();
		assertTrue(after, after.contains("\"libraryFolder\":\"shelf\""));
		assertTrue(after, after.contains("\"libraryShelfCount\":1"));
		assertTrue(after, after.contains("\"libraryCount\":2"));
		assertFalse("棚の位置はフォルダ名だけを出す", after.contains(temp.getRoot().getAbsolutePath()));
		assertFalse("セッション情報に本棚の一覧を載せてはならない", after.contains("cover-a.epub"));
	}

	@Test
	public void libraryApiDoesNotLeakAbsolutePaths() throws Exception
	{
		// api/session と同じ方針。棚の位置はフォルダ名、本の位置は棚からの相対だけ
		Path shelf = temp.getRoot().toPath().resolve("shelf");
		EpubFixture.standard().writeTo(shelf.resolve("sub").resolve("deep").resolve("a.epub"));
		this.session.setLibrary(shelf, LibraryScanner.scan(shelf, 5, null));

		String json = get(base() + "api/library").body();
		assertFalse("ホスト上の絶対パスを公開してはならない",
			json.contains(temp.getRoot().getAbsolutePath()));
		assertTrue(json, json.contains("\"subFolder\":\"sub/deep\""));
	}

	@Test
	public void anEmptyLibraryIsStillValidJson() throws Exception
	{
		String json = get(base() + "api/library").body();
		assertTrue(json, json.contains("\"count\":0"));
		assertTrue(json, json.contains("\"folderName\":null"));
		assertTrue(json, json.contains("\"books\":[]"));
	}

	@Test
	public void registeringTheLibraryDoesNotStealTheDefaultBook() throws Exception
	{
		// 本棚を「先に」読み込んだときに棚の 1 冊目が既定になると、
		// 変換した本を開いたつもりが別の本が表示される。
		// setUp のセッションは既に既定を持っているので、順序を作れる新しい
		// セッションで確かめる (既定が埋まった後では何を登録しても変わらない)
		Path shelf = temp.getRoot().toPath().resolve("order-shelf");
		EpubFixture.standard().writeTo(shelf.resolve("shelf-book.epub"));
		Path converted = EpubFixture.standard()
			.writeTo(temp.getRoot().toPath().resolve("converted.epub"));

		try (PreviewSession fresh = new PreviewSession()) {
			fresh.setLibrary(shelf, LibraryScanner.scan(shelf, 3, null));
			assertNull("本棚の登録だけで既定の本が決まってしまっている", fresh.getDefaultBookId());

			String convertedId = fresh.addBook(converted);
			assertEquals("変換した本が既定にならない", convertedId, fresh.getDefaultBookId());
		}
	}

	@Test
	public void openingABookThatIsAlreadyOnTheShelfStillMakesItTheDefault() throws Exception
	{
		// 出力先フォルダをそのまま棚にすると必ずこの経路を通る。
		// 登録済みとして早期 return すると既定が決まらず、
		// ビューアーを既定 URL で開いても何も表示されない
		Path shelf = temp.getRoot().toPath().resolve("out-shelf");
		Path converted = EpubFixture.standard().writeTo(shelf.resolve("converted.epub"));

		try (PreviewSession fresh = new PreviewSession()) {
			fresh.setLibrary(shelf, LibraryScanner.scan(shelf, 3, null));
			assertNull(fresh.getDefaultBookId());

			String id = fresh.addBook(converted);
			assertEquals("棚にある本を開いても既定が決まらない", id, fresh.getDefaultBookId());
		}
	}

	@Test
	public void theSessionApiDoesNotCarryTheWholeShelf() throws Exception
	{
		// api/session は起動のたびに読まれる。棚の全冊 (最大 2000 件) を載せると
		// 毎回それが付いて回る。棚は api/library が返す
		shelfWith("cover-a", "plain-b");

		String json = get(base() + "api/session").body();
		assertTrue("開いている本が消えている", json.contains("\"fileName\":\"book.epub\""));
		assertFalse("セッション情報に本棚の本が載っている", json.contains("cover-a.epub"));
		assertFalse(json.contains("plain-b.epub"));
	}

	@Test
	public void switchingShelvesForgetsTheBooksThatAreGone() throws Exception
	{
		// library だけ入れ替えて books を残すと、棚を切り替えるたびに登録が単調増加する
		shelfWith("cover-a", "plain-b");
		String staleId = coverBookId();
		// setUp の book.epub + 棚の 2 冊
		assertEquals(3, this.session.getBooks().size());

		Path other = temp.getRoot().toPath().resolve("other-shelf");
		EpubFixture.standard().writeTo(other.resolve("c.epub"));
		this.session.setLibrary(other, LibraryScanner.scan(other, 3, null));

		// book.epub + 新しい棚の 1 冊。前の棚の 2 冊は忘れている
		assertEquals("棚を切り替えても前の棚の登録が残っている", 2, this.session.getBooks().size());
		assertNull("棚から外れた本が残っている", this.session.getBook(staleId));
		assertNotNull("開いている本まで消えている", this.session.getBook(this.bookId));
	}

	@Test
	public void anOpenedShelfBookSurvivesTheShelfSwitch() throws Exception
	{
		// 表示中の本を消すと、ビューアーが開いているページが丸ごと 404 になる
		shelfWith("cover-a");
		String coverId = coverBookId();
		assertEquals(200, get(base() + "api/book/" + coverId).statusCode());

		Path other = temp.getRoot().toPath().resolve("other-shelf");
		EpubFixture.standard().writeTo(other.resolve("c.epub"));
		this.session.setLibrary(other, LibraryScanner.scan(other, 3, null));

		assertNotNull("展開済みの本まで捨てている", this.session.getBook(coverId));
		assertEquals(200, get(base() + "api/book/" + coverId).statusCode());
	}

	@Test
	public void coverEtagFollowsTheFileEvenWithoutARescan() throws Exception
	{
		// 本棚のスキャンは起動時の 1 回しか走らない。ETag がスキャン時の値で
		// 固定されていると、変換し直しても 304 を返し続けて古い表紙が出る
		shelfWith("cover-a");
		String coverId = coverBookId();
		Path epub = this.session.getBook(coverId).getEpubFile();

		String first = get(base() + "api/library/cover/" + coverId)
			.headers().firstValue("ETag").orElse("");

		java.nio.file.Files.setLastModifiedTime(epub,
			java.nio.file.attribute.FileTime.fromMillis(
				java.nio.file.Files.getLastModifiedTime(epub).toMillis() + 5000));

		HttpResponse<String> after = this.client.send(
			HttpRequest.newBuilder(URI.create(base() + "api/library/cover/" + coverId))
				.header("If-None-Match", first).GET().build(),
			HttpResponse.BodyHandlers.ofString());

		assertEquals("差し替わったのに 304 を返している", 200, after.statusCode());
		org.junit.Assert.assertNotEquals(first, after.headers().firstValue("ETag").orElse(""));
	}

	@Test
	public void theListPicksUpACoverAddedAfterTheScan() throws Exception
	{
		// 一覧を出し直しても hasCover:false のままだと、ビューアーはサムネイルを
		// 取りに来ない。表紙エンドポイントだけを新しくしても届かない
		shelfWith("plain-b");
		Path epub = this.session.getBooks().stream()
			.filter(b -> b.getEpubFile().getFileName().toString().equals("plain-b.epub"))
			.findFirst().orElseThrow().getEpubFile();
		assertTrue(get(base() + "api/library").body().contains("\"hasCover\":false"));

		// 表紙付きに変換し直す
		EpubFixture.withEpub3Cover().writeTo(epub);
		java.nio.file.Files.setLastModifiedTime(epub,
			java.nio.file.attribute.FileTime.fromMillis(
				java.nio.file.Files.getLastModifiedTime(epub).toMillis() + 5000));

		String json = get(base() + "api/library").body();
		assertTrue("再変換で付いた表紙が一覧に反映されない", json.contains("\"hasCover\":true"));
	}

	@Test
	public void aRegeneratedBookWithADifferentCoverPathStillShowsACover() throws Exception
	{
		// サイズと更新時刻を見直すだけでは足りない。再変換で OPF の表紙 href が
		// 変わると、古いパスを新しい ZIP に探しに行って「表紙なし」に落ちる
		shelfWith("cover-a");
		String coverId = coverBookId();
		Path epub = this.session.getBook(coverId).getEpubFile();
		assertEquals(200, get(base() + "api/library/cover/" + coverId).statusCode());

		// 同じパスに、表紙の置き場所が違う EPUB を書き直す
		EpubFixture renamed = EpubFixture.standard();
		renamed.putBytes("OPS/images/front.png", EpubFixture.PNG_1PX);
		renamed.put("OPS/package.opf", EpubFixture.packageOpf().replace(
			"    <item id=\"ncx\"",
			"    <item id=\"cover-img\" properties=\"cover-image\" href=\"images/front.png\""
			+ " media-type=\"image/png\"/>\n    <item id=\"ncx\""));
		renamed.writeTo(epub);
		java.nio.file.Files.setLastModifiedTime(epub,
			java.nio.file.attribute.FileTime.fromMillis(
				java.nio.file.Files.getLastModifiedTime(epub).toMillis() + 5000));

		assertEquals("再変換で表紙が消えている", 200,
			get(base() + "api/library/cover/" + coverId).statusCode());
	}

	@Test
	public void coverApiServesAJpegThumbnail() throws Exception
	{
		shelfWith("cover-a");
		String coverId = coverBookId();

		HttpResponse<byte[]> response = this.client.send(
			HttpRequest.newBuilder(URI.create(base() + "api/library/cover/" + coverId)).GET().build(),
			HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(200, response.statusCode());
		assertEquals("image/jpeg", response.headers().firstValue("Content-Type").orElse(""));
		assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
		assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body())));
	}

	@Test
	public void coverApiRevalidatesInsteadOfExpiring() throws Exception
	{
		// 本棚は数百枚を並べるのでスクロールのたびに作り直すと重い。一方で
		// 「変換し直したら新しい方を見たい」ので、期限付きキャッシュではなく
		// 毎回問い合わせて 304 を返す
		shelfWith("cover-a");
		String coverId = coverBookId();

		HttpResponse<String> first = get(base() + "api/library/cover/" + coverId);
		String etag = first.headers().firstValue("ETag").orElse("");
		assertTrue("ETag が付いていない", !etag.isEmpty());
		assertEquals("no-cache", first.headers().firstValue("Cache-Control").orElse(""));

		HttpResponse<String> second = this.client.send(
			HttpRequest.newBuilder(URI.create(base() + "api/library/cover/" + coverId))
				.header("If-None-Match", etag).GET().build(),
			HttpResponse.BodyHandlers.ofString());
		assertEquals(304, second.statusCode());
		assertTrue("304 なのに本体を返している", second.body().isEmpty());
	}

	@Test
	public void coverApiIs404ForBooksWithoutOne() throws Exception
	{
		shelfWith("plain-b");
		String plainId = this.session.getBooks().stream()
			.filter(b -> b.getEpubFile().getFileName().toString().equals("plain-b.epub"))
			.findFirst().orElseThrow().getId();

		assertEquals(404, get(base() + "api/library/cover/" + plainId).statusCode());
		// 本棚に載っていない本 (変換して開いただけの本) も対象外
		assertEquals(404, get(base() + "api/library/cover/" + this.bookId).statusCode());
		assertEquals(404, get(base() + "api/library/cover/nosuchbook").statusCode());
	}

	@Test
	public void libraryBooksAreNotExtractedUntilOpened() throws Exception
	{
		// 数百冊を並べても展開コストが出ないことが本棚の前提
		shelfWith("cover-a", "plain-b");
		String coverId = coverBookId();
		get(base() + "api/library");
		get(base() + "api/library/cover/" + coverId);

		assertNull("一覧を出しただけで展開している", this.session.getBook(coverId).getDir());

		// 選択して初めて展開される
		assertEquals(200, get(base() + "api/book/" + coverId).statusCode());
		assertNotNull(this.session.getBook(coverId).getDir());
	}

	/** 表紙付きの本の bookId */
	private String coverBookId()
	{
		return this.session.getBooks().stream()
			.filter(b -> b.getEpubFile().getFileName().toString().equals("cover-a.epub"))
			.findFirst().orElseThrow().getId();
	}

	@Test
	public void settingsRoundTripThroughApi() throws Exception
	{
		assertEquals("{}", get(base() + "api/settings").body());

		HttpRequest post = HttpRequest.newBuilder(URI.create(base() + "api/settings"))
			.POST(HttpRequest.BodyPublishers.ofString("{\"theme\":\"dark\"}"))
			.build();
		HttpResponse<String> saved = this.client.send(post, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, saved.statusCode());
		assertTrue(saved.body().contains("\"saved\":true"));

		assertEquals("{\"theme\":\"dark\"}", get(base() + "api/settings").body());
	}

	private HttpResponse<String> post(String path) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(path))
			.POST(HttpRequest.BodyPublishers.noBody()).build();
		return this.client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	public void revealOpensTheRegisteredEpubOnly() throws Exception
	{
		// 実際にファイラを起動すると CI で窓が開くので差し替える
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);

		assertEquals(204, post(base() + "api/book/" + this.bookId + "/reveal").statusCode());
		assertEquals(1, opened.size());
		// 開く対象はリクエストではなくセッションが持つ EPUB から決まること
		assertEquals(this.session.getBook(this.bookId).getEpubFile(), opened.get(0));
	}

	@Test
	public void revealRejectsUnknownBookAndGetRequests() throws Exception
	{
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);

		// 未登録の bookId ではファイラを起動しない
		assertEquals(404, post(base() + "api/book/nosuchbook/reveal").statusCode());
		// GET で開けると <img src> 等で意図せず起動できてしまう
		assertEquals(405, get(base() + "api/book/" + this.bookId + "/reveal").statusCode());
		// bookId が空だと prefix と suffix が重なる。以前は substring(9, 8) で
		// StringIndexOutOfBoundsException になり 500 を返していた。
		// このサーバは未知パスへの POST を一律 405 にするので、ここも 405 になる
		assertEquals(405, post(base() + "api/book/reveal").statusCode());
		// スラッシュ入りの bookId も引けないだけ
		assertEquals(404, post(base() + "api/book/a/b/reveal").statusCode());
		assertTrue("拒否したのにファイラを起動している", opened.isEmpty());
	}

	@Test
	public void revealIsRejectedWhenTheFolderIsGone() throws Exception
	{
		// kindlegen 経路では EPUB を消してから展開済みのものを配信し続けることがある。
		// 存在しないパスでファイラを起動すると、Windows はマイドキュメントを開いてしまう
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);

		Path gone = temp.getRoot().toPath().resolve("gone").resolve("book.epub");
		String goneId = this.session.addBook(gone);

		assertEquals(404, post(base() + "api/book/" + goneId + "/reveal").statusCode());
		assertTrue("フォルダが無いのにファイラを起動している", opened.isEmpty());
	}

	/** ヘッダ付きの POST。ブラウザからのクロスオリジン POST を再現する */
	private HttpResponse<String> postFrom(String path, String body, String... headers)
		throws IOException, InterruptedException
	{
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(path))
			.POST(HttpRequest.BodyPublishers.ofString(body));
		for (int i = 0; i < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
		return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	public void postFromAnotherOriginIsRejected() throws Exception
	{
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);

		// 防御がパス上のトークン単独だと、トークンを知る第三者のページから
		// クロスオリジンで POST を打てる。単純 POST はプリフライトを伴わず CORS では止まらない
		assertEquals(403, postFrom(base() + "api/book/" + this.bookId + "/reveal", "",
			"Origin", "http://evil.example").statusCode());
		assertTrue("他オリジンからの POST でファイラを起動している", opened.isEmpty());

		// 設定も書き換えられない
		assertEquals(403, postFrom(base() + "api/settings", "{\"theme\":\"dark\"}",
			"Origin", "http://evil.example").statusCode());
		assertEquals("{}", get(base() + "api/settings").body());

		// heartbeat / bye も同じ扱い。守るのは clients の登録・削除で、
		// 偽のタブを生かし続けたり、bye で CLI を短い猶予のまま終了させたりできないようにする。
		// (トークンが一致した時点で lastContactNanos は更新される。これは GET でも同じで、
		//  トークン単独防御のままの部分。ここで守れるのはタブ単位の生存管理の方)
		assertEquals(403, postFrom(base() + "api/heartbeat?tab=x", "",
			"Origin", "http://evil.example").statusCode());
		assertEquals(403, postFrom(base() + "api/bye?tab=x", "",
			"Origin", "http://evil.example").statusCode());
		assertFalse("拒否したのにタブとして登録している", this.server.isCloseNotified());
	}

	@Test
	public void headerComparisonToleratesCaseAndSpacing() throws Exception
	{
		String url = base() + "api/heartbeat?tab=x";
		// ヘッダ名は JDK 側で正規化されるが、値の揺れはこちらで吸収する必要がある
		assertEquals(204, postFrom(url, "", "Origin", " " + origin().toUpperCase(java.util.Locale.ROOT) + " ",
			"Sec-Fetch-Site", " Same-Origin ").statusCode());
		// 揺れを吸収した結果として他オリジンまで通してしまわないこと
		assertEquals(403, postFrom(url, "", "Origin", " HTTP://EVIL.EXAMPLE ").statusCode());
	}

	@Test
	public void postFromTheViewersOwnOriginIsAccepted() throws Exception
	{
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);

		// ビューアーが実際に送るのはこの組み合わせ
		assertEquals(204, postFrom(base() + "api/book/" + this.bookId + "/reveal", "",
			"Origin", origin(), "Sec-Fetch-Site", "same-origin").statusCode());
		assertEquals(1, opened.size());
		assertEquals(204, postFrom(base() + "api/heartbeat?tab=x", "",
			"Origin", origin(), "Sec-Fetch-Site", "same-origin").statusCode());
	}

	@Test
	public void postWithoutOriginHeaderIsAccepted() throws Exception
	{
		// ブラウザは GET / HEAD 以外で必ず Origin を付けるため、無い = ブラウザ発ではない。
		// CSRF は被害者のブラウザを踏み台にする攻撃なので、ここを弾いても防御にはならず、
		// curl / java.net.http からの利用が壊れるだけ
		java.util.List<Path> opened = new java.util.ArrayList<>();
		this.server.setRevealer(opened::add);
		assertEquals(204, post(base() + "api/book/" + this.bookId + "/reveal").statusCode());
		assertEquals(1, opened.size());
	}

	@Test
	public void crossSiteFetchMetadataIsRejectedEvenWithoutOrigin() throws Exception
	{
		// Sec-Fetch-Site も Forbidden header name でページの JavaScript からは詐称できない。
		// Origin と独立に見て、どちらか一方でも他所を指していたら拒否する
		String url = base() + "api/heartbeat?tab=x";
		assertEquals(403, postFrom(url, "", "Sec-Fetch-Site", "cross-site").statusCode());
		assertEquals(403, postFrom(url, "", "Sec-Fetch-Site", "same-site").statusCode());
		assertEquals(204, postFrom(url, "", "Sec-Fetch-Site", "same-origin").statusCode());
		// アドレスバー直打ちなどユーザー操作起点。POST では通常起きないが敵ではない
		assertEquals(204, postFrom(url, "", "Sec-Fetch-Site", "none").statusCode());
		// 自オリジンを名乗っていても Sec-Fetch-Site が他所なら拒否する
		assertEquals(403, postFrom(url, "", "Origin", origin(), "Sec-Fetch-Site", "cross-site").statusCode());
	}

	@Test
	public void opaqueOriginIsRejected() throws Exception
	{
		// sandbox iframe や data: からの POST は Origin: null を送る。同一オリジンとは認めない
		assertEquals(403, postFrom(base() + "api/heartbeat?tab=x", "", "Origin", "null").statusCode());
		// ポートが違えば別オリジン (ローカルの別サーバからの踏み台を防ぐ)
		assertEquals(403, postFrom(base() + "api/heartbeat?tab=x", "",
			"Origin", "http://127.0.0.1:" + (this.server.getPort() + 1)).statusCode());
	}

	@Test
	public void loopbackOriginsCoverTheAliasesUsersMayType() throws Exception
	{
		// URL はログにも出るので、ユーザーが localhost で開き直すことがある。
		// ホスト名を解決して判定すると攻撃者の Origin で名前解決が走るため、表記を列挙する
		java.util.Set<String> origins = PreviewServer.loopbackOrigins("127.0.0.1", 12345);
		assertTrue(origins.contains("http://127.0.0.1:12345"));
		assertTrue(origins.contains("http://localhost:12345"));
		assertTrue(origins.contains("http://[::1]:12345"));
		assertFalse(origins.contains("http://127.0.0.1:12346"));
		// bind 先が IPv4 のときは自身の表記が 127.0.0.1 と重複する (Set.of だと落ちる)
		assertEquals(3, origins.size());

		java.util.Set<String> ipv6 = PreviewServer.loopbackOrigins("[0:0:0:0:0:0:0:1]", 80);
		assertTrue(ipv6.contains("http://[0:0:0:0:0:0:0:1]:80"));
		assertTrue(ipv6.contains("http://[::1]:80"));
	}

	@Test
	public void heartbeatResetsTheIdleTimer() throws Exception
	{
		// CLI プレビューは heartbeat が途絶えたらブラウザが閉じられたとみなして終了する。
		// 閾値より確実に長く待ってから beat を送り、値が「戻る」ことを検証する
		// (待ち時間が短いと、更新処理を削除してもテストが通ってしまう)
		Thread.sleep(300);
		long beforeBeat = this.server.getMillisSinceLastContact();
		assertTrue("前提: 十分な無通信時間が経過していること (" + beforeBeat + "ms)", beforeBeat >= 250);

		assertEquals(204, post(base() + "api/heartbeat").statusCode());

		long afterBeat = this.server.getMillisSinceLastContact();
		assertTrue("heartbeat で無通信時間がリセットされること (" + beforeBeat + " -> " + afterBeat + ")",
			afterBeat < beforeBeat);
	}

	@Test
	public void closeNotificationIsRetractedByALaterHeartbeat() throws Exception
	{
		// タブを閉じた通知の後に heartbeat が届いたら、終了予定を取り消す
		// (bfcache から復帰した場合など)
		assertFalse(this.server.isCloseNotified());

		assertEquals(204, post(base() + "api/bye?tab=x").statusCode());
		assertTrue("閉じた通知が記録されること", this.server.isCloseNotified());

		assertEquals(204, post(base() + "api/heartbeat?tab=x").statusCode());
		assertFalse("復帰したら終了しないこと", this.server.isCloseNotified());
		assertFalse(goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS * 3));
	}

	@Test
	public void clientIdFallsBackWhenNotSupplied() throws Exception
	{
		// タブ ID を送ってこないクライアントも 1 タブとして扱う
		assertEquals(204, post(base() + "api/heartbeat").statusCode());
		assertFalse(goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS * 3));
	}

	@Test
	public void idleThresholdsDistinguishCloseNotification()
	{
		// 閉じた通知があれば短い猶予、無ければ長い無通信時間で判断する
		assertTrue("閉じた通知の猶予は heartbeat 間隔(15秒)より長いこと",
			PreviewServer.CLOSE_GRACE_MILLIS > 15_000L);
		assertTrue("無通信の猶予はバックグラウンドタブの抑制(約1分間隔)に耐えること",
			PreviewServer.IDLE_TIMEOUT_MILLIS >= 180_000L);
		assertTrue(PreviewServer.IDLE_TIMEOUT_MILLIS > PreviewServer.CLOSE_GRACE_MILLIS);
	}

	/** now を進めた状態で判定させる */
	private boolean goneAfterMillis(long millis)
	{
		return this.server.isViewerGone(System.nanoTime() + millis * 1_000_000L);
	}

	@Test
	public void aliveTabKeepsTheServerRunningPastTheCloseGrace() throws Exception
	{
		this.server.noteClientAlive("tab-1");
		assertFalse("通信直後に終了判定してはならない", goneAfterMillis(0));
		// バックグラウンドタブは heartbeat が約 60 秒間隔まで間引かれる。
		// 閉じた通知の猶予(20秒)を超えても生存扱いでなければならない
		assertFalse("抑制された heartbeat の間隔で終了してはならない",
			goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS * 3));
	}

	@Test
	public void closingOneTabDoesNotKillAnotherLiveTab() throws Exception
	{
		// 2 つ開いて前面タブだけ閉じたとき、残ったバックグラウンドタブを巻き添えにしない
		this.server.noteClientAlive("tab-front");
		this.server.noteClientAlive("tab-back");
		this.server.noteClientGone("tab-front");

		assertFalse("閉じた通知が記録されてはならない (生存タブがある)", this.server.isCloseNotified());
		assertFalse("残ったタブがあるうちは終了しない",
			goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS * 3));
	}

	@Test
	public void lastTabClosingEndsTheSessionAfterTheShortGrace() throws Exception
	{
		this.server.noteClientAlive("tab-only");
		this.server.noteClientGone("tab-only");

		assertTrue("最後のタブが閉じたら記録されること", this.server.isCloseNotified());
		assertFalse("猶予内は終了しない", goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS / 2));
		assertTrue("猶予を過ぎたら終了する", goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS + 1000));
	}

	@Test
	public void silentTabEventuallyExpires() throws Exception
	{
		// 閉じた通知が届かないままブラウザが落ちた場合は、長い方の猶予で終了する
		this.server.noteClientAlive("tab-silent");

		assertFalse(goneAfterMillis(PreviewServer.IDLE_TIMEOUT_MILLIS / 2));
		assertTrue(goneAfterMillis(PreviewServer.IDLE_TIMEOUT_MILLIS + 1000));
	}

	@Test
	public void heartbeatRegistersTheTabFromTheQueryString() throws Exception
	{
		assertEquals(204, post(base() + "api/heartbeat?tab=abc").statusCode());
		assertFalse(goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS * 3));

		assertEquals(204, post(base() + "api/bye?tab=abc").statusCode());
		assertTrue("同じタブ ID で閉じたら終了対象になること",
			goneAfterMillis(PreviewServer.CLOSE_GRACE_MILLIS + 1000));
	}

	@Test
	public void wrongTokenLooksTheSameForEveryMethod() throws Exception
	{
		// メソッドで応答が割れるとトークンの当たり判定を推測されるため、常に 404 にする
		String wrong = origin() + "/p/deadbeef/api/heartbeat";
		assertEquals(404, get(wrong).statusCode());

		HttpRequest post = HttpRequest.newBuilder(URI.create(wrong))
			.POST(HttpRequest.BodyPublishers.noBody()).build();
		assertEquals(404, this.client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode());

		HttpRequest put = HttpRequest.newBuilder(URI.create(wrong))
			.PUT(HttpRequest.BodyPublishers.noBody()).build();
		assertEquals(404, this.client.send(put, HttpResponse.BodyHandlers.ofString()).statusCode());
	}

	@Test
	public void postIsRejectedOnNonSettingsPaths() throws Exception
	{
		HttpRequest post = HttpRequest.newBuilder(URI.create(base() + "api/session"))
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();
		assertEquals(405, this.client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode());
	}

	@Test
	public void pathDecodingKeepsPlusAndRejectsBrokenEscapes()
	{
		assertEquals("a+b.png", PreviewServer.decodePath("a+b.png"));
		assertEquals("あ.png", PreviewServer.decodePath("%E3%81%82.png"));
		assertEquals(null, PreviewServer.decodePath("%zz"));
		assertEquals(null, PreviewServer.decodePath("abc%4"));
	}

	@Test
	public void contentTypeMapping()
	{
		// XML 系は charset を付けない (BOM / XML 宣言による判定を潰さないため)
		assertEquals("application/xhtml+xml", PreviewServer.contentType("a/b.xhtml"));
		assertEquals("application/xml", PreviewServer.contentType("a/b.xml"));
		// CSS も BOM / @charset の判定を潰さないよう charset を付けない
		assertEquals("text/css", PreviewServer.contentType("a/b.css"));
		assertEquals("image/png", PreviewServer.contentType("a/b.PNG"));
		assertEquals("font/otf", PreviewServer.contentType("a/b.otf"));
		assertEquals("application/octet-stream", PreviewServer.contentType("mimetype"));
	}

	// ---- 本棚の「続きを取る」（internal #11） ----

	/** 掲載元のある本と無い本の棚を作り、掲載元のある本の ID を返す */
	private String[] shelfForUpdate() throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("upd");
		EpubFixture.withSource("https://ncode.syosetu.com/n1234ab/").writeTo(shelf.resolve("web.epub"));
		EpubFixture.standard().writeTo(shelf.resolve("local.epub"));
		this.session.setLibrary(List.of(new LibraryShelf(shelf, LibraryScanner.scan(shelf, 3, null))));
		String json = get(base() + "api/library").body();
		java.util.regex.Matcher web = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"[^}]*\"fileName\":\"web.epub\"").matcher(json);
		java.util.regex.Matcher local = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"[^}]*\"fileName\":\"local.epub\"").matcher(json);
		assertTrue(json, web.find());
		assertTrue(json, local.find());
		return new String[]{ web.group(1), local.group(1) };
	}

	/** 仕事が終わるまで待って、最後の状態の JSON を返す */
	private String waitForJob(String jobJson) throws Exception
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"job\":\"([0-9a-f]+)\"").matcher(jobJson);
		assertTrue(jobJson, m.find());
		String body = jobJson;
		for (int i = 0; i < 200; i++) {
			body = get(base() + "api/jobs/" + m.group(1)).body();
			if (!body.contains("\"state\":\"queued\"") && !body.contains("\"state\":\"running\"")) return body;
			Thread.sleep(20);
		}
		return body;
	}

	@Test
	public void updatingWithoutAnUpdaterIsUnavailable() throws Exception
	{
		String[] ids = shelfForUpdate();
		HttpResponse<String> response = post(base() + "api/book/" + ids[0] + "/update");
		assertEquals(503, response.statusCode());
		assertTrue(response.body(), response.body().contains("\"error\""));
	}

	@Test
	public void aBookWithoutASourceCannotBeUpdated() throws Exception
	{
		String[] ids = shelfForUpdate();
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		this.server.setBookUpdater((url, file) -> { calls.incrementAndGet(); return new BookUpdater.Result(true, false, "ok"); });
		assertEquals(400, post(base() + "api/book/" + ids[1] + "/update").statusCode());
		assertEquals(404, post(base() + "api/book/nosuchbook/update").statusCode());
		assertEquals("GET では更新しない", 405, get(base() + "api/book/" + ids[0] + "/update").statusCode());
		assertEquals(403, postFrom(base() + "api/book/" + ids[0] + "/update", "", "Origin", "https://evil.example").statusCode());
		assertEquals(0, calls.get());
	}

	@Test
	public void anUpdateRunsInTheBackgroundAndReportsItsState() throws Exception
	{
		String[] ids = shelfForUpdate();
		java.util.List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
		this.server.setBookUpdater((url, file) -> {
			calls.add(url + " " + file.getFileName());
			return new BookUpdater.Result(true, false, "変換しました");
		});
		HttpResponse<String> response = post(base() + "api/book/" + ids[0] + "/update");
		assertEquals(202, response.statusCode());
		String done = waitForJob(response.body());
		assertTrue(done, done.contains("\"state\":\"done\""));
		assertTrue(done, done.contains("\"message\":\"変換しました\""));
		assertEquals(List.of("https://ncode.syosetu.com/n1234ab/ web.epub"), calls);
	}

	@Test
	public void noUpdateAndFailuresAreReported() throws Exception
	{
		String[] ids = shelfForUpdate();
		this.server.setBookUpdater((url, file) -> new BookUpdater.Result(false, true, "更新はありません"));
		String noUpdate = waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		assertTrue(noUpdate, noUpdate.contains("\"state\":\"noUpdate\""));

		this.server.setBookUpdater((url, file) -> { throw new IllegalStateException("落ちた"); });
		String failedByException = waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		assertTrue(failedByException, failedByException.contains("\"state\":\"failed\""));

		//Error でも「実行中」のまま残さない（残ると、同じ本の仕事として返し続けて二度と更新できない。PR #118 のゲート2）
		this.server.setBookUpdater((url, file) -> { throw new AssertionError("落ちた"); });
		String failed = waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		assertTrue(failed, failed.contains("\"state\":\"failed\""));
		assertTrue(failed, failed.contains("落ちた"));
		assertEquals(404, get(base() + "api/jobs/0123456789abcdef01234567").statusCode());
	}

	/** 守りで止めた理由（作品が見つからない・話数が減った）は、仕事の状態として出す。「減ったまま更新する」は更新する側へ渡す */
	@Test
	public void guardStopsAreReportedAndFewerEpisodesCanBeAllowed() throws Exception
	{
		String[] ids = shelfForUpdate();
		this.server.setBookUpdater((url, file) -> new BookUpdater.Result(false, false, "掲載元で作品が見つかりません (HTTP 404)", "gone"));
		String gone = waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		assertTrue(gone, gone.contains("\"state\":\"gone\""));
		assertTrue(gone, gone.contains("404"));

		java.util.List<Boolean> allowed = new java.util.concurrent.CopyOnWriteArrayList<>();
		this.server.setBookUpdater(new BookUpdater() {
			@Override
			public Result update(String sourceUrl, java.nio.file.Path epubFile)
			{
				throw new AssertionError("守りのある呼び方を使う");
			}

			@Override
			public Result update(String sourceUrl, java.nio.file.Path epubFile, boolean allowFewerEpisodes)
			{
				allowed.add(allowFewerEpisodes);
				return allowFewerEpisodes ? new Result(true, false, "変換しました")
					: new Result(false, false, "話数が減っています (前 3 話 → 今 2 話)", "shrunk");
			}
		});
		String shrunk = waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		assertTrue(shrunk, shrunk.contains("\"state\":\"shrunk\""));
		String accepted = waitForJob(post(base() + "api/book/" + ids[0] + "/update?allowFewer=1").body());
		assertTrue(accepted, accepted.contains("\"state\":\"done\""));
		assertEquals(java.util.List.of(false, true), allowed);
	}

	/** 更新中の本に「減ったまま更新する」が来たら、ふつうの仕事にまとめずに断る（まとめると、その仕事はまた止まる） */
	@Test
	public void aDifferentChoiceForARunningBookIsRefused() throws Exception
	{
		String[] ids = shelfForUpdate();
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		this.server.setBookUpdater((url, file) -> {
			release.await(5, java.util.concurrent.TimeUnit.SECONDS);
			return new BookUpdater.Result(true, false, "ok");
		});
		String first = post(base() + "api/book/" + ids[0] + "/update").body();
		assertEquals(409, post(base() + "api/book/" + ids[0] + "/update?allowFewer=1").statusCode());
		assertEquals("同じ選び方なら同じ仕事", 202, post(base() + "api/book/" + ids[0] + "/update").statusCode());
		release.countDown();
		waitForJob(first);
	}

	/** 同じ本の仕事が終わっていなければ、新しく積まずに同じ仕事を返す（連打で同じ作品を何度も取りに行かない） */
	@Test
	public void theSameBookIsNotQueuedTwice() throws Exception
	{
		String[] ids = shelfForUpdate();
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		this.server.setBookUpdater((url, file) -> {
			calls.incrementAndGet();
			release.await(5, java.util.concurrent.TimeUnit.SECONDS);
			return new BookUpdater.Result(true, false, "ok");
		});
		String first = post(base() + "api/book/" + ids[0] + "/update").body();
		String second = post(base() + "api/book/" + ids[0] + "/update").body();
		java.util.regex.Pattern job = java.util.regex.Pattern.compile("\"job\":\"([0-9a-f]+)\"");
		java.util.regex.Matcher a = job.matcher(first);
		java.util.regex.Matcher b = job.matcher(second);
		assertTrue(a.find());
		assertTrue(b.find());
		assertEquals(a.group(1), b.group(1));
		release.countDown();
		waitForJob(first);
		assertEquals(1, calls.get());
	}

	/** 更新が済んだら、本棚の一覧に上書きした本の新しい題が出る（一覧は読むたびに本を読み直す） */
	@Test
	public void theShelfEntryIsReadAgainAfterAnUpdate() throws Exception
	{
		String[] ids = shelfForUpdate();
		this.server.setBookUpdater((url, file) -> {
			EpubFixture renamed = EpubFixture.withSource(url);
			renamed.put("OPS/package.opf", EpubFixture.withSourcesOpf("urn:uuid:" + com.github.hmdev.info.BookLedger.identifierFor(url), url)
				.replace("テスト書籍", "続きの入った書籍"));
			java.nio.file.Files.delete(file);
			renamed.writeTo(file);
			java.nio.file.Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
			return new BookUpdater.Result(true, false, "ok");
		});
		waitForJob(post(base() + "api/book/" + ids[0] + "/update").body());
		String json = get(base() + "api/library").body();
		assertTrue(json, json.contains("\"title\":\"続きの入った書籍\""));
	}

	/** まだ終わっていない仕事が上限まであれば、新しく積まずに断る（PR #118 の codex） */
	@Test
	public void aFullQueueRefusesNewUpdates() throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("many");
		for (int i = 0; i < PreviewServer.MAX_JOBS + 1; i++) {
			EpubFixture.withSource("https://ncode.syosetu.com/n" + (1000 + i) + "ab/").writeTo(shelf.resolve("b" + i + ".epub"));
		}
		this.session.setLibrary(List.of(new LibraryShelf(shelf, LibraryScanner.scan(shelf, 3, null))));
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		this.server.setBookUpdater((url, file) -> {
			release.await(10, java.util.concurrent.TimeUnit.SECONDS);
			return new BookUpdater.Result(true, false, "ok");
		});
		String json = get(base() + "api/library").body();
		java.util.regex.Matcher ids = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);
		java.util.List<Integer> statuses = new java.util.ArrayList<>();
		while (ids.find()) statuses.add(post(base() + "api/book/" + ids.group(1) + "/update").statusCode());
		release.countDown();
		assertEquals(PreviewServer.MAX_JOBS + 1, statuses.size());
		assertEquals("上限までは積む", PreviewServer.MAX_JOBS, statuses.stream().filter(c -> c == 202).count());
		assertEquals("上限を超えたら断る", Integer.valueOf(503), statuses.get(statuses.size() - 1));
	}

	// ---- Web 本棚（internal #11 の案 A） ----

	/** 試験用の Web 本棚。決めた場所と、選んだことにするフォルダを持つ */
	private static class FakeWebShelf implements WebShelf
	{
		volatile Path location;
		final java.util.List<Path> set = new java.util.concurrent.CopyOnWriteArrayList<>();
		volatile Path toPick;
		volatile boolean pickable = true;
		volatile java.util.concurrent.CountDownLatch holdPick;
		final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		@Override public Path location() { return this.location; }
		@Override public void setLocation(Path dir) { this.set.add(dir); this.location = dir; }
		@Override public boolean canPick() { return this.pickable; }
		@Override public Path pickFolder(Path initial) throws Exception
		{
			if (this.holdPick != null) {
				this.entered.countDown();
				this.holdPick.await(5, java.util.concurrent.TimeUnit.SECONDS);
			}
			return this.toPick;
		}
	}

	private HttpResponse<String> postText(String path, String body) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(path))
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
		return this.client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	public void webShelfNeedsTheApp() throws Exception
	{
		assertEquals(503, get(base() + "api/webshelf").statusCode());
		assertEquals(503, postText(base() + "api/webshelf", "/tmp/x").statusCode());
		assertEquals(501, post(base() + "api/webshelf/pick").statusCode());
	}

	/** 提案の場所は、最初の棚の下の Web。決めると、フォルダを作って開く側に渡す */
	@Test
	public void theWebShelfIsSuggestedAndSet() throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("shelf1");
		java.nio.file.Files.createDirectories(shelf);
		this.session.setLibrary(List.of(new LibraryShelf(shelf, LibraryScanner.scan(shelf, 3, null))));
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		String before = get(base() + "api/webshelf").body();
		assertTrue(before, before.contains("\"suggestion\":" + Json.str(shelf.toAbsolutePath().normalize().resolve("Web").toString())));
		assertFalse(before, before.contains("\"location\""));
		assertTrue(before, before.contains("\"canPick\":true"));
		assertTrue("変換するものが無ければ落とせない: " + before, before.contains("\"canDownload\":false"));
		this.server.setBookUpdater((u, f) -> new BookUpdater.Result(true, false, "ok"));
		assertTrue(get(base() + "api/webshelf").body().contains("\"canDownload\":true"));

		Path dir = temp.getRoot().toPath().resolve("web shelf").resolve("深い");
		HttpResponse<String> r = postText(base() + "api/webshelf", dir + "\r\n");
		assertEquals(r.body(), 200, r.statusCode());
		assertTrue("フォルダを作る", java.nio.file.Files.isDirectory(dir));
		assertEquals(List.of(dir.toRealPath()), web.set);
		assertTrue(r.body(), r.body().contains("\"location\":" + Json.str(dir.toRealPath().toString())));
	}

	/** 相対パス・空・読めないパスは断る。決めない */
	@Test
	public void aBadWebShelfPathIsRefused() throws Exception
	{
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		assertEquals(400, postText(base() + "api/webshelf", "relative/dir").statusCode());
		assertEquals(400, postText(base() + "api/webshelf", "   ").statusCode());
		Path file = temp.newFile("not-a-dir").toPath();
		assertEquals("ファイルの場所はフォルダにできない", 400, postText(base() + "api/webshelf", file.toString()).statusCode());
		assertEquals(400, postText(base() + "api/webshelf", "/" + "a".repeat(PreviewServer.MAX_PATH_BYTES + 10)).statusCode());
		assertTrue(web.set.isEmpty());
	}

	/** 末尾が空白のフォルダ名はそのまま使う（落とすと別のフォルダになる。PR の手元の codex） */
	@Test
	public void aTrailingSpaceInTheWebShelfNameIsKept() throws Exception
	{
		org.junit.Assume.assumeFalse("Windows のフォルダ名は末尾の空白を持てない", System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		Path dir = temp.getRoot().toPath().resolve("ends with space ");
		assertEquals(200, postText(base() + "api/webshelf", dir.toString()).statusCode());
		assertTrue(java.nio.file.Files.isDirectory(dir));
		assertEquals(dir.toRealPath(), web.set.get(0));
	}

	/** 書き込めないフォルダは断る（落とした本を置けない） */
	@Test
	public void anUnwritableWebShelfIsRefused() throws Exception
	{
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		java.io.File locked = temp.newFolder("locked");
		org.junit.Assume.assumeTrue("書き込みを禁じられない環境（root・Windows）では飛ばす", locked.setWritable(false) && !java.nio.file.Files.isWritable(locked.toPath()));
		try {
			assertEquals(400, postText(base() + "api/webshelf", locked.getAbsolutePath()).statusCode());
			assertTrue(web.set.isEmpty());
		} finally {
			locked.setWritable(true);
		}
	}

	/** 棚が上限まであるときは、今の棚に含まれる場所だけ決められる */
	@Test
	public void theWebShelfRespectsTheShelfLimit() throws Exception
	{
		java.util.List<LibraryShelf> shelves = new java.util.ArrayList<>();
		for (int i = 0; i < LibraryScanner.MAX_SHELVES; i++) {
			Path shelf = temp.getRoot().toPath().resolve("s" + i);
			java.nio.file.Files.createDirectories(shelf);
			shelves.add(new LibraryShelf(shelf, List.of()));
		}
		this.session.setLibrary(shelves);
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		assertEquals(409, postText(base() + "api/webshelf", temp.getRoot().toPath().resolve("elsewhere").toString()).statusCode());
		assertFalse("断ったら、作ったフォルダを残さない", java.nio.file.Files.exists(temp.getRoot().toPath().resolve("elsewhere")));
		//棚の中の場所は、別名のパス（シンボリックリンク）で書かれても棚の中（mac の /tmp と /private/tmp）
		Path alias = temp.getRoot().toPath().resolve("alias");
		try {
			java.nio.file.Files.createSymbolicLink(alias, temp.getRoot().toPath().resolve("s1"));
		} catch (UnsupportedOperationException | IOException e) {
			alias = null;
		}
		if (alias != null) assertEquals(200, postText(base() + "api/webshelf", alias.resolve("Web").toString()).statusCode());
		assertEquals(200, postText(base() + "api/webshelf", temp.getRoot().toPath().resolve("s0").resolve("Web").toString()).statusCode());
		assertTrue("実体のパスで渡す", web.set.stream().allMatch(p -> p.equals(p.toAbsolutePath().normalize())));
	}

	/** フォルダ選択は選んだパスか、選ばなかったことを返す。場所は決めない。同時に 2 つは出さない。出せなければ 501 */
	@Test
	public void pickingAFolderReturnsItWithoutSettingIt() throws Exception
	{
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		Path chosen = temp.newFolder("chosen").toPath();
		web.toPick = chosen;
		String picked = post(base() + "api/webshelf/pick").body();
		assertTrue(picked, picked.contains("\"path\":" + Json.str(chosen.toAbsolutePath().normalize().toString())));
		assertTrue("選んだだけでは決めない", web.set.isEmpty());

		web.toPick = null;
		assertTrue(post(base() + "api/webshelf/pick").body().contains("\"cancelled\":true"));

		web.holdPick = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CompletableFuture<HttpResponse<String>> first = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try { return post(base() + "api/webshelf/pick"); } catch (Exception e) { throw new IllegalStateException(e); }
		});
		assertTrue("1 本目が選択画面を出した", web.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
		int second = post(base() + "api/webshelf/pick").statusCode();
		web.holdPick.countDown();
		assertEquals("出している最中は断る", 409, second);
		assertEquals(200, first.get().statusCode());
		assertEquals(405, get(base() + "api/webshelf/pick").statusCode());

		web.pickable = false;
		assertEquals(501, post(base() + "api/webshelf/pick").statusCode());
	}

	/** 断ったら、いま作ったフォルダは親まで残さない（PR のゲート2） */
	@Test
	public void aRefusedWebShelfLeavesNoCreatedParents() throws Exception
	{
		java.util.List<LibraryShelf> shelves = new java.util.ArrayList<>();
		for (int i = 0; i < LibraryScanner.MAX_SHELVES; i++) {
			Path shelf = temp.getRoot().toPath().resolve("p" + i);
			java.nio.file.Files.createDirectories(shelf);
			shelves.add(new LibraryShelf(shelf, List.of()));
		}
		this.session.setLibrary(shelves);
		this.server.setWebShelf(new FakeWebShelf());
		Path top = temp.getRoot().toPath().resolve("NewRoot");
		assertEquals(409, postText(base() + "api/webshelf", top.resolve("a").resolve("b").toString()).statusCode());
		assertFalse("作った親も消す", java.nio.file.Files.exists(top));
	}

	/**
	 * 棚の中の場所は、棚の一覧に書かれた綴りで記録する（実体のパスで書くと、一覧の重複の見分けや入れ子の畳み込みが効かない）。
	 * 起動中の本棚に棚を足すのは、棚の外のときだけ。足すのに失敗しても場所は決まる（PR のゲート2）
	 */
	@Test
	public void theWebShelfKeepsTheShelfSpellingAndAddsOnlyNewShelves() throws Exception
	{
		Path shelf = temp.getRoot().toPath().resolve("listed");
		java.nio.file.Files.createDirectories(shelf);
		Path alias = temp.getRoot().toPath().resolve("alias");
		try {
			java.nio.file.Files.createSymbolicLink(alias, shelf);
		} catch (UnsupportedOperationException | IOException e) {
			org.junit.Assume.assumeNoException("シンボリックリンクを作れない環境", e);
		}
		this.session.setLibrary(List.of(new LibraryShelf(alias, List.of())));
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		java.util.List<Path> added = new java.util.concurrent.CopyOnWriteArrayList<>();
		this.server.setShelfAdder(added::add);

		//実体のパスで頼んでも、棚の一覧の綴り（alias）で記録する。本棚には足さない
		assertEquals(200, postText(base() + "api/webshelf", shelf.toRealPath().resolve("Web").toString()).statusCode());
		assertEquals(alias.toAbsolutePath().normalize().resolve("Web"), web.set.get(0));
		assertTrue("棚の中なら足さない", added.isEmpty());

		//棚の外なら足す。足すのに失敗しても決まる
		this.server.setShelfAdder(folder -> { throw new IOException("読み直せない"); });
		Path outside = temp.getRoot().toPath().resolve("outside");
		assertEquals(200, postText(base() + "api/webshelf", outside.toString()).statusCode());
		assertEquals(outside.toRealPath(), web.location);
		this.server.setShelfAdder(added::add);
		Path outside2 = temp.getRoot().toPath().resolve("outside2");
		assertEquals(200, postText(base() + "api/webshelf", outside2.toString()).statusCode());
		assertEquals(List.of(outside2.toRealPath()), added);
	}

	/** 保存に失敗したら、いま作ったフォルダは残さない（PR の codex） */
	@Test
	public void aFailedSaveLeavesNoCreatedFolder() throws Exception
	{
		this.server.setWebShelf(new FakeWebShelf() {
			@Override public void setLocation(Path dir) { throw new IllegalStateException("保存できない"); }
		});
		Path top = temp.getRoot().toPath().resolve("Fresh");
		assertEquals(500, postText(base() + "api/webshelf", top.resolve("x").toString()).statusCode());
		assertFalse(java.nio.file.Files.exists(top));
	}

	/** 棚が上限まであっても、今の棚の親なら決められる（子の棚は畳まれて、棚の数は増えない。PR の codex） */
	@Test
	public void aParentOfTheShelvesFitsTheLimit() throws Exception
	{
		Path parent = temp.getRoot().toPath().resolve("parent");
		java.util.List<LibraryShelf> shelves = new java.util.ArrayList<>();
		for (int i = 0; i < LibraryScanner.MAX_SHELVES; i++) {
			Path shelf = parent.resolve("c" + i);
			java.nio.file.Files.createDirectories(shelf);
			shelves.add(new LibraryShelf(shelf, List.of()));
		}
		this.session.setLibrary(shelves);
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		assertEquals(200, postText(base() + "api/webshelf", parent.toString()).statusCode());
		assertEquals(1, web.set.size());
	}

	/** 提案の場所は、Web 本棚そのものからは作らない（Web 本棚の中の Web を提案しない。win2 の確認） */
	@Test
	public void theSuggestionSkipsTheWebShelfItself() throws Exception
	{
		Path webDir = temp.newFolder("myweb").toPath();
		Path other = temp.newFolder("other").toPath();
		this.session.setLibrary(List.of(new LibraryShelf(webDir, List.of()), new LibraryShelf(other, List.of())));
		FakeWebShelf web = new FakeWebShelf();
		web.location = webDir;
		this.server.setWebShelf(web);
		String json = get(base() + "api/webshelf").body();
		assertTrue(json, json.contains("\"suggestion\":" + Json.str(other.toAbsolutePath().normalize().resolve("Web").toString())));
	}

	/** 既にある壊れたリンクの先を頼まれて断っても、そのリンクは消さない（PR の codex） */
	@Test
	public void aRefusalKeepsAnExistingDanglingLink() throws Exception
	{
		this.server.setWebShelf(new FakeWebShelf());
		Path link = temp.getRoot().toPath().resolve("dangling");
		try {
			java.nio.file.Files.createSymbolicLink(link, temp.getRoot().toPath().resolve("gone"));
		} catch (UnsupportedOperationException | IOException e) {
			org.junit.Assume.assumeNoException("シンボリックリンクを作れない環境", e);
		}
		assertEquals(400, postText(base() + "api/webshelf", link.resolve("x").toString()).statusCode());
		assertTrue("利用者のリンクは残る", java.nio.file.Files.isSymbolicLink(link));
	}

	// ---- URL から落とす（internal #11 の案 A） ----

	@Test
	public void downloadingNeedsTheAppAWebShelfAndAUrl() throws Exception
	{
		assertEquals(503, postText(base() + "api/download", "https://ncode.syosetu.com/n1234ab/").statusCode());
		this.server.setBookUpdater((url, file) -> new BookUpdater.Result(true, false, "ok"));
		FakeWebShelf web = new FakeWebShelf();
		this.server.setWebShelf(web);
		for (String bad : new String[]{ "", "ftp://example.com/x", "https://exa mple.com/", "https:///nohost", "not a url" }) {
			assertEquals(bad, 400, postText(base() + "api/download", bad).statusCode());
		}
		HttpResponse<String> need = postText(base() + "api/download", "https://ncode.syosetu.com/n1234ab/");
		assertEquals("Web 本棚がまだ無ければ聞いてもらう", 409, need.statusCode());
		assertTrue(need.body(), need.body().contains("\"needShelf\":true"));
		assertEquals(405, get(base() + "api/download").statusCode());
		//決めてあった Web 本棚のフォルダが消えていても、聞き直す
		web.location = temp.getRoot().toPath().resolve("removed");
		assertEquals(409, postText(base() + "api/download", "https://ncode.syosetu.com/n1234ab/").statusCode());
	}

	private volatile String lastJob;

	/** 落とせたら、Web 本棚を本棚に出す（読み直す）。同じ URL は 1 つの仕事。落とせない本棚は失敗で知らせる */
	@Test
	public void aDownloadRunsAsAJobAndShowsTheShelf() throws Exception
	{
		Path shelfDir = temp.newFolder("web").toPath();
		FakeWebShelf web = new FakeWebShelf();
		web.location = shelfDir;
		this.server.setWebShelf(web);
		java.util.List<Path> added = new java.util.concurrent.CopyOnWriteArrayList<>();
		java.util.List<String> stateWhileAdding = new java.util.concurrent.CopyOnWriteArrayList<>();
		this.server.setShelfAdder(folder -> {
			added.add(folder);
			//本棚に出している最中は、まだ「済んだ」と言わない
			try {
				stateWhileAdding.add(get(base() + "api/jobs/" + lastJob).body());
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		});
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.List<String> got = new java.util.concurrent.CopyOnWriteArrayList<>();
		this.server.setBookUpdater(new BookUpdater() {
			@Override public Result update(String sourceUrl, Path epubFile) { return new Result(true, false, "ok"); }
			@Override public Result download(String url, Path dir) throws Exception
			{
				got.add(url + " -> " + dir);
				release.await(5, java.util.concurrent.TimeUnit.SECONDS);
				return new Result(true, false, "変換しました");
			}
		});
		String url = "https://ncode.syosetu.com/n1234ab/";
		String first = postText(base() + "api/download", "  " + url + "\n").body();
		String second = postText(base() + "api/download", url).body();
		java.util.regex.Matcher a = java.util.regex.Pattern.compile("\"job\":\"([0-9a-f]+)\"").matcher(first);
		java.util.regex.Matcher b = java.util.regex.Pattern.compile("\"job\":\"([0-9a-f]+)\"").matcher(second);
		assertTrue(first, a.find());
		assertTrue(second, b.find());
		assertEquals("同じ URL は同じ仕事", a.group(1), b.group(1));
		lastJob = a.group(1);
		release.countDown();
		String done = waitForJob(first);
		assertTrue(done, done.contains("\"state\":\"done\""));
		assertEquals(List.of(url + " -> " + shelfDir), got);
		assertEquals("落とした本を本棚に出す", List.of(shelfDir), added);
		assertEquals(1, stateWhileAdding.size());
		assertTrue(stateWhileAdding.get(0), stateWhileAdding.get(0).contains("\"state\":\"running\""));

		this.server.setBookUpdater((u, f) -> new BookUpdater.Result(true, false, "ok"));
		String unsupported = waitForJob(postText(base() + "api/download", "https://kakuyomu.jp/works/1").body());
		assertTrue(unsupported, unsupported.contains("\"state\":\"failed\""));
	}
}
