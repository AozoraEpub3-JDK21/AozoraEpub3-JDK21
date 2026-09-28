package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 監査項目32: ※ と 《》 の外字注記が混在すると『《』以降が行末まで消える。
 * 外字の特殊文字（※《》｜＃）は前に※をつけてエスケープし、isEscapedChar は直前の※の偶奇で判定する。
 * 裸の※が前にあると偶奇がずれて、エスケープ済みの『《』をルビ開始と誤判定する。
 */
public class EscapedSpecialCharTest
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
		converter.convertTextLineToEpub3(bw, converter.convertGaijiChuki(src, true, true), 0, false, false);
		bw.close();
		return sw.toString().trim();
	}

	@Test
	public void 裸の米印の後でも二重山括弧以降が消えない() throws Exception
	{
		// 対照: ※が1個（奇数）なら元から正しい
		assertEquals("<p>正常《あいう》えお</p>",
			convertLine("正常※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお"));
		// 裸の※ + エスケープ済み《 = ※※《（偶数）
		assertEquals("<p>破損※《あいう》えお</p>",
			convertLine("破損※※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお"));
		// 裸の※ + ※［＃米印］(※※) + エスケープ済み《 = ※※※※《（偶数）
		assertEquals("<p>破損※※《あいう》えお</p>",
			convertLine("破損※※［＃米印］※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお"));
	}

	@Test
	public void 開いたまま閉じないルビは本文を捨てずに出力する() throws Exception
	{
		// ルビ開始チェック中でない位置で『《』が開いて閉じない
		assertEquals("※《あいう》えお", converter.convertRubyText("※※《あいう※》えお").toString());
		// ルビ開始チェック中（漢字の後）の既存経路は変わらない
		assertEquals("漢字《かんじ", converter.convertRubyText("漢字《かんじ").toString());
	}

	@Test
	public void コードのみの外字注記も特殊文字をエスケープする() throws Exception
	{
		// U+ のみの注記は別の経路で変換されていて、エスケープが抜けていた
		assertEquals("※※", converter.convertGaijiChuki("※［＃U+203B］", true, true));
		assertEquals("※《", converter.convertGaijiChuki("※［＃U+300A］", true, true));
		assertEquals("※《", converter.convertGaijiChuki("※［＃始め二重山括弧、U+300A］", true, true));
		// escape=false なら今までどおり付けない
		assertEquals("《", converter.convertGaijiChuki("※［＃U+300A］", false, true));
		// 裸の※になって偶奇がずれ、行末まで消えていた
		assertEquals("<p>UCS※《あいう》えお</p>",
			convertLine("UCS※［＃U+203B］※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお"));
		// 裸の《になってルビとして扱われていた
		assertEquals("<p>漢字《かんじ》の後</p>",
			convertLine("漢字※［＃U+300A］かんじ※［＃U+300B］の後"));
	}
}
