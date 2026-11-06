package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
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
 * 行数が多い節は事前走査で数えて、従来の表組み（PAGE_MIDDLE_TABLE）で出力する。
 * 1列に収まらない行のある節（列の終わりまで届くと Books は白紙のページを足し、Thorium は列の終わりの文字を切る）は、
 * ブロックの高さを決めて折り返す（PAGE_MIDDLE_WRAP。internal #8）。折り返した列が多すぎれば表組み。
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

	/** 1列に収まらないと判定された (折り返す、か表組みに倒す)。字数の数え方の試験はこれで見る */
	static boolean overLong(BookInfo bookInfo, int lineNum)
	{
		return bookInfo.isWrapMiddleLine(lineNum) || bookInfo.isLongMiddleLine(lineNum);
	}

	/** 事前走査が控えた、その節を表組みに倒した理由 */
	static java.util.Set<String> reasons(BookInfo bookInfo, int lineNum)
	{
		for (BookInfo.MiddleSection m : bookInfo.getMiddleSections()) if (m.line == lineNum) return m.reasons;
		throw new AssertionError("節が無い: " + lineNum);
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
		assertFalse(overLong(bookInfo, 0));
		assertTrue(overLong(bookInfo, 3));
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
		assertFalse(overLong(bookInfo, 0));
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
		assertTrue(overLong(bookInfo, 0));
		assertTrue("行頭の空白も数える", overLong(bookInfo, 3));
		assertTrue("行末の空白も数える", overLong(bookInfo, 6));
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
		assertFalse(overLong(bookInfo, 0));
		assertTrue(overLong(bookInfo, 3));
		assertFalse(overLong(bookInfo, 6));
		assertTrue(overLong(bookInfo, 11));
		assertFalse("字下げの終わりの後は数えない", overLong(bookInfo, 16));
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
		assertTrue(overLong(bookInfo, 0));
		assertFalse(overLong(bookInfo, 4));
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
		assertTrue("brタグの改行", scan("［＃ページの左右中央］", "題<br/>名", "［＃改ページ］").isLongMiddleLine(0));
		assertTrue("文字のまま出力されるタグ", scan("［＃ページの左右中央］", "題<small>名</small>", "［＃改ページ］").isLongMiddleLine(0));
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
		assertFalse("改ページで閉じる", overLong(bookInfo, 3));
		assertFalse("ここで字下げ…終わり で閉じる", overLong(bookInfo, 6));
		assertFalse("字下げ終わり で閉じる", overLong(scan(
				"［＃ページの左右中央］",
				"［＃ここから１０字下げ］",
				"字",
				"［＃字下げ終わり］",
				repeat("題", n-5),
				"［＃改ページ］"), 0));
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
	public void 出力するコメントのある節は表組みに倒す() throws Exception
	{
		String[] lines = {
				"［＃ページの左右中央］",
				"題",
				"-------------------------------------------------------",
				"［＃改ページ］",
				"コメントの本文",
				"-------------------------------------------------------",
				"［＃改ページ］"};
		// コメントを出力しない設定では、コメントは節に入らない
		assertFalse(scan(lines).isLongMiddleLine(0));
		// 変換せずにそのまま出力する設定では、コメントの中の［＃改ページ］は改ページにならず本文が節に入る
		converter.setCommentPrint(true, false);
		assertTrue(scan(lines).isLongMiddleLine(0));
		// 変換して出力する設定でも、区切り線ごと出力されるので数えずに表組みに倒す
		converter.setCommentPrint(true, true);
		assertTrue(scan(lines).isLongMiddleLine(0));
		// 変換するコメントの中で始まる左右中央の節も、表組みに倒す
		converter.setCommentPrint(true, true);
		assertTrue(scan("本文",
				"-------------------------------------------------------",
				"［＃ページの左右中央］",
				"題",
				"-------------------------------------------------------",
				"［＃改ページ］").isLongMiddleLine(2));
		// 閉じないまま終わるコメントの中で始まる節も
		assertTrue(scan("本文",
				"-------------------------------------------------------",
				"［＃ページの左右中央］",
				"題").isLongMiddleLine(2));
		// 中身の無いコメントでも、区切り線が出力される
		converter.setCommentPrint(true, false);
		assertTrue(scan("［＃ページの左右中央］", "題",
				"-------------------------------------------------------",
				"-------------------------------------------------------",
				"［＃改ページ］").isLongMiddleLine(0));
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
		assertFalse(overLong(bookInfo, 2));
	}

	@Test
	public void エスケープされた縦線は一字に数える() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		// エスケープされた｜は表示される｜。印は数えないので 1 字に数える（監査項目 32）
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("字", n-1) + "※［＃縦線］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				repeat("字", n) + "※［＃縦線］",
				"［＃改ページ］");
		assertFalse(overLong(bookInfo, 0));
		assertTrue(overLong(bookInfo, 3));
	}

	@Test
	public void サロゲートペアは一字に数える() throws Exception
	{
		int n = AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS;
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("𠮟", n),
				"［＃改ページ］");
		assertFalse(overLong(bookInfo, 0));
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
		assertTrue(overLong(bookInfo, 0));
	}

	@Test
	public void 改ページの前の同じ行の本文は次の節に数えない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"題" + "［＃改ページ］" + repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1));
		assertFalse(overLong(bookInfo, 0));
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
		assertTrue(overLong(bookInfo, 0));
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
		assertTrue(bookInfo.isWrapMiddleLine(0));
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertTrue(bookInfo.isLongMiddleLine(2));
		assertFalse(overLong(bookInfo, 5));
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
		assertTrue(overLong(bookInfo, 0));
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
		assertFalse(overLong(bookInfo, 0));
		assertTrue(overLong(bookInfo, 2));
	}

	@Test
	public void 長い行だけの節は表組みにせず折り返す() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃３字下げ］［＃大見出し］第二章　" + repeat("長", 34) + "［＃大見出し終わり］",
				"［＃改ページ］",
				"［＃ページの左右中央］",
				"題",
				"［＃改ページ］");
		assertTrue(bookInfo.isWrapMiddleLine(0));
		assertFalse(bookInfo.isLongMiddleLine(0));
		assertFalse("短い節は折り返さない", bookInfo.isWrapMiddleLine(3));
	}

	@Test
	public void 折り返した列が多すぎれば表組み() throws Exception
	{
		int c = AozoraEpub3Converter.MIDDLE_WRAP_COLUMN_CHARS;
		int max = AozoraEpub3Converter.MIDDLE_LONG_LINES;
		// 長い行 (3 列) と短い行で、ちょうど max 列
		String[] fit = new String[max - 3 + 3];
		int i = 0;
		fit[i++] = "［＃ページの左右中央］";
		fit[i++] = repeat("長", c*3);
		for (int k=0; k<max-3; k++) fit[i++] = "短";
		fit[i++] = "［＃改ページ］";
		BookInfo b1 = scan(fit);
		assertTrue(b1.isWrapMiddleLine(0));
		assertFalse(b1.isLongMiddleLine(0));
		// 1 字増やすと長い行が 4 列になり max を超える (行数は max 以下のまま)
		fit[1] = repeat("長", c*3 + 1);
		BookInfo b2 = scan(fit);
		assertTrue(b2.isLongMiddleLine(0));
		assertFalse("表組みの節には折り返す印を付けない", b2.isWrapMiddleLine(0));
		assertTrue(reasons(b2, 0).toString(), reasons(b2, 0).contains("wrap-columns>" + max));
	}

	@Test
	public void 折り返す節では間の空行と短い行も列に数える() throws Exception
	{
		int c = AozoraEpub3Converter.MIDDLE_WRAP_COLUMN_CHARS;
		int max = AozoraEpub3Converter.MIDDLE_LONG_LINES;
		// 長い行 2 列 + 空行 (max-3) + 短い行 1 列 = max 列
		String[] lines = new String[max + 3];
		int i = 0;
		lines[i++] = "［＃ページの左右中央］";
		lines[i++] = repeat("長", c*2);
		for (int k=0; k<max-3; k++) lines[i++] = "";
		lines[i++] = "短";
		lines[i++] = "［＃改ページ］";
		BookInfo fit = scan(java.util.Arrays.copyOf(lines, i));
		assertTrue(fit.isWrapMiddleLine(0));
		assertFalse(fit.isLongMiddleLine(0));
		// 空行を 1 つ足すと max を超える
		String[] more = new String[i + 1];
		System.arraycopy(lines, 0, more, 0, 2);
		more[2] = "";
		System.arraycopy(lines, 2, more, 3, i - 2);
		BookInfo over = scan(more);
		assertTrue(over.isLongMiddleLine(0));
		assertTrue(reasons(over, 0).toString(), reasons(over, 0).contains("wrap-columns>" + max));
	}

	@Test
	public void 同じ行の改ページで閉じる節にも長い行の列を数える() throws Exception
	{
		int c = AozoraEpub3Converter.MIDDLE_WRAP_COLUMN_CHARS;
		int max = AozoraEpub3Converter.MIDDLE_LONG_LINES;
		String[] lines = new String[max + 2];
		int i = 0;
		lines[i++] = "［＃ページの左右中央］";
		for (int k=0; k<max-1; k++) lines[i++] = "短";
		// この行の 2 列で max を超える。改ページは同じ行
		lines[i++] = repeat("長", c*2) + "［＃改ページ］";
		BookInfo bookInfo = scan(java.util.Arrays.copyOf(lines, i));
		assertTrue(bookInfo.isLongMiddleLine(0));
		// 1 列に収まらない行が改ページの前にあれば、折り返す節になる
		BookInfo b2 = scan("［＃ページの左右中央］", repeat("長", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1) + "［＃改ページ］", "本文");
		assertTrue(b2.isWrapMiddleLine(0));
		assertFalse(b2.isLongMiddleLine(0));
	}

	@Test
	public void 長い行と画像のある節は表組み() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃挿絵（fig01.png）入る］",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertFalse(bookInfo.isWrapMiddleLine(0));
	}

	@Test
	public void 長い行の節も改ページの次が画像単ページなら折り返さない() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("あ", AozoraEpub3Converter.MIDDLE_LONG_LINE_CHARS+1),
				"［＃改ページ］",
				"［＃挿絵（cover.jpg）入る］",
				"［＃改ページ］",
				"本文");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertFalse(bookInfo.isWrapMiddleLine(0));
	}

	@Test
	public void 字下げは折り返した列ごとに場所を取る() throws Exception
	{
		int max = AozoraEpub3Converter.MIDDLE_LONG_LINES;
		// 字下げの無い 30 字は 2 列、１０字下げの 30 字は 1 列に 6 字で 5 列。短い行 6 列を足すと 8 列と 11 列
		String[] plain = new String[max];
		int i = 0;
		plain[i++] = "［＃ページの左右中央］";
		plain[i++] = repeat("長", 30);
		for (int k=0; k<6; k++) plain[i++] = "短";
		plain[i++] = "［＃改ページ］";
		BookInfo b1 = scan(java.util.Arrays.copyOf(plain, i));
		assertTrue(b1.isWrapMiddleLine(0));
		plain[1] = "［＃１０字下げ］" + repeat("長", 30);
		BookInfo b2 = scan(java.util.Arrays.copyOf(plain, i));
		assertTrue(b2.isLongMiddleLine(0));
		assertTrue(reasons(b2, 0).toString(), reasons(b2, 0).contains("wrap-columns>" + max));
		// ブロックの字下げも同じ
		BookInfo b3 = scan("［＃ページの左右中央］", "［＃ここから１０字下げ］", repeat("長", 30),
				"短", "短", "短", "短", "短", "短", "［＃ここで字下げ終わり］", "［＃改ページ］");
		assertTrue(b3.isLongMiddleLine(0));
	}

	@Test
	public void 字下げが大きく1列に字が入らない節は表組み() throws Exception
	{
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				"［＃２５字下げ］献辞",
				"［＃改ページ］");
		assertTrue(bookInfo.isLongMiddleLine(0));
		assertFalse(bookInfo.isWrapMiddleLine(0));
		int c = AozoraEpub3Converter.MIDDLE_WRAP_COLUMN_CHARS, min = AozoraEpub3Converter.MIDDLE_WRAP_MIN_CHARS;
		assertEquals(1, AozoraEpub3Converter.wrapColumns(c, 0));
		assertEquals(2, AozoraEpub3Converter.wrapColumns(c + 1, 0));
		assertEquals("字下げを除いた字で割る", 2, AozoraEpub3Converter.wrapColumns(3 + (c - 3) + 1, 3));
		assertEquals(AozoraEpub3Converter.MIDDLE_LONG_LINES + 1, AozoraEpub3Converter.wrapColumns(c + 1, c - min + 1));
		assertEquals(2, AozoraEpub3Converter.wrapColumns(c + 1, c - min));
		assertEquals("字下げの横に収まる行は 1 列", 1, AozoraEpub3Converter.wrapColumns(c, c - min + 1));
		assertEquals(1, AozoraEpub3Converter.wrapColumns(c, c - 1));
	}

	@Test
	public void 字下げの大きい短い行があっても長い行の節は折り返す() throws Exception
	{
		assertEquals("１３字下げの前提", 16, AozoraEpub3Converter.MIDDLE_WRAP_COLUMN_CHARS);
		// 献辞は字下げの横の 1 列に収まる
		BookInfo bookInfo = scan(
				"［＃ページの左右中央］",
				repeat("長", 30),
				"［＃１３字下げ］献辞",
				"［＃改ページ］");
		assertTrue(reasons(bookInfo, 0).toString(), bookInfo.isWrapMiddleLine(0));
		assertFalse(bookInfo.isLongMiddleLine(0));
	}

	@Test
	public void 折り返す節の左右中央は折り返しの改ページになる() throws Exception
	{
		converter.bookInfo = new BookInfo(null);
		converter.bookInfo.addWrapMiddleLine(5);
		converter.bookInfo.addWrapMiddleLine(6);
		converter.bookInfo.addLongMiddleLine(6);
		StringWriter sw = new StringWriter();
		BufferedWriter bw = new BufferedWriter(sw);
		converter.convertTextLineToEpub3(bw, "［＃ページの左右中央］", 5, false, false);
		assertSame(AozoraEpub3Converter.pageBreakMiddleWrap, converter.pageBreakTrigger);
		converter.pageBreakTrigger = null;
		converter.convertTextLineToEpub3(bw, "［＃ページの左右中央］", 6, false, false);
		assertSame("表組みが先", AozoraEpub3Converter.pageBreakMiddleTable, converter.pageBreakTrigger);
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
