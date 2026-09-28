package com.github.hmdev.converter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.BookInfo.TitleType;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 監査項目34: 縦書きの左右中央の節は横書きの親の中に縦書きのブロックを置いて出力するが、
 * そのブロックはページを超えると分割されず、はみ出した本文がページ送りで見えなくなる。
 * 行数が多い節と、1列に収まらない行のある節（列の終わりまで届くと Books は白紙のページを足し、
 * Thorium は列の終わりの文字を切る）は事前走査で数えて、従来の表組み（PAGE_MIDDLE_TABLE）で出力する。
 */
public class AozoraEpub3ConverterLongMiddleTest
{
	AozoraEpub3Converter converter;

	@Before
	public void setUp() throws Exception
	{
		converter = new AozoraEpub3Converter(new Epub3Writer(""), "");
		converter.vertical = true;
		//見出し注記の表を作る (CLI・GUI は変換前に必ず呼ぶ)
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
	}

	BookInfo scan(String... lines) throws Exception
	{
		String text = String.join("\n", lines) + "\n";
		return converter.getBookInfo(new File("test.txt"), new BufferedReader(new StringReader(text)),
				new ImageInfoReader(true, new File("test.txt")), TitleType.NONE, false);
	}

	static String repeat(String s, int n)
	{
		StringBuilder buf = new StringBuilder();
		for (int i=0; i<n; i++) buf.append(s);
		return buf.toString();
	}

	@Test
	public void 章題だけの中扉は短い() throws Exception
	{
		BookInfo bookInfo = scan(
				"本文",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから柱］作品名［＃ここで柱終わり］",
				"［＃３字下げ］［＃大見出し］第一章［＃大見出し終わり］",
				"［＃改ページ］",
				"本文");
		assertFalse(bookInfo.isLongMiddleLine(2));
	}

	@Test
	public void 一行の文字数の境目() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS),
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(3));
	}

	@Test
	public void 行数の境目() throws Exception
	{
		String[] lines = new String[2 + AozoraEpub3Converter.MIDDLE_LONG_LINES*2 + 1 + 2];
		int n = 0;
		lines[n++] = "［＃ページの左右中央］";
		for (int i=0; i<AozoraEpub3Converter.MIDDLE_LONG_LINES; i++) lines[n++] = "あ";
		lines[n++] = "［＃ページの左右中央］";
		int second = n-1;
		for (int i=0; i<AozoraEpub3Converter.MIDDLE_LONG_LINES+1; i++) lines[n++] = "い";
		lines[n++] = "［＃改ページ］";
		lines[n++] = "本文";
		BookInfo bookInfo = scan(java.util.Arrays.copyOf(lines, n));
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(second));
	}

	@Test
	public void 注記とルビと空白は数えない() throws Exception
	{
		int half = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS / 2;
		// ルビ・注記・空白を数えると境目を超える量。表示される文字は境目ちょうど
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃ここから５字下げ］",
				repeat("漢《かんじ》", half) + repeat("　字［＃「字」に傍点］", half),
				"［＃ここで字下げ終わり］",
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 注記の後ろの同じ行の本文も数える() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］" + repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 改ページの前の同じ行の本文は次の節に数えない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題" + "［＃改ページ］" + repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1));
		assertFalse(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 短い行は注記をまたいで一行に数える() throws Exception
	{
		int half = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS / 2;
		// 注記で区切られていても同じ行なら1列に並ぶ
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", half) + "［＃傍点］" + repeat("い", half+1) + "［＃傍点終わり］",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 長い節の判定は次の節に持ち越さない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃ページの左右中央］",
				"［＃挿絵（fig01.png）入る］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"題",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(2));
		assertFalse(bookInfo.isLongMiddleLine(5));
	}

	@Test
	public void 画像のある節は長い側に倒す() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃挿絵（fig01.png）入る］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"<img src=\"fig02.png\"/>",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(3));
	}

	@Test
	public void 改ページが無いまま終わる節も数える() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1));
		assertTrue(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 次の左右中央で前の節を閉じる() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題",
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(2));
	}

	@Test
	public void 長い節の左右中央は表組みの改ページになる() throws Exception
	{
		converter.bookInfo = new BookInfo(null);
		converter.bookInfo.addLongMiddleLine(5);
		StringWriter sw = new StringWriter();
		BufferedWriter bw = new BufferedWriter(sw);
		converter.convertTextLineToEpub3(bw, "［＃ページの左右中央］", 5, false, false);
		assertSame(AozoraEpub3Converter.pageBreakMiddleTable, converter.pageBreakTrigger);
		converter.pageBreakTrigger = null;
		converter.convertTextLineToEpub3(bw, "［＃ページの左右中央］", 6, false, false);
		assertSame(AozoraEpub3Converter.pageBreakMiddle, converter.pageBreakTrigger);
	}
}
