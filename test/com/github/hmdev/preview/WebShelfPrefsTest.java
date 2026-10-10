package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WebShelfPrefsTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void theLocationRoundTripsAndOnlyAbsolutePathsAreRead() {
		Properties p = new Properties();
		assertNull(WebShelfPrefs.load(p));
		Path dir = temp.getRoot().toPath().resolve("web");
		WebShelfPrefs.store(p, dir);
		assertEquals(dir.toAbsolutePath().normalize(), WebShelfPrefs.load(p));
		p.setProperty(WebShelfPrefs.KEY, "relative/web");
		assertNull("相対パスは読まない（どこを指すかが起動の仕方で変わる）", WebShelfPrefs.load(p));
		p.setProperty(WebShelfPrefs.KEY, "  ");
		assertNull(WebShelfPrefs.load(p));
		WebShelfPrefs.store(p, null);
		assertNull(p.getProperty(WebShelfPrefs.KEY));
	}

	/** 提案の場所: 最初の棚の下の Web。棚が無ければ書類フォルダの下、書類フォルダも無ければホームの下 */
	@Test
	public void theSuggestionFollowsTheAgreement() throws Exception {
		Path home = temp.newFolder("home").toPath();
		Path shelf = temp.newFolder("shelf").toPath();
		assertEquals(shelf.resolve("Web"), WebShelfPrefs.suggest(List.of(shelf, home), home));
		assertEquals(home.resolve("AozoraEpub3"), WebShelfPrefs.suggest(List.of(), home));
		Files.createDirectories(home.resolve("Documents"));
		assertEquals(home.resolve("Documents").resolve("AozoraEpub3"), WebShelfPrefs.suggest(null, home));
	}
}
