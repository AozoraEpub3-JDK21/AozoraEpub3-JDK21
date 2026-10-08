package com.github.hmdev.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
}
