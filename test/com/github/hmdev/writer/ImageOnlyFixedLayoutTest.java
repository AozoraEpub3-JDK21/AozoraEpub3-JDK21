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
		setParams(writer, svg, 0, 0, 0, SectionInfo.IMAGE_SIZE_TYPE_HEIGHT);
	}

	private static void setParams(Epub3Writer writer, boolean svg, int resizeW, int resizeH, int rotate, int sizeType) {
		writer.setImageParam(600, 800, 600, 800, resizeW, resizeH, 480, 640, 600,
			sizeType, true, svg, rotate,
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

	/** 横長と縦長の画像を入れた zip を作る */
	private File imageZip() throws Exception {
		File zip = File.createTempFile("wide", ".zip", tempFolder.getRoot());
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			int[][] sizes = {{2000, 1000}, {800, 1200}, {1600, 1000}};
			for (int i = 0; i < sizes.length; i++) {
				zos.putNextEntry(new ZipEntry("p" + i + ".png"));
				java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
				ImageIO.write(new BufferedImage(sizes[i][0], sizes[i][1], BufferedImage.TYPE_INT_RGB), "png", png);
				zos.write(png.toByteArray());
				zos.closeEntry();
			}
		}
		return zip;
	}

	/** 手元の zip から画像だけの本を作る。cover を指定すると表紙ページも入れる */
	private File writeBook(File zip, Epub3ImageWriter writer, boolean coverPage, boolean tocPage, String name) throws Exception {
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		bookInfo.insertTocPage = tocPage;
		if (coverPage) {
			bookInfo.insertCoverPage = true;
			bookInfo.coverImageIndex = 0;
		}
		File epub = tempFolder.newFile(name + ".epub");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		return epub;
	}

	/** spine に載っている XHTML を順に返す */
	private static List<String> spinePages(ZipFile zf) throws Exception {
		String opf = read(zf, "OPS/package.opf");
		List<String> pages = new ArrayList<>();
		//目次はリフローのページなので除く
		opf = opf.replaceAll("<itemref idref=\"nav\"[^>]*/>", "");
		Matcher ref = Pattern.compile("<itemref idref=\"([^\"]+)\"").matcher(opf);
		while (ref.find()) {
			Matcher item = Pattern.compile("<item id=\"" + Pattern.quote(ref.group(1)) + "\" href=\"([^\"]+)\"").matcher(opf);
			assertTrue(ref.group(1) + " が manifest に無い", item.find());
			pages.add("OPS/" + item.group(1));
		}
		return pages;
	}

	/** SVG のページ（表紙も）の viewBox と image の寸法が、そのページの viewport と同じであること */
	private void assertSvgSizesMatchViewport(File epub) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			for (String page : spinePages(zf)) {
				String xhtml = read(zf, page);
				Matcher vp = VIEWPORT.matcher(xhtml);
				assertTrue(page + " に viewport が無い: " + xhtml, vp.find());
				assertTrue(page + " の viewBox が viewport と違う: " + xhtml,
					xhtml.contains("viewBox=\"0 0 " + vp.group(1) + " " + vp.group(2) + "\""));
				assertTrue(page + " の image の寸法が viewport と違う: " + xhtml,
					xhtml.contains("<image width=\"" + vp.group(1) + "\" height=\"" + vp.group(2) + "\""));
			}
		}
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
		assertSvgSizesMatchViewport(epub);
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

	/** 縮小すると出力した画像の大きさが元と変わる。viewport は出力した大きさに合わせる */
	@Test
	public void viewportFollowsResizedImage() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false, 500, 500, 0, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
		File epub = writeBook(imageZip(), writer, false, false, "resized");
		assertEveryPageHasViewportOfItsImage(epub);
		try (ZipFile zf = new ZipFile(epub)) {
			assertTrue("縮小されていない: " + read(zf, "OPS/xhtml/0001.xhtml"), read(zf, "OPS/xhtml/0001.xhtml").contains("width=500"));
		}
	}

	/** 回転すると縦と横が入れ替わる */
	@Test
	public void viewportFollowsRotatedImage() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
			setParams(writer, svg, 0, 0, 90, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
			File epub = writeBook(imageZip(), writer, true, false, "rotated" + svg);
			assertEveryPageHasViewportOfItsImage(epub);
			if (svg) assertSvgSizesMatchViewport(epub);
		}
	}

	/** 別ファイルの表紙（w x h）をつけた画像だけの本を作る */
	private File writeBookWithExternalCover(int w, int h, boolean svg, int sizeType, String name) throws Exception {
		File cover = File.createTempFile("cover", ".png", tempFolder.getRoot());
		ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", cover);
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, svg, 0, 0, 0, sizeType);
		File zip = imageZip();
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		bookInfo.insertCoverPage = true;
		bookInfo.coverFileName = cover.getPath();
		File epub = tempFolder.newFile(name + ".epub");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		return epub;
	}

	/** 別ファイルの表紙は縮小して入る。ページの大きさは縮小した後の画像に合わせる */
	@Test
	public void externalCoverPageHasViewportOfTheStoredImage() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			File epub = writeBookWithExternalCover(3000, 4000, svg, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, "extcover" + svg);
			assertEveryPageHasViewportOfItsImage(epub);
			if (svg) assertSvgSizesMatchViewport(epub);
			try (ZipFile zf = new ZipFile(epub)) {
				assertTrue(read(zf, "OPS/xhtml/cover.xhtml"), read(zf, "OPS/xhtml/cover.xhtml").contains("width=600, height=800"));
			}
		}
	}

	/** 表紙のページは表紙の縦横比で幅か高さに合わせる（ページの出力を画像の後へ動かしても効くこと） */
	@Test
	public void externalCoverPageFitsByItsAspect() throws Exception {
		try (ZipFile zf = new ZipFile(writeBookWithExternalCover(2000, 1000, false, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, "fitw"))) {
			String cover = read(zf, "OPS/xhtml/cover.xhtml");
			assertTrue(cover, cover.contains("<body class=\"fit_w\">"));
		}
		try (ZipFile zf = new ZipFile(writeBookWithExternalCover(1000, 2000, false, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, "fith"))) {
			String cover = read(zf, "OPS/xhtml/cover.xhtml");
			assertTrue(cover, cover.contains("<body class=\"fit_h\">"));
		}
	}

	/** BMP はページにならないので、original-resolution は次の画像の大きさになる */
	@Test
	public void originalResolutionSkipsImageThatIsNotAPage() throws Exception {
		File zip = File.createTempFile("bmpfirst", ".zip", tempFolder.getRoot());
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			String[][] images = {{"p0.bmp", "bmp", "800", "1100"}, {"p1.png", "png", "810", "1110"}};
			for (String[] image : images) {
				zos.putNextEntry(new ZipEntry(image[0]));
				java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
				ImageIO.write(new BufferedImage(Integer.parseInt(image[2]), Integer.parseInt(image[3]), BufferedImage.TYPE_INT_RGB), image[1], out);
				zos.write(out.toByteArray());
				zos.closeEntry();
			}
		}
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false);
		try (ZipFile zf = new ZipFile(writeBook(zip, writer, false, false, "bmpfirst"))) {
			String opf = read(zf, "OPS/package.opf");
			assertTrue(opf, opf.contains("<meta name=\"original-resolution\" content=\"810x1110\"/>"));
		}
	}

	/** 高さの割合は画面に対する値なので、ページが画像の大きさの本では画像が小さくなる */
	@Test
	public void imagePagesHaveNoPercentHeight() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false, 0, 0, 0, SectionInfo.IMAGE_SIZE_TYPE_HEIGHT);
		File epub = writeBook(imageZip(), writer, false, false, "height");
		try (ZipFile zf = new ZipFile(epub)) {
			for (String page : spinePages(zf)) {
				assertFalse(page + " に割合の高さがある: " + read(zf, page), read(zf, page).contains("min-height:"));
			}
		}
	}

	/** 目次ページは GUI で入る。文字のページなのでリフローにする（viewport が要らない） */
	@Test
	public void tocPageInSpineIsReflowable() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false);
		File epub = writeBook(imageZip(), writer, false, true, "toc");
		try (ZipFile zf = new ZipFile(epub)) {
			assertTrue(read(zf, "OPS/package.opf"), read(zf, "OPS/package.opf").contains("<itemref idref=\"nav\" linear=\"yes\" properties=\"rendition:layout-reflowable\"/>"));
		}
		assertEveryPageHasViewportOfItsImage(epub);
	}

	/** original-resolution は表紙の有無に関わらず、出力した先頭のページの大きさ */
	@Test
	public void originalResolutionIsTheFirstPage() throws Exception {
		for (boolean cover : new boolean[]{false, true}) {
			Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
			setParams(writer, false, 500, 500, 0, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
			File epub = writeBook(imageZip(), writer, cover, false, "res" + cover);
			try (ZipFile zf = new ZipFile(epub)) {
				String opf = read(zf, "OPS/package.opf");
				assertFalse("変数のまま出ている: " + opf, opf.contains("${"));
				assertTrue(opf, opf.contains("<meta name=\"original-resolution\" content=\"500x250\"/>"));
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
		File cover = File.createTempFile("cover", ".png", tempFolder.getRoot());
		ImageIO.write(new BufferedImage(1000, 1400, BufferedImage.TYPE_INT_RGB), "png", cover);
		bookInfo.insertCoverPage = true;
		bookInfo.coverFileName = cover.getPath();
		bookInfo.insertTocPage = true;
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
			assertTrue("表紙のページが無い", pages.contains("OPS/xhtml/cover.xhtml"));
			assertTrue(read(zf, "OPS/package.opf").contains("<meta property=\"rendition:layout\">reflowable</meta>"));
			//本全体がリフローなので、目次に properties を付けない
			assertTrue(read(zf, "OPS/package.opf"), read(zf, "OPS/package.opf").contains("<itemref idref=\"nav\" linear=\"yes\"/>"));
		}
	}
}
