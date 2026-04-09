package com.github.hmdev.config;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * AozoraEpub3.ini のデフォルト設定確認テスト (A4-A6 相当)
 *
 * narou.rb 連携に必要な設定が INI にデフォルトで有効になっていることを検証する。
 *
 * 実行方法:
 *   gradlew test --tests "com.github.hmdev.config.DefaultIniSettingsTest"
 */
public class DefaultIniSettingsTest {

	private Properties loadIni() throws Exception {
		File ini = new File("AozoraEpub3.ini");
		assertTrue("AozoraEpub3.ini が存在すること", ini.exists());
		Properties props = new Properties();
		try (FileInputStream fis = new FileInputStream(ini)) {
			props.load(fis);
		}
		return props;
	}

	/** A4: title.xhtml 生成 (TitlePageWrite=1, TitlePage=2=TITLE_HORIZONTAL) */
	@Test
	public void testTitlePageEnabled() throws Exception {
		Properties props = loadIni();
		assertEquals("TitlePageWrite は 1 (title.xhtml 生成有効)", "1", props.getProperty("TitlePageWrite"));
		assertEquals("TitlePage は 2 (TITLE_HORIZONTAL)", "2", props.getProperty("TitlePage"));
	}

	/** A5/A6: OPF guide 要素 + nav.xhtml landmarks (TocPage=1) */
	@Test
	public void testTocPageEnabled() throws Exception {
		Properties props = loadIni();
		assertEquals("TocPage は 1 (TOCページ + guide/landmarks 出力有効)", "1", props.getProperty("TocPage"));
	}

	/** 目次にタイトルを含む (TitleToc=1) */
	@Test
	public void testTitleTocEnabled() throws Exception {
		Properties props = loadIni();
		assertEquals("TitleToc は 1 (目次にタイトルページを含む)", "1", props.getProperty("TitleToc"));
	}
}
