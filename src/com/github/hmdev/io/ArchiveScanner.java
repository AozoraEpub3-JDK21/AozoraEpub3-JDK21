package com.github.hmdev.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

/**
 * Scans archive files once and extracts text/image entry information.
 */
final class ArchiveScanner {
	private ArchiveScanner() {
	}
	
	/**
	 * zip の中のテキストと画像を読む。テキストが 1 つ壊れていても、その 1 つだけを飛ばして残りを読む（internal #36。読み方は {@link ZipEntries}）
	 * @param skippedTexts 読めずに飛ばしたテキストの「名前 (理由)」を足す
	 */
	static void scanZip(Path zipPath, List<ArchiveCache.TextEntry> textEntries, List<String> imageEntries, List<String> skippedTexts) throws IOException {
		ZipEntries.forEach(zipPath, false, (entryName, opener) -> {
			if (entryName.endsWith("/")) return true;
			String entryExt = entryName.substring(entryName.lastIndexOf('.') + 1).toLowerCase();
			if ("txt".equalsIgnoreCase(entryExt)) {
				byte[] content;
				try (InputStream is = opener.open()) {
					content = ArchiveCache.readFully(is);
				} catch (IOException | RuntimeException e) {
					skipText(skippedTexts, entryName, e);
					return true;
				}
				textEntries.add(new ArchiveCache.TextEntry(entryName, content));
			} else if (isImageExtension(entryExt)) {
				imageEntries.add(entryName);
			}
			return true;
		});
	}

	/** 読めないテキストの名前と理由を控える（ログに出すのは ArchiveCache。画像の一覧を作るだけの読み方では出さない） */
	private static void skipText(List<String> skippedTexts, String entryName, Exception e) {
		if (skippedTexts != null) skippedTexts.add(entryName + " (" + e + ")");
	}

	static void scanRar(Path rarPath, List<ArchiveCache.TextEntry> textEntries, List<String> imageEntries, List<String> skippedTexts) throws IOException, RarException {
		try (Archive archive = new Archive(rarPath.toFile())) {
			for (FileHeader fileHeader : archive.getFileHeaders()) {
				if (fileHeader.isDirectory()) continue;

				String entryName = fileHeader.getFileName().replace('\\', '/');
				String entryExt = entryName.substring(entryName.lastIndexOf('.') + 1).toLowerCase();

				if ("txt".equalsIgnoreCase(entryExt)) {
					// extract to byte array
					Path tmpPath = Files.createTempFile("rarTxt", ".txt");
					tmpPath.toFile().deleteOnExit();
					//1 つのテキストが読めなくても、その 1 つだけ飛ばす（zip と同じ。internal #36）
					try {
						//getInputStream は展開の失敗（RarException）を別のスレッドで握りつぶし、途中で切れた本文を返す。
						//extractFile は投げるので、壊れたテキストを飛ばせる
						try (OutputStream fos = Files.newOutputStream(tmpPath)) {
							archive.extractFile(fileHeader, fos);
						}
						try (InputStream fis = Files.newInputStream(tmpPath)) {
							byte[] content = ArchiveCache.readFully(fis);
							textEntries.add(new ArchiveCache.TextEntry(entryName, content));
						}
					} catch (IOException | RarException | RuntimeException e) {
						skipText(skippedTexts, entryName, e);
					} finally {
						Files.deleteIfExists(tmpPath);
					}
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
