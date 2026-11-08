import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 読める画像が 1 枚も無い書庫は本にせず失敗にする。想定内の失敗なので、ログにスタックトレースを出さない（internal #77）。
 * 前は書く側（Epub3Writer）と呼ぶ側（convertFile）の 2 か所から同じ「画像がありませんでした」のトレースが出た。
 */
public class AozoraEpub3NoImagesLogTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void archiveWithNoReadableImageFailsWithoutAStackTrace() throws Exception {
		ByteArrayOutputStream bmp = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "bmp", bmp);
		byte[] truncated = java.util.Arrays.copyOf(bmp.toByteArray(), 2000);
		File zip = tempFolder.newFile("bmponly.zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			for (String name : new String[]{"01.bmp", "02.bmp"}) {
				zos.putNextEntry(new ZipEntry(name));
				zos.write(truncated);
				zos.closeEntry();
			}
		}
		File dst = tempFolder.newFolder();
		File ini = tempFolder.newFile();
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out, orgErr = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
		int result;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする（AozoraEpub3TextBmpTest と同じ）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
			result = AozoraEpub3.run(List.of("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), "-c", "0", zip.getPath()).toArray(new String[0]));
		} finally {
			System.setOut(orgOut);
			System.setErr(orgErr);
			//テンプレートの場所を偽のクラスパスで決めた Velocity を、後の試験へ持ち越さない
			Velocity.reset();
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		String log = out.toString(StandardCharsets.UTF_8) + "\n----- stderr -----\n" + err.toString(StandardCharsets.UTF_8);
		assertTrue("失敗にならない: " + result + "\n" + log, result != 0);
		assertEquals("EPUB が残った", 0, dst.listFiles((d, n) -> n.endsWith(".epub")).length);
		assertTrue(log, log.contains("エラーが発生しました : 画像がありませんでした"));
		assertTrue("ログを捕まえられていない（stderr に logger の行が無い）:\n" + log, err.toString(StandardCharsets.UTF_8).contains("EPUB 変換に失敗"));
		assertFalse("スタックトレースが出た:\n" + log, log.contains("\tat "));
		assertFalse("書く側のエラーが重なった:\n" + log, log.contains("EPUB 出力中にエラーが発生"));
	}
}
