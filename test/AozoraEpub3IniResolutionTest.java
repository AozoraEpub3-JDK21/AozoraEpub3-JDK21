import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * {@code -i} 指定が無いときの ini 探索のテスト（docs/code-audit-followups.md 項目 26）。
 *
 * <p>CLI は ini だけカレントディレクトリ相対で読んでいたため、配布フォルダの外から
 * {@code java -jar /path/to/AozoraEpub3.jar} を実行すると同梱 ini が無言で無視されていた。
 * jar と同じ場所を優先し、無ければ従来どおりカレントを見る。</p>
 */
public class AozoraEpub3IniResolutionTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** jar と同じ場所に ini があればそれを読む */
	@Test
	public void prefersIniBesideJar() throws IOException {
		File jarDir = tempFolder.newFolder("dist");
		Path beside = jarDir.toPath().resolve("AozoraEpub3.ini");
		Files.write(beside, "TocPage=1".getBytes(StandardCharsets.UTF_8));

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarDir.getAbsolutePath()+File.separator, "AozoraEpub3.ini");

		assertEquals(beside.toFile().getAbsolutePath(), resolved.getAbsolutePath());
	}

	/** jar と同じ場所に無ければカレント相対の File を返す（従来の挙動） */
	@Test
	public void fallsBackToWorkingDirectory() throws IOException {
		File jarDir = tempFolder.newFolder("dist-empty");

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarDir.getAbsolutePath()+File.separator, "AozoraEpub3.ini");

		assertEquals("AozoraEpub3.ini", resolved.getPath());
		assertFalse("jar 隣のパスを返してはいけない", resolved.isAbsolute());
	}

	/** jarPath が空（クラスパス実行など）でもカレント相対に落ちる */
	@Test
	public void handlesEmptyJarPath() {
		assertEquals("AozoraEpub3.ini",
			AozoraEpub3.resolveDefaultIniFile("", "AozoraEpub3.ini").getPath());
		assertEquals("AozoraEpub3.ini",
			AozoraEpub3.resolveDefaultIniFile(null, "AozoraEpub3.ini").getPath());
	}

	/** jar と同じ名前のディレクトリがあっても誤って選ばない */
	@Test
	public void ignoresDirectoryWithSameName() throws IOException {
		File jarDir = tempFolder.newFolder("dist-dir");
		Files.createDirectories(jarDir.toPath().resolve("AozoraEpub3.ini"));

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarDir.getAbsolutePath()+File.separator, "AozoraEpub3.ini");

		assertEquals("AozoraEpub3.ini", resolved.getPath());
	}
}
