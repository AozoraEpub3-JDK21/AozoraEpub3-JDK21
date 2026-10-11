import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CLI の一括変換で、1 冊の失敗が後ろの本を止めないことのテスト。
 *
 * internal #31: 壊れた書庫・画像の無い書庫は、その本だけ失敗にして次の本へ進む
 *   （修正前は壊れた書庫の例外が run() の外側の catch まで抜け、画像の無い書庫は return 1 で一括が止まった）。
 *
 * run() の呼び方は AozoraEpub3BatchOutPathErrorTest と同じ。
 */
public class AozoraEpub3BatchPerBookTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File book(File dir, String fileName, String title) throws Exception {
		File txt = new File(dir, fileName);
		Files.write(txt.toPath(), (title + "\n著\n\n　本文です。\n").getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	private int run(String... args) {
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int exitCode;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			//先に走った試験の静的な Velocity を空にする（AozoraEpub3BatchOutPathErrorTest 参照）
			Velocity.reset();
			System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
			exitCode = AozoraEpub3.run(args);
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		lastLog = baos.toString(StandardCharsets.UTF_8);
		System.out.println(lastLog);
		return exitCode;
	}
	private String lastLog;

	/** 壊れた cbz と、画像の無い zip の後ろに置いた txt も変換される。失敗は終了コードに残る */
	@Test
	public void aBrokenOrEmptyArchiveDoesNotStopTheRestOfTheBatch() throws Exception {
		File src = tempFolder.newFolder("src");
		File dst = tempFolder.newFolder("dst");
		//cbz は書庫の中身を数えずに画像のみとして読み込むので、壊れていると読み込みの例外になる
		File broken = new File(src, "broken.cbz");
		Files.write(broken.toPath(), "this is not a zip file at all".getBytes(StandardCharsets.UTF_8));
		//txt も画像も無い zip（画像のみとして扱われ、画像が無い）
		File empty = new File(src, "empty.zip");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(empty))) {
			zos.putNextEntry(new ZipEntry("readme.dat"));
			zos.write(new byte[]{1, 2, 3});
			zos.closeEntry();
		}
		File txt = book(src, "ok.txt", "続く本");
		File ini = tempFolder.newFile("empty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(),
			broken.getPath(), empty.getPath(), txt.getPath());

		assertTrue("後ろの txt が変換されていない:\n" + lastLog, new File(dst, "[著] 続く本.epub").isFile());
		assertTrue("壊れた書庫を中止したことが出ていない:\n" + lastLog, lastLog.contains("変換中止: " + broken.getAbsolutePath()));
		assertTrue("画像の無い書庫を中止したことが出ていない:\n" + lastLog, lastLog.contains("変換中止: " + empty.getAbsolutePath()));
		assertEquals("1 冊でも失敗があれば非 0", 1, exitCode);
	}
}
