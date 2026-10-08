package com.github.hmdev.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.CRC32;

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
	 * dir に、名前（拡張子なし）＋拡張子の名前で書けるようにする（internal #16）。
	 * 255 バイトに収まる名前と、収まらなくてもその場所のファイルシステムが受け付ける名前（Windows・mac は文字数で数える）は、
	 * そのまま返す。受け付けないときだけ {@link #fitFileName} で切る
	 */
	public static String fitFileNameIn(File dir, String baseName, String ext)
	{
		if (utf8Length(baseName + ext) <= MAX_NAME_BYTES) return baseName;
		if (nameAccepted(dir, baseName + ext)) return baseName;
		return fitFileName(baseName, ext);
	}

	/** その場所で、この名前のファイルを作れるか。作れないと分かったときだけ false（ほかの失敗は後の書き込みに任せる） */
	static boolean nameAccepted(File dir, String name)
	{
		try {
			Path d = dir.toPath();
			Files.createDirectories(d);
			Path p = d.resolve(name);
			if (Files.exists(p)) return true;
			Files.createFile(p);
			Files.delete(p);
			return true;
		} catch (java.nio.file.FileAlreadyExistsException e) {
			return true;
		} catch (java.nio.file.FileSystemException e) {
			return false;
		} catch (IOException | RuntimeException e) {
			return true;
		}
	}

	/**
	 * 拡張子を足した名前が {@link #MAX_NAME_BYTES} バイトに収まるよう、名前（拡張子なし）の後ろを切り、
	 * 元の名前から作る印（"~" と 16 進 6 桁）を付ける。末尾だけ違う題（上・下など）が同じ名前にならず、同じ名前からは毎回同じ名前になる。
	 * 文字の途中では切らない。切った後の末尾の空白とドットは落とす。収まっていれば、そのまま返す
	 */
	public static String fitFileName(String baseName, String ext)
	{
		int budget = MAX_NAME_BYTES - utf8Length(ext);
		if (utf8Length(baseName) <= budget) return baseName;
		CRC32 crc = new CRC32();
		crc.update(baseName.getBytes(StandardCharsets.UTF_8));
		String mark = String.format("~%06x", crc.getValue() & 0xffffff);
		budget -= mark.length();
		StringBuilder sb = new StringBuilder();
		int bytes = 0;
		for (int i = 0; i < baseName.length(); ) {
			int cp = baseName.codePointAt(i);
			int len = utf8Length(cp);
			if (bytes + len > budget) break;
			sb.appendCodePoint(cp);
			bytes += len;
			i += Character.charCount(cp);
		}
		int end = sb.length();
		while (end > 0 && (sb.charAt(end-1) == ' ' || sb.charAt(end-1) == '.' || sb.charAt(end-1) == '\u3000')) end--;
		return sb.substring(0, end) + mark;
	}

	static int utf8Length(String s)
	{
		int n = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			n += utf8Length(cp);
			i += Character.charCount(cp);
		}
		return n;
	}

	static int utf8Length(int cp)
	{
		return cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
	}
}
