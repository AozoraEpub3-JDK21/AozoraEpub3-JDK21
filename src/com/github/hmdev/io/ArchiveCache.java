package com.github.hmdev.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.github.junrar.exception.RarException;

/**
 * Caches archive contents to avoid multiple file scans.
 * Holds text file count, text content, and image entry names.
 */
public class ArchiveCache {
	private final File archiveFile;
	private final String ext;
	
	// cached results
	private int textFileCount = -1;
	private List<TextEntry> textEntries;
	private List<String> imageEntries;
	private boolean scanned = false;
	
	public ArchiveCache(File archiveFile, String ext) {
		this.archiveFile = archiveFile;
		this.ext = ext;
	}
	
	public synchronized void scan() throws IOException, RarException {
		if (scanned) return;
		
		textEntries = new ArrayList<>();
		imageEntries = new ArrayList<>();
		
		if ("zip".equals(ext) || "txtz".equals(ext)) {
			ArchiveScanner.scanZip(archiveFile, textEntries, imageEntries);
		} else if ("rar".equals(ext)) {
			ArchiveScanner.scanRar(archiveFile, textEntries, imageEntries);
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
