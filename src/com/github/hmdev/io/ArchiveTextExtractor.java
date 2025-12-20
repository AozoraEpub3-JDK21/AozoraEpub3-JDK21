package com.github.hmdev.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

	/**
	 * Opens an InputStream for the txt content from txt/zip/txtz/rar.
	 * Uses cache for archives to avoid multiple scans.
	 * Caller must close the returned stream.
	 */
	public static InputStream getTextInputStream(File srcFile, String ext, ImageInfoReader imageInfoReader, String[] textEntryName, int txtIdx)
			throws IOException, RarException {
		if ("txt".equals(ext)) {
			return new FileInputStream(srcFile);
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
			LogAppender.append("txt, zip, rar, txtz, cbz のみ変換可能です: ");
			LogAppender.println(srcFile.getPath());
		}
		return null;
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
