package com.github.hmdev.config;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Properties;

/**
 * GUI / CLI 共有の目次設定既定値テーブルの検証。
 *
 * docs/code-audit-followups.md 項目 22:
 * CLI が Chapter* キーを {@code "1".equals(...)} で直接読んでいたため、
 * キーを持たない ini では章見出しが目次に入らなかった。
 *
 * 実行方法:
 *   gradlew test --tests "com.github.hmdev.config.SettingDefaultsTest"
 */
public class SettingDefaultsTest {

	/** キーが無ければ GUI のチェックボックス初期値と同じ値になる */
	@Test
	public void missingKeyFallsBackToGuiDefault() {
		Properties empty = new Properties();
		//見出し注記はすべて ON が GUI の初期値。ここが false だと章見出しが目次に入らない
		assertTrue("ChapterH", SettingDefaults.getBoolean(empty, "ChapterH"));
		assertTrue("ChapterH1", SettingDefaults.getBoolean(empty, "ChapterH1"));
		assertTrue("ChapterH2", SettingDefaults.getBoolean(empty, "ChapterH2"));
		assertTrue("ChapterH3", SettingDefaults.getBoolean(empty, "ChapterH3"));
		assertTrue("ChapterName", SettingDefaults.getBoolean(empty, "ChapterName"));
		assertTrue("ChapterSection", SettingDefaults.getBoolean(empty, "ChapterSection"));
		assertTrue("ChapterExclude", SettingDefaults.getBoolean(empty, "ChapterExclude"));
		assertTrue("TitleToc", SettingDefaults.getBoolean(empty, "TitleToc"));
		//OFF が初期値のもの
		assertFalse("SameLineChapter", SettingDefaults.getBoolean(empty, "SameLineChapter"));
		assertFalse("ChapterUseNextLine", SettingDefaults.getBoolean(empty, "ChapterUseNextLine"));
		assertFalse("ChapterNumOnly", SettingDefaults.getBoolean(empty, "ChapterNumOnly"));
		assertFalse("ChapterNumTitle", SettingDefaults.getBoolean(empty, "ChapterNumTitle"));
		assertFalse("ChapterNumParen", SettingDefaults.getBoolean(empty, "ChapterNumParen"));
		assertFalse("ChapterNumParenTitle", SettingDefaults.getBoolean(empty, "ChapterNumParenTitle"));
		assertFalse("ChapterPattern", SettingDefaults.getBoolean(empty, "ChapterPattern"));
		assertFalse("CoverPageToc", SettingDefaults.getBoolean(empty, "CoverPageToc"));
		assertFalse("NavNest", SettingDefaults.getBoolean(empty, "NavNest"));
		assertFalse("NcxNest", SettingDefaults.getBoolean(empty, "NcxNest"));

		assertEquals(64, SettingDefaults.getInt(empty, "MaxChapterNameLength"));
	}

	/** ini に書かれていれば既定値より ini が優先される (既定 ON を OFF にできる) */
	@Test
	public void iniValueOverridesDefault() {
		Properties props = new Properties();
		props.setProperty("ChapterH2", "");
		props.setProperty("ChapterName", "0");
		props.setProperty("SameLineChapter", "1");
		props.setProperty("MaxChapterNameLength", "20");

		assertFalse("空文字は OFF", SettingDefaults.getBoolean(props, "ChapterH2"));
		assertFalse("\"1\" 以外は OFF", SettingDefaults.getBoolean(props, "ChapterName"));
		assertTrue("\"1\" は ON", SettingDefaults.getBoolean(props, "SameLineChapter"));
		assertEquals(20, SettingDefaults.getInt(props, "MaxChapterNameLength"));

		//既定 ON のキーは触らなければ ON のまま
		assertTrue(SettingDefaults.getBoolean(props, "ChapterH"));
	}

	/** 数値でない値は既定値にフォールバックする */
	@Test
	public void brokenIntValueFallsBackToDefault() {
		Properties props = new Properties();
		props.setProperty("MaxChapterNameLength", "abc");
		assertEquals(64, SettingDefaults.getInt(props, "MaxChapterNameLength"));
	}

	/** 表に無いキーはその場で失敗する (かつての hapterNumParenTitle のようなタイポを黙って false にしない) */
	@Test
	public void unknownKeyThrows() {
		Properties props = new Properties();
		try {
			SettingDefaults.getBoolean(props, "hapterNumParenTitle");
			fail("未定義キーは IllegalArgumentException になること");
		} catch (IllegalArgumentException expected) {
			//期待どおり
		}
		try {
			SettingDefaults.getInt("ChapterNameLength");
			fail("未定義キーは IllegalArgumentException になること");
		} catch (IllegalArgumentException expected) {
			//期待どおり
		}
	}

	/** GUI が保存する値 ("1" / "") をそのまま読み戻せる */
	@Test
	public void roundTripsGuiWrittenValues() {
		for (String key : SettingDefaults.booleanKeys()) {
			Properties on = new Properties();
			on.setProperty(key, "1");
			assertTrue(key+" は ON で読み戻せること", SettingDefaults.getBoolean(on, key));

			Properties off = new Properties();
			off.setProperty(key, "");
			assertFalse(key+" は OFF で読み戻せること", SettingDefaults.getBoolean(off, key));
		}
	}
}
