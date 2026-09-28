package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JTextArea;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 監査項目38: 前方参照注記の直後のルビが閉じていないと、replaceChukiSufTag が
 * indexOf("》") の -1 を確かめずに substring して例外になり、前走査で起きるので変換全体が止まっていた。
 * エスケープ済みの ※》 もルビの終わりと読んで、傍点がルビの読みの中に入っていた。
 */
public class AozoraEpub3ConverterUnclosedRubyTest
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
	public void 注記の後ろの閉じていないルビで止まらず本文も残す() throws Exception
	{
		assertEquals("［＃傍点］猫［＃傍点終わり］《ねこ", replace("猫［＃「猫」に傍点］《ねこ"));
		assertEquals("<p><span class=\"sesame\">猫</span>《ねこ</p>", convertLine("猫［＃「猫」に傍点］《ねこ"));
	}

	@Test
	public void 注記の後ろのルビのエスケープ済みの閉じ括弧を終わりと読まない() throws Exception
	{
		assertEquals("［＃傍点］猫《ね※》こ》［＃傍点終わり］です。",
			replace("猫［＃「猫」に傍点］《ね※［＃終わり二重山括弧］こ》です。"));
		assertEquals("<p><span class=\"sesame\"><ruby>猫<rt>ね》こ</rt></ruby></span>です。</p>",
			convertLine("猫［＃「猫」に傍点］《ね※［＃終わり二重山括弧］こ》です。"));
	}

	@Test
	public void 閉じていないルビの後ろのルビや注記を拾わない() throws Exception
	{
		// 後ろのルビの》を拾って、別のルビや注記ごと注記の前へ動かしていた（生の注記が漏れる・入れ子が壊れる）
		assertEquals("［＃傍点］猫［＃傍点終わり］《ねこ　［＃傍点］犬《いぬ》［＃傍点終わり］です",
			replace("猫［＃「猫」に傍点］《ねこ　犬［＃「犬」に傍点］《いぬ》です"));
		assertEquals("［＃傍点］猫［＃傍点終わり］《ねこ　犬《いぬ》", replace("猫［＃「猫」に傍点］《ねこ　犬《いぬ》"));
	}

	@Test
	public void 読みの中に注記がある閉じたルビは今までどおり注記の前に移す() throws Exception
	{
		// 小書きなどの外字は変換で［＃小書き］こ［＃小書き終わり］になって読みの中に入る
		assertEquals("<p><span class=\"sesame\"><ruby>猫<rt>ね<span class=\"kogaki\">こ</span></rt></ruby></span>です。</p>",
			convertLine("猫［＃「猫」に傍点］《ね※［＃小書き平仮名こ］》です。"));
	}

	@Test
	public void 空のルビの閉じ括弧を飛ばさない() throws Exception
	{
		assertEquals("［＃傍点］猫《》［＃傍点終わり］です。犬《いぬ》", replace("猫［＃「猫」に傍点］《》です。犬《いぬ》"));
	}

	@Test
	public void 閉じたルビは今までどおり注記の前に移す() throws Exception
	{
		assertEquals("<p><span class=\"sesame\"><ruby>猫<rt>ねこ</rt></ruby></span>です。</p>",
			convertLine("猫［＃「猫」に傍点］《ねこ》です。"));
	}

	@Test
	public void 親文字の無い二重山括弧はルビにせず文字として出す() throws Exception
	{
		// 修正前はルビとして開き、行末や後ろの《》｜で《以降を捨てていた
		assertEquals("<p>です。《ねこ</p>", convertLine("です。《ねこ"));
		assertEquals("<p>です。《ねこ》</p>", convertLine("です。《ねこ》"));
		assertEquals("<p>「漢字」《かんじ》</p>", convertLine("「漢字」《かんじ》"));
		assertEquals("<p>です。《ねこ　<ruby>犬<rt>いぬ</rt></ruby></p>", convertLine("です。《ねこ　犬《いぬ》"));
		// 親文字がある形は今までどおり（対照）
		assertEquals("<p>猫《ねこ</p>", convertLine("猫《ねこ"));
		assertEquals("<p><ruby>漢字<rt>かんじ</rt></ruby>の後</p>", convertLine("漢字《かんじ》の後"));
		assertEquals("<p><ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("｜漢字《かんじ》"));
		assertEquals("<p><ruby>ひらがな<rt>ひらがな</rt></ruby></p>", convertLine("ひらがな《ひらがな》"));
	}

	@Test
	public void 親文字の無い二重山括弧は警告を出す() throws Exception
	{
		// 文字として出すようにしても、原稿の誤りに気づく手がかりは残す
		JTextArea log = new JTextArea();
		LogAppender.setTextArea(log);
		try {
			convertLine("です。《ねこ》");
		} finally {
			LogAppender.setTextArea(null);
		}
		assertTrue(log.getText(), log.getText().contains("[WARN] ルビ開始文字無し"));
	}

	@Test
	public void 閉じていないルビの後ろにルビや注記があっても壊さない() throws Exception
	{
		assertEquals("<p><span class=\"sesame\">猫</span>《ねこ　<span class=\"sesame\"><ruby>犬<rt>いぬ</rt></ruby></span>です</p>",
			convertLine("猫［＃「猫」に傍点］《ねこ　犬［＃「犬」に傍点］《いぬ》です"));
		assertEquals("<p><span class=\"sesame\">猫</span>《ねこ　<ruby>犬<rt>いぬ</rt></ruby></p>",
			convertLine("猫［＃「猫」に傍点］《ねこ　犬［＃「犬」に「いぬ」のルビ］"));
	}
}
