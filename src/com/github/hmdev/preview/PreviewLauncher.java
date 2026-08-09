package com.github.hmdev.preview;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
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
			session.addBook(epubFile);
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
	 * フォルダを走査して本棚に取り込む。ブラウザは開かない。
	 *
	 * <p>キャッシュの読み込みと更新まで面倒を見る。
	 * {@link LibraryScanner#scan} はキャッシュを読むだけで更新しないため、
	 * 呼び出し側でこれを忘れると毎回全冊が再パースになる。</p>
	 *
	 * @return 取り込んだ冊数
	 */
	public int loadLibrary(Path folder) throws IOException
	{
		LibraryIndexCache cache = new LibraryIndexCache();
		cache.load();
		List<LibraryEntry> entries = LibraryScanner.scan(folder, LibraryScanner.DEFAULT_MAX_DEPTH, cache);
		cache.update(entries);
		this.session.setLibrary(folder, entries);
		logger.info("本棚を読み込みました: {} ({} 冊)", folder, entries.size());
		return entries.size();
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
