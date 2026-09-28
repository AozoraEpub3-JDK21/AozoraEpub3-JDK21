package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 監査項目37: 前方参照注記（［＃「…」に傍点］など）の対象がエスケープ済みの》で終わると、
 * getTargetStart が》のエスケープを1つ手前の位置で判定してルビの終わりと読み、
 * 《を探して行頭より前まで戻って -1 を返し、insert で例外になって変換全体が止まっていた。
 */
public class AozoraEpub3ConverterForwardRefEscapeTest
{
	AozoraEpub3Converter converter;

	@Before
	public void setUp() throws Exception
	{
		converter = new AozoraEpub3Converter(new Epub3Writer(""), "");
		converter.bookInfo = new BookInfo(null);
		converter.vertical = true;
	}

	String replace(String src)
	{
		return converter.replaceChukiSufTag(converter.convertGaijiChuki(src, true, false));
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
	public void エスケープ済みの閉じ括弧で終わる対象でも止まらない() throws Exception
	{
		assertEquals("題名は［＃傍線］猫※》［＃傍線終わり］です。",
			replace("題名は猫※［＃終わり二重山括弧］［＃「猫※［＃終わり二重山括弧］」に傍線］です。"));
		assertEquals("閉じ括弧だけ［＃太字］※》［＃太字終わり］です。",
			replace("閉じ括弧だけ※［＃終わり二重山括弧］［＃「※［＃終わり二重山括弧］」は太字］です。"));
		assertEquals("<p>題名は<span class=\"underline\">猫》</span>です。</p>",
			convertLine("題名は猫※［＃終わり二重山括弧］［＃「猫※［＃終わり二重山括弧］」に傍線］です。"));
	}

	@Test
	public void エスケープ済みの二重山括弧で囲んだ対象の始まりがずれない() throws Exception
	{
		// 修正前は》をルビの終わりと読んで行頭側の《まで戻り、傍点が「書名は」まで掛かっていた
		assertEquals("書名は［＃傍点］※《猫※》［＃傍点終わり］です。",
			replace("書名は※［＃始め二重山括弧］猫※［＃終わり二重山括弧］［＃「※［＃始め二重山括弧］猫※［＃終わり二重山括弧］」に傍点］です。"));
	}

	@Test
	public void ルビの閉じ括弧は今までどおりルビとして飛ばす() throws Exception
	{
		assertEquals("題名は［＃傍線］猫［＃傍線終わり］です。", replace("題名は猫［＃「猫」に傍線］です。"));
		assertEquals("書名は［＃傍点］｜漢字《かんじ》［＃傍点終わり］です。", replace("書名は｜漢字《かんじ》［＃「漢字」に傍点］です。"));
	}

	@Test
	public void ルビの開き括弧が無い閉じ括弧は普通の文字として数える() throws Exception
	{
		// エスケープされていない》の前に《が無い（入力の誤り）。行頭まで広げず対象の長さで止める
		assertEquals("［＃傍点］壊れた》［＃傍点終わり］です。", replace("壊れた》［＃「壊れた》」に傍点］です。"));
		assertEquals("前文の［＃傍点］壊れた》［＃傍点終わり］です。", replace("前文の壊れた》［＃「壊れた》」に傍点］です。"));
		// 前にエスケープ済みの文字があっても、そこで止まってルビ扱いにしない
		assertEquals("米※※［＃傍点］壊れた》［＃傍点終わり］です。", replace("米※［＃米印］壊れた》［＃「壊れた》」に傍点］です。"));
		assertEquals("※《前文の［＃傍点］壊れた》［＃傍点終わり］です。", replace("※［＃始め二重山括弧］前文の壊れた》［＃「壊れた》」に傍点］です。"));
		assertEquals("米※※｜壊れた》《こわれた》です。", replace("米※［＃米印］壊れた》［＃「壊れた》」に「こわれた」のルビ］です。"));
	}

	@Test
	public void 対応する角括弧が無い閉じ角括弧でも止まらない() throws Exception
	{
		// 入力の誤り。どこに掛かるかは保証しないが、例外で変換全体を止めない
		assertEquals("［＃傍点］猫］［＃傍点終わり］です。", replace("猫］［＃「猫」に傍点］です。"));
	}

	@Test
	public void ルビの読みにエスケープ済みの文字があっても読みの中にタグを入れない() throws Exception
	{
		assertEquals("書名は［＃傍点］｜米《こめ※※》［＃傍点終わり］です。", replace("書名は｜米《こめ※［＃米印］》［＃「米」に傍点］です。"));
	}

	@Test
	public void ルビを付ける前方参照注記でもエスケープ済みの閉じ括弧で止まらない() throws Exception
	{
		assertEquals("題名は｜猫※》《ねこ》です。",
			replace("題名は猫※［＃終わり二重山括弧］［＃「猫※［＃終わり二重山括弧］」に「ねこ」のルビ］です。"));
	}
}
