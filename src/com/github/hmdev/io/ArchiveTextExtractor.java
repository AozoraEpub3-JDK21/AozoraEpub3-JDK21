package com.github.hmdev.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.util.LogAppender;
import com.github.junrar.exception.RarException;

/**
 * Handles archive/txt inputs: counts available txt entries and opens streams for conversion.
 * Uses caching to avoid multiple archive scans.
 */
public final class ArchiveTextExtractor {
	private static final Map<String, ArchiveCache> cacheMap = new HashMap<>();
	
	private ArchiveTextExtractor() {
	}
	
	/**
	 * Gets or creates an ArchiveCache for the given file.
	 */
	private static synchronized ArchiveCache getCache(File srcFile, String ext) {
		String key = srcFile.getAbsolutePath();
		ArchiveCache cache = cacheMap.get(key);
		if (cache == null) {
			cache = new ArchiveCache(srcFile, ext);
			cacheMap.put(key, cache);
		}
		return cache;
	}
	
	/**
	 * Clears cache for a specific file (call after conversion complete).
	 */
	public static synchronized void clearCache(File srcFile) {
		cacheMap.remove(srcFile.getAbsolutePath());
	}

	/** ファイルの拡張子が、書庫ではなくそのまま読むテキスト（txt と Markdown）か。画像は入力のフォルダから探す */
	public static boolean isPlainText(String ext) {
		return "txt".equals(ext) || "md".equals(ext);
	}

	/** その入力を読む文字コード。Markdown は青空文庫形式に直した UTF-8 を渡すので、-enc や GUI の指定によらず UTF-8 */
	public static String encodingFor(String ext, String encType) {
		return "md".equals(ext) ? "UTF-8" : encType;
	}

	/** Markdown のファイルを青空文庫形式のテキストにする（internal #72）。Markdown 自体は UTF-8 として読む */
	static String markdownAsAozora(File srcFile) throws IOException {
		byte[] bytes = Files.readAllBytes(srcFile.toPath());
		String md;
		try {
			md = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
				.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
				.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		} catch (java.nio.charset.CharacterCodingException e) {
			//黙って化けさせない。読めない字は置き換えて続ける（Shift_JIS などの .md）
			LogAppender.warn(0, "Markdown を UTF-8 として読めない字がありました（UTF-8 で保存し直してください）: " + srcFile.getName());
			md = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		}
		if (md.startsWith("\uFEFF")) md = md.substring(1);
		String name = srcFile.getName();
		int dot = name.lastIndexOf('.');
		return com.github.hmdev.converter.MarkdownToAozora.convert(md, dot > 0 ? name.substring(0, dot) : name);
	}

	/**
	 * Opens an InputStream for the txt content from txt/md/zip/txtz/rar.
	 * Uses cache for archives to avoid multiple scans.
	 * Caller must close the returned stream.
	 */
	public static InputStream getTextInputStream(File srcFile, String ext, ImageInfoReader imageInfoReader, String[] textEntryName, int txtIdx)
			throws IOException, RarException {
		if ("txt".equals(ext)) {
			return Files.newInputStream(srcFile.toPath());
		} else if ("md".equals(ext)) {
			//Markdown は青空文庫形式に直して渡す。読み手は UTF-8 で読む（{@link #encodingFor}）
			return new java.io.ByteArrayInputStream(markdownAsAozora(srcFile).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} else if ("zip".equals(ext) || "txtz".equals(ext) || "rar".equals(ext)) {
			ArchiveCache cache = getCache(srcFile, ext);
			cache.scan();
			
			String entryName = cache.getTextEntryName(txtIdx);
			if (entryName == null) {
				LogAppender.append(ext + "内にtxtファイルがありません: ");
				LogAppender.println(srcFile.getName());
				return null;
			}
			
			if (imageInfoReader != null) imageInfoReader.setArchiveTextEntry(entryName);
			if (textEntryName != null) textEntryName[0] = entryName;
			
			return cache.getTextInputStream(txtIdx, null);
		} else {
			LogAppender.append("txt, md, zip, rar, txtz, cbz のみ変換可能です: ");
			LogAppender.println(srcFile.getPath());
		}
		return null;
	}

	/**
	 * 数えたときに読めずに飛ばしたテキストの数（zip/txtz/rar。internal #36）。
	 * 利用者に見えるログにも出す。本ごとに控えを捨てて読み直すので、ログは数えるこの 1 回だけ（読み直しでは出さない）。
	 * countZipText / countRarText が通った後に呼ぶ（数え損ねた書庫を読み直さない）
	 */
	public static int reportSkippedTexts(File srcFile, String ext) {
		try {
			List<String> skipped = getCache(srcFile, ext).getSkippedTexts();
			for (String s : skipped) {
				LogAppender.println("[WARN] 書庫の中のテキストが読めないので飛ばします : " + s + " (" + srcFile.getName() + ")");
			}
			return skipped.size();
		} catch (IOException | RarException e) {
			return 0;
		}
	}

	/** Counts txt entries in zip/txtz archives. Uses cache. */
	public static int countZipText(File zipFile) throws IOException {
		try {
			ArchiveCache cache = getCache(zipFile, "zip");
			return cache.getTextFileCount();
		} catch (RarException e) {
			throw new IOException(e);
		}
	}

	/** Counts txt entries in rar archives. Uses cache. */
	public static int countRarText(File rarFile) throws IOException, RarException {
		ArchiveCache cache = getCache(rarFile, "rar");
		return cache.getTextFileCount();
	}
}
