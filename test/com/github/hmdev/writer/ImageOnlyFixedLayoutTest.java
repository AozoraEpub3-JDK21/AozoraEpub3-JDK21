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
 * ページごとに大きさを変えると Apple Books が先頭と違う形のページを切るので、本全体で 1 つにする。
 */
public class ImageOnlyFixedLayoutTest {

	private static final Pattern VIEWPORT = Pattern.compile("<meta name=\"viewport\" content=\"width=(\\d+), height=(\\d+)\"/>");
	private static final Pattern IMAGE = Pattern.compile("(?:src|xlink:href)=\"\\.\\./images/([^\"]+)\"");
	private static final Pattern PLACED_IMG = Pattern.compile("img\\{position:absolute;left:(\\d+)px;top:(\\d+)px;width:(\\d+)px;height:(\\d+)px;");
	private static final Pattern PLACED_SVG = Pattern.compile("<image x=\"(\\d+)\" y=\"(\\d+)\" width=\"(\\d+)\" height=\"(\\d+)\"");

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
		return writeBook(zip, writer, coverPage, false, "image" + svg + coverPage);
	}

	private static String read(ZipFile zf, String name) throws Exception {
		ZipEntry e = zf.getEntry(name);
		assertTrue(name + " が無い", e != null);
		return new String(zf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
	}

	/** 横長と縦長の画像を入れた zip を作る */
	private File imageZip() throws Exception {
		return imageZip(new int[][]{{2000, 1000}, {800, 1200}, {1600, 1000}});
	}

	private File imageZip(int[][] sizes) throws Exception {
		File zip = File.createTempFile("wide", ".zip", tempFolder.getRoot());
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
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
		return writeBook(zip, writer, bookInfo -> {
			bookInfo.insertTocPage = tocPage;
			if (coverPage) {
				bookInfo.insertCoverPage = true;
				bookInfo.coverImageIndex = 0;
			}
		}, name);
	}

	private File writeBook(File zip, Epub3ImageWriter writer, java.util.function.Consumer<BookInfo> setup, String name) throws Exception {
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		setup.accept(bookInfo);
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

	/** そのページの画像を zip から読む */
	private static BufferedImage imageOf(ZipFile zf, String xhtml) throws Exception {
		Matcher img = IMAGE.matcher(xhtml);
		assertTrue("画像が無い: " + xhtml, img.find());
		return ImageIO.read(new ByteArrayInputStream(zf.getInputStream(zf.getEntry("OPS/images/" + img.group(1))).readAllBytes()));
	}

	/** 全ページが同じ大きさで、実際の画像が縦横比を保ってページの中央いっぱいに置かれていること */
	private void assertEveryPageIs(File epub, int width, int height) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			assertTrue(read(zf, "OPS/package.opf"), read(zf, "OPS/package.opf").contains("<meta name=\"original-resolution\" content=\"" + width + "x" + height + "\"/>"));
			List<String> pages = spinePages(zf);
			assertFalse("spine が空", pages.isEmpty());
			for (String page : pages) {
				String xhtml = read(zf, page);
				Matcher vp = VIEWPORT.matcher(xhtml);
				assertTrue(page + " に viewport が無い: " + xhtml, vp.find());
				assertTrue(page + " で viewport が head の外にある", vp.start() < xhtml.indexOf("</head>"));
				assertEquals(page + " の幅", width, Integer.parseInt(vp.group(1)));
				assertEquals(page + " の高さ", height, Integer.parseInt(vp.group(2)));
				assertFalse(page + " に viewport が 2 つある", vp.find());
				assertFalse(page + " の body に class がある: " + xhtml, xhtml.contains("<body class"));
				boolean svg = xhtml.contains("<svg");
				if (svg) assertTrue(page + " の viewBox: " + xhtml, xhtml.contains("viewBox=\"0 0 " + width + " " + height + "\""));
				Matcher placed = (svg ? PLACED_SVG : PLACED_IMG).matcher(xhtml);
				assertTrue(page + " に画像の位置が無い: " + xhtml, placed.find());
				int x = Integer.parseInt(placed.group(1)), y = Integer.parseInt(placed.group(2));
				int w = Integer.parseInt(placed.group(3)), h = Integer.parseInt(placed.group(4));
				assertTrue(page + " がページからはみ出す: " + xhtml, x + w <= width && y + h <= height);
				assertTrue(page + " が左右の中央に無い: " + xhtml, Math.abs(2*x + w - width) <= 1);
				assertTrue(page + " が上下の中央に無い: " + xhtml, Math.abs(2*y + h - height) <= 1);
				assertTrue(page + " がページいっぱいでない: " + xhtml, w == width || h == height);
				BufferedImage image = imageOf(zf, xhtml);
				assertTrue(page + " の縦横比が " + image.getWidth() + "x" + image.getHeight() + " と違う: " + xhtml,
					Math.abs((long)w*image.getHeight() - (long)h*image.getWidth()) <= Math.max(image.getWidth(), image.getHeight()));
			}
		}
	}

	@Test
	public void imagePagesShareOnePageSize() throws Exception {
		assertEveryPageIs(writeImageBook(false, false), 1200, 1600);
	}

	@Test
	public void coverPageSharesThePageSize() throws Exception {
		File epub = writeImageBook(false, true);
		try (ZipFile zf = new ZipFile(epub)) {
			assertTrue("表紙のページが spine に無い", spinePages(zf).contains("OPS/xhtml/cover.xhtml"));
		}
		assertEveryPageIs(epub, 1200, 1600);
	}

	@Test
	public void svgPagesShareOnePageSizeAndHaveTitle() throws Exception {
		File epub = writeImageBook(true, false);
		assertEveryPageIs(epub, 1200, 1600);
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

	/** いちばん多い大きさがページの大きさになる。先頭が横長でも本全体の基準にしない */
	@Test
	public void pageSizeIsTheMostCommonImage() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
			setParams(writer, svg, 0, 0, 0, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
			File zip = imageZip(new int[][]{{1200, 800}, {800, 1200}, {800, 1200}, {3000, 500}});
			assertEveryPageIs(writeBook(zip, writer, false, false, "common" + svg), 800, 1200);
		}
	}

	/** 同数なら先に出てきた大きさ */
	@Test
	public void pageSizeTieGoesToTheFirstImage() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false, 0, 0, 0, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
		assertEveryPageIs(writeBook(imageZip(), writer, false, false, "tie"), 2000, 1000);
	}

	/** 縮小すると出力した画像の大きさが元と変わる。ページは出力した大きさに合わせる */
	@Test
	public void pageSizeFollowsResizedImage() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false, 500, 500, 0, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
		assertEveryPageIs(writeBook(imageZip(), writer, false, false, "resized"), 500, 250);
	}

	/** 回転すると縦と横が入れ替わる */
	@Test
	public void pageSizeFollowsRotatedImage() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
			setParams(writer, svg, 0, 0, 90, SectionInfo.IMAGE_SIZE_TYPE_ASPECT);
			File epub = writeBook(imageZip(), writer, true, false, "rotated" + svg);
			assertEveryPageIs(epub, 1000, 2000);
			//回転が止まってもページと画像は一致するので、回転したことを別に見る
			try (ZipFile zf = new ZipFile(epub)) {
				boolean rotated = false;
				for (String page : spinePages(zf)) {
					BufferedImage image = imageOf(zf, read(zf, page));
					rotated |= image.getWidth() == 1000 && image.getHeight() == 1600;
				}
				assertTrue("1600x1000 の画像が回転していない", rotated);
			}
		}
	}

	/** 別ファイルの表紙（w x h）をつけた画像だけの本を作る */
	private File writeBookWithExternalCover(int w, int h, boolean svg, int sizeType, String name) throws Exception {
		File cover = File.createTempFile("cover", ".png", tempFolder.getRoot());
		ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", cover);
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, svg, 0, 0, 0, sizeType);
		return writeBook(imageZip(), writer, bookInfo -> {
			bookInfo.insertCoverPage = true;
			bookInfo.coverFileName = cover.getPath();
		}, name);
	}

	/** 別ファイルの表紙は数えない。縮小して入り、本文の大きさのページの中に収まる */
	@Test
	public void externalCoverFitsInsideThePageOfTheBody() throws Exception {
		for (boolean svg : new boolean[]{false, true}) {
			File epub = writeBookWithExternalCover(3000, 4000, svg, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, "extcover" + svg);
			assertEveryPageIs(epub, 2000, 1000);
			try (ZipFile zf = new ZipFile(epub)) {
				String cover = read(zf, "OPS/xhtml/cover.xhtml");
				assertEquals(600, imageOf(zf, cover).getWidth());
				assertEquals(800, imageOf(zf, cover).getHeight());
			}
		}
	}

	/** BMP はページにならないので、ページの大きさに数えない */
	@Test
	public void pageSizeSkipsImageThatIsNotAPage() throws Exception {
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
		assertEveryPageIs(writeBook(zip, writer, false, false, "bmpfirst"), 810, 1110);
	}

	/** 高さの割合は画面に対する値なので、ページが画像の大きさの本では画像が小さくなる */
	@Test
	public void imagePagesHaveNoPercentHeight() throws Exception {
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer, false, 0, 0, 0, SectionInfo.IMAGE_SIZE_TYPE_HEIGHT);
		File epub = writeBook(imageZip(), writer, false, false, "height");
		try (ZipFile zf = new ZipFile(epub)) {
			for (String page : spinePages(zf)) {
				assertFalse(page + " に割合の高さがある: " + read(zf, page), Pattern.compile("min-height: *[1-9]").matcher(read(zf, page)).find());
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
		assertEveryPageIs(epub, 2000, 1000);
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
				assertFalse(page + " に画素の配置がある", read(zf, page).contains("position:absolute"));
			}
			assertTrue("表紙のページが無い", pages.contains("OPS/xhtml/cover.xhtml"));
			assertTrue(read(zf, "OPS/package.opf").contains("<meta property=\"rendition:layout\">reflowable</meta>"));
			//本全体がリフローなので、目次に properties を付けない
			assertTrue(read(zf, "OPS/package.opf"), read(zf, "OPS/package.opf").contains("<itemref idref=\"nav\" linear=\"yes\"/>"));
		}
	}
}
