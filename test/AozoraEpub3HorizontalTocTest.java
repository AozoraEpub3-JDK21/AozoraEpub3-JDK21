import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * internal #73: -hor は目次（nav.xhtml）も横書きにする。
 * 修正前は ini の TocVertical=1（配布の既定）のまま目次だけ縦書きになり、Kindle でページの向きが逆になった。
 *
 * run() の呼び方は AozoraEpub3BatchPerBookTest と同じ。
 */
public class AozoraEpub3HorizontalTocTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String lastLog;

	private int run(String... args) {
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int exitCode;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			//先に走った試験の静的な Velocity を空にする
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

	private String nav(boolean hor) throws Exception {
		File src = tempFolder.newFolder();
		File dst = tempFolder.newFolder();
		File txt = new File(src, "book.txt");
		Files.write(txt.toPath(), ("表題\n著\n\n［＃大見出し］第一章［＃大見出し終わり］\n　本文です。\n"
			+ "［＃改ページ］\n［＃大見出し］第二章［＃大見出し終わり］\n　続きです。\n").getBytes(StandardCharsets.UTF_8));
		File ini = new File(src, "test.ini");
		Files.write(ini.toPath(), "TocPage=1\nTocVertical=1\n".getBytes(StandardCharsets.UTF_8));
		int exitCode = hor
			? run("-i", ini.getPath(), "-enc", "UTF-8", "-of", "-hor", "-d", dst.getPath(), txt.getPath())
			: run("-i", ini.getPath(), "-enc", "UTF-8", "-of", "-d", dst.getPath(), txt.getPath());
		assertTrue("変換に失敗した:\n" + lastLog, exitCode == 0);
		File epub = new File(dst, "book.epub");
		assertTrue("EPUB が無い:\n" + lastLog, epub.isFile());
		try (ZipFile zf = new ZipFile(epub)) {
			ZipEntry e = zf.getEntry("OPS/xhtml/nav.xhtml");
			assertNotNull("nav.xhtml が無い", e);
			try (InputStream is = zf.getInputStream(e)) {
				return new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
	}

	@Test
	public void horMakesTheTocHorizontalEvenWithTocVerticalInTheIni() throws Exception {
		String nav = nav(true);
		assertFalse("-hor なのに目次が縦書き:\n" + nav, nav.contains("vertical-rl"));
	}

	/** -hor が無ければ ini の TocVertical に従う（縦書きの本は今まで通り） */
	@Test
	public void withoutHorTheTocFollowsTheIni() throws Exception {
		String nav = nav(false);
		assertTrue("TocVertical=1 なのに目次が縦書きでない:\n" + nav, nav.contains("vertical-rl"));
	}
}
