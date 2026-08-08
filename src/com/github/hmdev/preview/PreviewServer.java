package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * プレビュー配信用のローカル HTTP サーバ。
 *
 * <p>EPUB を {@code file://} で直接開くと {@code application/xhtml+xml} の扱いと
 * {@code @font-face} の読み込みがブラウザ・OS ごとに不安定なため、
 * HTTP 配信することが本方式の本質となる。</p>
 *
 * <p>ローカルにポートを開くため、以下を必須の防御とする。</p>
 * <ol>
 *   <li>ループバックアドレスにのみ bind する (LAN へ露出させない)</li>
 *   <li>ポートは 0 を指定して OS 任せのランダム割り当てにする</li>
 *   <li>URL に起動ごとのワンタイムトークンを含め、不一致は 404 にする</li>
 *   <li>パスは URL デコード後に正規化し、展開ルート配下であることを検証する</li>
 * </ol>
 */
public class PreviewServer implements AutoCloseable
{
	private static final Logger logger = LoggerFactory.getLogger(PreviewServer.class);

	/**
	 * クラスパスから配信を許可するアセット (ホワイトリスト)。
	 * viewer.js を分割したファイルを追加したら、ここと viewer.html の script タグの両方を更新する。
	 */
	private static final String[] ALLOWED_ASSETS = {
		"viewer.css",
		"viewer-core.js",
		"viewer-util.js",
		"viewer-settings.js",
		"viewer-toc.js",
		"viewer-frame.js",
		"viewer-events.js",
		"viewer-inspector.js"
	};

	/**
	 * ビューアーが居なくなったとみなすまでの無通信時間。
	 * バックグラウンドタブでは heartbeat の setInterval がブラウザに抑制される
	 * (Chrome は hidden タブを概ね 1 分に 1 回まで間引く) ため、
	 * 送信間隔 15 秒に対して十分長く取る。
	 * 短くすると「別のタブを見ていただけでサーバが終了する」ことになる。
	 */
	public static final long IDLE_TIMEOUT_MILLIS = 300_000L;

	/**
	 * タブを閉じた通知 (sendBeacon) を受けた後の猶予。
	 * 別タブが生きていれば heartbeat 間隔 (15 秒) 以内に通知が取り消されるため、
	 * それより長く取る。
	 */
	public static final long CLOSE_GRACE_MILLIS = 20_000L;

	private final PreviewSession session;
	private final HttpServer server;
	private final ExecutorService executor;
	private final String basePath;
	private final PreviewSettingsStore settingsStore;
	/** URL に載せるホスト表記。IPv6 なら角括弧付き */
	private final String host;
	/**
	 * 最後にビューアーと通信した時刻。
	 * CLI プレビューは「ブラウザを閉じたらサーバも終わる」ようにしたいが、
	 * ブラウザの終了を直接検知する手段が無いため、
	 * ビューアーからの定期的な heartbeat が途絶えたことで判断する。
	 */
	private volatile long lastContactNanos = System.nanoTime();
	/**
	 * 「閉じた」通知を受け、生きているタブが 1 つも無くなったか。
	 * 生存判定は {@link #clients} で行うため、これはあくまで
	 * 「短い猶予で終わってよい」ことを示すフラグ。
	 */
	private volatile boolean closeNotified;
	/**
	 * 開いているビューアー (タブ) ごとの最終確認時刻。キーはタブが生成した ID。
	 *
	 * <p>サーバ全体で 1 つのフラグにすると、タブを 2 つ開いて片方を閉じたときに
	 * 残ったタブにも短い猶予が適用されてしまう。
	 * バックグラウンドタブは heartbeat が抑制される (Chrome は概ね 60 秒間隔まで間引く) ため、
	 * それだけで「生きているのに終了する」ことになる。タブ単位で持つ必要がある。</p>
	 */
	private final Map<String, Long> clients = new ConcurrentHashMap<>();

	public PreviewServer(PreviewSession session) throws IOException
	{
		this(session, new PreviewSettingsStore());
	}

	PreviewServer(PreviewSession session, PreviewSettingsStore settingsStore) throws IOException
	{
		this.session = session;
		this.settingsStore = settingsStore;
		this.basePath = "/p/" + session.getToken();
		InetAddress loopback = InetAddress.getLoopbackAddress();
		this.server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
		// 実際に bind したアドレスから URL を組み立てる。
		// IPv6 を優先する環境では ::1 で待ち受けるため、127.0.0.1 を決め打ちすると繋がらない
		this.host = urlHost(loopback);
		this.executor = Executors.newFixedThreadPool(4, runnable -> {
			Thread thread = new Thread(runnable, "aozora-preview-http");
			thread.setDaemon(true);
			return thread;
		});
		this.server.setExecutor(this.executor);
		this.server.createContext("/", this::handle);
	}

	/** サーバを開始する */
	public void start()
	{
		this.server.start();
		logger.info("プレビューサーバを開始しました: {}", getUrl());
	}

	/** ビューアーの URL */
	public String getUrl()
	{
		return "http://" + this.host + ":" + this.server.getAddress().getPort() + this.basePath + "/";
	}

	/**
	 * URL に載せられる形のホスト表記にする。
	 * IPv6 は角括弧で囲み、スコープ ID ({@code ::1%lo0} の {@code %lo0}) は落とす。
	 */
	static String urlHost(InetAddress address)
	{
		String literal = address.getHostAddress();
		int scope = literal.indexOf('%');
		if (scope >= 0) literal = literal.substring(0, scope);
		return (address instanceof java.net.Inet6Address) ? "[" + literal + "]" : literal;
	}

	/** 実際に割り当てられたポート */
	public int getPort()
	{
		return this.server.getAddress().getPort();
	}

	/**
	 * 最後にビューアーと通信してからの経過ミリ秒。
	 * PC のスリープ復帰や時刻補正で壁時計が跳んでも誤判定しないよう nanoTime を使う。
	 */
	public long getMillisSinceLastContact()
	{
		return (System.nanoTime() - this.lastContactNanos) / 1_000_000L;
	}

	/** ビューアーがタブを閉じたと通知してきたか (その後 heartbeat が来たら false に戻る) */
	public boolean isCloseNotified()
	{
		return this.closeNotified;
	}

	/** クエリ文字列から tab=... を取り出す。無ければ既定のキー */
	static String clientIdOf(HttpExchange exchange)
	{
		String query = exchange.getRequestURI().getRawQuery();
		if (query != null) {
			for (String part : query.split("&")) {
				if (part.startsWith("tab=")) {
					String id = part.substring(4);
					if (!id.isEmpty()) return id;
				}
			}
		}
		// タブ ID を送ってこないクライアントは 1 つのタブとして扱う
		return "default";
	}

	/** タブが生きていることを記録する */
	void noteClientAlive(String clientId)
	{
		long now = System.nanoTime();
		// GUI 経路では isViewerGone() が呼ばれず剪定の機会が無いため、ここでも掃除する
		pruneExpiredClients(now);
		this.clients.put(clientId, now);
		this.closeNotified = false;
	}

	/** 長く音沙汰の無いタブを忘れる */
	private void pruneExpiredClients(long nowNanos)
	{
		long limitNanos = IDLE_TIMEOUT_MILLIS * 1_000_000L;
		this.clients.values().removeIf(seen -> nowNanos - seen >= limitNanos);
	}

	/**
	 * タブが閉じられたことを記録する。
	 * 他に開いているタブが残っていれば、短い猶予による終了はしない。
	 */
	void noteClientGone(String clientId)
	{
		this.clients.remove(clientId);
		this.closeNotified = this.clients.isEmpty();
	}

	/**
	 * ビューアーがもう見ていないとみなせるか。
	 *
	 * <p>生きているタブが 1 つでもあれば終了しない。
	 * 全て居なくなった場合、閉じた通知によるものなら短い猶予で、
	 * 単に通信が途絶えただけなら長い猶予で判断する。</p>
	 */
	public boolean isViewerGone()
	{
		return isViewerGone(System.nanoTime());
	}

	/** 経過時間を指定できる版 (テスト用) */
	boolean isViewerGone(long nowNanos)
	{
		// 長く音沙汰の無いタブは閉じられたものとみなす
		pruneExpiredClients(nowNanos);
		if (!this.clients.isEmpty()) return false;

		long idleMillis = (nowNanos - this.lastContactNanos) / 1_000_000L;
		return this.closeNotified ? idleMillis >= CLOSE_GRACE_MILLIS : idleMillis >= IDLE_TIMEOUT_MILLIS;
	}

	@Override
	public void close()
	{
		this.server.stop(0);
		this.executor.shutdownNow();
	}

	// ------------------------------------------------------------------
	// ルーティング
	// ------------------------------------------------------------------

	private void handle(HttpExchange exchange)
	{
		try {
			String rawPath = exchange.getRequestURI().getRawPath();
			// トークンが一致しないリクエストは存在自体を伏せる。
			// メソッド判定より先に行う (405 と 404 で応答が割れると存在を推測されるため)
			if (rawPath == null
				|| (!rawPath.equals(this.basePath) && !rawPath.startsWith(this.basePath + "/"))) {
				respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
				return;
			}
			//トークンが一致した = ビューアーが生きている
			this.lastContactNanos = System.nanoTime();

			// 末尾スラッシュが無いとページ内の相対 URL が /p/api/... に解決されて壊れる
			if (rawPath.equals(this.basePath)) {
				// ?book=... を落とすと既定の本が開いてしまうので引き継ぐ。
				// メソッドを変えない 308 を使う
				String query = exchange.getRequestURI().getRawQuery();
				String location = this.basePath + "/" + ((query == null || query.isEmpty()) ? "" : "?" + query);
				exchange.getResponseHeaders().set("Location", location);
				exchange.getResponseHeaders().set("Cache-Control", "no-store");
				exchange.sendResponseHeaders(308, -1);
				return;
			}

			String method = exchange.getRequestMethod();
			boolean read = "GET".equals(method) || "HEAD".equals(method);
			if (!read && !"POST".equals(method)) {
				respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
				return;
			}
			String rest = rawPath.substring(this.basePath.length());
			if (rest.startsWith("/")) rest = rest.substring(1);
			String decoded = decodePath(rest);
			if (decoded == null) {
				// パーセントエスケープが壊れている
				respond(exchange, 400, "text/plain; charset=utf-8", "Bad Request".getBytes(StandardCharsets.UTF_8));
				return;
			}
			rest = decoded;

			if (rest.equals("api/heartbeat")) {
				// ビューアーが生きていることの通知。上で最終通信時刻を更新済み
				noteClientAlive(clientIdOf(exchange));
				respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
				return;
			}
			if (rest.equals("api/bye")) {
				// タブを閉じたときの sendBeacon。猶予を待たずに終了できるようにする
				noteClientGone(clientIdOf(exchange));
				respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
				return;
			}
			if (rest.equals("api/settings")) {
				serveSettings(exchange, method);
				return;
			}
			if (!read) {
				respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
				return;
			}
			if (rest.isEmpty() || rest.equals("index.html")) {
				serveClasspath(exchange, "viewer.html", "text/html; charset=utf-8");
			} else if (rest.startsWith("asset/")) {
				serveAsset(exchange, rest.substring("asset/".length()));
			} else if (rest.equals("api/session")) {
				respondJson(exchange, this.session.sessionJson());
			} else if (rest.startsWith("api/book/")) {
				serveBookApi(exchange, rest.substring("api/book/".length()));
			} else if (rest.startsWith("book/")) {
				serveBookFile(exchange, rest.substring("book/".length()));
			} else {
				respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			// InvalidPathException など非検査例外で接続が切れないよう、必ず応答を返す
			logger.debug("プレビューリクエストの処理に失敗しました", e);
			try {
				respond(exchange, 500, "text/plain; charset=utf-8",
					("Internal Server Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
			} catch (IOException ignored) {
				/* 意図的: レスポンスも返せない状態なので諦める */
			}
		} finally {
			exchange.close();
		}
	}

	/** 表示設定の取得 / 保存 */
	private void serveSettings(HttpExchange exchange, String method) throws IOException
	{
		if ("POST".equals(method)) {
			byte[] body = exchange.getRequestBody().readNBytes(PreviewSettingsStore.MAX_BYTES + 1);
			boolean saved = body.length <= PreviewSettingsStore.MAX_BYTES
				&& this.settingsStore.save(new String(body, StandardCharsets.UTF_8));
			StringBuilder buf = new StringBuilder();
			buf.append('{');
			Json.prop(buf, "saved", saved);
			buf.append('}');
			respond(exchange, saved ? 200 : 400, "application/json; charset=utf-8",
				buf.toString().getBytes(StandardCharsets.UTF_8));
			return;
		}
		respondJson(exchange, this.settingsStore.load());
	}

	/**
	 * URL パスのパーセントエスケープをデコードする。
	 * 壊れたエスケープは受け付けない (400 を返すため)。
	 *
	 * @return デコード結果。エスケープが壊れていれば null
	 */
	static String decodePath(String path)
	{
		return PathUtils.decodeUriStrict(path);
	}

	/** /api/book/{bookId} と /api/book/{bookId}/inspect */
	private void serveBookApi(HttpExchange exchange, String rest) throws IOException
	{
		int slash = rest.indexOf('/');
		String bookId = (slash < 0) ? rest : rest.substring(0, slash);
		String action = (slash < 0) ? "" : rest.substring(slash + 1);
		if (this.session.getBook(bookId) == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		try {
			if (action.isEmpty()) {
				respondJson(exchange, this.session.bookJson(bookId));
			} else if (action.equals("inspect")) {
				respondJson(exchange, this.session.inspectJson(bookId));
			} else {
				respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			logger.warn("EPUB の解析に失敗しました: {}", bookId, e);
			StringBuilder buf = new StringBuilder();
			buf.append('{');
			Json.prop(buf, "error", String.valueOf(e.getMessage()));
			buf.append('}');
			respond(exchange, 500, "application/json; charset=utf-8", buf.toString().getBytes(StandardCharsets.UTF_8));
		}
	}

	/** /book/{bookId}/{EPUB 内パス} */
	private void serveBookFile(HttpExchange exchange, String rest) throws IOException
	{
		int slash = rest.indexOf('/');
		if (slash < 0) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
			return;
		}
		String bookId = rest.substring(0, slash);
		String relative = rest.substring(slash + 1);
		PreviewSession.Book book = this.session.getBook(bookId);
		if (book == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		// 未展開のまま個別ファイルを要求された場合に備える
		this.session.ensureExtracted(bookId);

		String normalized = PathUtils.normalizeRelative(relative);
		if (normalized == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
			return;
		}
		Path root = book.getDir().toRealPath();
		// 正規化後にルート配下であることを必ず検証する (パストラバーサル防止)。
		// resolveInside は Windows で ':' '?' 等を含む名前が InvalidPathException になる件も吸収する
		// (非チェック例外がハンドラを抜けると 404 ではなく接続断になる)
		Path target = PathUtils.resolveInside(root, normalized);
		if (target == null || !Files.isRegularFile(target)) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
			return;
		}
		// iframe の sandbox と二重に、EPUB 由来のコンテンツからスクリプトを実行させない
		exchange.getResponseHeaders().set("Content-Security-Policy",
			"script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
		// 挿絵や埋め込みフォントは大きくなりうる。全体をヒープに載せず読みながら書き出す
		respondFile(exchange, contentType(normalized), target);
	}

	/** ファイルをヒープに全部載せずにストリーミングで返す */
	private void respondFile(HttpExchange exchange, String contentType, Path file) throws IOException
	{
		long size = Files.size(file);
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		if ("HEAD".equals(exchange.getRequestMethod())) {
			exchange.sendResponseHeaders(200, -1);
			return;
		}
		exchange.sendResponseHeaders(200, size);
		try (OutputStream out = exchange.getResponseBody()) {
			Files.copy(file, out);
		}
	}

	/** クラスパス上のビューアーアセットを配信する */
	private void serveAsset(HttpExchange exchange, String name) throws IOException
	{
		for (String allowed : ALLOWED_ASSETS) {
			if (allowed.equals(name)) {
				serveClasspath(exchange, name, contentType(name));
				return;
			}
		}
		respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
	}

	private void serveClasspath(HttpExchange exchange, String name, String contentType) throws IOException
	{
		try (InputStream in = PreviewServer.class.getResourceAsStream("assets/" + name)) {
			if (in == null) {
				respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
				return;
			}
			respond(exchange, 200, contentType, in.readAllBytes());
		}
	}

	private void respondJson(HttpExchange exchange, String json) throws IOException
	{
		respond(exchange, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
	}

	private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException
	{
		exchange.getResponseHeaders().set("Content-Type", contentType);
		// プレビューは常に最新を見たいのでキャッシュさせない
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		// 204/304 はボディを持てない。長さ 0 を渡すと JDK が警告を出し続けるので -1 を渡す
		// (heartbeat は 15 秒ごとに来るため、ここを誤るとログが埋まる)
		if (status == 204 || status == 304 || "HEAD".equals(exchange.getRequestMethod())) {
			exchange.sendResponseHeaders(status, -1);
			return;
		}
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	/**
	 * 拡張子から Content-Type を決める。
	 * XHTML を {@code application/xhtml+xml} で返さないとブラウザが HTML として扱い、
	 * 縦書き用の CSS 適用結果が実際と変わってしまう。
	 */
	static String contentType(String path)
	{
		return switch (PathUtils.extensionOf(path)) {
			// XML 系は charset を付けない。HTTP の charset は BOM や XML 宣言より優先されるため、
			// UTF-16 で作られた EPUB を UTF-8 と誤って解釈させてしまう。
			// 付けなければブラウザが XML の規則に従って自力で判定する
			case "xhtml" -> "application/xhtml+xml";
			case "html", "htm" -> "text/html; charset=utf-8";
			// CSS も charset を付けない。HTTP の charset は BOM や @charset より優先されるため、
			// UTF-16 のスタイルシートを持つ EPUB でスタイルが失われる
			case "css" -> "text/css";
			case "js" -> "text/javascript; charset=utf-8";
			case "json" -> "application/json; charset=utf-8";
			case "ncx" -> "application/x-dtbncx+xml";
			case "opf" -> "application/oebps-package+xml";
			case "xml" -> "application/xml";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "svg" -> "image/svg+xml";
			case "webp" -> "image/webp";
			case "ttf" -> "font/ttf";
			case "otf" -> "font/otf";
			case "woff" -> "font/woff";
			case "woff2" -> "font/woff2";
			case "txt" -> "text/plain; charset=utf-8";
			default -> "application/octet-stream";
		};
	}
}
