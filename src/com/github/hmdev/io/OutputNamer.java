package com.github.hmdev.io;

import java.io.File;

import com.github.hmdev.info.BookInfo;

/**
 * Generates output file names for converted EPUB files while applying
 * creator/title-based naming and basic sanitization.
 */
public final class OutputNamer {
	private OutputNamer() {
	}

	public static File generateOutFile(File srcFile, File dstPath, BookInfo bookInfo, boolean autoFileName, String outExt) {
		if (dstPath == null) {
			dstPath = srcFile.getAbsoluteFile().getParentFile();
		}

		String outFileName;
		if (autoFileName && bookInfo != null && (hasText(bookInfo.creator) || hasText(bookInfo.title))) {
			StringBuilder builder = new StringBuilder();
			builder.append(dstPath.getAbsolutePath()).append("/");
			if (hasText(bookInfo.creator)) {
				String creator = sanitize(bookInfo.creator);
				if (creator.length() > 64) {
					creator = creator.substring(0, 64);
				}
				builder.append("[").append(creator).append("] ");
			}
			if (bookInfo.title != null) {
				builder.append(sanitize(bookInfo.title));
			}
			if (builder.length() > 250) {
				builder.setLength(250);
			}
			outFileName = builder.toString();
		} else {
			outFileName = dstPath.getAbsolutePath() + "/" + srcFile.getName().replaceFirst("\\.[^\\.]+$", "");
		}

		if (outExt == null || outExt.length() == 0) {
			outExt = ".epub";
		}

		File outFile = new File(outFileName + outExt);
		outFile.setWritable(true);
		return outFile;
	}

	private static boolean hasText(String value) {
		return value != null && value.trim().length() > 0;
	}

	private static String sanitize(String value) {
		return value.replaceAll("[\\\\|\\/|\\:|\\*|\\?|\\<|\\>|\\||\\\"|\t]", "");
	}
}
