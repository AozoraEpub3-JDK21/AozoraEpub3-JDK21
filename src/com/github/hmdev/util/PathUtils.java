package com.github.hmdev.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * パスの実パス解決（パストラバーサル対策の比較に使う）
 */
public class PathUtils
{
	/** 実在する最も近い祖先を toRealPath() で解決し、残りのセグメントを連結して返す。
	 * path 自身が存在しない場合でも、途中のディレクトリが symlink / junction で
	 * 別の場所を指しているケースを解決できるようにするため。
	 * 壊れた symlink に当たった場合は toRealPath() が IOException を投げ、
	 * 呼び出し元では「安全でないパス」として扱われる（fail closed）。 */
	public static Path realPath(Path path) throws IOException {
		Path abs = path.toAbsolutePath().normalize();
		//symlink 自体も「実在する」とみなすため NOFOLLOW_LINKS で遡る
		Path existing = abs;
		while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
			existing = existing.getParent();
		}
		if (existing == null) return abs;
		Path real = existing.toRealPath();
		if (existing.getNameCount() == abs.getNameCount()) return real;
		return real.resolve(abs.subpath(existing.getNameCount(), abs.getNameCount())).normalize();
	}

	/** 名前 1 つの上限（Linux の ext4 などは UTF-8 で 255 バイト。Windows・mac は 255 文字なので、こちらを守れば足りる） */
	public static final int MAX_NAME_BYTES = 255;

	/**
	 * 拡張子を足した名前が {@link #MAX_NAME_BYTES} バイトに収まるよう、名前（拡張子なし）の後ろを切る。
	 * 文字の途中では切らない。切ったときだけ、末尾の空白とドットを落とす（Windows では名前の末尾に置けない）。
	 * 収まっていれば、そのまま返す
	 */
	public static String fitFileName(String baseName, String ext)
	{
		int budget = MAX_NAME_BYTES - ext.getBytes(StandardCharsets.UTF_8).length;
		if (baseName.getBytes(StandardCharsets.UTF_8).length <= budget) return baseName;
		StringBuilder sb = new StringBuilder();
		int bytes = 0;
		for (int i = 0; i < baseName.length(); ) {
			int cp = baseName.codePointAt(i);
			int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
			if (bytes + len > budget) break;
			sb.appendCodePoint(cp);
			bytes += len;
			i += Character.charCount(cp);
		}
		int end = sb.length();
		while (end > 0 && (sb.charAt(end-1) == ' ' || sb.charAt(end-1) == '.' || sb.charAt(end-1) == '\u3000')) end--;
		return sb.substring(0, end);
	}
}
