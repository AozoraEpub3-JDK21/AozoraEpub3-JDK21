package com.github.hmdev.preview;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * 本棚フォルダの設定 ({@code AozoraEpub3.ini}) の読み書き。
 *
 * <p>キーは連番 ({@code PreviewLibraryDir.1} / {@code .2} …) にする。
 * 既存の {@code DstPathList} のようなカンマ区切りにはしない。Windows のフォルダ名には
 * カンマを入れられるため、区切り文字を持ち込むとそのフォルダを登録できなくなる。</p>
 *
 * <p>この設定は<b>プロファイルではなく全体設定</b>に置く。
 * {@code AozoraEpub3Applet.loadProperties} / {@code setProperties} は
 * {@code profiles/*.ini} にも使われるため、そこへ混ぜるとプロファイルを切り替えるたびに
 * 棚が入れ替わってしまう。</p>
 */
public final class PreviewLibraryPrefs
{
	/** 連番キーの接頭辞 */
	public static final String KEY_PREFIX = "PreviewLibraryDir.";

	private PreviewLibraryPrefs() {}

	/**
	 * 設定から棚のフォルダを読み出す。
	 *
	 * <p>番号の抜け (手で編集した ini など) は詰めて読む。空欄と重複は落とし、
	 * {@link LibraryScanner#MAX_SHELVES} 個で打ち切る。
	 * 入れ子の棚を畳むのは {@link PreviewLauncher#normalizeShelfFolders} の仕事なので
	 * ここではしない (設定画面には書いたとおりに見せる)。</p>
	 *
	 * @param props 全体設定。null なら空リストを返す
	 * @return 棚のフォルダ (設定に書かれた文字列のまま)
	 */
	public static List<String> load(Properties props)
	{
		if (props == null) return new ArrayList<>();
		//番号順に並べ直してから詰める。Properties は順序を持たないため、
		//stringPropertyNames() の順に読むと ini の見た目と並びが変わる
		TreeMap<Integer, String> numbered = new TreeMap<>();
		for (String name : props.stringPropertyNames()) {
			if (!name.startsWith(KEY_PREFIX)) continue;
			Integer index = parseIndex(name.substring(KEY_PREFIX.length()));
			if (index == null) continue;
			numbered.put(index, props.getProperty(name));
		}
		return sanitize(numbered.values());
	}

	/**
	 * 棚のフォルダを設定へ書き戻す。
	 *
	 * <p>書く前に既存の連番キーを<b>すべて消す</b>。上書きだけで済ませると、
	 * 3 個から 2 個へ減らしたときに {@code .3} が残って次の起動で復活する。</p>
	 *
	 * @param props 全体設定
	 * @param folders 棚のフォルダ。空欄・重複・{@link LibraryScanner#MAX_SHELVES} 超過は落とす
	 */
	public static void store(Properties props, List<String> folders)
	{
		if (props == null) return;
		//列挙しながら消さない (ConcurrentModificationException)
		List<String> stale = new ArrayList<>();
		for (String name : props.stringPropertyNames()) {
			if (name.startsWith(KEY_PREFIX) && parseIndex(name.substring(KEY_PREFIX.length())) != null) {
				stale.add(name);
			}
		}
		for (String name : stale) props.remove(name);

		List<String> cleaned = sanitize(folders == null ? List.of() : folders);
		for (int i = 0; i < cleaned.size(); i++) {
			props.setProperty(KEY_PREFIX + (i + 1), cleaned.get(i));
		}
	}

	/** 空欄を落とし、重複を畳み、上限で打ち切る */
	private static List<String> sanitize(Iterable<String> folders)
	{
		List<String> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String folder : folders) {
			if (folder == null) continue;
			String trimmed = folder.trim();
			if (trimmed.isEmpty()) continue;
			if (!seen.add(dedupeKey(trimmed))) continue;
			result.add(trimmed);
			if (result.size() >= LibraryScanner.MAX_SHELVES) break;
		}
		return result;
	}

	/**
	 * 重複判定用のキー。
	 *
	 * <p>末尾の区切りや {@code .} を含む書き方の違いを吸収するため正規化した絶対パスで比べる。
	 * 大文字小文字は畳まない — Windows では同じフォルダでも Linux では別のフォルダであり、
	 * どちらに寄せても片方で間違えるため、OS 判定を持ち込まない方を選ぶ。</p>
	 */
	public static String dedupeKey(String folder)
	{
		try {
			return Path.of(folder).toAbsolutePath().normalize().toString();
		} catch (InvalidPathException e) {
			//Windows で使えない文字を含む等。設定に書かれた文字列のまま比べる
			return folder;
		}
	}

	/** 連番部分。数字以外が混ざっていれば null (別のキーとして無視する) */
	private static Integer parseIndex(String suffix)
	{
		if (suffix.isEmpty()) return null;
		for (int i = 0; i < suffix.length(); i++) {
			if (suffix.charAt(i) < '0' || suffix.charAt(i) > '9') return null;
		}
		try {
			return Integer.valueOf(suffix);
		} catch (NumberFormatException e) {
			//桁が多すぎる場合。連番として扱わない
			return null;
		}
	}
}
