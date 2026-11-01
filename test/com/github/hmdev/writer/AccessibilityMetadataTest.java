package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.util.VelocityTestUtils;

/**
 * EPUB Accessibility のパッケージのメタデータ（§2.2）。1.2 の草案（2026-09-12）では accessModeSufficient も必須。
 * 実物について言えることだけを書く。
 * 画像に代替テキストは無く、規格への適合も確かめていない。
 */
public class AccessibilityMetadataTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static void setParams(Epub3Writer writer) {
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
	}

	private String textBook(boolean vertical, boolean cover) throws Exception {
		return textBook(vertical, cover ? "png" : null, false, "本文です。\n");
	}

	/** @param coverFormat 別ファイルの表紙の形式。null なら表紙なし */
	private String textBook(boolean vertical, String coverFormat, boolean kindle, String body) throws Exception {
		Path txt = tempFolder.newFile("book.txt").toPath();
		Files.write(txt, ("表題\n著者\n\n" + body).getBytes(StandardCharsets.UTF_8));
		ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "png", tempFolder.newFile("fig.png"));
		Epub3Writer writer = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		setParams(writer);
		writer.setIsKindle(kindle);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		bookInfo.vertical = vertical;
		if (coverFormat != null) {
			File image = tempFolder.newFile("cover." + coverFormat);
			ImageIO.write(new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB), coverFormat, image);
			bookInfo.insertCoverPage = true;
			bookInfo.coverFileName = image.getPath();
		}
		File epub = tempFolder.newFile("text.epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		return opf(epub);
	}

	private String imageBook() throws Exception {
		File zip = tempFolder.newFile("images.zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			zos.putNextEntry(new ZipEntry("p0.png"));
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB), "png", png);
			zos.write(png.toByteArray());
			zos.closeEntry();
		}
		Epub3ImageWriter writer = new Epub3ImageWriter(VelocityTestUtils.templateDir() + File.separator);
		setParams(writer);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		File epub = tempFolder.newFile("image.epub");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		return opf(epub);
	}

	private static String opf(File epub) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			return new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<String> values(String opf, String property) {
		List<String> values = new ArrayList<>();
		Matcher m = Pattern.compile("<meta property=\"schema:" + property + "\">([^<]*)</meta>").matcher(opf);
		while (m.find()) values.add(m.group(1));
		return values;
	}

	/** 確かめていないことを名乗らない */
	private static void assertMakesNoUnverifiedClaim(String opf) {
		assertFalse(opf, opf.contains("dcterms:conformsTo"));
		assertFalse(opf, opf.contains("alternativeText"));
		assertEquals(opf, 1, values(opf, "accessibilityHazard").size());
		assertEquals(opf, 1, values(opf, "accessibilitySummary").size());
		assertFalse(opf, opf.contains("${"));
	}

	@Test
	public void plainTextBookIsTextual() throws Exception {
		String opf = textBook(true, false);
		assertEquals(List.of("textual"), values(opf, "accessMode"));
		assertEquals(List.of("textual"), values(opf, "accessModeSufficient"));
		assertEquals(List.of("tableOfContents", "verticalWriting"), values(opf, "accessibilityFeature"));
		assertEquals(List.of("none"), values(opf, "accessibilityHazard"));
		assertFalse("画像の無い本で代替テキストの話をしない", values(opf, "accessibilitySummary").get(0).contains("代替テキスト"));
		assertMakesNoUnverifiedClaim(opf);
	}

	@Test
	public void horizontalBookSaysSo() throws Exception {
		assertEquals(List.of("tableOfContents", "horizontalWriting"), values(textBook(false, false), "accessibilityFeature"));
	}

	/** 本文の画像に代替テキストが無いので、文字だけで足りるとは言えない */
	@Test
	public void bodyImageAlsoNeedsSight() throws Exception {
		String opf = textBook(true, null, false, "本文です。\n［＃挿絵（fig.png）入る］\n");
		assertEquals(List.of("textual", "visual"), values(opf, "accessMode"));
		assertEquals(List.of("textual,visual"), values(opf, "accessModeSufficient"));
		assertTrue(opf, values(opf, "accessibilitySummary").get(0).contains("代替テキストはありません"));
		//PNG は動くかもしれない
		assertEquals(List.of("unknown"), values(opf, "accessibilityHazard"));
		assertMakesNoUnverifiedClaim(opf);
	}

	/** 表紙は読むのに要らない（EPUB Accessibility Techniques 1.1 §3.1） */
	@Test
	public void coverAloneDoesNotNeedSight() throws Exception {
		String opf = textBook(true, "png", false, "本文です。\n");
		assertEquals(List.of("textual"), values(opf, "accessMode"));
		assertEquals(List.of("textual"), values(opf, "accessModeSufficient"));
		assertFalse(opf, values(opf, "accessibilitySummary").get(0).contains("代替テキスト"));
		assertEquals(List.of("unknown"), values(opf, "accessibilityHazard"));
		assertMakesNoUnverifiedClaim(opf);
	}

	@Test
	public void jpegCoverHasNoHazard() throws Exception {
		assertEquals(List.of("none"), values(textBook(true, "jpg", false, "本文です。\n"), "accessibilityHazard"));
	}

	/** .mobi の出力は変えない */
	@Test
	public void kindleOutputHasNoMetadata() throws Exception {
		assertFalse(textBook(true, null, true, "本文です。\n").contains("schema:access"));
	}

	@Test
	public void imageBookIsVisual() throws Exception {
		String opf = imageBook();
		assertEquals(List.of("visual"), values(opf, "accessMode"));
		assertEquals(List.of("visual"), values(opf, "accessModeSufficient"));
		assertEquals(List.of("tableOfContents"), values(opf, "accessibilityFeature"));
		assertEquals(List.of("unknown"), values(opf, "accessibilityHazard"));
		assertMakesNoUnverifiedClaim(opf);
	}
}
