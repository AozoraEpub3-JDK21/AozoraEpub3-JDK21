package com.github.hmdev.preview;

import java.nio.file.Path;

/**
 * 本棚に並べる EPUB 1 冊の書誌情報。
 *
 * <p>EPUB を展開せず ZIP エントリを直接読んで作る ({@link LibraryScanner})。
 * 展開は一覧から選択したときに初めて行う (遅延展開)。</p>
 *
 * @param file EPUB の絶対パス
 * @param size ファイルサイズ。{@code modifiedMillis} と合わせてキャッシュの有効判定に使う
 * @param modifiedMillis 最終更新時刻 (epoch millis)
 * @param title {@code dc:title}。取れなければ null
 * @param creator {@code dc:creator}。取れなければ null
 * @param coverEntry 表紙画像の EPUB ルート相対パス。無ければ null
 */
public record LibraryEntry(
	Path file,
	long size,
	long modifiedMillis,
	String title,
	String creator,
	String coverEntry)
{
	/** 一覧に出す表示名。書名が取れなければファイル名 */
	public String displayName()
	{
		if (this.title != null && !this.title.isEmpty()) return this.title;
		return this.file.getFileName().toString();
	}

	/** キャッシュが指すファイルと同一とみなせるか (サイズ + 更新時刻の一致) */
	public boolean matches(long size, long modifiedMillis)
	{
		return this.size == size && this.modifiedMillis == modifiedMillis;
	}
}
