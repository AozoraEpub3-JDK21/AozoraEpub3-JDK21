package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB (ZIP) を一時ディレクトリへ展開する。
 *
 * <p>ブラウザへ HTTP 配信するために実ファイルへ展開する必要があるが、
 * 入力は必ずしも自分で生成した EPUB とは限らないため、
 * Zip Slip (エントリ名の {@code ../}) と展開爆弾の双方を防ぐ。</p>
 */
public class EpubExtractor
{
	/** 1 エントリあたりの展開上限 (512MB)。挿絵入りでもこれを超えることはない */
	static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;
	/** 1 ファイルあたりの展開合計上限 (2GB) */
	static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
	/** エントリ数の上限 */
	static final int MAX_ENTRIES = 50_000;

	private EpubExtractor() {}

	/**
	 * epubFile を targetDir 配下へ展開する。targetDir は存在しなければ作成する。
	 *
	 * @throws IOException 展開に失敗した場合、または安全でないエントリを検出した場合
	 */
	public static void extract(Path epubFile, Path targetDir) throws IOException
	{
		extract(epubFile, targetDir, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES, MAX_ENTRIES);
	}

	/**
	 * 上限を指定して展開する。
	 * テストが実際に数百MBを書かずに上限の計上を検証できるようにするためのオーバーロード。
	 */
	static void extract(Path epubFile, Path targetDir,
		long maxEntryBytes, long maxTotalBytes, int maxEntries) throws IOException
	{
		Files.createDirectories(targetDir);
		Path root = targetDir.toRealPath();
		long total = 0;
		int count = 0;

		try (ZipFile zip = new ZipFile(epubFile.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (++count > maxEntries) {
					throw new IOException("EPUB のエントリ数が多すぎます: " + epubFile);
				}
				if (PathUtils.escapesRoot(entry.getName())) {
					// ".." でルート外へ抜けようとするエントリ (Zip Slip)
					throw new IOException("EPUB に不正なエントリが含まれています: " + entry.getName());
				}
				String normalized = PathUtils.normalizeRelative(entry.getName());
				if (normalized == null) {
					// "./" のような実体の無いエントリ。無害なので読み飛ばす
					// (ここで EPUB 全体を拒否すると、他ツール製の EPUB が開けなくなる)
					continue;
				}
				Path target = root.resolve(normalized).normalize();
				if (!target.startsWith(root)) {
					throw new IOException("EPUB に不正なエントリが含まれています: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				total += copy(zip, entry, target, total, maxEntryBytes, maxTotalBytes);
			}
		}
	}

	/** エントリ 1 件を書き出し、書き出したバイト数を返す */
	private static long copy(ZipFile zip, ZipEntry entry, Path target, long totalSoFar,
		long maxEntryBytes, long maxTotalBytes) throws IOException
	{
		long written = 0;
		byte[] buffer = new byte[8192];
		try (InputStream in = zip.getInputStream(entry);
			 OutputStream out = Files.newOutputStream(target)) {
			int read;
			while ((read = in.read(buffer)) > 0) {
				written += read;
				if (written > maxEntryBytes || totalSoFar + written > maxTotalBytes) {
					throw new IOException("EPUB の展開サイズが上限を超えました: " + entry.getName());
				}
				out.write(buffer, 0, read);
			}
		} catch (IOException e) {
			// 上限超過や I/O エラーで中断したときに書きかけを残さない。
			// 後始末の失敗で元の原因を隠してはならない
			try {
				Files.deleteIfExists(target);
			} catch (IOException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		}
		return written;
	}
}
