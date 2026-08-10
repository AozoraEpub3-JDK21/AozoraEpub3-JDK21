package com.github.hmdev.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * {@link SettingDefaults} の既定値が、GUI が実際に書き出す初期状態と一致することを検証する。
 *
 * <p>フィクスチャ {@code test_data/gui_default_settings.ini} は、GUI をまっさらな作業ディレクトリで
 * 起動して終了させ、実際に保存された ini から実行時状態（ウィンドウ位置・履歴・プロファイル名）だけを
 * 取り除いたもの（2026-08-10 実測、116 キー）。GUI は終了時に全キーを書き出し、OFF は
 * {@code Key=} の空値として保存する。</p>
 *
 * <p>このテストがあるのは、GUI のウィジェット初期値と CLI のキー不在時の既定値がずれる事故が
 * 繰り返し起きているため（docs/code-audit-followups.md 項目 22 / 24）。GUI 側の初期値を変えたら
 * このフィクスチャを取り直し、表と揃っていることをここで担保する。</p>
 *
 * 実行方法:
 *   gradlew test --tests "com.github.hmdev.config.SettingDefaultsGuiParityTest"
 */
public class SettingDefaultsGuiParityTest {

	private static Properties guiDefaults;

	@BeforeClass
	public static void loadFixture() throws Exception {
		guiDefaults = new Properties();
		Path fixture = Paths.get("test_data", "gui_default_settings.ini");
		if (Files.exists(fixture)) {
			try (InputStream in = Files.newInputStream(fixture)) {
				guiDefaults.load(in);
			}
		} else {
			//テストがクラスパス経由で実行される場合（作業ディレクトリがプロジェクトルートでない）
			try (InputStream in = SettingDefaultsGuiParityTest.class.getClassLoader()
					.getResourceAsStream("gui_default_settings.ini")) {
				assertTrue("フィクスチャ gui_default_settings.ini が見つからない", in != null);
				guiDefaults.load(in);
			}
		}
	}

	/** boolean の既定値が GUI の保存値（"1" が ON、空文字が OFF）と一致する */
	@Test
	public void booleanDefaultsMatchGuiFixture() {
		List<String> mismatches = new ArrayList<>();
		for (String key : SettingDefaults.booleanKeys()) {
			String saved = guiDefaults.getProperty(key);
			if (saved == null) {
				mismatches.add(key+": GUI が書き出していないキー（フィクスチャに無い）");
				continue;
			}
			boolean guiValue = "1".equals(saved);
			boolean tableValue = SettingDefaults.isSelected(key);
			if (guiValue != tableValue) {
				mismatches.add(key+": GUI="+guiValue+" / 表="+tableValue);
			}
		}
		assertEquals("GUI 初期値と既定値テーブルの不一致: "+mismatches, 0, mismatches.size());
	}

	/** int の既定値が GUI の保存値と一致する */
	@Test
	public void intDefaultsMatchGuiFixture() {
		List<String> mismatches = new ArrayList<>();
		for (String key : SettingDefaults.intKeys()) {
			String saved = guiDefaults.getProperty(key);
			if (saved == null) {
				mismatches.add(key+": GUI が書き出していないキー（フィクスチャに無い）");
				continue;
			}
			int guiValue;
			try {
				guiValue = Integer.parseInt(saved.trim());
			} catch (NumberFormatException e) {
				mismatches.add(key+": GUI の保存値が数値でない ("+saved+")");
				continue;
			}
			int tableValue = SettingDefaults.getInt(key);
			if (guiValue != tableValue) {
				mismatches.add(key+": GUI="+guiValue+" / 表="+tableValue);
			}
		}
		assertEquals("GUI 初期値と既定値テーブルの不一致: "+mismatches, 0, mismatches.size());
	}

	/**
	 * 項目 24 で判明した「同梱 ini に足すと CLI の挙動が変わるキー」が、表に載って
	 * GUI と揃っていることを固定する。ここが崩れると、同梱 ini を使わない CLI 利用者
	 * （-i で自作 ini を渡す人）のドリフトが復活する。
	 */
	@Test
	public void driftKeysAreCovered() {
		String[] booleans = {"CoverPage", "TitlePageWrite", "TocPage", "TocVertical", "AutoYoko", "PageBreak", "FitImage"};
		for (String key : booleans) {
			assertTrue(key+" が boolean の表にあること", SettingDefaults.booleanKeys().contains(key));
		}
		String[] ints = {"TitlePage", "PageBreakSize", "PageBreakEmptyLine", "PageBreakEmptySize",
			"PageBreakChapterSize", "ImageSizeType", "SinglePageSizeW", "SinglePageSizeH",
			"JpegQuality", "MaxCoverLine", "DakutenType"};
		for (String key : ints) {
			assertTrue(key+" が int の表にあること", SettingDefaults.intKeys().contains(key));
		}
		//項目 24 で確認した具体値（GUI 初期値）
		assertTrue("PageBreak は GUI 既定 ON", SettingDefaults.isSelected("PageBreak"));
		assertTrue("TocVertical は GUI 既定 縦書き", SettingDefaults.isSelected("TocVertical"));
		assertTrue("FitImage は GUI 既定 ON", SettingDefaults.isSelected("FitImage"));
		assertEquals("JpegQuality", 85, SettingDefaults.getInt("JpegQuality"));
		assertEquals("MaxCoverLine", 10, SettingDefaults.getInt("MaxCoverLine"));
	}
}
