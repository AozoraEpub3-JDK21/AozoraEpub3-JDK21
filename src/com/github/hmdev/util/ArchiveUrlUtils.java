package com.github.hmdev.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * アーカイブ（zip / txtz / rar）URL の判定とダウンロードを行う共通ユーティリティ。
 *
 * <p>青空文庫のテキスト zip のように、URL 自体がアーカイブを指す場合は
 * HTML スクレイピング（{@code WebAozoraConverter}）ではなく
 * ファイルを直接ダウンロードして通常のローカルファイル変換経路に流す必要がある。
 * この振り分け判定とダウンロード処理を GUI（{@code AozoraEpub3Applet.convertWeb}）と
 * CLI（{@code AozoraEpub3.run} の -url 処理）で共有するために切り出した。
 * 経緯は docs/code-audit-followups.md の「### 16.」を参照。
 *
 * <p>拡張子判定は GUI にあった実装（{@code lastIndexOf('.')} 以降を小文字化して比較）を
 * そのまま踏襲している。クエリ文字列付き URL の扱いを変えると GUI の既存挙動が変わるため
 * 意図的に据え置いている。
 */
public final class ArchiveUrlUtils
{
	private static final Logger logger = LoggerFactory.getLogger(ArchiveUrlUtils.class);

	private ArchiveUrlUtils() {}

	/** URL 末尾の拡張子（小文字）。'.' が無い場合は URL 全体が返る（GUI 実装の踏襲） */
	static public String urlExtension(String urlString)
	{
		if (urlString == null) return "";
		return urlString.substring(urlString.lastIndexOf('.')+1).toLowerCase();
	}

	/** URL がアーカイブ（zip / txtz / rar）を直接指しているか */
	static public boolean isArchiveUrl(String urlString)
	{
		String ext = urlExtension(urlString);
		return ext.equals("zip") || ext.equals("txtz") || ext.equals("rar");
	}

	/** ダウンロード先のローカルファイルパスを求める。
	 * URL のスキーム以降をファイル名に使えない文字を除去したうえで、その末尾要素を dstPath 直下に配置する
	 * @param urlString アーカイブの URL
	 * @param dstPath 出力先ディレクトリ（null 可。GUI 互換のため文字列連結して扱う） */
	static public File getArchiveDstFile(String urlString, File dstPath)
	{
		String urlPath = CharUtils.replaceInvalidFileChars(urlString.substring(urlString.indexOf("//")+2));
		return new File(dstPath+"/"+new File(urlPath).getName());
	}

	/** アーカイブ URL をダウンロードして dstPath 直下に保存する。
	 * 同名ファイルがある場合は上書きする。
	 * 途中で失敗した場合は不完全なファイルを残さず削除する
	 * （壊れた zip を正常なアーカイブと誤認して「読み込めません」になるため）。
	 * @return 保存したファイル
	 * @throws IOException ダウンロードに失敗した場合 */
	static public File downloadArchive(String urlString, File dstPath) throws IOException
	{
		File srcFile = getArchiveDstFile(urlString, dstPath);
		LogAppender.println("出力先にダウンロードします : "+srcFile.getCanonicalPath());
		Files.createDirectories(srcFile.getParentFile().toPath());
		//ダウンロード
		URL url;
		try {
			url = new java.net.URI(urlString).toURL();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		boolean downloaded = false;
		try {
			//try-with-resources 化（元の GUI 実装は Files.newOutputStream が失敗すると入力側が閉じられなかった）
			try (BufferedInputStream bis = new BufferedInputStream(NetUtils.openStream(url), 8192);
				BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(srcFile.toPath()))) {
				byte[] buf = new byte[8192];
				int len;
				while ((len = bis.read(buf)) > 0) {
					bos.write(buf, 0, len);
				}
				downloaded = true;
			}
		} finally {
			//読み込みタイムアウト等で中断した場合、途中まで書かれた zip を残さない
			//（正常な青空 zip と誤認されて「読み込めません」になるため）
			if (!downloaded) {
				try {
					if (Files.deleteIfExists(srcFile.toPath())) {
						LogAppender.println("ダウンロードに失敗したため途中のファイルを削除しました : "+srcFile.getPath());
					}
				} catch (Exception e) {
					logger.warn("ダウンロード途中ファイルの削除に失敗: {}", srcFile, e);
				}
			}
		}
		return srcFile;
	}
}
