import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CLI で何冊かまとめて変換するとき、1 冊の出力先を断られても残りの本を変換することのテスト（internal #20）。
 *
 * 修正前は getOutFile の IOException が run() の外側の catch まで抜けて一括が止まり、
 * 後ろの本は試されもしなかった（GUI はその本だけ「[ERROR] 出力パスエラー」を出して次へ進む）。
 * 深いフォルダで名前を切れずに断るのは名前を切る必要がある題の長い本だけなので、
 * 同じ一括の短い題の本は変換できるはず。
 *
 * main() ではなく run() を呼ぶ（main() は失敗時に System.exit するため。AozoraEpub3ExitCodeTest 参照）。
 */
public class AozoraEpub3BatchOutPathErrorTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 出力先のパスがちょうど length 文字のフォルダ（長さは実パスで数えるので、実パスから作る） */
	private File folderOfLength(int length) throws Exception {
		File dir = tempFolder.getRoot().toPath().toRealPath().toFile();
		while (dir.getAbsolutePath().length() < length - 20) {
			dir = new File(dir, "deepfolder");
		}
		int rest = length - dir.getAbsolutePath().length() - 1;
		dir = new File(dir, "d".repeat(rest));
		dir.mkdirs();
		assertEquals(length, dir.getAbsolutePath().length());
		return dir;
	}

	private File book(String fileName, String title) throws Exception {
		File txt = new File(tempFolder.newFolder(), fileName);
		Files.write(txt.toPath(), (title + "\n著\n\n　本文です。\n").getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	/**
	 * 出力先が 243 文字だと、題から作る名前（拡張子を除いたフルパスで 250 文字まで）に残るのは 6 文字。
	 * 長い題は切る必要があるが印（7 文字）が入らないので、Windows では断る。短い題「[著] 短」（5 文字）は切らずに入る。
	 */
	@Test
	public void aRefusedOutputPathDoesNotStopTheRestOfTheBatch() throws Exception {
		File dst = folderOfLength(243);
		File longBook = book("long.txt", "とても長い題の作品".repeat(10));
		File shortBook = book("short.txt", "短");
		File ini = tempFolder.newFile("empty.ini");

		String orgOsName = System.getProperty("os.name");
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int exitCode;
		try {
			//深すぎて断るのは Windows だけ（mac・Linux は切らずに書く）なので、Windows として走らせる
			System.setProperty("os.name", "Windows 11");
			//Gradle のテスト JVM では java.class.path が gradle-worker.jar になり template/ を解決できないので、
			//プロジェクト直下の jar から起動したことにする（AozoraEpub3ExitCodeTest 参照）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			//静的な Velocity は先に走った試験の初期化が残る（全体の中だけ「Failed to merge template」になる）。
			//空にしておけば、run() の Epub3Writer が上の template/ で初期化し直す
			Velocity.reset();
			System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
			exitCode = AozoraEpub3.run(new String[]{
				"-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(),
				longBook.getPath(), shortBook.getPath()});
		} finally {
			System.setOut(orgOut);
			if (orgOsName == null) System.clearProperty("os.name"); else System.setProperty("os.name", orgOsName);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		String log = baos.toString(StandardCharsets.UTF_8);
		System.out.println(log);

		//後ろの短い題の本は変換される
		File shortEpub = new File(dst, "[著] 短.epub");
		assertTrue("短い題の本が変換されていない:\n" + log, shortEpub.isFile());
		String[] epubs = dst.list((d, name) -> name.endsWith(".epub"));
		assertEquals("断られた長い題の本は書かれない:\n" + log, 1, epubs.length);
		//長い題の本の失敗は、ログと終了コードに残る
		assertTrue("断った理由が出ていない:\n" + log, log.contains("[ERROR] 出力パスエラー: 出力先のフォルダが深すぎて"));
		assertTrue("どの本を中止したかが出ていない:\n" + log, log.contains("変換中止: " + longBook.getAbsolutePath()));
		assertEquals("1 冊でも失敗があれば非 0", 1, exitCode);
	}
}
