import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
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
 * CLI の -device kindle は、画像だけの本も Kindle 向けに書く（GUI と同じ）。
 * 前は画像の本の writer に Kindle の印が届かず、見開きの組（page-spread）が出なかった（internal #81）。
 */
public class AozoraEpub3KindleImageTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String convert(String... extra) throws Exception {
		File zip = tempFolder.newFile("pages" + System.nanoTime() + ".zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			for (int i = 0; i < 5; i++) {
				zos.putNextEntry(new ZipEntry(String.format("%02d.png", i)));
				ImageIO.write(new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB), "png", zos);
				zos.closeEntry();
			}
		}
		File dst = tempFolder.newFolder();
		File ini = tempFolder.newFile();
		List<String> args = new ArrayList<>(List.of("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), "-c", "0"));
		args.addAll(List.of(extra));
		args.add(zip.getPath());
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする（AozoraEpub3TextBmpTest と同じ）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			assertEquals(0, AozoraEpub3.run(args.toArray(new String[0])));
		} finally {
			System.setOut(orgOut);
			Velocity.reset();
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		File[] epubs = dst.listFiles((d, n) -> n.endsWith(".epub"));
		assertEquals(1, epubs.length);
		try (ZipFile zf = new ZipFile(epubs[0])) {
			return new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<String> spreads(String opf) {
		List<String> sides = new ArrayList<>();
		Matcher m = Pattern.compile("<itemref idref=\"sec[^\"]+\" linear=\"yes\"(?: properties=\"page-spread-(left|right)\")?").matcher(opf);
		while (m.find()) sides.add(m.group(1));
		return sides;
	}

	@Test
	public void kindleDeviceGivesImageOnlyBooksSpreads() throws Exception {
		String opf = convert("-device", "kindle");
		assertEquals(opf, List.of("right", "left", "right", "left"), spreads(opf));
		assertTrue("Kindle 用の CSS が無い:\n" + opf, opf.contains("css/kindle_image.css"));
	}

	/** -device kindle を付けなければ、これまでどおり page-spread は付けない */
	@Test
	public void withoutKindleThereAreNoSpreads() throws Exception {
		List<String> none = new ArrayList<>();
		for (int i = 0; i < 4; i++) none.add(null);
		String opf = convert();
		assertEquals(opf, none, spreads(opf));
	}
}
