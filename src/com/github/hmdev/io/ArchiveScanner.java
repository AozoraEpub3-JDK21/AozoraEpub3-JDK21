package com.github.hmdev.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

/**
 * Scans archive files once and extracts text/image entry information.
 */
final class ArchiveScanner {
	private ArchiveScanner() {
	}
	
	static void scanZip(File zipFile, List<ArchiveCache.TextEntry> textEntries, List<String> imageEntries) throws IOException {
		try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new BufferedInputStream(new FileInputStream(zipFile), 65536), "MS932", false)) {
			ArchiveEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String entryName = entry.getName();
				String entryExt = entryName.substring(entryName.lastIndexOf('.') + 1).toLowerCase();
				
				if ("txt".equalsIgnoreCase(entryExt)) {
					byte[] content = ArchiveCache.readFully(zis);
					textEntries.add(new ArchiveCache.TextEntry(entryName, content));
				} else if (isImageExtension(entryExt)) {
					imageEntries.add(entryName);
				}
			}
		}
	}
	
	static void scanRar(File rarFile, List<ArchiveCache.TextEntry> textEntries, List<String> imageEntries) throws IOException, RarException {
		try (Archive archive = new Archive(rarFile)) {
			for (FileHeader fileHeader : archive.getFileHeaders()) {
				if (fileHeader.isDirectory()) continue;
				
				String entryName = fileHeader.getFileName().replace('\\', '/');
				String entryExt = entryName.substring(entryName.lastIndexOf('.') + 1).toLowerCase();
				
				if ("txt".equalsIgnoreCase(entryExt)) {
					// extract to byte array
					File tmpFile = File.createTempFile("rarTxt", ".txt");
					tmpFile.deleteOnExit();
					try (FileOutputStream fos = new FileOutputStream(tmpFile);
					     InputStream is = archive.getInputStream(fileHeader)) {
						byte[] buf = new byte[8192];
						int len;
						while ((len = is.read(buf)) > 0) {
							fos.write(buf, 0, len);
						}
					}
					try (FileInputStream fis = new FileInputStream(tmpFile)) {
						byte[] content = ArchiveCache.readFully(fis);
						textEntries.add(new ArchiveCache.TextEntry(entryName, content));
					}
					tmpFile.delete();
				} else if (isImageExtension(entryExt)) {
					imageEntries.add(entryName);
				}
			}
		}
	}
	
	private static boolean isImageExtension(String ext) {
		return "jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext) || 
		       "png".equalsIgnoreCase(ext) || "gif".equalsIgnoreCase(ext) ||
		       "bmp".equalsIgnoreCase(ext);
	}
}
