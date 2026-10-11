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

	/** 配布の ini の説明（コメント）とキーの順は残る。変わるのは足したキーの行だけ（internal #30） */
	@Test
	public void theCommentsOfTheIniSurvive() throws Exception {
		File ini = new File(temp.getRoot(), "AozoraEpub3.ini");
		byte[] shipped = Files.readAllBytes(Path.of("AozoraEpub3.ini"));
		Files.write(ini.toPath(), shipped);
		Path dir = temp.newFolder("web").toPath();
		new IniWebShelf(ini).setLocation(dir);
		byte[] out = Files.readAllBytes(ini.toPath());
		org.junit.Assert.assertTrue("元の中身がそのまま先頭に残る",
			new String(out, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith(new String(shipped, java.nio.charset.StandardCharsets.ISO_8859_1)));
		Properties after = read(ini);
		assertEquals(dir.toString(), after.getProperty(WebShelfPrefs.KEY));
		assertEquals(List.of(dir.toString()), PreviewLibraryPrefs.load(after));
	}

	/** 書き換えても ini の権限は変えない。同じ場所を決め直しただけなら書かない（PR のゲート2） */
	@Test
	public void thePermissionsStayAndTheSameLocationDoesNotRewrite() throws Exception {
		File ini = new File(temp.getRoot(), "AozoraEpub3.ini");
		Files.write(ini.toPath(), "# 説明\nVertical=1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		java.util.Set<java.nio.file.attribute.PosixFilePermission> mode;
		try {
			mode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
			Files.setPosixFilePermissions(ini.toPath(), mode);
		} catch (UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("POSIX の権限が無い環境", e);
			return;
		}
		Path dir = temp.newFolder("web").toPath();
		IniWebShelf shelf = new IniWebShelf(ini);
		shelf.setLocation(dir);
		assertEquals(mode, Files.getPosixFilePermissions(ini.toPath()));
		java.nio.file.attribute.FileTime written = java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(ini.toPath(), written);
		shelf.setLocation(dir);
		assertEquals("決め直しただけでは書かない", written, Files.getLastModifiedTime(ini.toPath()));
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

	/** ini がリンクなら、リンクは残してリンクの先を書き換える（PR の codex） */
	@Test
	public void aLinkedIniStaysALink() throws Exception {
		File real = new File(temp.newFolder("shared"), "AozoraEpub3.ini");
		Files.write(real.toPath(), "Vertical=1\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
		Path link = temp.getRoot().toPath().resolve("AozoraEpub3.ini");
		try {
			Files.createSymbolicLink(link, real.toPath());
		} catch (UnsupportedOperationException | java.io.IOException e) {
			org.junit.Assume.assumeNoException("シンボリックリンクを作れない環境", e);
		}
		Path web = temp.newFolder("web").toPath();
		new IniWebShelf(link.toFile()).setLocation(web);
		org.junit.Assert.assertTrue("リンクのまま", Files.isSymbolicLink(link));
		assertEquals(web.toString(), read(real).getProperty(WebShelfPrefs.KEY));
		assertEquals("1", read(real).getProperty("Vertical"));
	}
}
