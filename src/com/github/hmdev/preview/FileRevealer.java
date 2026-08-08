package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ファイルを OS のファイルマネージャで開く。
 *
 * <p>ブラウザからはローカルのファイラを開けないため、ビューアーの「フォルダを開く」は
 * サーバ側でこれを呼ぶ。<b>対象パスはリクエストから受け取らず、
 * セッションに登録済みの EPUB からサーバ側で解決すること</b> (任意のパスを開かせない)。</p>
 *
 * <p>先行実装 (narou.rb の {@code Helper.open_directory} / narou.rs の
 * {@code commands/folder.rs}) も同じく「ID を受けてサーバ側でパスを解決し、
 * OS のファイラを起動する」設計を採っている。</p>
 */
final class FileRevealer
{
	private FileRevealer() {}

	/**
	 * file を含むフォルダを開く。macOS では file を選択した状態にする。
	 *
	 * <p><b>Windows で {@code explorer /select,<path>} は使わない。</b>
	 * {@code ProcessBuilder} は空白を含む引数を丸ごと引用符で囲むため、
	 * {@code "/select,D:\...\[テスト] 目次.epub"} という 1 つの引数になり、
	 * Explorer がこれを解釈できずマイドキュメントを開いてしまう
	 * (2026-08-08 に実機で確認)。Java からは引用の仕方を選べないので、
	 * ファイル選択は諦めて親フォルダを開く。narou.rb も同様にフォルダを開くだけ。</p>
	 *
	 * <p>起動したプロセスの終了は待たない。ファイラの終了コードは環境差が大きく、
	 * 待って判定すると誤検知になる。</p>
	 */
	static void reveal(Path file) throws IOException
	{
		Path target = file.toAbsolutePath();
		Path parent = target.getParent();
		Path folder = (parent == null) ? target : parent;
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		// kindlegen 経路では EPUB を消してから展開済みのものを配信し続けることがある。
		// 実体が無いのに選択を頼むと、macOS の open -R は何も開かずに終わり
		// (プロセス起動自体は成功するので気付けない)、Windows はマイドキュメントを開く
		boolean fileExists = java.nio.file.Files.isRegularFile(target);

		if (os.contains("mac") && fileExists) {
			// macOS の open は引数を素直に受け取るのでファイルを選択状態にできる
			new ProcessBuilder("open", "-R", target.toString()).start();
			return;
		}
		if (os.contains("mac")) {
			new ProcessBuilder("open", folder.toString()).start();
			return;
		}
		if (!java.nio.file.Files.isDirectory(folder)) {
			// 存在しないパスを渡すとエクスプローラがマイドキュメントを開いてしまう。
			// 呼び出し側 (PreviewServer) でも弾いているが、ここでも塞いでおく
			throw new IOException("フォルダがありません: " + folder);
		}
		// Desktop はパスを API として渡すので、空白や日本語で壊れない
		try {
			java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported()
				? java.awt.Desktop.getDesktop() : null;
			if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
				desktop.open(folder.toFile());
				return;
			}
		} catch (IOException | RuntimeException e) {
			/* 意図的: ヘッドレス等で使えない場合はコマンドにフォールバックする */
		}
		String command = os.contains("win") ? "explorer" : "xdg-open";
		new ProcessBuilder(command, folder.toString()).start();
	}
}
