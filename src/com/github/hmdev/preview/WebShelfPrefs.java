package com.github.hmdev.preview;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Web 本棚の場所の設定（{@code AozoraEpub3.ini} の {@code WebShelfDir}）と、提案の場所。
 *
 * <p>棚の一覧（{@link PreviewLibraryPrefs}）と同じく、プロファイルではなく全体設定に置く。</p>
 */
public final class WebShelfPrefs
{
	public static final String KEY = "WebShelfDir";

	/** 提案の場所の、棚の下のフォルダ名 */
	static final String SHELF_CHILD = "Web";
	/** 提案の場所の、書類フォルダの下のフォルダ名 */
	static final String DOCUMENTS_CHILD = "AozoraEpub3";

	private WebShelfPrefs() {}

	/** 設定の場所。無い・空・パスとして読めない・絶対パスでないなら null */
	public static Path load(Properties props)
	{
		if (props == null) return null;
		String value = props.getProperty(KEY);
		if (value == null || value.isBlank()) return null;
		try {
			Path path = Path.of(value.trim());
			return path.isAbsolute() ? path.normalize() : null;
		} catch (InvalidPathException e) {
			return null;
		}
	}

	public static void store(Properties props, Path dir)
	{
		if (dir == null) props.remove(KEY);
		else props.setProperty(KEY, dir.toAbsolutePath().normalize().toString());
	}

	/**
	 * 提案の場所: 登録済みの最初の棚の下の {@code Web}、棚が無ければ書類フォルダの下の {@code AozoraEpub3}
	 * （書類フォルダが無ければホームの下）。internal #11 の合意
	 * @param shelves 登録済みの棚（先頭から見る）
	 * @param home ホームのフォルダ
	 */
	public static Path suggest(List<Path> shelves, Path home)
	{
		if (shelves != null) {
			for (Path shelf : shelves) {
				if (shelf != null) return shelf.toAbsolutePath().normalize().resolve(SHELF_CHILD);
			}
		}
		Path documents = home.resolve("Documents");
		return (Files.isDirectory(documents) ? documents : home).resolve(DOCUMENTS_CHILD);
	}
}
