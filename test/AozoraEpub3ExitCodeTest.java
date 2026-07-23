import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CLI の終了コードのテスト（監査 #2）。
 *
 * 修正前は Epub3Writer が例外を握り潰していたため、変換が失敗しても
 * 終了コードは常に 0 だった。narou.rb は終了コードで成否を判定するため、
 * 破損 EPUB が成功扱いで取り込まれていた。
 *
 * main() ではなく run() を呼ぶ（main() は失敗時に System.exit するため、
 * テスト JVM を巻き込んで落としてしまう）。
 */
public class AozoraEpub3ExitCodeTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** -h は正常終了 */
	@Test
	public void helpReturnsZero() {
		assertEquals(0, AozoraEpub3.run(new String[]{"-h"}));
	}

	/** 引数なしは使用方法を表示して異常終了 */
	@Test
	public void noArgumentsReturnsNonZero() {
		assertEquals(1, AozoraEpub3.run(new String[]{}));
	}

	/** 存在しない入力ファイルは異常終了 */
	@Test
	public void missingInputFileReturnsNonZero() throws Exception {
		Path outDir = tempFolder.newFolder("out").toPath();
		Path missing = tempFolder.getRoot().toPath().resolve("not_exist.txt");
		assertEquals(1, AozoraEpub3.run(new String[]{
			"-of", "-d", outDir.toString(), missing.toString()}));
	}

	/** 存在しない出力先パスは異常終了 */
	@Test
	public void missingDstPathReturnsNonZero() throws Exception {
		Path txt = tempFolder.newFile("sample.txt").toPath();
		Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));
		Path missingDir = tempFolder.getRoot().toPath().resolve("no_such_dir");
		assertEquals(1, AozoraEpub3.run(new String[]{
			"-of", "-d", missingDir.toString(), txt.toString()}));
	}

	/** 正常に変換できた場合は 0（上記の異常系が誤検知でないことの確認）。
	 *
	 * Gradle のテスト JVM では java.class.path が gradle-worker.jar になるため
	 * run() が template/ を解決できず "Template not found: mimetype" で失敗する。
	 * CLI を end-to-end で動かすテストが @Ignore されているのと同じ理由
	 * （IniCssIntegrationTest 参照）。CI の Actions workflow でカバーする。
	 *
	 * 成功経路の検証自体は Epub3WriterErrorHandlingTest#keepsEpubAndClosesSrcOnSuccess
	 * が明示的な template パスを渡す形で行っている。 */
	@Ignore("Runs CLI end-to-end; covered in Actions workflow")
	@Test
	public void successfulConversionReturnsZero() throws Exception {
		Path txt = tempFolder.newFile("sample.txt").toPath();
		Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));
		Path outDir = tempFolder.newFolder("out").toPath();

		assertEquals(0, AozoraEpub3.run(new String[]{
			"-of", "-d", outDir.toString(), txt.toString()}));

		try (Stream<Path> files = Files.list(outDir)) {
			assertTrue("EPUB が出力されていない",
				files.anyMatch(p -> p.toString().endsWith(".epub")));
		}
	}
}
