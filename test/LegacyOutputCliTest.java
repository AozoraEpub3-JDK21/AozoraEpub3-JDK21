import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** コマンドラインが、出力の拡張子と -device を writer に渡す。設定はオフで揃える */
public class LegacyOutputCliTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String classPath;

	/** run は java.class.path から template/ の場所を決める。jar でない値なら、今のフォルダ（リポジトリ）を見る */
	@Before
	public void useTemplatesInTheRepository() {
		classPath = System.getProperty("java.class.path");
		System.setProperty("java.class.path", ".");
	}

	@After
	public void restoreClassPath() {
		System.setProperty("java.class.path", classPath);
	}

	private String iniText = "LegacyOutput=\n";

	private File ini() throws Exception {
		File ini = File.createTempFile("off", ".ini", tempFolder.getRoot());
		Files.write(ini.toPath(), iniText.getBytes(StandardCharsets.UTF_8));
		return ini;
	}

	private File text() throws Exception {
		File txt = File.createTempFile("sample", ".txt", tempFolder.getRoot());
		Files.write(txt.toPath(), "表題\n著者\n\n本文です。\n".getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	private File images() throws Exception {
		File zip = tempFolder.newFile("images.zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			zos.putNextEntry(new ZipEntry("p0.png"));
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB), "png", png);
			zos.write(png.toByteArray());
			zos.closeEntry();
		}
		return zip;
	}

	/** 変換して、出来た本に toc.ncx があるかを返す */
	private boolean hasNcx(File input, String... options) throws Exception {
		File out = tempFolder.newFolder();
		String[] head = {"-i", ini().getPath(), "-enc", "UTF-8", "-of", "-d", out.getPath()};
		String[] args = new String[head.length + options.length + 1];
		System.arraycopy(head, 0, args, 0, head.length);
		System.arraycopy(options, 0, args, head.length, options.length);
		args[args.length - 1] = input.getPath();
		//main は失敗すると System.exit するので、試験の JVM ごと落ちる。run を呼ぶ
		assertEquals(0, AozoraEpub3.run(args));
		File[] books = out.listFiles((d, n) -> n.endsWith(".epub"));
		assertNotNull(books);
		assertEquals(1, books.length);
		try (ZipFile zf = new ZipFile(books[0])) {
			return zf.getEntry("OPS/toc.ncx") != null;
		}
	}

	@Test
	public void plainEpubFollowsTheSetting() throws Exception {
		assertEquals(false, hasNcx(text(), "-ext", ".epub"));
		iniText = "LegacyOutput=1\n";
		assertEquals(true, hasNcx(text(), "-ext", ".epub"));
	}

	/** キーを持たない設定では、既定どおり出さない */
	@Test
	public void settingsWithoutTheKeyOmitThem() throws Exception {
		iniText = "";
		assertEquals(false, hasNcx(text()));
	}

	@Test
	public void koboExtensionKeepsTheElements() throws Exception {
		assertEquals(true, hasNcx(text(), "-ext", ".kepub.epub"));
	}

	@Test
	public void koboExtensionKeepsThemOnImageBooks() throws Exception {
		assertEquals(true, hasNcx(images(), "-ext", ".fxl.kepub.epub"));
	}

	@Test
	public void deviceKindleKeepsThemOnTextBooks() throws Exception {
		assertEquals(true, hasNcx(text(), "-device", "kindle"));
	}

	@Test
	public void deviceKindleKeepsThemOnImageBooks() throws Exception {
		assertEquals(true, hasNcx(images(), "-device", "kindle"));
	}
}
