package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Epub3Writer.displayTextLength のテスト。
 * タイトルページのfont-size段階調整に使う表示文字数の計算を検証する。
 */
public class Epub3WriterDisplayTextLengthTest
{
	@Test
	public void plainText()
	{
		assertEquals(5, Epub3Writer.displayTextLength("あいうえお"));
	}

	@Test
	public void nullText()
	{
		assertEquals(0, Epub3Writer.displayTextLength(null));
	}

	@Test
	public void emptyText()
	{
		assertEquals(0, Epub3Writer.displayTextLength(""));
	}

	/** ルビはrtを除き親文字のみ数える （前｜漢字《かんじ》 → 前<ruby>漢字<rt>かんじ</rt></ruby>） */
	@Test
	public void rubyCountsBaseTextOnly()
	{
		assertEquals(3, Epub3Writer.displayTextLength("前<ruby>漢字<rt>かんじ</rt></ruby>"));
	}

	@Test
	public void rubyWithRpCountsBaseTextOnly()
	{
		assertEquals(2, Epub3Writer.displayTextLength("<ruby>漢字<rp>（</rp><rt>かんじ</rt><rp>）</rp></ruby>"));
	}

	/** 外字画像は1文字として数える */
	@Test
	public void gaijiImageCountsAsOneChar()
	{
		assertEquals(5, Epub3Writer.displayTextLength("外字<img src=\"../gaiji/u2eb66.png\" alt=\"〓\"/>あり"));
	}

	/** 文字実体参照は1文字として数える */
	@Test
	public void characterEntityCountsAsOneChar()
	{
		assertEquals(3, Epub3Writer.displayTextLength("A&amp;B"));
		assertEquals(3, Epub3Writer.displayTextLength("A&#x3042;B"));
	}

	/** その他のタグは除去して数える */
	@Test
	public void otherTagsAreStripped()
	{
		assertEquals(2, Epub3Writer.displayTextLength("<b>太字</b>"));
	}

	/** サロゲートペアはコードポイントで1文字と数える */
	@Test
	public void surrogatePairCountsAsOneChar()
	{
		assertEquals(3, Epub3Writer.displayTextLength("𠀋の話")); // 𠀋の話
	}

	/** なろうの実タイトル相当の長さが素直に数えられる */
	@Test
	public void longNarouTitle()
	{
		String title = "S級探索者を5人育てたら全員に独立された中年コーチ、暇つぶしの初心者講座配信が「人類の攻略常識」を書き換え始める";
		assertEquals(title.length(), Epub3Writer.displayTextLength(title));
	}
}
