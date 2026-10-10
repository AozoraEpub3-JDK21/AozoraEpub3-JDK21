package com.github.hmdev.info;

/**
 * Web 本棚に落とした本の短い名前（internal #11 の案 A。2026-10-09 利用者と合意）。
 *
 * <p>既定は {@code [作者] 題}。長い題は、先頭の {@code 【…】} を外し、{@code ～} 以降の副題を落とす。
 * それでも長ければ {@link #MAX_CHARS} 文字で切る。</p>
 */
public final class ShelfNames
{
	/** 名前の長さの目安（文字数。作者の部分も含む） */
	public static final int MAX_CHARS = 50;

	private ShelfNames() {}

	/**
	 * 台帳の txt の名前（{@code [作者] 題}、作者が無ければ題だけ）から、短い名前を作る。
	 * 題が消えてしまうときは、外す前の題を使う
	 */
	public static String shortBaseName(String textBaseName)
	{
		if (textBaseName == null || textBaseName.isBlank()) return textBaseName;
		String author = "";
		String title = textBaseName;
		if (textBaseName.startsWith("[")) {
			int close = textBaseName.indexOf("] ");
			if (close > 0) {
				author = textBaseName.substring(0, close + 2);
				title = textBaseName.substring(close + 2);
			}
		}
		String shortTitle = title;
		//先頭の【…】を外す（いくつ続いても）
		while (shortTitle.startsWith("【") && shortTitle.indexOf('】') > 0) {
			shortTitle = shortTitle.substring(shortTitle.indexOf('】') + 1).strip();
		}
		//～（全角のチルダ）と〜（波ダッシュ）以降の副題を落とす
		int tilde = indexOfAny(shortTitle, '～', '〜');
		if (tilde > 0) shortTitle = shortTitle.substring(0, tilde).strip();
		if (shortTitle.isEmpty()) shortTitle = title;
		return cut(author + shortTitle, MAX_CHARS);
	}

	private static int indexOfAny(String s, char a, char b)
	{
		int i = s.indexOf(a);
		int j = s.indexOf(b);
		if (i < 0) return j;
		if (j < 0) return i;
		return Math.min(i, j);
	}

	/** 文字数（サロゲートペアは 1 文字）で切る。切った後ろの空白は落とす */
	private static String cut(String s, int max)
	{
		if (s.codePointCount(0, s.length()) <= max) return s;
		return s.substring(0, s.offsetByCodePoints(0, max)).strip();
	}
}
