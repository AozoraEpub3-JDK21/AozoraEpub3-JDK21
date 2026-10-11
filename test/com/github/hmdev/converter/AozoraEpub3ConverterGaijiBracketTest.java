package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.CharUtils;
import com.github.hmdev.writer.Epub3Writer;

/** 外字から出た［ ］は注記の括弧として読まない（監査項目 36） */
public class AozoraEpub3ConverterGaijiBracketTest
{
	AozoraEpub3Converter converter;

	@Before
	public void setUp() throws Exception
	{
		converter = new AozoraEpub3Converter(new Epub3Writer(""), "");
		converter.bookInfo = new BookInfo(null);
		converter.vertical = true;
	}

	String convertLine(String src) throws Exception
	{
		StringWriter out = new StringWriter();
		BufferedWriter bw = new BufferedWriter(out);
		converter.convertTextLineToEpub3(bw, src, 0, false, false);
		bw.flush();
		return out.toString().strip();
	}

	/** 監査の例: 外字の［の後ろの ＃中見出し］ は見出しにならない */
	@Test
	public void aGaijiOpenBracketDoesNotOpenAnAnnotation() throws Exception
	{
		assertEquals("<p>本文［＃中見出し］後</p>", convertLine("本文※［＃U+FF3B］＃中見出し］後"));
		assertEquals("<p>本文［＃中見出し］後</p>", convertLine("本文※［＃始め角括弧、1-1-46］＃中見出し］後"));
	}

	/** 外字の］は外側の注記を閉じない */
	@Test
	public void aGaijiCloseBracketDoesNotCloseAnAnnotation() throws Exception
	{
		String out = convertLine("猫※［＃終わり角括弧］［＃「猫※［＃終わり角括弧］」に傍点］です");
		assertEquals("<p><span class=\"sesame\">猫］</span>です</p>", out);
	}

	/** 目次・表題の名前でも［］に戻る（注記として消えない） */
	@Test
	public void chapterNamesGetTheBracketsBack() throws Exception
	{
		String name = CharUtils.getChapterName(CharUtils.removeRuby(
			converter.convertGaijiChuki("第※［＃U+FF3B］＃１※［＃U+FF3D］話", true, false)), 100, false);
		assertEquals("第［＃１］話", name);
	}

	/** 本文に代わりの文字が紛れていても、括弧として読まず、出力にも出さない */
	@Test
	public void theSubstituteCharactersInTheTextAreDropped() throws Exception
	{
		String out = convertLine("前" + CharUtils.GAIJI_OPEN_BRACKET + "＃中見出し" + CharUtils.GAIJI_CLOSE_BRACKET + "後");
		assertEquals("<p>前＃中見出し後</p>", out);
	}

	/** 外字の水準で〓に置き換える設定でも、外字の［］は［］のまま（代わりの文字を判定に見せない） */
	@Test
	public void gaijiFallbackKeepsGaijiBrackets() throws Exception
	{
		converter.setGaijiFallback(com.github.hmdev.util.JisLevelUtil.LEVEL_4, false);
		assertEquals("<p>本文［＃中見出し］後</p>", convertLine("本文※［＃U+FF3B］＃中見出し］後"));
	}

	/** 行頭の字下げ: 外字の［で始まる行も、［で始まる行と同じく字下げしない */
	@Test
	public void aLineStartingWithAGaijiBracketIsNotIndented() throws Exception
	{
		converter.forceIndent = true;
		String plain = convertLine("［本文］");
		String gaiji = convertLine("※［＃始め角括弧、1-1-46］本文※［＃終わり角括弧、1-1-47］");
		assertEquals(plain, gaiji);
	}

	/** 表題に外字の［］があっても、目次の表題の名前が注記として削られない */
	@Test
	public void theTitleChapterKeepsGaijiBrackets() throws Exception
	{
		java.io.BufferedReader src = new java.io.BufferedReader(new java.io.StringReader(
			"第※［＃U+FF3B］＃１※［＃U+FF3D］話\n著者\n\n本文です。\n"));
		BookInfo info = converter.getBookInfo(new java.io.File("x.txt"), src, null, BookInfo.TitleType.TITLE_AUTHOR, false);
		assertEquals("第［＃１］話", info.title);
		//目次の表題には著者が続く（「第［＃１］話　著者」）
		String name = info.getChapterLineInfo(info.titleLine).getChapterName();
		assertTrue(name, name.startsWith("第［＃１］話"));
	}

	/** 挿絵のファイル名に外字の［があっても、画像は［］の名前で探す（代わりの文字のまま探さない） */
	@Test
	public void anImageFileNameGetsTheBracketBack() throws Exception
	{
		String tag = converter.convertGaijiChuki("［＃挿絵（foo※［＃U+FF3B］.png）入る］", true, false);
		assertEquals("foo［.png", converter.getImageChukiFileName(tag, tag.indexOf('（')));
	}

	/** 本物の注記は今までどおり */
	@Test
	public void realAnnotationsStillWork() throws Exception
	{
		String out = convertLine("［＃中見出し］見出し［＃中見出し終わり］");
		assertTrue(out, out.contains("見出し"));
		assertFalse(out, out.contains("［＃"));
	}
}
