package com.github.hmdev.web;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * 話の題・章の題の数字は narou.rb の hankaku_num_to_zenkaku_num（converterbase.rb:583）と同じ（internal #43）。
 * 半角の 2 桁は縦中横、話の題の先頭の半角 3 桁も縦中横、それ以外の半角の数字は全角、もとから全角の数字はそのまま。
 * 数字の間のカンマは全角。
 * 実機の narou.rb の出力（第［＃縦中横］54［＃縦中横終わり］話・第１００話・４０００・［＃縦中横］300［＃縦中横終わり］年前の遺跡・１００話）と同じ形。ネットワークに出ない
 */
public class WebAozoraConverterHeadingNumberTest
{
	private static WebAozoraConverter converter() throws Exception
	{
		WebAozoraConverter c = WebAozoraConverter.createWebAozoraConverter("https://ncode.syosetu.com/n0000xx/", new File("web"));
		assertNotNull(c);
		return c;
	}

	private static String print(String text, WebAozoraConverter.Heading heading) throws Exception
	{
		return print(converter(), text, heading);
	}

	private static String print(WebAozoraConverter c, String text, WebAozoraConverter.Heading heading) throws Exception
	{
		Method m = WebAozoraConverter.class.getDeclaredMethod("printText", BufferedWriter.class, String.class, WebAozoraConverter.Heading.class);
		m.setAccessible(true);
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			m.invoke(c, bw, text, heading);
		}
		return sw.toString();
	}

	private static String sub(String text) throws Exception
	{
		return print(text, WebAozoraConverter.Heading.SUBTITLE);
	}

	@Test
	public void commaNumberStaysWhole() throws Exception
	{
		assertEquals("第１話　１，０００円", sub("第1話　1,000円"));
		assertEquals("１，０００，０００円", sub("1,000,000円"));
	}

	@Test
	public void digitsFollowNarouRb() throws Exception
	{
		assertEquals("第［＃縦中横］54［＃縦中横終わり］話　包囲（４対４０００）", sub("第54話　包囲（4対4000）"));
		assertEquals("先頭でない 3 桁は全角", "第１００話", sub("第100話"));
		assertEquals("話の題の先頭の 3 桁は縦中横", "［＃縦中横］300［＃縦中横終わり］年前の遺跡", sub("300年前の遺跡"));
		assertEquals("先頭の 4 桁は全角", "２０２４年", sub("2024年"));
		assertEquals("もとから全角の数字はそのまま", "１００話", sub("１００話"));
		assertEquals("カンマの後ろの 2 桁は縦中横（narou.rb と同じ）", "１，［＃縦中横］23［＃縦中横終わり］", sub("1,23"));
		assertEquals("数字の間でないカンマは変えない", "あ,い", sub("あ,い"));
	}

	@Test
	public void chapterTitleHasNoHeadRule() throws Exception
	{
		//章の題は漢数字にしない（2026-10-12 までは本文と同じく漢数字になった）。先頭の 3 桁の決まりも無い
		assertEquals("第［＃縦中横］10［＃縦中横終わり］章", print("第10章", WebAozoraConverter.Heading.CHAPTER));
		assertEquals("１００章", print("100章", WebAozoraConverter.Heading.CHAPTER));
	}

	@Test
	public void kanjiSettingDoesNotMatter() throws Exception
	{
		//漢数字化を切っていても見出しの決まりで変換する（narou.rb は漢数字化しないときも hankaku_num_to_zenkaku_num）
		//設定を変えた変換器そのもので確かめる
		WebAozoraConverter c = converter();
		boolean saved = c.getFormatSettings().isEnableConvertNumToKanji();
		try {
			c.getFormatSettings().setEnableConvertNumToKanji(false);
			assertEquals("第１話　１，０００円", print(c, "第1話　1,000円", WebAozoraConverter.Heading.SUBTITLE));
			assertEquals("第［＃縦中横］10［＃縦中横終わり］章", print(c, "第10章", WebAozoraConverter.Heading.CHAPTER));
		} finally {
			c.getFormatSettings().setEnableConvertNumToKanji(saved);
		}
	}

	@Test
	public void protectedEnglishIsNotLost() throws Exception
	{
		//題の英語は退避されて目印になる。目印の中の番号を全角にすると戻らずに消えた（Opus の確かめ。n9623lp の 7 話）
		String out = sub("【Side Story】兄の決意");
		assertFalse(out, out.contains("英文"));
		assertTrue(out, out.contains("Side Story"));
		assertTrue(out, out.endsWith("兄の決意"));
		//目印の後ろの数字は変換する
		String withNumber = print("【Side Story】第1話", WebAozoraConverter.Heading.CHAPTER);
		assertTrue(withNumber, withNumber.contains("Side Story") && withNumber.endsWith("第１話"));
	}
}
