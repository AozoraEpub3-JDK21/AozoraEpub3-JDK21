package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** インスペクタ (R6) が出す宣言値の集計 */
public class EpubInspectionTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void categorizesByMediaType()
	{
		assertEquals(EpubInspection.Category.XHTML,
			EpubInspection.categorize("application/xhtml+xml", "OPS/xhtml/text.xhtml"));
		assertEquals(EpubInspection.Category.CSS,
			EpubInspection.categorize("text/css", "OPS/css/vertical_text.css"));
		assertEquals(EpubInspection.Category.IMAGE,
			EpubInspection.categorize("image/jpeg", "OPS/images/cover.jpg"));
		assertEquals(EpubInspection.Category.FONT,
			EpubInspection.categorize("font/ttf", "OPS/gaiji/u3042-u3099.ttf"));
		assertEquals(EpubInspection.Category.NCX,
			EpubInspection.categorize("application/x-dtbncx+xml", "OPS/toc.ncx"));
	}

	@Test
	public void categorizesByExtensionWhenMediaTypeMissing()
	{
		assertEquals(EpubInspection.Category.FONT, EpubInspection.categorize("", "OPS/gaiji/a.otf"));
		assertEquals(EpubInspection.Category.IMAGE, EpubInspection.categorize(null, "OPS/images/a.PNG"));
		assertEquals(EpubInspection.Category.OTHER, EpubInspection.categorize("", "OPS/data.bin"));
	}

	@Test
	public void legacyOpentypeMediaTypeIsFont()
	{
		// EPUB2 世代の EPUB が使う media-type
		assertEquals(EpubInspection.Category.FONT,
			EpubInspection.categorize("application/vnd.ms-opentype", "OPS/font.otf"));
	}

	@Test
	public void jsonContainsStructureAndBreakdown() throws IOException
	{
		Path root = EpubFixture.standard().explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		String json = new EpubInspection(root, opf, null).toJson();

		// EPUB 3.x でも package version は "3.0" が正しい
		assertTrue(json.contains("\"packageVersion\":\"3.0\""));
		assertTrue(json.contains("\"pageProgressionDirection\":\"rtl\""));
		assertTrue(json.contains("\"title\":\"テスト書籍\""));
		assertTrue(json.contains("\"spineCount\":3"));
		assertTrue(json.contains("\"category\":\"font\""));
		assertTrue(json.contains("OPS/css/vertical_text.css"));
		assertTrue(json.contains("OPS/gaiji/u3042-u3099.ttf"));
	}

	@Test
	public void extractedSizeCountsFilesOutsideTheManifest() throws IOException
	{
		// mimetype / META-INF/container.xml / OPF 自身は manifest に載らない。
		// 内訳の合計で代用すると「展開後サイズ」が常に過少になる
		Path root = EpubFixture.standard().explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		String json = new EpubInspection(root, opf, null).toJson();

		long manifestTotal = 0;
		for (ManifestItem item : opf.getManifest()) {
			Path file = root.resolve(item.path());
			if (Files.isRegularFile(file)) manifestTotal += Files.size(file);
		}
		long reported = Long.parseLong(
			json.replaceAll(".*\"extractedSize\":(\\d+).*", "$1"));

		assertTrue("manifest 外のファイルも数えること (" + reported + " > " + manifestTotal + ")",
			reported > manifestTotal);
		// mimetype と container.xml のぶんは最低でも含まれる
		long outside = Files.size(root.resolve("mimetype"))
			+ Files.size(root.resolve("META-INF/container.xml"))
			+ Files.size(root.resolve("OPS/package.opf"));
		assertEquals(manifestTotal + outside, reported);
	}
}
