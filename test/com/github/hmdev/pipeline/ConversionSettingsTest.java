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
 * 改ページの大きさの計算）が変わっていないことを固定する。CLI の出力が master と同じことは、
 * 3 つの ini × 19 の入力で EPUB を項目ごとに比べて確かめた（PR に記録）。
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
}
