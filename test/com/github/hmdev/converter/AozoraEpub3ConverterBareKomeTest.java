package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.CharUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 本文に書かれた ※ が、外字注記のエスケープの数え方を狂わせない（監査項目 32 / upstream kyukyunyorituryo/AozoraEpub3#34）。
 * 以前はエスケープを ※ で表して、※ の個数の偶奇で判定していたので、裸の ※ の後ろの『《』（外字）がルビの開始と読まれ、
 * 行末までの本文が消えた
 */
public class AozoraEpub3ConverterBareKomeTest
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

	/** upstream #34 の例: 裸の ※ の後ろの外字の《 */
	@Test
	public void aBareKomeBeforeAGaijiOpenBracketKeepsTheRestOfTheLine() throws Exception
	{
		assertEquals("<p>※《１</p>", convertLine("※※［＃始め二重山括弧］１"));
		assertEquals("<p>※※《１</p>", convertLine("※※［＃米印］※［＃始め二重山括弧］１"));
		assertEquals("<p>注※《かんじ》の後</p>", convertLine("注※※［＃U+300A］かんじ※［＃U+300B］の後"));
	}

	/** 見出しの中でも消えない（閉じタグも残る） */
	@Test
	public void aHeadingWithABareKomeKeepsItsText() throws Exception
	{
		String out = convertLine("［＃中見出し］破損※※［＃始め二重山括弧］１［＃中見出し終わり］");
		assertTrue(out, out.contains("破損※《１"));
		assertEquals(out, out.split("<h", -1).length - 1, out.split("</h", -1).length - 1);
	}

	/** 裸の ※ の後ろの本物のルビはルビのまま。※ は文字として残る */
	@Test
	public void aBareKomeBeforeARealRubyLeavesTheRubyAlone() throws Exception
	{
		assertEquals("<p>米※<ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("米※漢字《かんじ》"));
		assertEquals("<p>※※<ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("※※漢字《かんじ》"));
	}

	/** エスケープされた ※（※［＃米印］）の後ろの本物のルビも、ルビのまま */
	@Test
	public void anEscapedKomeBeforeARealRubyLeavesTheRubyAlone() throws Exception
	{
		assertEquals("<p>※<ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("※［＃米印］｜漢字《かんじ》"));
	}

	/** 本文に印の文字（制御文字）があっても、エスケープと読まず、出力にも出さない */
	@Test
	public void theMarkerCharacterInTheTextIsDropped() throws Exception
	{
		String marker = String.valueOf(CharUtils.ESCAPE);
		assertEquals("<p><ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("漢字" + marker + "《かんじ》"));
	}

	/** 注記の記号に見える文字（［※［＃井げた］文字］）は、目次・表題の名前で注記として消えない */
	@Test
	public void aLiteralBracketHashInANameIsNotTakenForAnAnnotation() throws Exception
	{
		String name = CharUtils.getChapterName(CharUtils.removeRuby(
			converter.convertGaijiChuki("章［※［＃井げた］文字］", true, false)), 100, false);
		assertEquals("章［＃文字］", name);
	}

	/** 発行者（先頭が発行者）にも印を残さない。OPF に制御文字を書かない */
	@Test
	public void thePublisherHasNoMarker() throws Exception
	{
		java.io.BufferedReader src = new java.io.BufferedReader(new java.io.StringReader(
			"※［＃米印］出版\n題名\n著者\n\n本文です。\n"));
		BookInfo info = converter.getBookInfo(new java.io.File("x.txt"), src, null, BookInfo.TitleType.TITLE_AUTHOR, true);
		assertEquals("※出版", info.publisher);
	}

	/** 同じ長さのルビ（1 字ずつ振る）は、親文字にエスケープ済みの文字があれば使わない。読みが空の親文字に付かない */
	@Test
	public void aSameLengthRubyOverAnEscapedCharStaysOneRuby() throws Exception
	{
		assertEquals("<p><ruby>※<rt>・・</rt></ruby></p>", convertLine("｜※［＃米印］《・・》"));
	}

	/** 割り注の改行は、ルビの読みの中のエスケープ済みの文字で切らない（ルビが本文として出ない） */
	@Test
	public void aWarichuBreakIsNotPlacedInsideARubyReading() throws Exception
	{
		String out = convertLine("［＃割り注］漢字仮《か※［＃米印］》名［＃割り注終わり］");
		assertTrue(out, out.contains("<rt>か※</rt>"));
		assertTrue(out, !out.contains("《"));
	}

	/** 中身がエスケープ済みの文字 1 つだけの割り注でも、変換が止まらない */
	@Test
	public void aOneCharWarichuDoesNotStopTheConversion() throws Exception
	{
		String out = convertLine("前［＃割り注］※［＃米印］［＃割り注終わり］後");
		assertTrue(out, out.contains("※") && out.contains("後"));
	}

	/** 本文に手で書いた ※《 ※｜ は、もうエスケープにならない（青空文庫の書式では ※［＃始め二重山括弧］ などと書く）。
	 * 前は内部のエスケープが ※ だったので、手で書いた ※ も同じに効いていた（案内はしていない） */
	@Test
	public void aHandWrittenKomeIsJustAKome() throws Exception
	{
		assertEquals("<p>記号※<ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("記号※｜漢字《かんじ》"));
	}

	/** 目次・表題の名前（getChapterName）にも印を残さない */
	@Test
	public void chapterNamesHaveNoMarker() throws Exception
	{
		String name = CharUtils.getChapterName(CharUtils.removeRuby(
			converter.convertGaijiChuki("章※［＃始め二重山括弧］１※［＃終わり二重山括弧］※［＃米印］", true, false)), 100, false);
		assertEquals("章《１》※", name);
	}
}
