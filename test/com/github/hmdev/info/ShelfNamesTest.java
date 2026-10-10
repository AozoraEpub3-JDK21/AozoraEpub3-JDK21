package com.github.hmdev.info;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShelfNamesTest {

	@Test
	public void longTitlesLoseTheirBracketsAndSubtitles() {
		assertEquals("[茨木野] 辺境の【杖職人】が、自分の作る魔法杖は世界最高だと気付くまで",
			ShelfNames.shortBaseName("[茨木野] 【書籍化】辺境の【杖職人】が、自分の作る魔法杖は世界最高だと気付くまで ～「魔力ゼロ、愛想もない」と婚約破棄された私が、帝都でひっそり店を開いたら～"));
		assertEquals("連なる【】は全部外す", "[a] 題", ShelfNames.shortBaseName("[a] 【書籍化】【コミカライズ】題"));
		assertEquals("波ダッシュも", "[a] 題", ShelfNames.shortBaseName("[a] 題〜副題〜"));
		assertEquals("短い題はそのまま", "[作者] 題", ShelfNames.shortBaseName("[作者] 題"));
		assertEquals("作者が無い", "題", ShelfNames.shortBaseName("【完結】題～副題"));
	}

	@Test
	public void aTitleThatWouldVanishIsKept() {
		assertEquals("[a] 【告知】", ShelfNames.shortBaseName("[a] 【告知】"));
		assertEquals("[a] ～副題だけ～", ShelfNames.shortBaseName("[a] ～副題だけ～"));
	}

	@Test
	public void stillLongNamesAreCutByCharacters() {
		String title = "あ".repeat(80);
		String name = ShelfNames.shortBaseName("[作者] " + title);
		assertEquals(ShelfNames.MAX_CHARS, name.codePointCount(0, name.length()));
		String emoji = "😀".repeat(60);
		String cut = ShelfNames.shortBaseName(emoji);
		assertEquals("サロゲートペアを割らない", ShelfNames.MAX_CHARS, cut.codePointCount(0, cut.length()));
	}

	/** 本棚で付け直す名前として使えないもの */
	@Test
	public void badNamesAreRefused() {
		org.junit.Assert.assertNull(ShelfNames.invalidReason("[作者] 題 第二部"));
		for (String bad : new String[]{ "", "  ", ".hidden", "末尾が点.", "末尾が空白 ", " 先頭が空白", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b",
				"tab\tin", "CON", "con.txt", "LPT1", "あ".repeat(201) }) {
			org.junit.Assert.assertNotNull("断る: [" + bad + "]", ShelfNames.invalidReason(bad));
		}
		org.junit.Assert.assertNull("200 文字までは使える", ShelfNames.invalidReason("あ".repeat(200)));
		org.junit.Assert.assertNull("CON で始まるだけの名前は使える", ShelfNames.invalidReason("CONTACT"));
	}

	/** 本の形の拡張子は、二重のものをまとめて 1 つ */
	@Test
	public void extensionsKeepTheBookFormat() {
		assertEquals(".epub", ShelfNames.extensionOf("題.epub"));
		assertEquals(".kepub.epub", ShelfNames.extensionOf("題.kepub.epub"));
		assertEquals(".fxl.kepub.epub", ShelfNames.extensionOf("題.fxl.kepub.epub"));
		assertEquals(".KEPUB.EPUB", ShelfNames.extensionOf("題.KEPUB.EPUB"));
		assertEquals("名前が拡張子だけなら、最後の . から", ".epub", ShelfNames.extensionOf(".kepub.epub"));
		assertEquals("", ShelfNames.extensionOf("題"));
	}
}
