package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EPUB (ZIP) を一時ディレクトリへ展開する。
 *
 * <p>ブラウザへ HTTP 配信するために実ファイルへ展開する必要があるが、
 * 入力は必ずしも自分で生成した EPUB とは限らないため、
 * Zip Slip (エントリ名の {@code ../}) と展開爆弾の双方を防ぐ。</p>
 */
public class EpubExtractor
{
	private static final Logger logger = LoggerFactory.getLogger(EpubExtractor.class);

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
		// 大文字小文字を畳んだ名前 -> 実際に書き出したエントリ名。名前の衝突検出に使う。
		// 「この展開で書いたもの」だけを対象にするので、展開先に前回の残骸があっても
		// 全エントリを読み飛ばしてしまうことはない
		Map<String, String> written = new HashMap<>();

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
				Path target;
				try {
					target = root.resolve(normalized).normalize();
				} catch (java.nio.file.InvalidPathException e) {
					// この OS のファイル名として表現できない
					// (Linux で作られた "a?b.jpg" は Windows に書き出せない)。
					// 非チェック例外なので、飲まないと展開全体が落ちる。
					// 挿絵 1 枚のために本全体を開けなくしないよう読み飛ばす
					logger.warn("この OS では扱えない名前のためエントリを読み飛ばします: {}", entry.getName());
					continue;
				}
				// PathUtils が一次的に封じ込めており、ここは最終アサーション (通常到達しない)
				if (!target.startsWith(root)) {
					throw new IOException("EPUB に不正なエントリが含まれています: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				// 大文字小文字を区別しない FS (Windows / 既定の APFS) では "Text.xhtml" と "text.xhtml" が
				// 同一ファイルになり、後から来たエントリが無警告で前を上書きして「章の中身が別章」になる。
				// OS を見て分岐するのではなく実体が既にあるかで判定するので、
				// 区別する FS (Linux) では両方とも展開され、従来の挙動を変えない
				// putIfAbsent にすること。put だと読み飛ばした名前で上書きされ、
				// 3 つ目に 1 つ目と完全同名のエントリが来たときに従来の上書きではなく skip になる
				String prior = written.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
				if (prior != null && !prior.equals(normalized) && Files.exists(target)) {
					logger.warn("先に展開した [{}] と同じ実体を指すため読み飛ばします"
						+ " (大文字小文字を区別しないファイルシステム): {}", prior, entry.getName());
					continue;
				}
				Files.createDirectories(target.getParent());
				// Windows の予約デバイス名 ("OPS/NUL/x.jpg" の NUL) は createDirectories が
				// 例外を投げずに何も作らないため、続く書き込みが NoSuchFileException になり
				// 本全体の展開が失敗する。作れたことを確認して、この 1 件だけを読み飛ばす。
				// (例外は捕まえない。ディスク満杯や権限エラーは従来どおり中断させる)
				if (!Files.isDirectory(target.getParent())) {
					logger.warn("この OS では作れない格納先のためエントリを読み飛ばします: {}", entry.getName());
					continue;
				}
				total += copy(zip, entry, target, total, maxEntryBytes, maxTotalBytes);
				if (!Files.exists(target)) {
					// "OPS/NUL" のような予約デバイス名は書き込みが成功したように見えて実体が残らない。
					// 黙って 404 になると原因が追えないので記録する
					logger.warn("この OS ではデバイス名として扱われ保存されませんでした: {}", entry.getName());
				}
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
