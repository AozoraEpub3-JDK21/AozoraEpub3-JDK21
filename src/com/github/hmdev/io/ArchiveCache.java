package com.github.hmdev.io;

import com.github.hmdev.util.LogAppender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.github.junrar.exception.RarException;

/**
 * Caches archive contents to avoid multiple file scans.
 * Holds text file count, text content, and image entry names.
 */
public class ArchiveCache {
	private final Path archivePath;
	private final String ext;

	// cached results
	private int textFileCount = -1;
	private List<TextEntry> textEntries;
	private List<String> imageEntries;
	/** 読めずに飛ばしたテキストの「名前 (理由)」（internal #36） */
	private List<String> skippedTexts;
	private boolean scanned = false;
	/** 飛ばしたテキストを利用者に見えるログに出す（本文を読む ArchiveTextExtractor の控えだけ。画像の一覧を作るだけの控えは出さない） */
	private final boolean logSkipped;

	public ArchiveCache(File archiveFile, String ext) {
		this(archiveFile, ext, false);
	}

	public ArchiveCache(File archiveFile, String ext, boolean logSkipped) {
		this.archivePath = archiveFile.toPath();
		this.ext = ext;
		this.logSkipped = logSkipped;
	}

	public synchronized void scan() throws IOException, RarException {
		if (scanned) return;

		textEntries = new ArrayList<>();
		imageEntries = new ArrayList<>();
		skippedTexts = new ArrayList<>();

		if ("zip".equals(ext) || "txtz".equals(ext)) {
			ArchiveScanner.scanZip(archivePath, textEntries, imageEntries, skippedTexts);
		} else if ("rar".equals(ext)) {
			ArchiveScanner.scanRar(archivePath, textEntries, imageEntries, skippedTexts);
		}
		if (logSkipped) {
			for (String skipped : skippedTexts) {
				LogAppender.println("[WARN] 書庫の中のテキストが読めないので飛ばします : " + skipped + " (" + archivePath.getFileName() + ")");
			}
		}
		
		textFileCount = textEntries.size();
		scanned = true;
	}
	
	public int getTextFileCount() throws IOException, RarException {
		if (!scanned) scan();
		return textFileCount;
	}
	
	public InputStream getTextInputStream(int txtIdx, String[] textEntryNameOut) throws IOException, RarException {
		if (!scanned) scan();
		
		if (txtIdx < 0 || txtIdx >= textEntries.size()) {
			return null;
		}
		
		TextEntry entry = textEntries.get(txtIdx);
		if (textEntryNameOut != null && textEntryNameOut.length > 0) {
			textEntryNameOut[0] = entry.name;
		}
		
		return new ByteArrayInputStream(entry.content);
	}
	
	public List<String> getImageEntries() throws IOException, RarException {
		if (!scanned) scan();
		return new ArrayList<>(imageEntries);
	}
	
	/** 読めずに飛ばしたテキストの数 */
	public int getSkippedTextCount() throws IOException, RarException {
		if (!scanned) scan();
		return skippedTexts.size();
	}

	public String getTextEntryName(int txtIdx) throws IOException, RarException {
		if (!scanned) scan();
		if (txtIdx < 0 || txtIdx >= textEntries.size()) return null;
		return textEntries.get(txtIdx).name;
	}
	
	static class TextEntry {
		String name;
		byte[] content;
		
		TextEntry(String name, byte[] content) {
			this.name = name;
			this.content = content;
		}
	}
	
	static byte[] readFully(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int len;
		while ((len = is.read(buf)) > 0) {
			baos.write(buf, 0, len);
		}
		return baos.toByteArray();
	}
}
