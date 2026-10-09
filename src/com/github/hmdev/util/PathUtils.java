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

	/** 255 バイトで切っても作れないときに試す上限（Linux の eCryptfs で暗号化したフォルダは 143 バイトまで） */
	public static final int[] SMALLER_NAME_LIMITS = { 143 };

	/**
	 * dir に、名前（拡張子なし）＋拡張子の名前で書けるようにする（internal #16）。
	 * 255 バイトに収まる名前と、収まらなくてもその場所のファイルシステムが受け付ける名前（Windows・mac は文字数で数える）は、
	 * そのまま返す。長さで弾かれるときだけ {@link #fitFileName} で切る
	 */
	public static String fitFileNameIn(File dir, String baseName, String ext)
	{
		if (utf8Length(baseName + ext) <= MAX_NAME_BYTES) return baseName;
		//フォルダを含む名前（拡張子の側も）は試さない（dir の外にファイルを作らないように。呼び出し元は名前 1 つだけを渡す）
		String whole = baseName + ext;
		if (whole.indexOf('/') >= 0 || whole.indexOf('\\') >= 0) return baseName;
		if (nameAccepted(dir, baseName + ext)) return baseName;
		String fitted = fitFileName(baseName, ext, MAX_NAME_BYTES);
		for (int limit : SMALLER_NAME_LIMITS) {
			if (nameAccepted(dir, fitted + ext)) break;
			fitted = fitFileName(baseName, ext, limit);
		}
		return fitted;
	}

	/**
	 * その場所で、この名前のファイルを作れるか。長さで弾かれると分かったときだけ false。
	 * 試しは同じバイト数の別の名前（先頭が "."）で行う。本物の名前の空のファイルが残ったり、同期のアプリに見えたりしないように
	 */
	static boolean nameAccepted(File dir, String name)
	{
		Path d = dir.toPath();
		try {
			Files.createDirectories(d);
		} catch (IOException | RuntimeException e) {
			return true; //場所が使えない。名前は変えず、後の書き込みに理由を出させる
		}
		if (canCreate(d, probeName(name))) return true;
		//短い名前なら作れるときだけ、長さで弾かれたとみなす（権限・使えない文字などの失敗では名前を変えない）
		return !canCreate(d, ".aozora-probe-" + Long.toHexString(System.nanoTime()) + ".tmp");
	}

	/**
	 * name と UTF-8 のバイト数も文字数（UTF-16）も同じで、先頭の 1 文字だけ違う名前。
	 * バイトで数える場所（Linux）でも文字で数える場所（Windows・mac）でも、本物の名前と同じ長さで試せる。
	 * 先頭が ASCII なら "."（隠しファイル）にする
	 */
	static String probeName(String name)
	{
		int first = name.codePointAt(0);
		int[] candidates;
		switch (utf8Length(first)) {
			case 1: candidates = new int[]{ '.', '_' }; break;
			case 2: candidates = new int[]{ 0xDF, 0xF0 }; break;          //ß ð
			case 3: candidates = new int[]{ 0x3007, 0x3006 }; break;      //〇 〆
			default: candidates = new int[]{ 0x20000, 0x20001 }; break;  //𠀀 𠀁
		}
		int head = candidates[0] != first ? candidates[0] : candidates[1];
		return new String(Character.toChars(head)) + name.substring(Character.charCount(first));
	}

	/** 作れたら消す（消せなくても作れたことに変わりはない） */
	static boolean canCreate(Path dir, String name)
	{
		Path p;
		try {
			p = dir.resolve(name);
		} catch (RuntimeException e) {
			return false;
		}
		try {
			Files.createFile(p);
		} catch (java.nio.file.FileAlreadyExistsException e) {
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
		try {
			Files.delete(p);
		} catch (IOException e) {
			//試しの名前なので、残っても本物の出力とはぶつからない
		}
		return true;
	}

	/**
	 * 拡張子を足した名前が {@link #MAX_NAME_BYTES}（または maxBytes）バイトに収まるよう、名前（拡張子なし）の後ろを切り、
	 * 元の名前から作る印（"~" と 16 進 6 桁）を付ける。末尾だけ違う題（上・下など）が同じ名前にならず、同じ名前からは毎回同じ名前になる。
	 * 文字の途中では切らない。切った後の末尾の空白とドットは落とす。収まっていれば、そのまま返す
	 */
	public static String fitFileName(String baseName, String ext)
	{
		return fitFileName(baseName, ext, MAX_NAME_BYTES);
	}

	public static String fitFileName(String baseName, String ext, int maxBytes)
	{
		int budget = maxBytes - utf8Length(ext);
		if (utf8Length(baseName) <= budget) return baseName;
		String mark = cutMark(baseName);
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
		return trimCutEnd(sb) + mark;
	}

	/** 切った名前に付ける印の長さ（"~" と 16 進 6 桁） */
	public static final int CUT_MARK_LENGTH = 7;

	/**
	 * 名前（拡張子なし）が maxChars <b>文字</b>（UTF-16 の char の数。Windows の MAX_PATH の数え方）に収まるよう後ろを切り、
	 * {@link #fitFileName(String, String, int)} と同じ印を付ける。文字の途中（サロゲートペアの間）では切らず、
	 * 切った後の末尾の空白とドットは落とす。収まっていれば、そのまま返す。
	 * <p>パス全体の長さで切ると、末尾だけ違う題（上・下など）が同じ名前になり、あとの本が前の本を上書きする（internal #16）。</p>
	 * @param maxChars 印を含めた上限。{@link #CUT_MARK_LENGTH} 以上であること（ちょうどなら印だけの名前になる）
	 */
	public static String fitFileNameChars(String baseName, int maxChars)
	{
		if (baseName.length() <= maxChars) return baseName;
		String mark = cutMark(baseName);
		int budget = maxChars - mark.length();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < baseName.length(); ) {
			int cp = baseName.codePointAt(i);
			int len = Character.charCount(cp);
			if (sb.length() + len > budget) break;
			sb.appendCodePoint(cp);
			i += len;
		}
		return trimCutEnd(sb) + mark;
	}

	/** 元の名前から作る印。同じ名前からは毎回同じ印になる */
	static String cutMark(String baseName)
	{
		CRC32 crc = new CRC32();
		crc.update(baseName.getBytes(StandardCharsets.UTF_8));
		return String.format("~%06x", crc.getValue() & 0xffffff);
	}

	/** 切った後の末尾の空白とドットを落とす（Windows は末尾のドット・空白を落として別の名前にするため） */
	static String trimCutEnd(StringBuilder sb)
	{
		int end = sb.length();
		while (end > 0 && (sb.charAt(end-1) == ' ' || sb.charAt(end-1) == '.' || sb.charAt(end-1) == '\u3000')) end--;
		return sb.substring(0, end);
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
