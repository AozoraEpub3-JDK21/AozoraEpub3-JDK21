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

	/**
	 * 本棚で付け直す名前（拡張子なし）として使えないなら、その理由。使えるなら null。
	 * ファイル名に使えない文字、. で始まる名前（本棚に並ばなくなる）、末尾の . と空白（Windows が落とす）、長すぎる名前を断る
	 */
	public static String invalidReason(String name)
	{
		if (name == null || name.isBlank()) return "名前を入れてください";
		if (name.codePointCount(0, name.length()) > 200) return "名前が長すぎます（200 文字まで）";
		if (name.startsWith(".")) return "名前を . で始めることはできません（本棚に並ばなくなります）";
		if (name.endsWith(".") || name.endsWith(" ") || name.startsWith(" ")) return "名前の前後に空白や . は使えません";
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 0x20 || "\\/:*?\"<>|".indexOf(c) >= 0) return "名前に使えない文字があります: " + (c < 0x20 ? "制御文字" : String.valueOf(c));
		}
		String upper = name.toUpperCase(java.util.Locale.ROOT);
		int dot = upper.indexOf('.');
		String stem = dot >= 0 ? upper.substring(0, dot) : upper;
		if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return "Windows で使えない名前です: " + name;
		return null;
	}

	/** 二重の拡張子（設定で選べる出力の形）。長いものから見る */
	private static final String[] COMPOUND_EXTS = { ".fxl.kepub.epub", ".kepub.epub" };

	/**
	 * 本の名前の拡張子（{@code .fxl.kepub.epub}・{@code .kepub.epub} はまとめて 1 つ）。無ければ ""。
	 * 名前を変えても、更新しても、本の形（Kobo・固定レイアウト）を変えないように（PR のゲート2）
	 */
	public static String extensionOf(String fileName)
	{
		String lower = fileName.toLowerCase(java.util.Locale.ROOT);
		for (String ext : COMPOUND_EXTS) {
			if (lower.endsWith(ext) && lower.length() > ext.length()) return fileName.substring(fileName.length() - ext.length());
		}
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(dot) : "";
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
