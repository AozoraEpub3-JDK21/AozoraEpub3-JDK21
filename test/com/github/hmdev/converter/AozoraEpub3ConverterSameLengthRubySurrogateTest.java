package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/** 同じ長さのルビを 1 字ずつ振るとき、4バイト文字の対と異体字セレクタを割らない（監査項目 33） */
public class AozoraEpub3ConverterSameLengthRubySurrogateTest
{
	/** 𠀋 U+2000B */
	static final String SIP = "𠀋";
	static final String VS1 = "\uFE00";
	/** U+E0100 */
	static final String IVS17 = "\uDB40\uDD00";

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
		String result = out.toString().strip();
		assertNoLoneSurrogate(result);
		return result;
	}

	/** 上位サロゲートの直後には必ず下位サロゲートがあり、下位サロゲートの直前には必ず上位サロゲートがある */
	static void assertNoLoneSurrogate(String s)
	{
		for (int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isHighSurrogate(c))
				assertTrue("単独の上位サロゲート at "+i+": "+s, i+1 < s.length() && Character.isLowSurrogate(s.charAt(i+1)));
			if (Character.isLowSurrogate(c))
				assertTrue("単独の下位サロゲート at "+i+": "+s, i > 0 && Character.isHighSurrogate(s.charAt(i-1)));
		}
	}

	/** 𠀋 は 1 字、読みは 2 字なので長さが合わず、ルビは一つにまとめる（master では対が 2 つの rt に割れていた） */
	@Test
	public void aSingleSupplementaryCharWithTwoReadingCharsGetsOneRuby() throws Exception
	{
		assertEquals("<p><ruby>"+SIP+"<rt>ああ</rt></ruby></p>", convertLine(SIP+"《ああ》"));
	}

	/** 2 字の 4バイト文字には 1 字ずつ、対ごと振る */
	@Test
	public void twoSupplementaryCharsGetOneRubyEach() throws Exception
	{
		assertEquals("<p><ruby>"+SIP+"<rt>あ</rt>"+SIP+"<rt>あ</rt></ruby></p>", convertLine(SIP+SIP+"《ああ》"));
	}

	/** BMP の字と 4バイト文字が混ざっても字ごとに振る */
	@Test
	public void aMixedBaseGetsOneRubyPerCodePoint() throws Exception
	{
		assertEquals("<p><ruby>漢<rt>あ</rt>"+SIP+"<rt>あ</rt></ruby></p>", convertLine("漢"+SIP+"《ああ》"));
		assertEquals("<p><ruby>"+SIP+"<rt>あ</rt>漢<rt>あ</rt></ruby></p>", convertLine("｜"+SIP+"漢《ああ》"));
	}

	/** サロゲートもセレクタも無い親文字は master と同じ出力 */
	@Test
	public void plainBmpBasesAreUnchanged() throws Exception
	{
		assertEquals("<p><ruby>漢<rt>あ</rt>字<rt>あ</rt></ruby></p>", convertLine("漢字《ああ》"));
		assertEquals("<p><ruby>々<rt>あ</rt>々<rt>あ</rt></ruby></p>", convertLine("々々《ああ》"));
		//GaijiFallbackTest の濁点の例（既定の設定）
		assertEquals("<p><ruby>〻<rt>あ</rt>゛<rt>あ</rt></ruby></p>", convertLine("〻゛《ああ》"));
		assertEquals("<p><ruby>漢字<rt>かんじ</rt></ruby></p>", convertLine("漢字《かんじ》"));
	}

	/** 異体字セレクタは基底字と同じ rt の前に置き、セレクタ自身には振らない */
	@Test
	public void aVariationSelectorStaysWithItsBase() throws Exception
	{
		converter.printIvsBMP = true;
		assertEquals("<p><ruby>葛"+VS1+"<rt>あ</rt>葛<rt>あ</rt></ruby></p>", convertLine("葛"+VS1+"葛《ああ》"));
		//セレクタ付きの 1 字に 2 字の読みは長さが合わない（master ではセレクタに rt が付いていた）
		assertEquals("<p><ruby>葛"+VS1+"<rt>ああ</rt></ruby></p>", convertLine("葛"+VS1+"《ああ》"));
		//既定（printIvsBMP=false）ではセレクタを出さないが、区切りは同じ
		converter.printIvsBMP = false;
		assertEquals("<p><ruby>葛<rt>あ</rt>葛<rt>あ</rt></ruby></p>", convertLine("葛"+VS1+"葛《ああ》"));
	}

	/** U+E0100～ の IVS（サロゲートの対）も基底字から離さない */
	@Test
	public void aSupplementaryIvsStaysWithItsBase() throws Exception
	{
		assertEquals("<p><ruby><span class=\"upr\">葛"+IVS17+"</span><rt>あ</rt>葛<rt>あ</rt></ruby></p>",
				convertLine("葛"+IVS17+"葛《ああ》"));
		assertEquals("<p><ruby><span class=\"upr\">"+SIP+IVS17+"</span><rt>あ</rt>"+SIP+"<rt>あ</rt></ruby></p>",
				convertLine(SIP+IVS17+SIP+"《ああ》"));
	}

	/** 基底字の無いセレクタは 1 字に数えられないので、ルビを一つにまとめる（セレクタに rt を付けない。中身はまとめた場合の出力のまま） */
	@Test
	public void aLeadingSelectorFallsBackToOneRuby() throws Exception
	{
		assertEquals("<p><ruby>"+VS1+"葛<rt>ああ</rt></ruby></p>", convertLine("｜"+VS1+"葛《ああ》"));
	}

	@Test
	public void rubyBaseUnitsSplitsByCharacter()
	{
		char[] ch = ("漢"+SIP+"葛"+VS1+SIP+IVS17+"字").toCharArray();
		int[] units = AozoraEpub3Converter.rubyBaseUnits(ch, 0, ch.length);
		org.junit.Assert.assertArrayEquals(new int[]{0, 1, 3, 5, 9, 10}, units);
		assertNull(AozoraEpub3Converter.rubyBaseUnits("\uD840漢".toCharArray(), 0, 2));
		assertNull(AozoraEpub3Converter.rubyBaseUnits("\uDC0B漢".toCharArray(), 0, 2));
		assertNull(AozoraEpub3Converter.rubyBaseUnits((VS1+"漢").toCharArray(), 0, 2));
		//範囲の端で割れている対も 1 字ずつ振らない
		assertNull(AozoraEpub3Converter.rubyBaseUnits(("漢"+SIP).toCharArray(), 0, 2));
	}

	/** 崩れたルビ（｜で親文字の始まりだけが動き、範囲が逆になる）でも変換は止まらない */
	@Test
	public void aReversedRubyRangeDoesNotStopTheConversion() throws Exception
	{
		//読みが同じ字の繰り返し（｜｜）なので、1 字ずつの判定まで届き、範囲が逆のまま字の区切りを作ろうとする
		String out = convertLine("漢《｜｜》");
		org.junit.Assert.assertTrue(out, out.contains("漢"));
		org.junit.Assert.assertTrue(convertLine("漢《あ｜字》").contains("漢"));
	}
}
