import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.preview.PreviewLibraryPrefs;
import com.github.hmdev.preview.WebShelfPrefs;

/** CLI の Web 本棚: ini に場所と棚を書く。ほかの設定は残す。同じ棚を二重に足さない */
public class IniWebShelfTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Properties read(File ini) throws Exception {
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(ini.toPath())) { p.load(in); }
		return p;
	}

	@Test
	public void theLocationAndTheShelfAreWrittenToTheIni() throws Exception {
		File ini = new File(temp.getRoot(), "AozoraEpub3.ini");
		Properties before = new Properties();
		before.setProperty("Vertical", "1");
		before.setProperty(PreviewLibraryPrefs.KEY_PREFIX + "1", temp.getRoot().getAbsolutePath());
		try (var out = Files.newOutputStream(ini.toPath())) { before.store(out, null); }

		IniWebShelf shelf = new IniWebShelf(ini);
		assertNull(shelf.location());
		Path dir = temp.newFolder("web").toPath();
		shelf.setLocation(dir);
		shelf.setLocation(dir);

		Properties after = read(ini);
		assertEquals("ほかの設定は残る", "1", after.getProperty("Vertical"));
		assertEquals(dir.toAbsolutePath().normalize().toString(), after.getProperty(WebShelfPrefs.KEY));
		assertEquals("棚に足す（二度決めても 1 つ）", List.of(temp.getRoot().getAbsolutePath(), dir.toString()), PreviewLibraryPrefs.load(after));
		assertEquals(dir.toAbsolutePath().normalize(), shelf.location());
		assertEquals("一時ファイルを残さない", 0, temp.getRoot().listFiles((d, n) -> n.endsWith(".tmp")).length);
	}

	/** ini が無ければ作る */
	@Test
	public void aMissingIniIsCreated() throws Exception {
		File ini = new File(temp.getRoot(), "sub/AozoraEpub3.ini");
		Path dir = temp.newFolder("web").toPath();
		new IniWebShelf(ini).setLocation(dir);
		assertEquals(dir.toString(), read(ini).getProperty(WebShelfPrefs.KEY));
	}

	/** 既にある ini が読めなければ、書かずに失敗にする（空のまま書き戻すと、ほかの設定が消える） */
	@Test
	public void anUnreadableIniIsNotOverwritten() throws Exception {
		File ini = new File(temp.getRoot(), "AozoraEpub3.ini");
		byte[] broken = "Vertical=1\nBad=\\uZZZZ\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
		Files.write(ini.toPath(), broken);
		try {
			new IniWebShelf(ini).setLocation(temp.newFolder("web").toPath());
			org.junit.Assert.fail("読めない ini に書いた");
		} catch (java.io.IOException expected) {
			/* 意図的: 書かずに失敗する */
		}
		org.junit.Assert.assertArrayEquals(broken, Files.readAllBytes(ini.toPath()));
	}

	/** CLI は決めてある Web 本棚を棚の先頭に加える（棚が上限を超えると後ろから落ちるので） */
	@Test
	public void theCliPutsTheWebShelfFirst() throws Exception {
		File ini = new File(temp.getRoot(), "AozoraEpub3.ini");
		Path web = temp.newFolder("web").toPath();
		new IniWebShelf(ini).setLocation(web);
		try {
			AozoraEpub3.registerBookUpdater(null, ini.getPath());
			java.util.List<Path> folders = new java.util.ArrayList<>(List.of(temp.newFolder("a").toPath()));
			AozoraEpub3.addWebShelf(folders);
			assertEquals(web.toAbsolutePath().normalize(), folders.get(0));
			assertEquals(2, folders.size());
		} finally {
			com.github.hmdev.preview.PreviewLauncher.setWebShelf(null);
			com.github.hmdev.preview.PreviewLauncher.setBookUpdater(null);
		}
	}
}
