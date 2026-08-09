package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

/**
 * 本棚フォルダの設定 (連番キー) の読み書き。
 * GUI から切り離してここで検証できるよう、Swing に依存させていない。
 */
public class PreviewLibraryPrefsTest
{
	/** 設定に書かれた文字列を、正規化した絶対パスに揃える (比較用) */
	private static String abs(String folder)
	{
		return Path.of(folder).toAbsolutePath().normalize().toString();
	}

	@Test
	public void loadReturnsEmptyWhenNothingConfigured()
	{
		assertTrue(PreviewLibraryPrefs.load(new Properties()).isEmpty());
		assertTrue(PreviewLibraryPrefs.load(null).isEmpty());
	}

	@Test
	public void loadKeepsNumericOrderNotPropertiesOrder()
	{
		Properties props = new Properties();
		props.setProperty("PreviewLibraryDir.2", abs("b"));
		props.setProperty("PreviewLibraryDir.1", abs("a"));
		props.setProperty("PreviewLibraryDir.10", abs("j"));
		//10 は 2 の後。文字列順に並べると 1, 10, 2 になってしまう
		assertEquals(List.of(abs("a"), abs("b"), abs("j")), PreviewLibraryPrefs.load(props));
	}

	@Test
	public void loadClosesGapsAndDropsBlanks()
	{
		Properties props = new Properties();
		props.setProperty("PreviewLibraryDir.1", abs("a"));
		props.setProperty("PreviewLibraryDir.3", "  ");
		props.setProperty("PreviewLibraryDir.5", abs("c"));
		assertEquals(List.of(abs("a"), abs("c")), PreviewLibraryPrefs.load(props));
	}

	@Test
	public void trailingSpacesInAFolderNameSurviveTheRoundTrip()
	{
		//Unix 系ではフォルダ名の末尾に空白を置ける。trim() すると別のフォルダを指してしまう
		String spaced = abs("library") + " ";
		Properties props = new Properties();
		PreviewLibraryPrefs.store(props, List.of(spaced));
		assertEquals(spaced, props.getProperty("PreviewLibraryDir.1"));
		assertEquals(List.of(spaced), PreviewLibraryPrefs.load(props));
	}

	@Test
	public void loadIgnoresKeysThatOnlyLookLikeTheSeries()
	{
		Properties props = new Properties();
		props.setProperty("PreviewLibraryDir.1", abs("a"));
		props.setProperty("PreviewLibraryDir.x", abs("x"));
		props.setProperty("PreviewLibraryDirs", abs("s"));
		props.setProperty("PreviewLibraryDir.1.enabled", "1");
		assertEquals(List.of(abs("a")), PreviewLibraryPrefs.load(props));
	}

	@Test
	public void duplicateFoldersAreFoldedRegardlessOfHowTheyAreWritten()
	{
		Properties props = new Properties();
		props.setProperty("PreviewLibraryDir.1", abs("out"));
		props.setProperty("PreviewLibraryDir.2", abs("out") + java.io.File.separator);
		props.setProperty("PreviewLibraryDir.3", abs("out" + java.io.File.separator + "." ));
		props.setProperty("PreviewLibraryDir.4", abs("other"));
		//書き方が違うだけの同じフォルダは 1 つに畳む (同じ本を二重に数えない)
		assertEquals(2, PreviewLibraryPrefs.load(props).size());
	}

	@Test
	public void loadStopsAtTheShelfLimit()
	{
		Properties props = new Properties();
		for (int i = 1; i <= LibraryScanner.MAX_SHELVES + 3; i++) {
			props.setProperty("PreviewLibraryDir." + i, abs("shelf" + i));
		}
		assertEquals(LibraryScanner.MAX_SHELVES, PreviewLibraryPrefs.load(props).size());
	}

	@Test
	public void storeThenLoadRoundTrips()
	{
		Properties props = new Properties();
		List<String> folders = List.of(abs("a"), abs("b"));
		PreviewLibraryPrefs.store(props, folders);
		assertEquals(abs("a"), props.getProperty("PreviewLibraryDir.1"));
		assertEquals(abs("b"), props.getProperty("PreviewLibraryDir.2"));
		assertEquals(folders, PreviewLibraryPrefs.load(props));
	}

	@Test
	public void storeRemovesTrailingKeysWhenShelvesAreDeleted()
	{
		Properties props = new Properties();
		PreviewLibraryPrefs.store(props, List.of(abs("a"), abs("b"), abs("c")));
		PreviewLibraryPrefs.store(props, List.of(abs("a")));
		//上書きだけで済ませると .2 / .3 が残り、次の起動で消したはずの棚が戻る
		assertNull(props.getProperty("PreviewLibraryDir.2"));
		assertNull(props.getProperty("PreviewLibraryDir.3"));
		assertEquals(List.of(abs("a")), PreviewLibraryPrefs.load(props));
	}

	@Test
	public void storeLeavesOtherSettingsAlone()
	{
		Properties props = new Properties();
		props.setProperty("LastDir", abs("last"));
		props.setProperty("PreviewLibraryDirNote", "keep me");
		PreviewLibraryPrefs.store(props, List.of(abs("a")));
		assertEquals(abs("last"), props.getProperty("LastDir"));
		assertEquals("keep me", props.getProperty("PreviewLibraryDirNote"));
	}

	@Test
	public void storeClearsEverythingWhenTheListIsEmpty()
	{
		Properties props = new Properties();
		PreviewLibraryPrefs.store(props, List.of(abs("a"), abs("b")));
		PreviewLibraryPrefs.store(props, List.of());
		assertTrue(PreviewLibraryPrefs.load(props).isEmpty());
		PreviewLibraryPrefs.store(props, null);
		assertTrue(PreviewLibraryPrefs.load(props).isEmpty());
	}

	@Test
	public void dedupeKeyFallsBackToTheRawTextForUnusablePaths()
	{
		//Windows で使えない文字。Path.of が例外を投げても設定の読み書きは止めない
		String weird = "C:\\a\u0000b";
		assertEquals(weird, PreviewLibraryPrefs.dedupeKey(weird));
		assertFalse(PreviewLibraryPrefs.dedupeKey("a").equals(PreviewLibraryPrefs.dedupeKey("b")));
	}
}
