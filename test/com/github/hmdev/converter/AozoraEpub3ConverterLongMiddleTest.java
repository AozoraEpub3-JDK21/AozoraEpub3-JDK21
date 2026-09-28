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
	public void 注記とルビは数えない() throws Exception
	{
		int half = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS / 2;
		// ルビ・注記を数えると境目を超える量。表示される文字は境目ちょうど
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("漢《かんじ》", half) + repeat("｜字《じ》［＃「字」に傍点］", half),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 全角の空白も一字に数える() throws Exception
	{
		int half = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS / 2;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題" + repeat("　", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS) + "名",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("　", half+1) + repeat("字", half),
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("字", half) + repeat("　", half+1),
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertTrue("行頭の空白も数える", bookInfo.isLongMiddleLine(3));
		assertTrue("行末の空白も数える", bookInfo.isLongMiddleLine(6));
	}

	@Test
	public void 間の空行は一列に数え前後の空行は数えない() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINES;
		String[] between = new String[n + 3];
		int i = 0;
		between[i++] = "［＃ページの左右中央］";
		between[i++] = "題";
		for (int k=0; k<n-1; k++) between[i++] = "";
		between[i++] = "名";
		between[i++] = "［＃改ページ］";
		// 題・空行 n-1・名 で n+1 列
		assertTrue(scan(between).isLongMiddleLine(0));

		String[] edges = new String[n*2 + 4];
		i = 0;
		edges[i++] = "［＃ページの左右中央］";
		for (int k=0; k<n; k++) edges[i++] = "";
		edges[i++] = "題";
		for (int k=0; k<n; k++) edges[i++] = "";
		edges[i++] = "［＃改ページ］";
		edges[i++] = "本文";
		assertFalse("前後の空行は出力されない", scan(java.util.Arrays.copyOf(edges, i)).isLongMiddleLine(0));
	}

	@Test
	public void 字下げの字数も数える() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃３字下げ］［＃大見出し］" + repeat("章", n-3) + "［＃大見出し終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃３字下げ］［＃大見出し］" + repeat("章", n-2) + "［＃大見出し終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］",
				repeat("字", n-10),
				"［＃ここで字下げ終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］",
				repeat("字", n-9),
				"［＃ここで字下げ終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("字", n),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(3));
		assertFalse(bookInfo.isLongMiddleLine(6));
		assertTrue(bookInfo.isLongMiddleLine(11));
		assertFalse("字下げの終わりの後は数えない", bookInfo.isLongMiddleLine(16));
	}

	@Test
	public void 同じ行のブロックの字下げも数える() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］" + repeat("字", n-9),
				"［＃ここで字下げ終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］" + repeat("字", n-10),
				"［＃ここで字下げ終わり］",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertFalse(bookInfo.isLongMiddleLine(4));
	}

	@Test
	public void 下寄せと大きな文字と改行のある節は表組みに倒す() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"献辞",
				"［＃地付き］名前",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃地から２字上げ］名前",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃２段階大きな文字］題［＃大きな文字終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"題［＃改行］名",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"題",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(4));
		assertTrue(bookInfo.isLongMiddleLine(7));
		assertTrue("改行の注記", bookInfo.isLongMiddleLine(10));
		assertFalse(bookInfo.isLongMiddleLine(13));
	}

	@Test
	public void 改ページの次が画像単ページなら表組みに倒す() throws Exception
	{
		// 表紙に移す画像の前の改ページは変換時に省かれ、後ろの本文が左右中央の節に入る
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"献辞",
				"［＃改ページ］",
				"［＃挿絵（cover.jpg）入る］",
				"［＃改ページ］",
				"本文",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"献辞",
				"［＃改ページ］",
				"本文");
		assertTrue(bookInfo.isImageSectionLine(3));
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertFalse(bookInfo.isLongMiddleLine(7));
	}

	@Test
	public void 字数の読めない字下げは表組みに倒し変換は止めない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃１２３４５６７８９０１字下げ］本文",
				"［＃ページの左右中央］",
				"［＃１２３４５６７８９０１字下げ］題",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃3字下げ］題",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから２字下げ、折り返して４字下げ］",
				"題",
				"［＃ここで字下げ終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから２字下げ、８字詰め］",
				"題",
				"［＃ここで字下げ終わり］",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(1));
		assertTrue("半角の数字", bookInfo.isLongMiddleLine(4));
		assertTrue("折り返して", bookInfo.isLongMiddleLine(7));
		assertTrue("字詰め", bookInfo.isLongMiddleLine(12));
	}

	@Test
	public void 字下げは改ページと字下げ終わりの別の書き方で閉じる() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ここから１０字下げ］",
				"閉じ忘れた字下げ",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("題", n-5),
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］",
				"字",
				"［＃ここで字下げ、改行天付き終わり］",
				repeat("題", n-5),
				"［＃改ページ］");
		assertFalse("改ページで閉じる", bookInfo.isLongMiddleLine(3));
		assertFalse("ここで字下げ…終わり で閉じる", bookInfo.isLongMiddleLine(6));
	}

	@Test
	public void 出力しないコメントの中の空行は数えない() throws Exception
	{
		String[] lines = new String[AozoraEpub3Converter.MIDDLE_LONG_LINES + 8];
		int i = 0;
		lines[i++] = "［＃ページの左右中央］";
		lines[i++] = "題";
		lines[i++] = "-------------------------------------------------------";
		for (int k=0; k<AozoraEpub3Converter.MIDDLE_LONG_LINES; k++) lines[i++] = "";
		lines[i++] = "-------------------------------------------------------";
		lines[i++] = "名";
		lines[i++] = "［＃改ページ］";
		assertFalse(scan(java.util.Arrays.copyOf(lines, i)).isLongMiddleLine(0));
	}

	@Test
	public void 行の途中で始まる節は前の文字を数えない() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題",
				"ああ［＃ページの左右中央］" + repeat("い", n-1),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(2));
	}

	@Test
	public void サロゲートペアは一字に数える() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("𠮟", n),
				"［＃改ページ］");
		assertFalse(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 括弧の後ろに点のある注記は画像とみなさない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題［＃（注）. ］",
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
