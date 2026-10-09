package com.github.hmdev.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

import com.github.hmdev.info.BookInfo;

/**
 * ini から変換の設定を読むテスト（internal #11 の H1）。
 *
 * CLI が {@code AozoraEpub3.run} の中で読んでいたものを移したので、読み方（キーが無いときの既定値・旧名・
 * 改ページの大きさの計算）と、変換器に入れる順番・引数の位置を固定する。
 * CLI の出力が master と同じことは、PR #115 で 3 つの ini × 19 の入力の EPUB を項目ごとに比べて確かめた
 * （その比べ方は試験には入っていない。ここの升が固定するのは、読み方と入れ方）。
 */
public class ConversionSettingsTest {

	private static Properties props(String... keyValues) {
		Properties p = new Properties();
		for (int i = 0; i < keyValues.length; i += 2) p.setProperty(keyValues[i], keyValues[i + 1]);
		return p;
	}

	@Test
	public void anEmptyIniUsesTheGuiDefaults() {
		ConversionSettings s = ConversionSettings.fromProps(new Properties());
		assertEquals(64, s.maxChapterNameLength);
		assertEquals(1, s.dakutenType);
		assertEquals("", s.chapterPattern);
		// GUI の既定は「表題ページを出す（1）」「400KB で改ページする」
		assertEquals(1, s.titlePage);
		assertEquals(400 * 1024, s.forcePageBreakSize);
		assertEquals(0, s.gaijiFallbackLevel);
	}

	@Test
	public void theTitlePageIsNoneUnlessItIsWritten() {
		assertEquals(BookInfo.TITLE_NONE, ConversionSettings.fromProps(props("TitlePageWrite", "", "TitlePage", "1")).titlePage);
		assertEquals(1, ConversionSettings.fromProps(props("TitlePageWrite", "1", "TitlePage", "1")).titlePage);
	}

	@Test
	public void pageBreakSizesAreInKilobytesAndOnlyWhenEnabled() {
		ConversionSettings off = ConversionSettings.fromProps(props("PageBreak", "", "PageBreakSize", "50"));
		assertEquals(0, off.forcePageBreakSize);
		ConversionSettings on = ConversionSettings.fromProps(props(
			"PageBreak", "1", "PageBreakSize", "50",
			"PageBreakEmpty", "1", "PageBreakEmptyLine", "2", "PageBreakEmptySize", "10",
			"PageBreakChapter", "1", "PageBreakChapterSize", "5"));
		assertEquals(50 * 1024, on.forcePageBreakSize);
		assertEquals(2, on.forcePageBreakEmpty);
		assertEquals(10 * 1024, on.forcePageBreakEmptySize);
		assertEquals(1, on.forcePageBreakChapter);
		assertEquals(5 * 1024, on.forcePageBreakChapterSize);
	}

	/** GUI が書くのは MaxChapterNameLength。旧名 ChapterNameLength は、新しい名前が無いときだけ読む */
	@Test
	public void theOldChapterNameLengthKeyIsReadOnlyWithoutTheNewOne() {
		assertEquals(33, ConversionSettings.fromProps(props("ChapterNameLength", "33")).maxChapterNameLength);
		assertEquals(20, ConversionSettings.fromProps(props("MaxChapterNameLength", "20", "ChapterNameLength", "33")).maxChapterNameLength);
	}

	/** ChapterPattern=1 だけで ChapterPatternText が無い手書きの ini は、空のパターン（null を渡すと警告が出た。項目 23） */
	@Test
	public void aPatternWithoutTextIsEmpty() {
		assertEquals("", ConversionSettings.fromProps(props("ChapterPattern", "1")).chapterPattern);
		assertEquals("^第", ConversionSettings.fromProps(props("ChapterPattern", "1", "ChapterPatternText", "^第")).chapterPattern);
		assertEquals("", ConversionSettings.fromProps(props("ChapterPatternText", "^第")).chapterPattern);
	}

	@Test
	public void theGaijiFallbackLevelIsZeroUnlessEnabled() {
		assertEquals(0, ConversionSettings.fromProps(props("GaijiFallbackLevel", "2")).gaijiFallbackLevel);
		assertEquals(2, ConversionSettings.fromProps(props("GaijiFallback", "1", "GaijiFallbackLevel", "2")).gaijiFallbackLevel);
	}

	@Test
	public void flagsAreReadFromTheirOwnKeys() {
		ConversionSettings s = ConversionSettings.fromProps(props(
			"ChapterNumParenTitle", "1", "NoIllust", "1", "IvsBMP", "1", "MarkId", "1", "TocVertical", "1"));
		assertTrue(s.chapterNumParenTitle);
		assertTrue(s.noIllust);
		assertTrue(s.printIvsBMP);
		assertFalse(s.printIvsSSP);
		assertTrue(s.withMarkId);
		assertTrue(s.tocVertical);
	}

	/** 変換器が受け取った呼び出しを、名前と引数で順に記録する */
	static final class RecordingConverter extends com.github.hmdev.converter.AozoraEpub3Converter {
		final java.util.List<String> calls = new java.util.ArrayList<>();
		RecordingConverter() throws Exception {
			super(new com.github.hmdev.writer.Epub3Writer(com.github.hmdev.util.VelocityTestUtils.templateDir() + java.io.File.separator,
				com.github.hmdev.util.VelocityTestUtils.engineForTemplateSubpath("")),
				com.github.hmdev.util.VelocityTestUtils.templateDir().getParent() + java.io.File.separator);
			this.calls.clear();
		}
		@Override public void setNoIllust(boolean v) { calls.add("noIllust " + v); }
		@Override public void setWithMarkId(boolean v) { calls.add("markId " + v); }
		@Override public void setAutoYoko(boolean a, boolean b, boolean c, boolean d) { calls.add("autoYoko " + a + " " + b + " " + c + " " + d); }
		@Override public void setCharOutput(int a, boolean b, boolean c) { calls.add("charOutput " + a + " " + b + " " + c); }
		@Override public void setGaijiFallback(int a, boolean b) { calls.add("gaiji " + a + " " + b); }
		@Override public void setSpaceHyphenation(int a) { calls.add("spaceHyp " + a); }
		@Override public void setCommentPrint(boolean a, boolean b) { calls.add("comment " + a + " " + b); }
		@Override public void setRemoveEmptyLine(int a, int b) { calls.add("emptyLine " + a + " " + b); }
		@Override public void setForcePageBreak(int a, int b, int c, int d, int e) { calls.add("pageBreak " + a + " " + b + " " + c + " " + d + " " + e); }
		@Override public void setChapterLevel(int maxLength, boolean exclude, boolean nextLine, boolean section, boolean h, boolean h1, boolean h2, boolean h3,
				boolean sameLine, boolean name, boolean numOnly, boolean numTitle, boolean numParen, boolean numParenTitle, String pattern) {
			calls.add("chapter " + maxLength + " " + exclude + nextLine + section + h + h1 + h2 + h3 + sameLine + name
				+ numOnly + numTitle + numParen + numParenTitle + " " + pattern);
		}
	}

	/**
	 * 変換器に入れる順番と引数の位置（CLI が入れていた形）を固定する。値は位置ごとに変えてあるので、
	 * 同じ型の引数（改ページの 5 つ・目次の 13 の真偽値など）を入れ違えると赤になる（#115 のゲート2）
	 */
	@Test
	public void theConverterGetsEveryValueInItsOwnPositionAndOrder() throws Exception {
		RecordingConverter c = new RecordingConverter();
		ConversionSettings.fromProps(props(
			"NoIllust", "1", "MarkId", "",
			"AutoYoko", "1", "AutoYokoNum1", "", "AutoYokoNum3", "1", "AutoYokoEQ1", "",
			"DakutenType", "2", "IvsBMP", "1", "IvsSSP", "",
			"GaijiFallback", "1", "GaijiFallbackLevel", "3", "GaijiFallbackCode", "",
			"SpaceHyphenation", "4",
			"CommentPrint", "1", "CommentConvert", "",
			"RemoveEmptyLine", "5", "MaxEmptyLine", "6",
			"PageBreak", "1", "PageBreakSize", "7", "PageBreakEmpty", "1", "PageBreakEmptyLine", "8", "PageBreakEmptySize", "9",
			"PageBreakChapter", "1", "PageBreakChapterSize", "11",
			"MaxChapterNameLength", "12",
			"ChapterExclude", "1", "ChapterUseNextLine", "", "ChapterSection", "1", "ChapterH", "",
			"ChapterH1", "1", "ChapterH2", "", "ChapterH3", "1", "SameLineChapter", "",
			"ChapterName", "1", "ChapterNumOnly", "", "ChapterNumTitle", "1", "ChapterNumParen", "", "ChapterNumParenTitle", "1",
			"ChapterPattern", "1", "ChapterPatternText", "^X"
		)).applyTo(c);
		assertEquals(java.util.List.of(
			"noIllust true",
			"markId false",
			"autoYoko true false true false",
			"charOutput 2 true false",
			"gaiji 3 false",
			"spaceHyp 4",
			"comment true false",
			"emptyLine 5 6",
			"pageBreak " + (7 * 1024) + " 8 " + (9 * 1024) + " 1 " + (11 * 1024),
			"chapter 12 truefalsetruefalsetruefalsetruefalsetruefalsetruefalsetrue ^X"
		), c.calls);
	}
}
