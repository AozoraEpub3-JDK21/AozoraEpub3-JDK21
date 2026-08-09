package com.github.hmdev.preview;

import java.nio.file.Path;
import java.util.List;

/**
 * 本棚 1 つぶん。走査したフォルダと、その配下で見つかった本。
 *
 * <p>棚は複数登録できる ({@link PreviewSession#setLibrary(List)})。
 * 本が「どの棚のものか」を保つためにフォルダと一覧を組にして渡す。
 * 一覧に出す位置 (棚からの相対フォルダ) はこのフォルダを基準に求めるので、
 * 別々の棚の本を 1 つの {@code List<LibraryEntry>} に混ぜてはいけない。</p>
 *
 * @param folder 走査したフォルダ
 * @param entries {@link LibraryScanner#scan} の結果
 */
public record LibraryShelf(Path folder, List<LibraryEntry> entries)
{
	public LibraryShelf
	{
		entries = (entries == null) ? List.of() : List.copyOf(entries);
	}
}
