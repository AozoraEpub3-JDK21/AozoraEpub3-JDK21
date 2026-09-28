package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.CharUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 監査項目32 の一部: U+ のコードのみの外字注記は、特殊文字（※《》｜＃）の前に
 * エスケープの※を付ける処理を通らずに抜けていた。
 * 裸の※は後ろのエスケープ済み『《』の※の偶奇をずらして本文を消し、裸の『《』はルビになる。
 */
public class AozoraEpub3ConverterGaijiCodeEscapeTest
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
		StringWriter sw = new StringWriter();
		BufferedWriter bw = new BufferedWriter(sw);
		converter.convertTextLineToEpub3(bw, src, 0, false, false);
		bw.close();
		return sw.toString().trim();
	}

	@Test
	public void コードのみの注記も特殊文字をエスケープする() throws Exception
	{
		assertEquals("※※", converter.convertGaijiChuki("※［＃U+203B］", true, true));
		assertEquals("※《", converter.convertGaijiChuki("※［＃U+300A］", true, true));
		assertEquals("※》", converter.convertGaijiChuki("※［＃u+300b］", true, true));
		assertEquals("※｜", converter.convertGaijiChuki("※［＃U+FF5C］", true, true));
		assertEquals("※＃", converter.convertGaijiChuki("※［＃U+FF03］", true, true));
		// 名前付きの注記は元から付いていた（対照）
		assertEquals("※《", converter.convertGaijiChuki("※［＃始め二重山括弧、U+300A］", true, true));
		// 特殊文字でなければ付けない
		assertEquals("葛", converter.convertGaijiChuki("※［＃U+845b］", true, true));
		// escape=false なら今までどおり付けない
		assertEquals("《", converter.convertGaijiChuki("※［＃U+300A］", false, true));
	}

	@Test
	public void 異体字セレクタ付きでも先頭の特殊文字をエスケープする() throws Exception
	{
		// 2文字以上になるので、1文字のときだけ付ける判定をすり抜けていた
		assertEquals("※《\uFE00", converter.convertGaijiChuki("※［＃U+300A-FE00］", true, true));
		assertEquals("※｜\uFE00", converter.convertGaijiChuki("※［＃U+FF5C-FE00］", true, true));
		assertEquals("<p>漢字《\uFE00かんじ》\uFE00の後</p>",
			convertLine("漢字※［＃U+300A-FE00］かんじ※［＃U+300B-FE00］の後"));
	}

	@Test
	public void 二文字の外字は両方の特殊文字をエスケープする() throws Exception
	{
		// U+XXXX-U+YYYY は2文字になる。2文字目の｜》が裸で残るとルビの判定がずれる
		assertEquals("葛※｜", converter.convertGaijiChuki("※［＃U+845B-U+FF5C］", true, true));
		assertEquals("※《※》", converter.convertGaijiChuki("※［＃U+300A-U+300B］", true, true));
		// 名前付きの注記でも、コードから得た2文字は同じ
		assertEquals("葛※｜", converter.convertGaijiChuki("※［＃「葛と縦線」、U+845B-U+FF5C］", true, true));
		assertEquals("<p>葛葛｜<ruby>漢字<rt>かんじ</rt></ruby></p>",
			convertLine("葛※［＃U+845B-U+FF5C］漢字《かんじ》"));
	}

	@Test
	public void 代替文字の中の注記はエスケープしない() throws Exception
	{
		// chuki_alt.txt の値は注記を含む。＃に※を付けると注記として読まれなくなる
		assertEquals("［＃縦中横］!!!［＃縦中横終わり］", converter.convertGaijiChuki("※［＃感嘆符三つ］", true, true));
		assertEquals("［＃小書き］こ［＃小書き終わり］", converter.convertGaijiChuki("※［＃小書き平仮名こ］", true, true));
	}

	@Test
	public void 表題の名前にエスケープの米印を残さない() throws Exception
	{
		// getChapterName はエスケープの※を外す文字を別に持っていて、＃が抜けていた
		// 表題（dc:title・ncx の表題・扉）は出力時に文字変換を通らないので ※＃ のまま出ていた
		assertEquals("章＃１", CharUtils.getChapterName(CharUtils.removeRuby(converter.convertGaijiChuki("章※［＃U+FF03］１", true, false)), 100, false));
		assertEquals("章＃１", CharUtils.getChapterName(CharUtils.removeRuby(converter.convertGaijiChuki("章※［＃井げた］１", true, false)), 100, false));
	}

	@Test
	public void コードのみの米印の後でも本文が消えない() throws Exception
	{
		assertEquals("<p>UCS※《あいう》えお</p>",
			convertLine("UCS※［＃U+203B］※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお"));
	}

	@Test
	public void コードのみの二重山括弧はルビにならない() throws Exception
	{
		assertEquals("<p>漢字《かんじ》の後</p>",
			convertLine("漢字※［＃U+300A］かんじ※［＃U+300B］の後"));
	}
}
