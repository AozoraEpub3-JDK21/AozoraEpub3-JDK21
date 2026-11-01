package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

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
 * 画像だけの本は固定レイアウトなので、全ページに viewport が要る。
 * 無いと epubcheck が HTM-046 のエラーを出し、Apple Books と Thorium では上下が切れ、
 * Google Play ブックスでは絵が出ない。
 */
public class ImageOnlyFixedLayoutTest {

	private static final Pattern VIEWPORT = Pattern.compile("<meta name=\"viewport\" content=\"width=(\\d+), height=(\\d+)\"/>");
	private static final Pattern IMAGE = Pattern.compile("(?:src|xlink:href)=\"\\.\\./images/([^\"]+)\"");

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Path projectRoot;
	private String templatePath;

	@Before
	public void setUp() throws Exception {
		projectRoot = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(projectRoot.resolve("template"))) {
			Path testClasses = Paths.get(
				ImageOnlyFixedLayoutTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			projectRoot = testClasses.getParent().getParent().getParent();
		}
		templatePath = projectRoot.resolve("template").toString();
		if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) templatePath += "/";
	}

	private static void setParams(Epub3Writer writer, boolean svg) {
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600,
			SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, svg, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
	}

	private File writeImageBook(boolean svg, boolean coverPage) throws Exception {
		File zip = projectRoot.resolve("test_data/test_png.zip").toFile();
		org.junit.Assume.assumeTrue("test_data/test_png.zip が無い", zip.isFile());
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, svg);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		if (coverPage) {
			bookInfo.insertCoverPage = true;
			bookInfo.coverImageIndex = 0;
		}
		File epub = tempFolder.newFile("image" + svg + coverPage + ".epub");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		return epub;
	}

	private static String read(ZipFile zf, String name) throws Exception {
		ZipEntry e = zf.getEntry(name);
		assertTrue(name + " が無い", e != null);
		return new String(zf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
	}

	/** spine に載っている XHTML を順に返す */
	private static List<String> spinePages(ZipFile zf) throws Exception {
		String opf = read(zf, "OPS/package.opf");
		List<String> pages = new ArrayList<>();
		Matcher ref = Pattern.compile("<itemref idref=\"([^\"]+)\"").matcher(opf);
		while (ref.find()) {
			Matcher item = Pattern.compile("<item id=\"" + Pattern.quote(ref.group(1)) + "\" href=\"([^\"]+)\"").matcher(opf);
			assertTrue(ref.group(1) + " が manifest に無い", item.find());
			pages.add("OPS/" + item.group(1));
		}
		return pages;
	}

	/** 全ページの viewport が、そのページの画像の実際の寸法と同じであること */
	private void assertEveryPageHasViewportOfItsImage(File epub) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			List<String> pages = spinePages(zf);
			assertFalse("spine が空", pages.isEmpty());
			for (String page : pages) {
				String xhtml = read(zf, page);
				Matcher vp = VIEWPORT.matcher(xhtml);
				assertTrue(page + " に viewport が無い: " + xhtml, vp.find());
				assertTrue(page + " で viewport が head の外にある", vp.start() < xhtml.indexOf("</head>"));
				assertFalse(page + " に viewport が 2 つある", vp.find());
				vp.reset(); vp.find();
				Matcher img = IMAGE.matcher(xhtml);
				assertTrue(page + " に画像が無い", img.find());
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(
					zf.getInputStream(zf.getEntry("OPS/images/" + img.group(1))).readAllBytes()));
				assertEquals(page + " の幅", image.getWidth(), Integer.parseInt(vp.group(1)));
				assertEquals(page + " の高さ", image.getHeight(), Integer.parseInt(vp.group(2)));
			}
		}
	}

	@Test
	public void imagePagesHaveViewportOfTheirImage() throws Exception {
		assertEveryPageHasViewportOfItsImage(writeImageBook(false, false));
	}

	@Test
	public void coverPageHasViewportOfItsImage() throws Exception {
		File epub = writeImageBook(false, true);
		try (ZipFile zf = new ZipFile(epub)) {
			assertTrue("表紙のページが spine に無い", spinePages(zf).contains("OPS/xhtml/cover.xhtml"));
		}
		assertEveryPageHasViewportOfItsImage(epub);
	}

	@Test
	public void svgPagesHaveViewportAndTitle() throws Exception {
		File epub = writeImageBook(true, false);
		assertEveryPageHasViewportOfItsImage(epub);
		try (ZipFile zf = new ZipFile(epub)) {
			for (String page : spinePages(zf)) {
				assertTrue(page + " に title が無い", read(zf, page).contains("<title>"));
			}
		}
	}

	@Test
	public void renditionLayoutOccursOnce() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			try (ZipFile zf = new ZipFile(writeImageBook(svg, false))) {
				String opf = read(zf, "OPS/package.opf");
				Matcher m = Pattern.compile("property=\"rendition:layout\">([^<]+)<").matcher(opf);
				assertTrue("svg=" + svg + " で rendition:layout が無い", m.find());
				assertEquals("pre-paginated", m.group(1));
				assertFalse("svg=" + svg + " で rendition:layout が重複している", m.find());
			}
		}
	}

	/** リフローの本（表紙ページつき）に viewport を入れない。リフローには意味が無く、epubcheck が指摘する */
	@Test
	public void reflowableBookHasNoViewport() throws Exception {
		Path txt = tempFolder.newFile("book.txt").toPath();
		Files.write(txt, "表題\n著者\n\n本文です。\n［＃改ページ］\n二ページ目です。\n".getBytes(StandardCharsets.UTF_8));
		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").toString());
		Epub3Writer writer = new Epub3Writer(templatePath, new VelocityEngine(vp));
		setParams(writer, false);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		File epub = tempFolder.newFile("text.epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		try (ZipFile zf = new ZipFile(epub)) {
			List<String> pages = spinePages(zf);
			assertFalse("spine が空", pages.isEmpty());
			for (String page : pages) {
				assertFalse(page + " に viewport がある", read(zf, page).contains("name=\"viewport\""));
			}
			assertTrue(read(zf, "OPS/package.opf").contains("<meta property=\"rendition:layout\">reflowable</meta>"));
		}
	}
}
