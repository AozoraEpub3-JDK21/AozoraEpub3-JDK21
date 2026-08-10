import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * {@code -i} 指定が無いときの ini 探索のテスト（docs/code-audit-followups.md 項目 26）。
 *
 * <p>探索順は<b>カレントディレクトリ優先 → jar と同じ場所へフォールバック</b>。
 * カレントに ini を置くのは利用者の能動的な使い分けなので jar 隣より優先し、
 * どちらにも無ければ従来どおりカレント相対の File を返す（呼び出し側で存在チェック）。</p>
 *
 * <p>プロセスのカレント（リポジトリルートの AozoraEpub3.ini）に依存しないよう、
 * workingDir を明示するオーバーロードでテストする。</p>
 */
public class AozoraEpub3IniResolutionTest {

	private static final String INI = "AozoraEpub3.ini";

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File workDir;
	private File jarDir;

	@Before
	public void setUp() throws IOException {
		workDir = tempFolder.newFolder("cwd");
		jarDir = tempFolder.newFolder("dist");
	}

	private String jarPath() {
		return jarDir.getAbsolutePath() + File.separator;
	}

	private Path createIni(File dir) throws IOException {
		Path ini = dir.toPath().resolve(INI);
		Files.write(ini, "TocPage=1".getBytes(StandardCharsets.UTF_8));
		return ini;
	}

	/** 1. 両方にあればカレントが勝つ（カレントの ini は利用者の意思表示） */
	@Test
	public void prefersWorkingDirWhenBothExist() throws IOException {
		Path inWorkDir = createIni(workDir);
		createIni(jarDir);

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarPath(), INI, workDir);

		assertEquals(inWorkDir.toFile(), resolved);
	}

	/** 2. カレントに無ければ jar と同じ場所（配布フォルダ外からの実行で同梱 ini が効く） */
	@Test
	public void fallsBackToJarDirectory() throws IOException {
		Path beside = createIni(jarDir);

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarPath(), INI, workDir);

		assertEquals(beside.toFile(), resolved);
	}

	/** 3. jarPath が空 / null（クラスパス実行など）はカレント側だけを見る */
	@Test
	public void handlesEmptyJarPath() throws IOException {
		Path inWorkDir = createIni(workDir);

		assertEquals(inWorkDir.toFile(),
			AozoraEpub3.resolveDefaultIniFile("", INI, workDir));
		assertEquals(inWorkDir.toFile(),
			AozoraEpub3.resolveDefaultIniFile(null, INI, workDir));
	}

	/** 4. 同じ名前のディレクトリは無視する（カレント側・jar 側とも） */
	@Test
	public void ignoresDirectoryWithSameName() throws IOException {
		Files.createDirectories(workDir.toPath().resolve(INI));
		Path beside = createIni(jarDir);

		File resolved = AozoraEpub3.resolveDefaultIniFile(jarPath(), INI, workDir);
		assertEquals("カレント側のディレクトリを飛ばして jar 隣のファイルを選ぶ",
			beside.toFile(), resolved);

		Files.delete(beside);
		Files.createDirectories(jarDir.toPath().resolve(INI));
		resolved = AozoraEpub3.resolveDefaultIniFile(jarPath(), INI, workDir);
		assertEquals("jar 側もディレクトリなら不在扱いでカレント側の File を返す",
			new File(workDir, INI), resolved);
	}

	/** 5. どちらにも無ければカレント側の File を返す（呼び出し側の存在チェックで既定値起動） */
	@Test
	public void returnsWorkingDirFileWhenNeitherExists() {
		File resolved = AozoraEpub3.resolveDefaultIniFile(jarPath(), INI, workDir);

		assertEquals(new File(workDir, INI), resolved);
	}
}
