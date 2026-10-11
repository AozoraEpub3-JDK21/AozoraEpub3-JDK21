import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 大きな挿絵を単ページに出す道で、挿絵の前の本文もルビ・文字の変換を通る（PR #135 の後退）。
 *
 * printImageChuki は改ページの前に本文を書き出すとき、convertEscapedText のままの buf を書いていた。
 * 前は外字のエスケープの ※ が見えるだけだったが、印が制御文字 \u0001 になって XHTML が XML として壊れた。ルビも記号のまま出ていた。
 */
public class AozoraEpub3ImagePageTextTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void textBeforeAPageImageIsConverted() throws Exception {
		File src = tempFolder.newFolder("src");
		File dst = tempFolder.newFolder("dst");
		//縦長の大きな画像（単ページに出る）
		BufferedImage image = new BufferedImage(1000, 1500, BufferedImage.TYPE_INT_RGB);
		ImageIO.write(image, "png", new File(src, "fig.png"));
		File txt = new File(src, "book.txt");
		Files.write(txt.toPath(), ("題名\n著者\n\n前※［＃終わり二重山括弧］と｜漢字《かんじ》［＃挿絵（fig.png）入る］\n後の行\n")
			.getBytes(StandardCharsets.UTF_8));
		File ini = tempFolder.newFile("empty.ini");

		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		int exitCode;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする（AozoraEpub3ExitCodeTest 参照）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			exitCode = AozoraEpub3.run(new String[]{ "-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), txt.getPath() });
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		assertEquals(0, exitCode);
		File[] epubs = dst.listFiles((d, n) -> n.endsWith(".epub"));
		assertEquals(1, epubs.length);
		StringBuilder xhtml = new StringBuilder();
		try (ZipFile zip = new ZipFile(epubs[0])) {
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				if (e.getName().endsWith(".xhtml")) xhtml.append(new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		String all = xhtml.toString();
		assertFalse("内部エスケープの印が XHTML に出ている", all.indexOf('\u0001') >= 0);
		assertTrue(all, all.contains("前》と<ruby>漢字<rt>かんじ</rt></ruby>"));
	}
}
