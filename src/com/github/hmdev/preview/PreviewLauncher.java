package com.github.hmdev.preview;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * プレビューの入り口。GUI と CLI は必ずこのクラスを経由する。
 *
 * <p>「サーバを起動するところ」と「ブラウザを開くところ」を分離してある。
 * ヘッドレス環境では後者が使えないため、テストは前者だけを対象にできる。</p>
 */
public class PreviewLauncher
{
	private static final Logger logger = LoggerFactory.getLogger(PreviewLauncher.class);

	/** 起動中のプレビュー。GUI から繰り返し開いてもサーバは 1 つに保つ */
	private static PreviewLauncher current;

	private final PreviewSession session;
	private final PreviewServer server;
	/** JVM 終了時の後始末。shutdown() で解除できるよう参照を保持する */
	private Thread shutdownHook;

	private PreviewLauncher(PreviewSession session, PreviewServer server)
	{
		this.session = session;
		this.server = server;
	}

	public PreviewSession getSession() { return this.session; }
	public PreviewServer getServer() { return this.server; }

	/** ビューアーの URL (既定の本を開く) */
	public String getUrl() { return this.server.getUrl(); }

	/** 指定した本を開く URL */
	public String getUrl(String bookId)
	{
		return this.server.getUrl() + "?book=" + bookId;
	}

	// ------------------------------------------------------------------

	/**
	 * サーバを起動して EPUB を 1 冊登録する。ブラウザは開かない。
	 *
	 * @throws IOException サーバの起動または一時ディレクトリの作成に失敗した場合
	 */
	public static PreviewLauncher startServer(Path epubFile) throws IOException
	{
		PreviewSession session = new PreviewSession();
		PreviewServer server;
		try {
			server = new PreviewServer(session);
			// 本を伴わない起動 (本棚だけを開く) を許す。既定の本が無い場合、
			// ビューアーは本棚を開いた状態で始まる
			if (epubFile != null) session.addBook(epubFile);
			server.start();
		} catch (IOException | RuntimeException e) {
			// サーバを起動できなければセッションを閉じる。
			// 放置するとロックと一時ディレクトリが JVM 存続中ずっと残る
			session.close();
			throw e;
		}
		PreviewLauncher launcher = new PreviewLauncher(session, server);
		launcher.shutdownHook = new Thread(() -> {
			server.close();
			session.close();
		}, "aozora-preview-shutdown");
		Runtime.getRuntime().addShutdownHook(launcher.shutdownHook);
		return launcher;
	}

	/**
	 * EPUB をプレビューする。既にプレビューが起動していればそこへ本を追加する。
	 *
	 * @param epubFile プレビューする EPUB
	 * @return 開いた URL
	 * @throws IOException 起動に失敗した場合
	 */
	public static synchronized String preview(File epubFile) throws IOException
	{
		if (epubFile == null || !epubFile.isFile()) {
			throw new IOException("EPUB が見つかりません: " + epubFile);
		}
		Path path = epubFile.toPath().toAbsolutePath();
		boolean createdSession = (current == null);
		if (createdSession) current = startServer(path);
		String bookId = createdSession ? current.session.getDefaultBookId() : current.session.addBook(path);
		try {
			// 展開と OPF 解析はここで済ませる。
			// 遅延展開のままだとブラウザが本文を要求するまで失敗が分からず、
			// 壊れた EPUB でも「開けた」と報告してしまう
			current.session.ensureExtracted(bookId);
			String url = current.getUrl(bookId);
			openInBrowser(url);
			return url;
		} catch (IOException | RuntimeException e) {
			// このセッションを今作ったのなら、失敗として片付ける
			if (createdSession) shutdown();
			throw e;
		}
	}

	/**
	 * 本棚だけを開く。既にプレビューが起動していればそこへ棚を足す。
	 *
	 * <p>本を 1 冊も伴わないので、ビューアーは本棚を開いた状態で始まる。</p>
	 *
	 * @param folders 棚にするフォルダ (複数可)
	 * @return 開いた URL
	 * @throws IOException 起動に失敗した場合、または 1 つも棚を読めなかった場合
	 */
	public static synchronized String previewLibrary(List<Path> folders) throws IOException
	{
		if (folders == null || folders.isEmpty()) throw new IOException("本棚のフォルダが指定されていません");
		loadLibraryInto(folders);
		try {
			String url = current.getUrl();
			openInBrowser(url);
			return url;
		} catch (IOException | RuntimeException e) {
			// 棚しか登録していないセッションはここで畳んでよい
			// (本を開いている最中なら loadLibraryInto が現行セッションを再利用している)
			if (current != null && current.session.getDefaultBookId() == null) shutdown();
			throw e;
		}
	}

	/**
	 * 起動中のプレビューに本棚を取り込む。まだ起動していなければサーバだけ先に立てる。
	 * ブラウザは開かない。
	 *
	 * <p><b>ブラウザを開く前に呼ぶこと。</b>ビューアーは起動時の {@code api/session}
	 * 一回で本棚ボタンを出すかどうかを決めるため、開いた後に足しても出ない。</p>
	 *
	 * @return 取り込んだ冊数
	 * @throws IOException サーバの起動に失敗した場合、または 1 つも棚を読めなかった場合
	 */
	public static synchronized int loadLibraryInto(List<Path> folders) throws IOException
	{
		boolean createdSession = (current == null);
		if (createdSession) current = startServer(null);
		try {
			return current.loadLibrary(folders);
		} catch (IOException | RuntimeException e) {
			// このセッションを今作ったのなら、失敗として片付ける
			if (createdSession) shutdown();
			throw e;
		}
	}

	/**
	 * フォルダを走査して本棚に取り込む。ブラウザは開かない。
	 *
	 * @return 取り込んだ冊数
	 */
	public int loadLibrary(Path folder) throws IOException
	{
		return loadLibrary(List.of(folder));
	}

	/**
	 * フォルダを走査して本棚に取り込む (複数可)。ブラウザは開かない。
	 *
	 * <p>キャッシュの読み込みと更新まで面倒を見る。
	 * {@link LibraryScanner#scan} はキャッシュを読むだけで更新しないため、
	 * 呼び出し側でこれを忘れると毎回全冊が再パースになる。</p>
	 *
	 * <p><b>上限は棚ごとではなく合計に掛ける。</b>棚ごとに
	 * {@link LibraryScanner#MAX_BOOKS} を許すと、棚を並べただけで一覧が数倍になり、
	 * 一覧を出すたびの stat も同じ倍率で増える (一覧は要求のたびに全冊 stat する)。</p>
	 *
	 * <p>読めなかったフォルダは飛ばして続ける。1 つ間違えただけで
	 * 残りの棚まで開けなくなるのを避けるため。全滅した場合だけ例外にする。</p>
	 *
	 * @param folders 棚にするフォルダ。重複と入れ子はここで畳む
	 * @return 取り込んだ冊数 (重複を除いた合計)
	 * @throws IOException 1 つも棚を読めなかった場合
	 */
	public int loadLibrary(List<Path> folders) throws IOException
	{
		List<Path> roots = normalizeShelfFolders(folders);
		LibraryIndexCache cache = new LibraryIndexCache();
		cache.load();

		List<LibraryShelf> shelves = new ArrayList<>(roots.size());
		List<LibraryEntry> scanned = new ArrayList<>();
		IOException failure = null;
		int remaining = LibraryScanner.MAX_BOOKS;
		for (Path root : roots) {
			if (remaining <= 0) {
				logger.warn("本棚の上限 {} 冊に達したため、以降のフォルダは読み込みません: {}",
					LibraryScanner.MAX_BOOKS, root);
				break;
			}
			List<LibraryEntry> entries;
			try {
				entries = LibraryScanner.scan(root, LibraryScanner.DEFAULT_MAX_DEPTH, cache, remaining);
			} catch (IOException e) {
				logger.warn("本棚を読み込めませんでした: {} ({})", root, e.toString());
				failure = e;
				continue;
			}
			remaining -= entries.size();
			scanned.addAll(entries);
			shelves.add(new LibraryShelf(root, entries));
		}
		if (shelves.isEmpty()) {
			throw (failure != null) ? failure : new IOException("本棚を読み込めませんでした");
		}
		cache.update(scanned);
		this.session.setLibrary(shelves);
		int total = this.session.libraryBookCount();
		logger.info("本棚を読み込みました: {} 個のフォルダ, {} 冊", shelves.size(), total);
		return total;
	}

	/**
	 * 棚のフォルダを整える。
	 *
	 * <p>同じフォルダの重複と、<b>既に登録した棚の配下にあるフォルダ</b>を落とす。
	 * 「出力先フォルダ」と「その親」を両方登録するのは普通に起きるが、
	 * 親を走査すれば子も含まれるので、子を別の棚として走査するのは二度手間になる。
	 * 冊数の上限を無駄に食う点でも損。</p>
	 */
	static List<Path> normalizeShelfFolders(List<Path> folders)
	{
		List<Path> roots = new ArrayList<>();
		for (Path folder : folders) {
			if (folder == null) continue;
			Path absolute = folder.toAbsolutePath().normalize();
			boolean covered = false;
			for (Path existing : roots) {
				if (absolute.startsWith(existing)) { covered = true; break; }
			}
			if (covered) {
				logger.info("既に登録した棚に含まれるため読み込みません: {}", absolute);
				continue;
			}
			// 逆に、後から親を指定された場合は子を畳む
			roots.removeIf(existing -> existing.startsWith(absolute));
			roots.add(absolute);
			if (roots.size() >= LibraryScanner.MAX_SHELVES) {
				logger.warn("本棚のフォルダは {} 個までです。以降は読み込みません", LibraryScanner.MAX_SHELVES);
				break;
			}
		}
		return roots;
	}

	/** 起動中のプレビューがあれば返す。無ければ null */
	public static synchronized PreviewLauncher getCurrent() { return current; }

	/** 起動中のプレビューを停止する */
	public static synchronized void shutdown()
	{
		if (current == null) return;
		if (current.shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(current.shutdownHook);
			} catch (IllegalStateException e) {
				/* 意図的: 既に JVM 終了処理が始まっている場合は解除できない */
			}
			current.shutdownHook = null;
		}
		current.server.close();
		current.session.close();
		current = null;
	}

	// ------------------------------------------------------------------

	/**
	 * 既定ブラウザで URL を開く。
	 * {@link Desktop} が使えない環境向けに OS ごとのコマンドへフォールバックする。
	 */
	public static void openInBrowser(String url) throws IOException
	{
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		} catch (Exception e) {
			logger.debug("Desktop.browse に失敗したためコマンドで開きます", e);
		}
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		String[] command;
		if (os.contains("win")) {
			command = new String[] {"rundll32", "url.dll,FileProtocolHandler", url};
		} else if (os.contains("mac")) {
			command = new String[] {"open", url};
		} else {
			command = new String[] {"xdg-open", url};
		}
		new ProcessBuilder(command).start();
	}
}
