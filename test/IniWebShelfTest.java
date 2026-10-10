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
}
