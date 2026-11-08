import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本文の本の BMP（挿絵・表紙）と、形式を直す表紙（internal #77・#64）。
 * EPUB に BMP は入れられない（RSC-032）。本文の本は挿絵のページと OPF を画像より先に書くので、
 * 読めない画像を外せない。白い画像を入れ、OPF に載った画像は必ず読める物にする。
 */
public class AozoraEpub3TextBmpTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final Pattern ITEM = Pattern.compile("<item [^>]*href=\"(images/[^\"]+)\" media-type=\"([^\"]+)\"");

	private static byte[] bmp(int w, int h) throws Exception {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setColor(new java.awt.Color(30, 120, 200));
		g.fillRect(0, 0, w, h);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "bmp", out);
		return out.toByteArray();
	}

	/** ヘッダは読めるが絵が途中で切れた BMP */
	private static byte[] truncatedBmp() throws Exception {
		return java.util.Arrays.copyOf(bmp(300, 400), 2000);
	}

	private final StringBuilder log = new StringBuilder();

	/** CLI で変換して、できた EPUB を返す */
	private File convert(String... args) throws Exception {
		File dst = tempFolder.newFolder();
		File ini = tempFolder.newFile();
		List<String> all = new ArrayList<>(List.of("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath()));
		all.addAll(List.of(args));
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする（AozoraEpub3ImagePageTextTest と同じ）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
			assertEquals(0, AozoraEpub3.run(all.toArray(new String[0])));
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		log.append(baos.toString(StandardCharsets.UTF_8));
		File[] epubs = dst.listFiles((d, n) -> n.endsWith(".epub"));
		assertEquals(1, epubs.length);
		return epubs[0];
	}

	/** OPF に載った画像は全部 zip に在り、名前・media-type・中身の形式がそろい、読める。images の項目は全部 OPF に在る */
	private static List<String> assertImagesAreReadable(File epub) throws Exception {
		List<String> hrefs = new ArrayList<>();
		try (ZipFile zf = new ZipFile(epub)) {
			String opf = new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
			Matcher m = ITEM.matcher(opf);
			while (m.find()) {
				String href = m.group(1), type = m.group(2);
				hrefs.add(href);
				ZipEntry e = zf.getEntry("OPS/" + href);
				assertTrue(href + " が zip に無い", e != null);
				byte[] bytes = zf.getInputStream(e).readAllBytes();
				assertTrue(href + " が読めない（" + bytes.length + " バイト）", ImageIO.read(new ByteArrayInputStream(bytes)) != null);
				String ext = href.substring(href.lastIndexOf('.') + 1);
				String expected = ext.equals("jpg") ? "image/jpeg" : "image/" + ext;
				assertEquals(href, expected, type);
				String format = ImageIO.getImageReaders(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))).next().getFormatName().toLowerCase();
				assertEquals(href + " の中身", expected, format.equals("jpeg") ? "image/jpeg" : "image/" + format);
			}
			for (ZipEntry e : java.util.Collections.list(zf.entries())) {
				if (e.getName().startsWith("OPS/images/")) assertTrue(e.getName() + " が OPF に無い", hrefs.contains(e.getName().substring(4)));
			}
		}
		return hrefs;
	}

	private File textZip(String text, Object... entries) throws Exception {
		File zip = tempFolder.newFile("book" + System.nanoTime() + ".zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			zos.putNextEntry(new ZipEntry("t.txt"));
			zos.write(("題名\n著者\n\n" + text).getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			for (int i = 0; i < entries.length; i += 2) {
				zos.putNextEntry(new ZipEntry((String)entries[i]));
				zos.write((byte[])entries[i + 1]);
				zos.closeEntry();
			}
		}
		return zip;
	}

	private File plainText() throws Exception {
		File txt = new File(tempFolder.newFolder(), "plain.txt");
		Files.write(txt.toPath(), "題名\n著者\n\n本文\n".getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	/** zip の本文の BMP の挿絵は PNG で入る。読めない BMP は白い画像になる（外すとページと OPF が無い画像を指す） */
	@Test
	public void bmpIllustrationsInAZipBecomePng() throws Exception {
		File zip = textZip("本文一\n［＃挿絵（a.bmp）入る］\n本文二\n［＃挿絵（bad.bmp）入る］\n", "a.bmp", bmp(300, 400), "bad.bmp", truncatedBmp());
		List<String> images = assertImagesAreReadable(convert(zip.getPath()));
		assertEquals(List.of("images/0001.png", "images/0002.png"), images);
		assertTrue(log.toString(), log.toString().contains("画像形式変換: a.bmp → 0001.png"));
		assertTrue(log.toString(), log.toString().contains("画像が読み込めないので白い画像を入れます : bad.bmp"));
	}

	/** フォルダのテキストの BMP の挿絵も同じ（ファイルから読む道） */
	@Test
	public void bmpIllustrationsNextToATextFileBecomePng() throws Exception {
		File dir = tempFolder.newFolder();
		File txt = new File(dir, "t.txt");
		Files.write(txt.toPath(), "題名\n著者\n\n本文一\n［＃挿絵（a.bmp）入る］\n本文二\n［＃挿絵（bad.bmp）入る］\n".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(dir, "a.bmp").toPath(), bmp(300, 400));
		Files.write(new File(dir, "bad.bmp").toPath(), truncatedBmp());
		assertEquals(List.of("images/0001.png", "images/0002.png"), assertImagesAreReadable(convert(txt.getPath())));
	}

	/** 表紙に BMP のファイルを指定できる（前は「表紙画像フォーマットエラー」で表紙なし）。読めない BMP は白い表紙 */
	@Test
	public void bmpCoverFileBecomesPng() throws Exception {
		File cover = tempFolder.newFile("cover.bmp");
		Files.write(cover.toPath(), bmp(300, 400));
		assertEquals(List.of("images/0000.png"), assertImagesAreReadable(convert("-c", cover.getPath(), plainText().getPath())));
		Files.write(cover.toPath(), truncatedBmp());
		assertEquals(List.of("images/0000.png"), assertImagesAreReadable(convert("-c", cover.getPath(), plainText().getPath())));
	}

	/** Kindle 向けの JPEG でない表紙は、OPF にも image/jpeg で載る（前は image/png のまま＝OPF-029）。読めなければ白い JPEG */
	@Test
	public void kindleCoverIsListedAsTheJpegItIs() throws Exception {
		File cover = tempFolder.newFile("cover.png");
		ImageIO.write(new BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB), "png", cover);
		assertEquals(List.of("images/0000.jpg"), assertImagesAreReadable(convert("-device", "kindle", "-c", cover.getPath(), plainText().getPath())));
		File bad = tempFolder.newFile("bad.bmp");
		Files.write(bad.toPath(), truncatedBmp());
		assertEquals(List.of("images/0000.jpg"), assertImagesAreReadable(convert("-device", "kindle", "-c", bad.getPath(), plainText().getPath())));
		assertFalse(log.toString(), log.toString().contains("表紙画像取得エラー"));
	}
}
