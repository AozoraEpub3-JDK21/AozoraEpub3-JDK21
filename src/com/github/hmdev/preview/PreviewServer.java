package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Headers;
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
 *   <li>状態を変える POST は {@code Origin} / {@code Sec-Fetch-Site} で発信元を検査する
 *       ({@link #isAllowedPostSource})</li>
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
		"viewer-inspector.js",
		"viewer-library.js",
		"viewer-ranking.js"
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

	/** フォルダを開く処理。テストが実際にファイラを起動しないよう差し替えられるようにする */
	interface Revealer
	{
		void reveal(Path file) throws IOException;
	}

	/** 本をゴミ箱へ移す処理。テストが実際にゴミ箱へ移さないよう差し替えられるようにする */
	interface Trasher
	{
		void moveToTrash(Path file) throws IOException;
	}

	/**
	 * OS のゴミ箱へ移す（internal #54）。完全には消さない（ゴミ箱から戻せる）。ゴミ箱が使えない環境（画面の無い環境など）では
	 * 消さずに投げる
	 */
	static void moveToTrash(Path file) throws IOException
	{
		if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
			//見えているパスと、実体のパス（SUBST のドライブ・リンク・フォルダに付けたドライブの先）の両方で確かめる
			String refusal = windowsTrashRefusal(file.toAbsolutePath().toString(), file.toRealPath().toString(), PreviewServer::windowsDriveType);
			if (refusal != null) throw new IOException(refusal);
		}
		java.awt.Desktop desktop;
		try {
			desktop = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
		} catch (RuntimeException e) {
			desktop = null;
		}
		if (desktop == null || !desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
			throw new IOException("この環境ではゴミ箱へ移せないので、消しませんでした");
		}
		if (!desktop.moveToTrash(file.toFile())) throw new IOException("ゴミ箱へ移せませんでした");
	}

	/**
	 * Windows でゴミ箱へ移さない本の理由。移せないときは null。JDK の moveToTrash は SHFileOperation を
	 * FOF_ALLOWUNDO・FOF_NOCONFIRMATION で呼ぶので、ゴミ箱の無い場所（ネットワーク・USB などの取り外せるドライブ）では
	 * <b>確かめずに完全に消し</b>、それでも成功を返す。260 文字以上のパスは JDK が黙って切り詰めるので、別の物を消しうる。
	 * ローカルの固定ドライブ（DriveType が Fixed）の、短いパスだけを移す
	 * @param driveType ドライブ文字（大文字）から .NET の DriveType の名前（Fixed・Network・Removable…）。分からなければ null
	 */
	static String windowsTrashRefusal(String path, String realPath, java.util.function.Function<Character, String> driveType)
	{
		if (path.length() >= 259 || realPath.length() >= 259) return "パスが長すぎるので、消しませんでした（ゴミ箱へ正しく移せません）";
		if (!onDriveLetter(path) || !onDriveLetter(realPath)) {
			return "ネットワーク上の本は、ゴミ箱に入らずに消えることがあるので、消しませんでした";
		}
		//SUBST のドライブ・別のドライブへのリンクは、実体のドライブにゴミ箱があるとは限らない（SUBST にはゴミ箱が無い）
		if (Character.toUpperCase(path.charAt(0)) != Character.toUpperCase(realPath.charAt(0))) {
			return "別のドライブにつながった場所の本は、ゴミ箱に入らずに消えることがあるので、消しませんでした";
		}
		String type = driveType.apply(Character.toUpperCase(path.charAt(0)));
		if (!"Fixed".equals(type)) {
			return "このドライブ（ネットワーク・USB など）の本は、ゴミ箱に入らずに消えることがあるので、消しませんでした";
		}
		return null;
	}

	/** A:〜Z: で始まるパスか（UNC・\\?\Volume{…} などは違う） */
	private static boolean onDriveLetter(String path)
	{
		if (path.length() < 3 || path.charAt(1) != ':' || (path.charAt(2) != '\\' && path.charAt(2) != '/')) return false;
		char c = Character.toUpperCase(path.charAt(0));
		return c >= 'A' && c <= 'Z';
	}

	/** その本を消している最中なら、断る文を言い換える（「更新しています」と言わない） */
	private String busyMessage(String bookId, String whileUpdating)
	{
		boolean trashing = this.jobs.values().stream().anyMatch(j -> j.bookId.equals(bookId) && j.active() && JOB_TRASH.equals(j.kind));
		return trashing ? "この本はいま本棚から消しています" : whileUpdating;
	}

	/** ドライブの種類（.NET の DriveType の名前。訳されない）。分からなければ null */
	private static String windowsDriveType(char letter)
	{
		try {
			Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
				"[System.IO.DriveInfo]::new('" + letter + "').DriveType").redirectErrorStream(true).start();
			p.getOutputStream().close();
			if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
			return p.exitValue() == 0 ? out : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private final PreviewSession session;
	private final HttpServer server;
	private final ExecutorService executor;
	private final String basePath;
	private final PreviewSettingsStore settingsStore;
	/** 本棚のサムネイル。生成コストが高いのでセッション内で使い回す */
	private final LibraryCovers covers = new LibraryCovers();
	/** テストスレッドが書き HTTP スレッドが読むので volatile */
	private volatile Revealer revealer = FileRevealer::reveal;
	private volatile Trasher trasher = PreviewServer::moveToTrash;
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

	/*
	 * 開いているタブを使い回す（本や本棚を開くたびに新しいタブを足さない）。
	 *
	 * 見えているタブ（ウィンドウの手前のタブで、ブラウザが隠されていない）は、受け箱（api/inbox）を
	 * 1 秒ごとに見に来る。見えていないタブは来ない。2026-10-11 の spike（Safari・Chrome、mac）で、
	 * 別のアプリの窓に覆われたタブも visible のまま、隠したブラウザや奥のタブは hidden になると確かめた。
	 * 開く側は、最近見に来たタブの受け箱に URL を入れて、取りに来るのを少し待つ。来なければ
	 * 今までどおり新しいタブを開く。ページからタブを手前に出すことはブラウザが許さないので、前には出ない
	 */

	/** 見えているタブが受け箱を見に来る間隔（ビューアーの INBOX_POLL と合わせる） */
	static final long INBOX_POLL_MILLIS = 1_000L;

	/** この間に見に来たタブを「見えている」とみなす */
	static final long INBOX_FRESH_MILLIS = 3_000L;

	/** 受け箱に入れてから、取りに来るのを待つ間 */
	static final long INBOX_WAIT_MILLIS = 2_500L;

	/** タブが受け箱を最後に見に来た時刻（nanoTime）と、そのときウィンドウがフォーカスを持っていたか */
	private record InboxVisit(long nanos, boolean focused) { }

	private final Map<String, InboxVisit> inboxVisits = new ConcurrentHashMap<>();

	/**
	 * 受け箱に入っている、開いてほしい URL。state は 0＝待ち・1＝渡した・3＝受け取ったと返事が来た・2＝下げた。
	 * 移るのは compareAndSet だけで、取るのと下げるのが重なっても、どちらか一方だけが勝つ（PR #129 の codex）。
	 * 「使い回した」と言えるのは 3 だけ。渡した応答がタブに届かない（閉じた・切れた）こともあるので、
	 * タブが移る前に返事（ack）をもらう。返事が来なければ新しいタブを開く（何も出ないより、2 つ出る方がよい）
	 */
	private record Delivery(String id, String url, java.util.concurrent.atomic.AtomicInteger state,
		java.util.concurrent.CountDownLatch acked) { }

	private final Map<String, Delivery> inbox = new ConcurrentHashMap<>();

	/** 渡して返事を待っている受け渡し。鍵は Delivery.id */
	private final Map<String, Delivery> awaitingAck = new ConcurrentHashMap<>();

	/** 本棚の「続きを取る」を行うもの。本棚を開く側（GUI・CLI）が渡す。null なら更新できない */
	private volatile BookUpdater bookUpdater;
	/** 更新は 1 冊ずつ（サイトへの負担と、変換の静的な状態を共有しないため） */
	private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread t = new Thread(runnable, "aozora-preview-update");
		t.setDaemon(true);
		return t;
	});
	/** 更新の仕事。鍵は仕事の ID（推測されにくい乱数） */
	private final Map<String, UpdateJob> jobs = new ConcurrentHashMap<>();
	/** 覚えておく仕事の数の上限（終わったものから忘れる） */
	static final int MAX_JOBS = 64;
	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	static final String JOB_UPDATE = "update";
	static final String JOB_REGENERATE = "regenerate";
	static final String JOB_REFETCH = "refetch";
	/** 本棚から消している印（列には積まない。消している間、その本の仕事を断るため） */
	static final String JOB_TRASH = "trash";

	/** 更新の仕事 1 つ。state は queued・running・done・noUpdate・failed、守りで止めたら gone・shrunk */
	static final class UpdateJob
	{
		final String id;
		final String bookId;
		final long createdNanos = System.nanoTime();
		volatile String state = "queued";
		volatile String message = "";
		/** 「減ったまま更新」で頼まれた仕事 */
		final boolean allowFewer;
		/** 仕事の種類: update（続きを取る）・regenerate（取りに行かずにキャッシュから作り直す）・refetch（全話を取り直す）。internal #54 */
		final String kind;
		/** 新着を確かめた結果（目次の話数・新しい話・改稿された話）。確かめる仕事でなければ -1 */
		volatile int episodes = -1;
		volatile int newEpisodes = -1;
		volatile int revisedEpisodes = -1;

		UpdateJob(String id, String bookId, boolean allowFewer)
		{
			this(id, bookId, allowFewer, JOB_UPDATE);
		}

		UpdateJob(String id, String bookId, boolean allowFewer, String kind)
		{
			this.id = id;
			this.bookId = bookId;
			this.allowFewer = allowFewer;
			this.kind = kind;
		}

		boolean active()
		{
			return "queued".equals(this.state) || "running".equals(this.state);
		}
	}

	/** Web 本棚の場所の読み書き（開く側が渡す。無ければ Web 本棚は使えない） */
	private volatile WebShelf webShelf;

	/** 本棚の「新しい版があります」（internal #48）。本棚を開く側（GUI・CLI）が渡す。無ければ知らせない */
	private volatile UpdateNotice updateNotice;

	public void setUpdateNotice(UpdateNotice updateNotice)
	{
		this.updateNotice = updateNotice;
	}

	/** 試験から: 渡された知らせ（PreviewLauncher が起動するサーバに渡しているかを確かめる） */
	UpdateNotice getUpdateNotice()
	{
		return this.updateNotice;
	}
	/** フォルダ選択を出している最中か（同時に 2 つ出さない） */
	private final java.util.concurrent.atomic.AtomicBoolean picking = new java.util.concurrent.atomic.AtomicBoolean();
	/** Web 本棚のパスとして受け取る本文の上限（バイト） */
	static final int MAX_PATH_BYTES = 4096;

	public void setWebShelf(WebShelf webShelf)
	{
		this.webShelf = webShelf;
	}

	/** 起動中の本棚に棚を足すもの（PreviewLauncher が渡す） */
	interface ShelfAdder
	{
		void add(Path folder) throws IOException;
	}

	private volatile ShelfAdder shelfAdder;

	void setShelfAdder(ShelfAdder shelfAdder)
	{
		this.shelfAdder = shelfAdder;
	}

	/** 本棚の「続きを取る」を行うものを渡す（null で外す） */
	public void setBookUpdater(BookUpdater bookUpdater)
	{
		this.bookUpdater = bookUpdater;
	}
	/**
	 * POST を受け付ける {@code Origin} (小文字)。
	 * ビューアーに渡す URL のオリジンに加え、ログを見てユーザーが手で開く可能性のある
	 * ループバック別名も含める。
	 */
	private final Set<String> allowedOrigins;

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
		this.allowedOrigins = loopbackOrigins(this.host, this.server.getAddress().getPort());
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
	 * このサーバ自身を指す {@code Origin} の集合を作る (すべて小文字)。
	 *
	 * <p>ブラウザに渡すのは {@code urlHost} 由来の 1 つだけだが、
	 * URL はログにも出力しており、ユーザーが {@code localhost} で開き直すことがある。
	 * ホスト名を解決して判定すると、攻撃者が指定した {@code Origin} で
	 * 名前解決を走らせることになるため、<b>受け付ける表記を列挙する</b>方式にする。</p>
	 *
	 * @param urlHost {@link #urlHost} が返すホスト表記 (IPv6 は角括弧付き)
	 * @param port 実際に bind したポート
	 */
	static Set<String> loopbackOrigins(String urlHost, int port)
	{
		// Set.of と違い Set.copyOf は重複を許す。urlHost は下記のいずれかと一致するのが普通
		return Set.copyOf(List.of(
			"http://" + urlHost.toLowerCase(Locale.ROOT) + ":" + port,
			"http://localhost:" + port,
			"http://127.0.0.1:" + port,
			"http://[::1]:" + port));
	}

	/**
	 * 状態を変える POST を受け付けてよい発信元か。
	 *
	 * <p>防御はパスに載せたトークン単独だったため、トークンを知る第三者のページから
	 * クロスオリジンで POST を打てた。単純 POST はプリフライトを伴わないので
	 * CORS では止まらず、{@code reveal} は OS のファイラを起動する
	 * (影響が「ローカル閲覧」ではなく「プロセス起動」になる)。
	 * {@code bookId} も {@code b1} 連番で推測しやすい。</p>
	 *
	 * <p>2 つのヘッダを独立に見て、どちらか一方でも他所を指していたら拒否する。
	 * いずれも <b>Forbidden header name</b> でページの JavaScript からは詐称できない。</p>
	 *
	 * <p><b>この検査は「GET / HEAD 以外」を条件に掛かる。状態を変えるエンドポイントは
	 * 必ず POST で足すこと。</b>GET で足すと無検査で通る。</p>
	 *
	 * <p><b>両方とも無い場合は許可する。</b>ブラウザは GET / HEAD 以外では常に
	 * {@code Origin} を付ける (Fetch 仕様) ため、無いということはブラウザ発ではない。
	 * CSRF は被害者のブラウザを踏み台にする攻撃なので、ブラウザ以外からの POST は
	 * この脅威の対象外であり、ここで弾いても防御にはならない
	 * (ローカルでコードを実行できる相手には元より意味がない)。
	 * 一方で弾くと curl / {@code java.net.http} からの利用が壊れる。</p>
	 */
	private boolean isAllowedPostSource(HttpExchange exchange)
	{
		Headers headers = exchange.getRequestHeaders();

		// same-origin: ビューアーからの fetch / sendBeacon
		// none: アドレスバー直打ちなどユーザー操作起点 (POST では通常起きないが敵ではない)
		String site = headers.getFirst("Sec-Fetch-Site");
		if (site != null) {
			site = site.trim().toLowerCase(Locale.ROOT);
			if (!site.equals("same-origin") && !site.equals("none")) return false;
		}

		String origin = headers.getFirst("Origin");
		// "null" は sandbox iframe や data: からの POST。同一オリジンとは認めない
		if (origin != null && !this.allowedOrigins.contains(origin.trim().toLowerCase(Locale.ROOT))) {
			return false;
		}
		return true;
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

	/** 問い合わせの name の値（無ければ null。値はそのまま） */
	static String queryValue(HttpExchange exchange, String name)
	{
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null) return null;
		for (String part : query.split("&")) {
			if (part.startsWith(name + "=") && part.length() > name.length() + 1) return part.substring(name.length() + 1);
		}
		return null;
	}

	/** 問い合わせに {@code flag}（例: {@code focus=1}）がそのまま入っているか */
	static boolean hasQueryFlag(HttpExchange exchange, String flag)
	{
		String query = exchange.getRequestURI().getRawQuery();
		return query != null && java.util.Arrays.asList(query.split("&")).contains(flag);
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
		//忘れたタブの受け箱の記録も捨てる（タブ ID は開くたびに増える）
		this.inboxVisits.keySet().retainAll(this.clients.keySet());
	}

	/**
	 * タブが閉じられたことを記録する。
	 * 他に開いているタブが残っていれば、短い猶予による終了はしない。
	 */
	void noteClientGone(String clientId)
	{
		this.clients.remove(clientId);
		this.inboxVisits.remove(clientId);
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
		this.updateExecutor.shutdownNow();
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
			// 状態を変えるのは POST だけなので、ここで一度だけ発信元を見る。
			// 個々のハンドラに置くと、エンドポイントを足したときに漏れる
			if (!read && !isAllowedPostSource(exchange)) {
				logger.warn("プレビューへの他オリジンからの POST を拒否しました: {} (Origin={}, Sec-Fetch-Site={})",
					rawPath, exchange.getRequestHeaders().getFirst("Origin"),
					exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"));
				respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
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
			if (rest.equals("api/inbox")) {
				serveInbox(exchange, method);
				return;
			}
			if (rest.equals("api/settings")) {
				serveSettings(exchange, method);
				return;
			}
			// bookId が空の "api/book/reveal" では前後が重なるので、長さで弾いてから切り出す
			// (substring(9, 8) になり StringIndexOutOfBoundsException で 500 になっていた)
			if (rest.startsWith("api/book/") && rest.endsWith("/reveal")
				&& rest.length() > "api/book/".length() + "/reveal".length()) {
				// POST なので、下の read 判定 (GET/HEAD 以外を 405) より前に処理する
				serveReveal(exchange, method,
					rest.substring("api/book/".length(), rest.length() - "/reveal".length()));
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/trash")
				&& rest.length() > "api/book/".length() + "/trash".length()) {
				serveTrash(exchange, method,
					rest.substring("api/book/".length(), rest.length() - "/trash".length()));
				return;
			}
			if (rest.equals("api/webshelf")) {
				serveWebShelf(exchange, method);
				return;
			}
			//新しい版の知らせ（internal #48）。閉じる・切り替えるのは POST なので、下の read 判定より前に処理する
			if (rest.equals("api/update") || rest.startsWith("api/update/")) {
				serveUpdateNotice(exchange, method, rest);
				return;
			}
			if (rest.equals("api/download")) {
				serveDownload(exchange, method);
				return;
			}
			if (rest.equals("api/webshelf/pick")) {
				serveWebShelfPick(exchange, method);
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/check")
				&& rest.length() > "api/book/".length() + "/check".length()) {
				serveCheck(exchange, method, rest.substring("api/book/".length(), rest.length() - "/check".length()));
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/rename")
				&& rest.length() > "api/book/".length() + "/rename".length()) {
				serveRename(exchange, method, rest.substring("api/book/".length(), rest.length() - "/rename".length()));
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/update")
				&& rest.length() > "api/book/".length() + "/update".length()) {
				//POST なので、下の read 判定より前に処理する（reveal と同じ）
				serveUpdate(exchange, method,
					rest.substring("api/book/".length(), rest.length() - "/update".length()), JOB_UPDATE);
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/regenerate")
				&& rest.length() > "api/book/".length() + "/regenerate".length()) {
				serveUpdate(exchange, method,
					rest.substring("api/book/".length(), rest.length() - "/regenerate".length()), JOB_REGENERATE);
				return;
			}
			if (rest.startsWith("api/book/") && rest.endsWith("/refetch")
				&& rest.length() > "api/book/".length() + "/refetch".length()) {
				serveUpdate(exchange, method,
					rest.substring("api/book/".length(), rest.length() - "/refetch".length()), JOB_REFETCH);
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
			} else if (rest.equals("api/library")) {
				respondJson(exchange, this.session.libraryJson());
			} else if (rest.startsWith("api/library/cover/")) {
				serveCover(exchange, rest.substring("api/library/cover/".length()));
			} else if (rest.equals("api/ranking")) {
				serveRanking(exchange);
			} else if (rest.startsWith("api/jobs/")) {
				serveJob(exchange, rest.substring("api/jobs/".length()));
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

	/** テスト用。実際にファイラを起動せず呼び出しだけ記録できるようにする */
	void setRevealer(Revealer revealer)
	{
		this.revealer = revealer;
	}

	/** テスト用。実際にゴミ箱へ移さず呼び出しだけ記録できるようにする */
	void setTrasher(Trasher trasher)
	{
		this.trasher = trasher;
	}

	/**
	 * /api/book/{bookId}/trash — 本棚の本を OS のゴミ箱へ移し、本棚から外す (POST。internal #54)。
	 * 消す対象はリクエストから受け取らず、本棚に載っている本の記録から引く。キャッシュと台帳は残す
	 * （同じ作品を ＋ で落とし直せる）。その本の仕事（更新・名前を変える・確かめる）がまだなら断る
	 */
	private void serveTrash(HttpExchange exchange, String method, String bookId) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		LibraryEntry entry = this.session.getLibraryEntry(bookId);
		if (entry == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		//確かめてから移すまでの間に、その本の仕事が積まれないよう、消している印の仕事を列に置く。
		//移す間は列を握らない（macOS の JDK はゴミ箱へ移すのをアプリの主スレッドで待つ。ほかの本の仕事まで止めない）
		UpdateJob marker;
		synchronized (this.jobs) {
			if (this.jobs.values().stream().anyMatch(j -> (j.bookId.equals(bookId) || j.bookId.equals("check:" + bookId)) && j.active())) {
				respondJsonStatus(exchange, 409, errorJson(busyMessage(bookId, "この本はいま更新しています。終わってから消してください")));
				return;
			}
			if (!Files.isRegularFile(entry.file())) {
				//もう無い本は、本棚から外すだけ
				this.session.removeLibraryEntry(bookId);
				respondJsonStatus(exchange, 200, "{\"ok\":true}");
				return;
			}
			forgetOldJobs();
			marker = new UpdateJob(newJobId(), bookId, false, JOB_TRASH);
			marker.state = "running";
			this.jobs.put(marker.id, marker);
		}
		try {
			this.trasher.moveToTrash(entry.file());
		} catch (IOException | RuntimeException e) {
			logger.warn("本をゴミ箱へ移せませんでした: {}", entry.file(), e);
			marker.message = String.valueOf(e.getMessage());
			marker.state = "failed";
			respondJsonStatus(exchange, 500, errorJson(e.getMessage() == null ? "ゴミ箱へ移せませんでした" : e.getMessage()));
			return;
		} catch (Error e) {
			marker.state = "failed";
			throw e;
		}
		this.session.removeLibraryEntry(bookId);
		//印を終わらせる（残ると、まだ終わっていない仕事として数え続け、列の上限を食う）
		marker.state = "done";
		respondJsonStatus(exchange, 200, jobJson(marker));
	}

	/**
	 * /api/book/{bookId}/reveal — EPUB のあるフォルダを OS のファイラで開く (POST)。
	 *
	 * <p><b>開く対象はリクエストから受け取らない。</b>bookId を session で引いて
	 * 登録済みの EPUB パスを使う。生パスを受け取ると、ローカルサーバとはいえ
	 * 任意のフォルダを開かせる踏み台になる。</p>
	 */
	/**
	 * 本棚の 1 冊を、掲載元から取り直して上書きする仕事を列に積む（internal #11）。202 と仕事の ID を返す。
	 * 同じ本の仕事がまだ終わっていなければ、その仕事を返す。kind は仕事の種類（JOB_*。作り直す・全話を取り直すは internal #54）
	 */
	private void serveUpdate(HttpExchange exchange, String method, String bookId, String kind) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		BookUpdater updater = this.bookUpdater;
		if (updater == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚からは更新できません（アプリから本棚を開いてください）"));
			return;
		}
		LibraryEntry entry = this.session.getLibraryEntry(bookId);
		if (entry == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		if (entry.source() == null) {
			respondJsonStatus(exchange, 400, errorJson(JOB_REGENERATE.equals(kind) ? "Web から取った本ではないので、作り直せません"
				: JOB_REFETCH.equals(kind) ? "Web から取った本ではないので、取り直せません"
				: "Web から取った本ではないので、続きを取れません"));
			return;
		}
		//利用者が「減ったまま更新」を選んだ（話数が減って止めた本を、それでも取り直す）。作り直すときは使わない
		boolean allowFewer = !JOB_REGENERATE.equals(kind) && hasQueryFlag(exchange, "allowFewer=1");
		UpdateJob job;
		synchronized (this.jobs) {
			job = this.jobs.values().stream().filter(j -> j.bookId.equals(bookId) && j.active()).findFirst().orElse(null);
			//「減ったまま更新」を、ふつうの更新の仕事に黙ってまとめない（その仕事はまた減ったところで止まる。PR のゲート2）
			//種類の違う仕事（続きを取る・作り直す・全話を取り直す）も、互いにまとめない（頼んだのと違う仕事の結果を返さない）
			if (job != null && (job.allowFewer != allowFewer || !job.kind.equals(kind))) {
				respondJsonStatus(exchange, 409, errorJson(busyMessage(bookId, "この本はいま更新しています。終わってから選び直してください")));
				return;
			}
			if (job == null) {
				forgetOldJobs();
				//まだ終わっていない仕事でいっぱいなら断る（列が際限なく伸びないように。PR #118 の codex）
				if (this.jobs.values().stream().filter(UpdateJob::active).count() >= MAX_JOBS) {
					respondJsonStatus(exchange, 503, errorJson("更新の順番待ちがいっぱいです。しばらくしてから試してください"));
					return;
				}
				job = new UpdateJob(newJobId(), bookId, allowFewer, kind);
				this.jobs.put(job.id, job);
				UpdateJob submitted = job;
				this.updateExecutor.submit(() -> runUpdate(submitted, updater, entry, allowFewer));
			}
		}
		respondJsonStatus(exchange, 202, jobJson(job));
	}

	private void runUpdate(UpdateJob job, BookUpdater updater, LibraryEntry entry, boolean allowFewer)
	{
		job.state = "running";
		try {
			BookUpdater.Result result = JOB_REGENERATE.equals(job.kind) ? updater.regenerate(entry.source(), entry.file())
				: JOB_REFETCH.equals(job.kind) ? updater.refetch(entry.source(), entry.file(), allowFewer)
				: updater.update(entry.source(), entry.file(), allowFewer);
			job.message = result.message() == null ? "" : result.message();
			if (result.ok()) {
				//上書きした本の題・表紙は、本棚の一覧（api/library）が読むたびに読み直すので、ここでは何もしない
				job.state = "done";
			} else if (result.stop() != null) {
				//守りで止めた（"gone"・"shrunk"）。本棚の画面が理由ごとに出し分ける
				job.state = result.stop();
			} else {
				job.state = result.noUpdate() ? "noUpdate" : "failed";
			}
		} catch (UnsupportedOperationException e) {
			job.message = JOB_REFETCH.equals(job.kind) ? "この本棚からは取り直せません" : "この本棚からは作り直せません";
			job.state = "failed";
		} catch (Throwable e) {
			//Error（メモリ不足など）でも「実行中」のまま残さない。残ると、同じ本の仕事として返し続けて二度と更新できない（PR #118 のゲート2）
			logger.warn("本棚の更新に失敗しました: {}", entry.file(), e);
			job.message = String.valueOf(e.getMessage());
			job.state = "failed";
			if (e instanceof Error) throw (Error)e;
		}
	}

	/**
	 * 新しい版の知らせ（internal #48）。GET api/update は知らせ（確かめる間隔がたっていれば GitHub に問い合わせる）、
	 * POST api/update/dismiss は本文の版の知らせを閉じる、POST api/update/enabled は本文の true/false で切り替える
	 */
	private void serveUpdateNotice(HttpExchange exchange, String method, String rest) throws IOException
	{
		UpdateNotice notice = this.updateNotice;
		boolean read = "GET".equals(method) || "HEAD".equals(method);
		if (rest.equals("api/update") && read) {
			//?peek と HEAD は覚えている版だけで答える（外に通信しない。表示設定の欄を開いたとき。HEAD で時刻を進めない）
			boolean peek = "HEAD".equals(method) || hasQueryFlag(exchange, "peek");
			respondJson(exchange, notice == null ? "{\"available\":false,\"enabled\":false,\"show\":false}" : notice.json(!peek));
			return;
		}
		boolean dismiss = rest.equals("api/update/dismiss");
		boolean enable = rest.equals("api/update/enabled");
		if (!(dismiss || enable)) {
			respondJsonStatus(exchange, 404, errorJson("見つかりません"));
			return;
		}
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		if (notice == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚では新しい版の知らせを使えません（アプリから本棚を開いてください）"));
			return;
		}
		String body = new String(exchange.getRequestBody().readNBytes(64), StandardCharsets.UTF_8).trim();
		boolean ok;
		if (dismiss) {
			ok = notice.dismiss(body);
		} else if (body.equals("true") || body.equals("false")) {
			ok = notice.setEnabled(Boolean.parseBoolean(body));
		} else {
			ok = false;
		}
		if (!ok) {
			respondJsonStatus(exchange, 400, errorJson("受け付けられませんでした"));
			return;
		}
		//応答では問い合わせない（閉じる・切り替えで GitHub を待たせない。PR のゲート2）
		respondJson(exchange, notice.json(false));
	}

	/**
	 * Web 本棚（internal #11 の案 A）。GET は今の場所・提案の場所・フォルダ選択を出せるか。
	 * POST は本文のパスに決める: 絶対パスで、フォルダにでき、書き込めること。棚の上限（{@link LibraryScanner#MAX_SHELVES}）を超えるなら断る
	 */
	private void serveWebShelf(HttpExchange exchange, String method) throws IOException
	{
		WebShelf shelf = this.webShelf;
		if (shelf == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚では Web 本棚を使えません（アプリから本棚を開いてください）"));
			return;
		}
		if ("POST".equals(method)) {
			byte[] body = exchange.getRequestBody().readNBytes(MAX_PATH_BYTES + 1);
			//末尾の改行だけを落とす。前後の空白は落とさない（末尾が空白のフォルダ名もある。PR の手元の codex）
			String text = new String(body, StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$", "");
			if (text.isBlank()) text = "";
			Path dir;
			try {
				if (body.length > MAX_PATH_BYTES || text.isEmpty()) throw new InvalidPathException(text, "empty or too long");
				dir = Path.of(text);
			} catch (InvalidPathException e) {
				respondJsonStatus(exchange, 400, errorJson("フォルダのパスとして読めません"));
				return;
			}
			if (!dir.isAbsolute()) {
				respondJsonStatus(exchange, 400, errorJson("フォルダは絶対パスで指定してください（例 " + webShelfSuggestion() + "）"));
				return;
			}
			dir = dir.normalize();
			//断るときに消せるよう、いま作るフォルダのうち一番上を覚える（親から作ることもある。PR のゲート2）
			Path created = null;
			//リンクはたどらずに見る（壊れたリンクを「無い」と見なすと、断るときに利用者のリンクを消す。PR の codex）
			for (Path p = dir; p != null && !Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS); p = p.getParent()) created = p;
			Path real;
			try {
				Files.createDirectories(dir);
				//実体のパスで比べる（mac の /tmp は /private/tmp。別名のままだと、棚の中の場所が別の棚に見える）
				real = dir.toRealPath();
			} catch (IOException e) {
				removeCreated(dir, created);
				respondJsonStatus(exchange, 400, errorJson("フォルダを作れませんでした: " + dir));
				return;
			}
			Path inShelf = inShelf(real);
			String refusal = null;
			int status = 400;
			if (!Files.isDirectory(real) || !Files.isWritable(real)) {
				refusal = "このフォルダには書き込めません: " + dir;
			} else if (inShelf == null && shelvesWith(real) > LibraryScanner.MAX_SHELVES) {
				refusal = "棚は " + LibraryScanner.MAX_SHELVES + " 個までです。アプリの「プレビュー」タブで棚を減らしてください";
				status = 409;
			}
			if (refusal != null) {
				removeCreated(dir, created);
				respondJsonStatus(exchange, status, errorJson(refusal));
				return;
			}
			//棚の中なら、棚の一覧に書かれた綴りで記録する（一覧の重複の見分けと、入れ子の棚の畳み込みが効くように。PR のゲート2）
			Path location = inShelf != null ? inShelf : real;
			try {
				shelf.setLocation(location);
			} catch (IOException | RuntimeException e) {
				//決められなかったなら、いま作ったフォルダは残さない（PR の codex）
				removeCreated(dir, created);
				logger.warn("Web 本棚を決められませんでした: {}", location, e);
				respondJsonStatus(exchange, 500, errorJson("Web 本棚を決められませんでした: " + e.getMessage()));
				return;
			}
			//棚の外なら、起動中の本棚に棚を足す。今の棚の中なら、もう本棚に出ている（読み直さない）。
			//足すのに失敗しても場所は決まっている（次に本棚を開いたときに出る）ので、失敗にしない
			ShelfAdder adder = this.shelfAdder;
			if (inShelf == null && adder != null) {
				try {
					adder.add(location);
				} catch (IOException | RuntimeException e) {
					logger.warn("Web 本棚を本棚に足せませんでした: {}", location, e);
				}
			}
		}
		respondJson(exchange, webShelfJson(shelf));
	}

	/** フォルダ選択を出す（POST）。選んだパスか、選ばなかったことを返す。結果は場所として決めない（画面が確かめてから POST api/webshelf） */
	private void serveWebShelfPick(HttpExchange exchange, String method) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		WebShelf shelf = this.webShelf;
		if (shelf == null || !shelf.canPick()) {
			respondJsonStatus(exchange, 501, errorJson("フォルダ選択を出せません。パスを入力してください"));
			return;
		}
		if (!this.picking.compareAndSet(false, true)) {
			respondJsonStatus(exchange, 409, errorJson("フォルダ選択はもう開いています（ブラウザの外の窓を見てください）"));
			return;
		}
		Path picked;
		try {
			Path location = shelf.location();
			Path initial = location != null ? location : webShelfSuggestion();
			//提案の場所がまだ無ければ、あるところまで上のフォルダから開く（書類フォルダから開かないように。win2 の確認）
			while (initial != null && !Files.isDirectory(initial)) initial = initial.getParent();
			picked = shelf.pickFolder(initial);
		} catch (UnsupportedOperationException e) {
			respondJsonStatus(exchange, 501, errorJson("フォルダ選択を出せません。パスを入力してください"));
			return;
		} catch (Exception e) {
			logger.warn("フォルダ選択に失敗しました", e);
			respondJsonStatus(exchange, 500, errorJson("フォルダ選択に失敗しました: " + e.getMessage()));
			return;
		} finally {
			this.picking.set(false);
		}
		StringBuilder buf = new StringBuilder(128);
		buf.append('{');
		if (picked != null) Json.prop(buf, "path", picked.toAbsolutePath().normalize().toString());
		else Json.prop(buf, "cancelled", true);
		buf.append('}');
		respondJson(exchange, buf.toString());
	}

	private String webShelfJson(WebShelf shelf)
	{
		StringBuilder buf = new StringBuilder(256);
		buf.append('{');
		Path location = shelf.location();
		if (location != null) Json.prop(buf, "location", location.toString());
		Json.prop(buf, "suggestion", webShelfSuggestion().toString());
		Json.prop(buf, "canPick", shelf.canPick());
		//落とすには、変換をするもの（BookUpdater）も要る
		Json.prop(buf, "canDownload", this.bookUpdater != null);
		buf.append('}');
		return buf.toString();
	}

	/**
	 * 提案の場所（今の棚の最初の下の Web、無ければ書類フォルダの下）。Web 本棚そのものは外す
	 * （Web 本棚は先頭の棚になるので、外さないと「Web 本棚の中の Web」を提案する。win2 の確認）
	 */
	private Path webShelfSuggestion()
	{
		WebShelf shelf = this.webShelf;
		Path location = shelf != null ? shelf.location() : null;
		java.util.List<Path> shelves = new java.util.ArrayList<>(this.session.getLibraryFolders());
		if (location != null) shelves.removeIf(s -> s.toAbsolutePath().normalize().equals(location.toAbsolutePath().normalize()));
		return WebShelfPrefs.suggest(shelves, Path.of(System.getProperty("user.home")));
	}

	/** その場所を足して入れ子を畳んだら、棚がいくつになるか（今の棚の親なら、子の棚は畳まれて減る。PR の codex） */
	private int shelvesWith(Path real)
	{
		java.util.List<Path> folders = new java.util.ArrayList<>();
		for (Path shelf : this.session.getLibraryFolders()) {
			Path root = shelf.toAbsolutePath().normalize();
			try {
				root = root.toRealPath();
			} catch (IOException e) {
				/* 意図的: 棚が消えていれば、書いたままのパスで数える */
			}
			folders.add(root);
		}
		folders.add(real);
		return PreviewLauncher.foldShelfFolders(folders).size();
	}

	/**
	 * 今の棚のどれかに含まれるなら、その棚の綴りで書いたパス（含まれるなら、棚の数は増えない）。含まれなければ null
	 * @param real 実体のパス
	 */
	private Path inShelf(Path real)
	{
		for (Path shelf : this.session.getLibraryFolders()) {
			Path listed = shelf.toAbsolutePath().normalize();
			Path root = listed;
			try {
				root = listed.toRealPath();
			} catch (IOException e) {
				/* 意図的: 棚が消えていれば、書いたままのパスで比べる */
			}
			if (real.startsWith(root)) return listed.resolve(root.relativize(real));
		}
		return null;
	}

	/** dir から top まで、いま作った空のフォルダを消す */
	private static void removeCreated(Path dir, Path top)
	{
		if (top == null) return;
		for (Path p = dir; p != null; p = p.getParent()) {
			try {
				Files.deleteIfExists(p);
			} catch (IOException e) {
				return;
			}
			if (p.equals(top)) return;
		}
	}

	/**
	 * 本棚の本の名前を変える（POST、本文は拡張子を除いた新しい名前。internal #11 の案 A）。Web から取った本だけ。
	 * 更新している最中の本は断る。変えたら本棚を読み直す
	 */
	private void serveRename(HttpExchange exchange, String method, String bookId) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		BookUpdater updater = this.bookUpdater;
		if (updater == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚からは名前を変えられません（アプリから本棚を開いてください）"));
			return;
		}
		LibraryEntry entry = this.session.getLibraryEntry(bookId);
		if (entry == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		if (entry.source() == null) {
			respondJsonStatus(exchange, 400, errorJson("Web から取った本だけ名前を変えられます"));
			return;
		}
		byte[] body = exchange.getRequestBody().readNBytes(MAX_PATH_BYTES + 1);
		String name = new String(body, StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$", "");
		if (body.length > MAX_PATH_BYTES) {
			respondJsonStatus(exchange, 400, errorJson("名前が長すぎます"));
			return;
		}
		//名前を変えるのも仕事の列で（変換が鍵を持っている間、HTTP のスレッドを待たせない。PR のゲート2）。
		//同じ本の仕事（更新・名前を変える）がまだ終わっていなければ断る
		UpdateJob job;
		synchronized (this.jobs) {
			//更新・名前を変える・新着を確かめる仕事のどれかがまだなら断る（PR のゲート2）
			if (this.jobs.values().stream().anyMatch(j -> (j.bookId.equals(bookId) || j.bookId.equals("check:" + bookId)) && j.active())) {
				respondJsonStatus(exchange, 409, errorJson(busyMessage(bookId, "この本はいま更新しています。終わってから変えてください")));
				return;
			}
			forgetOldJobs();
			//まだ終わっていない仕事でいっぱいなら断る（更新・落とすと同じ。PR の codex）
			if (this.jobs.values().stream().filter(UpdateJob::active).count() >= MAX_JOBS) {
				respondJsonStatus(exchange, 503, errorJson("更新の順番待ちがいっぱいです。しばらくしてから試してください"));
				return;
			}
			job = new UpdateJob(newJobId(), bookId, false);
			this.jobs.put(job.id, job);
			UpdateJob submitted = job;
			this.updateExecutor.submit(() -> runRename(submitted, updater, entry, name));
		}
		respondJsonStatus(exchange, 202, jobJson(job));
	}

	private void runRename(UpdateJob job, BookUpdater updater, LibraryEntry entry, String name)
	{
		job.state = "running";
		try {
			BookUpdater.Result result = updater.rename(entry.source(), entry.file(), name);
			//本棚を読み直してから「済んだ」にする（本の ID が変わる）
			ShelfAdder adder = this.shelfAdder;
			if (result.ok() && adder != null) {
				try {
					adder.add(entry.file().getParent());
				} catch (IOException | RuntimeException e) {
					logger.warn("名前を変えた本を本棚に出せませんでした: {}", entry.file(), e);
				}
			}
			job.message = result.message() == null ? "" : result.message();
			job.state = result.ok() ? "done" : "failed";
		} catch (UnsupportedOperationException e) {
			job.message = "この本棚からは名前を変えられません";
			job.state = "failed";
		} catch (Throwable e) {
			logger.warn("名前を変えられませんでした: {}", entry.file(), e);
			job.message = String.valueOf(e.getMessage());
			job.state = "failed";
			if (e instanceof Error) throw (Error)e;
		}
	}

	/**
	 * 本棚の本の新着を確かめる仕事を積む（POST。internal #11）。目次だけを読み、新しい話と改稿された話を数える。
	 * 状態は checked（数えた）・shrunk・gone・failed。同じ本の確かめる仕事が終わっていなければ、その仕事を返す
	 */
	private void serveCheck(HttpExchange exchange, String method, String bookId) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		BookUpdater updater = this.bookUpdater;
		if (updater == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚からは確かめられません（アプリから本棚を開いてください）"));
			return;
		}
		LibraryEntry entry = this.session.getLibraryEntry(bookId);
		if (entry == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		if (entry.source() == null) {
			respondJsonStatus(exchange, 400, errorJson("Web から取った本ではないので、確かめられません"));
			return;
		}
		String key = "check:" + bookId;
		UpdateJob job;
		synchronized (this.jobs) {
			//更新・名前を変えている最中の本は確かめない（名前が変わると、積んだ確かめる仕事が古い名前を見る。PR の codex）
			if (this.jobs.values().stream().anyMatch(j -> j.bookId.equals(bookId) && j.active())) {
				respondJsonStatus(exchange, 409, errorJson(busyMessage(bookId, "この本はいま更新しています。終わってから確かめてください")));
				return;
			}
			job = this.jobs.values().stream().filter(j -> j.bookId.equals(key) && j.active()).findFirst().orElse(null);
			if (job == null) {
				forgetOldJobs();
				if (this.jobs.values().stream().filter(UpdateJob::active).count() >= MAX_JOBS) {
					respondJsonStatus(exchange, 503, errorJson("更新の順番待ちがいっぱいです。しばらくしてから試してください"));
					return;
				}
				job = new UpdateJob(newJobId(), key, false);
				this.jobs.put(job.id, job);
				UpdateJob submitted = job;
				this.updateExecutor.submit(() -> runCheck(submitted, updater, entry));
			}
		}
		respondJsonStatus(exchange, 202, jobJson(job));
	}

	private void runCheck(UpdateJob job, BookUpdater updater, LibraryEntry entry)
	{
		job.state = "running";
		try {
			BookUpdater.CheckResult result = updater.check(entry.source(), entry.file());
			job.message = result.message() == null ? "" : result.message();
			if (result.ok()) {
				job.episodes = result.episodes();
				job.newEpisodes = result.newEpisodes();
				job.revisedEpisodes = result.revisedEpisodes();
				job.state = "checked";
			} else {
				job.state = result.stop() != null ? result.stop() : "failed";
			}
		} catch (UnsupportedOperationException e) {
			job.message = "この本棚からは確かめられません";
			job.state = "failed";
		} catch (Throwable e) {
			logger.warn("新着を確かめられませんでした: {}", entry.file(), e);
			job.message = String.valueOf(e.getMessage());
			job.state = "failed";
			if (e instanceof Error) throw (Error)e;
		}
	}

	/** 落とす URL として受け取る本文の上限（バイト） */
	static final int MAX_URL_BYTES = 2048;

	/**
	 * 掲載元の URL の作品を Web 本棚に新しく落とす仕事を列に積む（POST、本文は URL。internal #11 の案 A）。202 と仕事の ID を返す。
	 * Web 本棚がまだ決まっていなければ 409（{@code needShelf}）で、画面が場所を聞く。同じ URL の仕事が終わっていなければ、その仕事を返す
	 */
	private void serveDownload(HttpExchange exchange, String method) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		BookUpdater updater = this.bookUpdater;
		WebShelf shelf = this.webShelf;
		if (updater == null || shelf == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚からは落とせません（アプリから本棚を開いてください）"));
			return;
		}
		byte[] body = exchange.getRequestBody().readNBytes(MAX_URL_BYTES + 1);
		String url = new String(body, StandardCharsets.UTF_8).strip();
		if (body.length > MAX_URL_BYTES || !isHttpUrl(url)) {
			respondJsonStatus(exchange, 400, errorJson("作品のページの URL（http:// か https:// で始まるもの）を貼ってください"));
			return;
		}
		Path location = shelf.location();
		if (location == null || !Files.isDirectory(location)) {
			StringBuilder buf = new StringBuilder(64);
			buf.append('{');
			Json.prop(buf, "needShelf", true);
			Json.prop(buf, "error", "Web 本棚の場所を決めてください");
			buf.append('}');
			respondJsonStatus(exchange, 409, buf.toString());
			return;
		}
		String key = "url:" + url;
		UpdateJob job;
		synchronized (this.jobs) {
			job = this.jobs.values().stream().filter(j -> j.bookId.equals(key) && j.active()).findFirst().orElse(null);
			if (job == null) {
				forgetOldJobs();
				if (this.jobs.values().stream().filter(UpdateJob::active).count() >= MAX_JOBS) {
					respondJsonStatus(exchange, 503, errorJson("更新の順番待ちがいっぱいです。しばらくしてから試してください"));
					return;
				}
				job = new UpdateJob(newJobId(), key, false);
				this.jobs.put(job.id, job);
				UpdateJob submitted = job;
				this.updateExecutor.submit(() -> runDownload(submitted, updater, url, location));
			}
		}
		respondJsonStatus(exchange, 202, jobJson(job));
	}

	private void runDownload(UpdateJob job, BookUpdater updater, String url, Path location)
	{
		job.state = "running";
		try {
			BookUpdater.Result result = updater.download(url, location);
			//落とした本を本棚に出してから「済んだ」にする（先に済んだにすると、画面が読み直す本棚にまだ出ていない。PR の手元の codex）
			ShelfAdder adder = this.shelfAdder;
			if (result.ok() && adder != null) {
				try {
					adder.add(location);
				} catch (IOException | RuntimeException e) {
					logger.warn("落とした本を本棚に出せませんでした: {}", location, e);
				}
			}
			job.message = result.message() == null ? "" : result.message();
			job.state = result.ok() ? "done" : "failed";
		} catch (UnsupportedOperationException e) {
			job.message = "この本棚からは落とせません";
			job.state = "failed";
		} catch (Throwable e) {
			logger.warn("落とせませんでした: {}", url, e);
			job.message = String.valueOf(e.getMessage());
			job.state = "failed";
			if (e instanceof Error) throw (Error)e;
		}
	}

	/** http・https の URL として読めるか（ホストがあること） */
	static boolean isHttpUrl(String url)
	{
		//空白などの URL に使えない文字は URI が断る
		try {
			java.net.URI uri = new java.net.URI(url);
			String scheme = uri.getScheme();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
		} catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	/**
	 * 受け箱（POST api/inbox?tab=…&focus=0|1）。見えているタブが 1 秒ごとに見に来る。
	 * 入っていれば {"open": URL} を返して空にする。無ければ 204。取ると状態が変わるので POST
	 */
	private void serveInbox(HttpExchange exchange, String method) throws IOException
	{
		if (!"POST".equals(method)) {
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		String tab = clientIdOf(exchange);
		//奥へ回った・隠れたタブの知らせ。見えているタブの候補から外す（外さないと、3 秒の間は来ないタブを待つ）
		if (hasQueryFlag(exchange, "hidden=1")) {
			this.inboxVisits.remove(tab);
			respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
			return;
		}
		//受け取ったという返事。タブはこれを送ってから移る
		String ack = queryValue(exchange, "ack");
		if (ack != null) {
			Delivery acked = this.awaitingAck.remove(ack);
			if (acked != null && acked.state.compareAndSet(1, 3)) acked.acked.countDown();
			respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
			return;
		}
		//生きているタブの登録は heartbeat だけがする。ここで登録し直すと、bye の後に遅れて届いた
		//見回りが閉じたタブを生き返らせ、CLI が 5 分終わらなくなる（PR #129 の codex）
		if (!this.clients.containsKey(tab)) {
			respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
			return;
		}
		boolean focused = hasQueryFlag(exchange, "focus=1");
		this.inboxVisits.put(tab, new InboxVisit(System.nanoTime(), focused));
		Delivery delivery = this.inbox.remove(tab);
		if (delivery == null || !delivery.state.compareAndSet(0, 1)) {
			respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
			return;
		}
		//取ったタブは移る（読み込み直すと別のタブ ID で来る）。候補から外す
		this.inboxVisits.remove(tab);
		this.awaitingAck.put(delivery.id, delivery);
		StringBuilder buf = new StringBuilder(96);
		buf.append('{');
		Json.prop(buf, "open", delivery.url);
		Json.prop(buf, "ack", delivery.id);
		buf.append('}');
		respondJson(exchange, buf.toString());
	}

	/**
	 * 見えているタブに URL を開いてもらう。フォーカスを持つタブ、無ければ最後に見に来たタブ。
	 * 取りに来るまで待つ（呼ぶのは画面のスレッド以外から）。
	 * @return 取りに来た。false なら誰にも渡していない（新しいタブを開くこと）
	 */
	public boolean offerToVisibleTab(String url) throws InterruptedException
	{
		return offerToVisibleTab(url, INBOX_WAIT_MILLIS);
	}

	/** 渡すのは 1 つずつ（同じタブの受け箱に 2 つ入れると、先の 1 つが上書きされて待ちぼうけになる） */
	private final Object offering = new Object();

	boolean offerToVisibleTab(String url, long waitMillis) throws InterruptedException
	{
		synchronized (this.offering) {
			return offerOnce(url, waitMillis);
		}
	}

	private boolean offerOnce(String url, long waitMillis) throws InterruptedException
	{
		long now = System.nanoTime();
		long fresh = INBOX_FRESH_MILLIS * 1_000_000L;
		String best = null;
		InboxVisit bestVisit = null;
		for (Map.Entry<String, InboxVisit> e : this.inboxVisits.entrySet()) {
			InboxVisit v = e.getValue();
			if (now - v.nanos > fresh || !this.clients.containsKey(e.getKey())) continue;
			if (bestVisit == null || (v.focused && !bestVisit.focused)
				|| (v.focused == bestVisit.focused && v.nanos > bestVisit.nanos)) {
				best = e.getKey();
				bestVisit = v;
			}
		}
		if (best == null) return false;
		//タブへは道（/p/…/?book=…）だけを渡す。タブは自分のオリジンで解決する。
		//localhost で開き直したタブに 127.0.0.1 の URL を渡すと、別のオリジンとして捨てられる（PR #129 の codex）
		Delivery delivery = new Delivery(newJobId(), pathOf(url), new java.util.concurrent.atomic.AtomicInteger(),
			new java.util.concurrent.CountDownLatch(1));
		this.inbox.put(best, delivery);
		try {
			delivery.acked.await(waitMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
		} finally {
			//取りに来なかった（奥へ回った・凍った）なら下げる。入れたままにすると、後で見に来たときに古い本へ飛ぶ。
			//渡したのに返事が来なければ、遅れた返事を受け付けない（新しいタブを開くので）
			if (delivery.state.compareAndSet(0, 2)) this.inbox.remove(best, delivery);
			if (delivery.state.compareAndSet(1, 2)) this.awaitingAck.remove(delivery.id, delivery);
		}
		return delivery.state.get() == 3;
	}

	/** URL の道と問い合わせ（スキームとホストを落とす） */
	static String pathOf(String url)
	{
		java.net.URI uri = java.net.URI.create(url);
		String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
		return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
	}

	/** サイトへ同時に出す数の枠（なろうとカクヨムで共有する。掴むのは同じ 4 本のスレッド） */
	private final java.util.concurrent.Semaphore rankingUpstream = RankingFetch.newUpstream();

	/** 本棚のランキング（なろう）。試験では差し替える */
	private volatile NarouRanking narouRanking = new NarouRanking(this.rankingUpstream);

	/** 本棚のランキング（カクヨム）。試験では差し替える */
	private volatile KakuyomuRanking kakuyomuRanking = new KakuyomuRanking(this.rankingUpstream);

	void setNarouRanking(NarouRanking narouRanking)
	{
		this.narouRanking = narouRanking;
	}

	void setKakuyomuRanking(KakuyomuRanking kakuyomuRanking)
	{
		this.kakuyomuRanking = kakuyomuRanking;
	}

	/**
	 * ランキング（GET api/ranking?site=narou&period=d&genre=0&serial=1）。
	 * なろうは API の応答の JSON を、カクヨム（site=kakuyomu）はページの __NEXT_DATA__ の JSON をそのまま返す。
	 * 落とせる本棚（アプリから開いた本棚）だけ。札から落とせないランキングは出さない
	 */
	private void serveRanking(HttpExchange exchange) throws IOException
	{
		if (this.bookUpdater == null || this.webShelf == null) {
			respondJsonStatus(exchange, 503, errorJson("この本棚ではランキングを使えません（アプリから本棚を開いてください）"));
			return;
		}
		String rawQuery = exchange.getRequestURI().getRawQuery();
		String site = queryValue(exchange, "site");
		byte[] body;
		try {
			if (site == null || site.equals("narou")) {
				NarouRanking.Query query = NarouRanking.Query.parse(rawQuery);
				if (query == null) {
					respondJsonStatus(exchange, 400, errorJson("ランキングの期間かジャンルの指定が読めません"));
					return;
				}
				body = this.narouRanking.fetch(query);
			} else if (site.equals("kakuyomu")) {
				KakuyomuRanking.Query query = KakuyomuRanking.Query.parse(rawQuery);
				if (query == null) {
					respondJsonStatus(exchange, 400, errorJson("ランキングの期間かジャンルの指定が読めません"));
					return;
				}
				body = this.kakuyomuRanking.fetch(query);
			} else {
				respondJsonStatus(exchange, 400, errorJson("ランキングのサイトの指定が読めません"));
				return;
			}
		} catch (RankingFetch.BusyException e) {
			respondJsonStatus(exchange, 503, errorJson(e.getMessage()));
			return;
		} catch (IOException e) {
			logger.info("ランキングを取れませんでした: {}", e.getMessage());
			respondJsonStatus(exchange, 502, errorJson("ランキングを取れませんでした: " + e.getMessage()));
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			respondJsonStatus(exchange, 503, errorJson("ランキングを取れませんでした（中断しました）"));
			return;
		}
		respond(exchange, 200, "application/json; charset=utf-8", body);
	}

	/** 仕事の状態（GET api/jobs/{id}） */
	private void serveJob(HttpExchange exchange, String jobId) throws IOException
	{
		UpdateJob job = this.jobs.get(jobId);
		if (job == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown job".getBytes(StandardCharsets.UTF_8));
			return;
		}
		respondJson(exchange, jobJson(job));
	}

	private static String jobJson(UpdateJob job)
	{
		StringBuilder buf = new StringBuilder(128);
		buf.append('{');
		Json.prop(buf, "job", job.id);
		Json.prop(buf, "bookId", job.bookId);
		Json.prop(buf, "state", job.state);
		Json.prop(buf, "message", job.message);
		if (job.episodes >= 0) {
			Json.prop(buf, "episodes", job.episodes);
			Json.prop(buf, "newEpisodes", job.newEpisodes);
			Json.prop(buf, "revisedEpisodes", job.revisedEpisodes);
		}
		buf.append('}');
		return buf.toString();
	}

	private static String errorJson(String message)
	{
		StringBuilder buf = new StringBuilder(64);
		buf.append('{');
		Json.prop(buf, "error", message);
		buf.append('}');
		return buf.toString();
	}

	/** 上限を超える分は、終わった仕事を古いものから忘れる（呼ぶのは jobs のロックの中） */
	private void forgetOldJobs()
	{
		if (this.jobs.size() < MAX_JOBS) return;
		this.jobs.values().stream().filter(j -> !j.active())
			.sorted(java.util.Comparator.comparingLong(j -> j.createdNanos))
			.limit(this.jobs.size() - MAX_JOBS + 1)
			.map(j -> j.id).toList()
			.forEach(this.jobs::remove);
	}

	private static String newJobId()
	{
		byte[] bytes = new byte[12];
		RANDOM.nextBytes(bytes);
		StringBuilder buf = new StringBuilder(24);
		for (byte b : bytes) buf.append(String.format("%02x", b));
		return buf.toString();
	}

	private void serveReveal(HttpExchange exchange, String method, String bookId) throws IOException
	{
		if (!"POST".equals(method)) {
			// GET で開けると <img src> 等で意図せず起動できてしまう
			respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
			return;
		}
		PreviewSession.Book book = this.session.getBook(bookId);
		if (book == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Unknown book".getBytes(StandardCharsets.UTF_8));
			return;
		}
		// kindlegen 経路では EPUB を消してから展開済みのものを配信し続けることがある。
		// フォルダごと無くなっているのに起動すると、Windows は存在しないパスを渡された
		// エクスプローラがマイドキュメントを開いてしまうので、ここで止める
		Path folder = book.getEpubFile().toAbsolutePath().getParent();
		if (folder == null || !Files.isDirectory(folder)) {
			respond(exchange, 404, "text/plain; charset=utf-8", "Folder not found".getBytes(StandardCharsets.UTF_8));
			return;
		}
		try {
			this.revealer.reveal(book.getEpubFile());
		} catch (IOException e) {
			logger.warn("フォルダを開けませんでした: {}", book.getEpubFile(), e);
			respond(exchange, 500, "text/plain; charset=utf-8",
				"Failed to open folder".getBytes(StandardCharsets.UTF_8));
			return;
		}
		respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
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

	/**
	 * /api/library/cover/{bookId} — 本棚のサムネイル。
	 *
	 * <p>他のレスポンスと違い <b>ETag による再検証</b>を許す。本棚は数百枚の画像を
	 * 一度に並べるため、スクロールのたびに作り直すと重い。一方でプレビューは
	 * 「変換し直したら新しい方を見たい」機能なので、期限付きキャッシュ
	 * ({@code max-age}) ではなく毎回問い合わせて 304 を返す形にする。
	 * ETag は EPUB のサイズと更新時刻から作るので、再変換すれば必ず変わる。</p>
	 */
	private void serveCover(HttpExchange exchange, String bookId) throws IOException
	{
		// 「いま」のファイル状態で読み直す。本棚のスキャンは起動時の 1 回しか
		// 走らないので、スキャン時の記録のままだと変換し直しても古い表紙を配り続け、
		// 再検証の意味が無くなる。表紙の href が変わる場合もあるので stat だけでは足りない
		LibraryEntry entry = this.session.refreshLibraryEntry(bookId);
		if (entry == null || entry.coverEntry() == null) {
			respond(exchange, 404, "text/plain; charset=utf-8", "No cover".getBytes(StandardCharsets.UTF_8));
			return;
		}
		String cacheKey = LibraryCovers.cacheKey(bookId, entry);
		String etag = LibraryCovers.etag(cacheKey);
		exchange.getResponseHeaders().set("ETag", etag);
		if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
			exchange.getResponseHeaders().set("Cache-Control", "no-cache");
			exchange.sendResponseHeaders(304, -1);
			return;
		}
		byte[] thumbnail = this.covers.thumbnail(cacheKey, entry);
		if (thumbnail == null) {
			// 壊れた画像・未対応形式。1 冊ぶん絵が出ないだけで本棚は使える
			respond(exchange, 404, "text/plain; charset=utf-8", "No cover".getBytes(StandardCharsets.UTF_8));
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		// EPUB 由来の画像なので、万一 HTML と誤解釈されないよう念を押す
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		if ("HEAD".equals(exchange.getRequestMethod())) {
			exchange.sendResponseHeaders(200, -1);
			return;
		}
		exchange.sendResponseHeaders(200, thumbnail.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(thumbnail);
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

	private void respondJsonStatus(HttpExchange exchange, int status, String json) throws IOException
	{
		respond(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
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
