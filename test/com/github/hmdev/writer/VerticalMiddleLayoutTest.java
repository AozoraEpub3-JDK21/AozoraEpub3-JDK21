package com.github.hmdev.writer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.SectionInfo;

/**
 * 監査項目34: 縦書きの左右中央の節の出力の組み方を、事前走査からテンプレートまで通して確かめる。
 * 1列に収まる短い節だけ横書きの親の中に縦書きのブロックを置き、
 * 1列に収まらない行のある節・ページ左・表題ページ・Kindle 向け・横書きの本は従来の表組みのまま。
 * （CLI の main/run は Gradle のテスト JVM では template/ を解決できないので、
 * Epub3WriterErrorHandlingTest と同じく template のパスを明示する）
 */
public class VerticalMiddleLayoutTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Path projectRoot;
	private String templatePath;
	private VelocityEngine velocityEngine;
	private Path txt;

	static final String TEXT = String.join("\n",
			"表題",
			"著者",
			"",
			"［＃改ページ］",
			"［＃ページの左右中央］",
			"［＃３字下げ］［＃大見出し］第一章［＃大見出し終わり］",
			"［＃改ページ］",
			"本文。",
			"［＃改ページ］",
			"［＃ページの左右中央］",
			"［＃３字下げ］［＃大見出し］一列に収まらないほど長い章題を持つ中扉です［＃大見出し終わり］",
			"［＃改ページ］",
			"本文。",
			"［＃改ページ］",
			"［＃ページの左］",
			"［＃地付き］ページ左",
			"［＃改ページ］",
			"本文。") + "\n";

	@Before
	public void setUp() throws Exception {
		projectRoot = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(projectRoot.resolve("template"))) {
			Path testClasses = Paths.get(
				VerticalMiddleLayoutTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			projectRoot = testClasses.getParent().getParent().getParent();
		}
		Path templateRoot = projectRoot.resolve("template");
		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class",
			"org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", templateRoot.toString());
		velocityEngine = new VelocityEngine(vp);
		templatePath = templateRoot.toString();
		if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) templatePath += "/";
		txt = tempFolder.newFile("sample.txt").toPath();
		Files.write(txt, TEXT.getBytes(StandardCharsets.UTF_8));
	}

	/** CLI（AozoraEpub3.run）と同じ順番で変換する */
	private File convert(boolean vertical, boolean kindle) throws Exception {
		Epub3Writer writer = new Epub3Writer(templatePath, velocityEngine);
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600,
			SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
		writer.setIsKindle(kindle);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		bookInfo.vertical = vertical;
		converter.vertical = vertical;
		bookInfo.titlePageType = BookInfo.TITLE_MIDDLE;
		File epub = tempFolder.newFile((vertical ? "v" : "h") + (kindle ? "k" : "") + ".epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		return epub;
	}

	static String read(ZipFile zf, String name) throws Exception {
		ZipEntry e = zf.getEntry(name);
		assertNotNull(name + " が含まれること", e);
		try (InputStream is = zf.getInputStream(e)) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void 短い中扉だけ横書きの親の中に縦書きのブロックを置く() throws Exception {
		try (ZipFile zf = new ZipFile(convert(true, false))) {
			String shortMiddle = read(zf, "OPS/xhtml/0001.xhtml");
			assertTrue(shortMiddle, shortMiddle.contains("lang=\"ja\" class=\"hltr\">"));
			assertTrue(shortMiddle, shortMiddle.contains("<div class=\"vrtl middle\">"));
			assertFalse(shortMiddle, shortMiddle.contains("<table"));
			assertTrue(shortMiddle, shortMiddle.contains("第一章"));
			assertTrue(shortMiddle, shortMiddle.trim().endsWith("</div>\n</body>\n</html>"));

			String longMiddle = read(zf, "OPS/xhtml/0003.xhtml");
			assertTrue(longMiddle, longMiddle.contains("<table class=\"middle\"><tr><td>"));
			assertTrue(longMiddle, longMiddle.contains("</td></tr></table>"));
			assertFalse(longMiddle, longMiddle.contains("hltr"));
			assertFalse(longMiddle, longMiddle.contains("vrtl"));

			String bottom = read(zf, "OPS/xhtml/0005.xhtml");
			assertTrue(bottom, bottom.contains("<table class=\"bottom\"><tr><td>"));
			assertFalse(bottom, bottom.contains("hltr"));

			String title = read(zf, "OPS/xhtml/title.xhtml");
			assertTrue(title, title.contains("<table class=\"middle\"><tr><td>"));
			assertFalse(title, title.contains("hltr"));

			String css = read(zf, "OPS/css/vertical_middle.css");
			assertTrue(css, css.contains("html.hltr div.vrtl {"));
		}
	}

	@Test
	public void Kindle向けは従来の表組み() throws Exception {
		try (ZipFile zf = new ZipFile(convert(true, true))) {
			String shortMiddle = read(zf, "OPS/xhtml/0001.xhtml");
			assertTrue(shortMiddle, shortMiddle.contains("<div class=\"kindle_outer\"><div class=\"kindle_inner\">"));
			assertTrue(shortMiddle, shortMiddle.contains("<table class=\"middle\"><tr><td>"));
			assertFalse(shortMiddle, shortMiddle.contains("hltr"));
		}
	}

	@Test
	public void 横書きの本は従来の表組み() throws Exception {
		try (ZipFile zf = new ZipFile(convert(false, false))) {
			String shortMiddle = read(zf, "OPS/xhtml/0001.xhtml");
			assertTrue(shortMiddle, shortMiddle.contains("<table class=\"middle\"><tr><td>"));
			assertFalse(shortMiddle, shortMiddle.contains("hltr"));
		}
	}
}
