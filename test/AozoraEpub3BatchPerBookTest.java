import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CLI の一括変換で、1 冊の失敗や 1 冊の表紙の決定が後ろの本へ持ち越されないことのテスト。
 *
 * internal #31: 壊れた書庫・画像の無い書庫は、その本だけ失敗にして次の本へ進む
 *   （修正前は壊れた書庫の例外が run() の外側の catch まで抜け、画像の無い書庫は return 1 で一括が止まった）。
 * internal #32: -c の値は本ごとに解決する
 *   （修正前は「-c 1」が 1 冊目の同名画像に書き換わり、2 冊目も 1 冊目の表紙になった）。
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
		//壊れた rar は中身を数える段で junrar の RarException（IOException ではない）を投げる
		File brokenRar = new File(src, "broken.rar");
		Files.write(brokenRar.toPath(), "this is not a rar file either".getBytes(StandardCharsets.UTF_8));
		File txt = book(src, "ok.txt", "続く本");
		File ini = tempFolder.newFile("empty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(),
			broken.getPath(), empty.getPath(), brokenRar.getPath(), txt.getPath());

		assertTrue("後ろの txt が変換されていない:\n" + lastLog, new File(dst, "[著] 続く本.epub").isFile());
		assertTrue("壊れた書庫を中止したことが出ていない:\n" + lastLog, lastLog.contains("変換中止: " + broken.getAbsolutePath()));
		assertTrue("画像の無い書庫を中止したことが出ていない:\n" + lastLog, lastLog.contains("変換中止: " + empty.getAbsolutePath()));
		assertTrue("壊れた rar を中止したことが出ていない:\n" + lastLog, lastLog.contains("変換中止: " + brokenRar.getAbsolutePath()));
		assertEquals("1 冊でも失敗があれば非 0", 1, exitCode);
	}

	/** 「-c 1」（入力と同じ名前の画像）は本ごとに解決する。b の表紙は b の画像 */
	@Test
	public void sameNameCoverIsResolvedPerBook() throws Exception {
		File dirA = tempFolder.newFolder("a");
		File dirB = tempFolder.newFolder("b");
		File dst = tempFolder.newFolder("dst");
		File a = book(dirA, "a.txt", "本A");
		File b = book(dirB, "b.txt", "本B");
		writePng(new File(dirA, "a.png"), Color.RED);
		writePng(new File(dirB, "b.png"), Color.BLUE);
		File ini = tempFolder.newFile("empty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), "-c", "1",
			a.getPath(), b.getPath());
		assertEquals("全件成功:\n" + lastLog, 0, exitCode);

		assertCoverColor(new File(dst, "[著] 本A.epub"), Color.RED);
		assertCoverColor(new File(dst, "[著] 本B.epub"), Color.BLUE);
	}

	private static void writePng(File file, Color color) throws Exception {
		BufferedImage img = new BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, 60, 80);
		g.dispose();
		ImageIO.write(img, "png", file);
	}

	private void assertCoverColor(File epub, Color expected) throws Exception {
		assertTrue("EPUB が無い: " + epub + "\n" + lastLog, epub.isFile());
		try (ZipFile zip = new ZipFile(epub)) {
			BufferedImage cover = null;
			String names = "";
			for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements();) {
				ZipEntry e = en.nextElement();
				names += e.getName() + "\n";
				String n = e.getName().toLowerCase();
				//表紙のほかに画像は無いので、images/ の唯一の画像が表紙
				if (n.startsWith("ops/images/") && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
					cover = ImageIO.read(zip.getInputStream(e));
				}
			}
			assertNotNull(epub.getName() + " に表紙画像が無い:\n" + names, cover);
			Color c = new Color(cover.getRGB(cover.getWidth() / 2, cover.getHeight() / 2));
			int dr = c.getRed() - expected.getRed(), dg = c.getGreen() - expected.getGreen(), db = c.getBlue() - expected.getBlue();
			assertTrue(epub.getName() + " の表紙の色が違う: " + c + " (期待 " + expected + ")",
				dr * dr + dg * dg + db * db < 40 * 40);
		}
	}

	/** 書庫の中のテキストが 1 つ壊れていても、残りの本は変換する。飛ばした本は失敗として終了コードに残す（internal #36） */
	@Test
	public void aCorruptTextInAZipSkipsOnlyThatBook() throws Exception {
		File src = tempFolder.newFolder("zsrc");
		File dst = tempFolder.newFolder("zdst");
		File zip = new File(src, "two.zip");
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 2000; i++) big.append("壊れる本の").append(i).append("行目です。\n");
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
			zos.putNextEntry(new java.util.zip.ZipEntry("1_bad.txt"));
			zos.write(big.toString().getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new java.util.zip.ZipEntry("2_good.txt"));
			zos.write("良い本\n著\n\n　本文です。\n".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		byte[] b = Files.readAllBytes(zip.toPath());
		int data = 30 + ((b[26] & 0xff) | ((b[27] & 0xff) << 8)) + ((b[28] & 0xff) | ((b[29] & 0xff) << 8));
		for (int i = data + 40; i < data + 80; i++) b[i] = (byte)0xff;
		Files.write(zip.toPath(), b);
		File ini = tempFolder.newFile("zempty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), zip.getPath());

		assertTrue("残りの本が変換されていない:\n" + lastLog, new File(dst, "[著] 良い本.epub").isFile());
		assertTrue("飛ばしたことが出ていない:\n" + lastLog, lastLog.contains("書庫の中のテキストが読めないので飛ばします : 1_bad.txt"));
		assertEquals("飛ばした本は失敗として数える", 1, exitCode);
	}

	/** 壊れた項目のローカルヘッダの後ろ（圧縮データ）を壊す */
	private static void corruptFirstEntry(File zip) throws Exception {
		byte[] b = Files.readAllBytes(zip.toPath());
		int data = 30 + ((b[26] & 0xff) | ((b[27] & 0xff) << 8)) + ((b[28] & 0xff) | ((b[29] & 0xff) << 8));
		for (int i = data + 40; i < data + 80; i++) b[i] = (byte)0xff;
		Files.write(zip.toPath(), b);
	}

	private static byte[] bigText() {
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 2000; i++) big.append("壊れる本の").append(i).append("行目です。\n");
		return big.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** テキストが何冊あっても、飛ばしたことは 1 回だけ出す（本ごとに読み直しても重ねない） */
	@Test
	public void aSkippedTextIsReportedOnceForAMultiBookZip() throws Exception {
		File src = tempFolder.newFolder("msrc");
		File dst = tempFolder.newFolder("mdst");
		File zip = new File(src, "three.zip");
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
			zos.putNextEntry(new java.util.zip.ZipEntry("1_bad.txt"));
			zos.write(bigText());
			zos.closeEntry();
			for (String t : new String[]{ "二冊目", "三冊目" }) {
				zos.putNextEntry(new java.util.zip.ZipEntry(t + ".txt"));
				zos.write((t + "\n著\n\n　本文です。\n").getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		corruptFirstEntry(zip);
		File ini = tempFolder.newFile("mempty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), zip.getPath());

		assertTrue(lastLog, new File(dst, "[著] 二冊目.epub").isFile());
		assertTrue(lastLog, new File(dst, "[著] 三冊目.epub").isFile());
		assertEquals("飛ばしたことは 1 回だけ:\n" + lastLog, 1, lastLog.split("テキストが読めないので飛ばします", -1).length - 1);
		assertEquals(1, exitCode);
	}

	/** 画像と同じ名前の壊れたテキスト（01.txt と 01.png）があっても、画像だけの本として変換できる（テキストを画像として開かない） */
	@Test
	public void aCorruptTextNamedLikeAnImageDoesNotBreakAnImageOnlyBook() throws Exception {
		File src = tempFolder.newFolder("isrc");
		File dst = tempFolder.newFolder("idst");
		File zip = new File(src, "comic.zip");
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
			zos.putNextEntry(new java.util.zip.ZipEntry("01.txt"));
			zos.write(bigText());
			zos.closeEntry();
			for (String n : new String[]{ "01.png", "02.png" }) {
				zos.putNextEntry(new java.util.zip.ZipEntry(n));
				java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(60, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
				javax.imageio.ImageIO.write(img, "png", zos);
				zos.closeEntry();
			}
		}
		corruptFirstEntry(zip);
		File ini = tempFolder.newFile("iempty.ini");

		int exitCode = run("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), zip.getPath());

		File[] epubs = dst.listFiles((d, n) -> n.endsWith(".epub"));
		assertEquals("画像だけの本ができていない:\n" + lastLog, 1, epubs.length);
		int images = 0;
		try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(epubs[0])) {
			for (java.util.zip.ZipEntry e : java.util.Collections.list(z.entries())) if (e.getName().startsWith("OPS/images/")) images++;
		}
		assertEquals("画像は 2 枚（テキストを画像として書かない）", 2, images);
		assertEquals("飛ばしたテキストは失敗として数える", 1, exitCode);
	}
}
