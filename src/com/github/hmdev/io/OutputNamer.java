package com.github.hmdev.io;

import java.io.File;

import com.github.hmdev.info.BookInfo;

/**
 * Generates output file names for converted EPUB files while applying
 * creator/title-based naming and basic sanitization.
 */
public final class OutputNamer {
	private static final int    MAX_CREATOR_LENGTH  = 64;
	private static final int    MAX_FILENAME_LENGTH = 250;
	private static final String DEFAULT_EXTENSION   = ".epub";

	private OutputNamer() {
	}

	public static File generateOutFile(File srcFile, File dstPath, BookInfo bookInfo, boolean autoFileName, String outExt) {
		if (srcFile == null) {
			throw new IllegalArgumentException("srcFile must not be null");
		}
		if (dstPath == null) {
			dstPath = srcFile.getAbsoluteFile().getParentFile();
		}

		String outFileName;
		if (autoFileName && bookInfo != null && (hasText(bookInfo.creator) || hasText(bookInfo.title))) {
			StringBuilder builder = new StringBuilder();
			builder.append(dstPath.getAbsolutePath()).append("/");
			if (hasText(bookInfo.creator)) {
				String creator = sanitize(bookInfo.creator);
				if (creator.length() > MAX_CREATOR_LENGTH) {
					creator = creator.substring(0, MAX_CREATOR_LENGTH);
				}
				builder.append("[").append(creator).append("] ");
			}
			if (bookInfo.title != null) {
				builder.append(sanitize(bookInfo.title));
			}
			if (builder.length() > MAX_FILENAME_LENGTH) {
				builder.setLength(MAX_FILENAME_LENGTH);
			}
			outFileName = builder.toString();
		} else {
			outFileName = dstPath.getAbsolutePath() + "/" + srcFile.getName().replaceFirst("\\.[^\\.]+$", "");
		}

		if (outExt == null || outExt.length() == 0) {
			outExt = DEFAULT_EXTENSION;
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
