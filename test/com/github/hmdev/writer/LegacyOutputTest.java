package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;

import com.github.hmdev.config.SettingDefaults;
import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.pipeline.GuiConversionSettings;
import com.github.hmdev.pipeline.WriterConfigurator;
import com.github.hmdev.util.VelocityTestUtils;

/**
 * EPUB 2 向けの要素（meta name="cover"、spine の toc、toc.ncx）の出し分け。
 * Kobo は端末が無く測れていないので、.kepub.epub では設定によらず出す。.mobi は目次が NCX 頼りなので同じ。
 */
public class LegacyOutputTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** null なら同梱のテンプレートを使う */
	private Path templateOverride;

	private static void setParams(Epub3Writer writer, boolean legacy, boolean kindle) {
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
		writer.setLegacyOutput(legacy);
		writer.setIsKindle(kindle);
	}

	private static org.apache.velocity.app.VelocityEngine engineFor(Path templates) {
		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", templates.toString());
		return new org.apache.velocity.app.VelocityEngine(vp);
	}

	private File textBook(boolean legacy, boolean kindle, String outputExt) throws Exception {
		return textBook(legacy, kindle, outputExt, true);
	}

	/** 出力先は一時ファイルの名前にする（本棚の更新と同じ。拡張子を名前から推測させない） */
	private File textBook(boolean legacy, boolean kindle, String outputExt, boolean vertical) throws Exception {
		Path txt = tempFolder.newFile().toPath();
		Files.write(txt, "表題\n著者\n\n本文です。\n".getBytes(StandardCharsets.UTF_8));
		Epub3Writer writer = templateOverride == null
			? new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""))
			: new Epub3Writer(templateOverride + File.separator, engineFor(templateOverride));
		setParams(writer, legacy, kindle);
		writer.setOutputExt(outputExt);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		bookInfo.vertical = vertical;
		File cover = tempFolder.newFile();
		ImageIO.write(new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB), "png", cover);
		bookInfo.insertCoverPage = true;
		bookInfo.coverFileName = cover.getPath();
		File epub = new File(tempFolder.newFolder(), ".title" + outputExt + ".123.tmp");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		return epub;
	}

	private File imageBook(boolean legacy, String outputExt) throws Exception {
		File zip = File.createTempFile("images", ".zip", tempFolder.getRoot());
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			zos.putNextEntry(new ZipEntry("p0.png"));
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB), "png", png);
			zos.write(png.toByteArray());
			zos.closeEntry();
		}
		Epub3ImageWriter writer = new Epub3ImageWriter(VelocityTestUtils.templateDir() + File.separator);
		setParams(writer, legacy, false);
		writer.setOutputExt(outputExt);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		bookInfo.insertCoverPage = true;
		bookInfo.coverImageIndex = 0;
		File epub = new File(tempFolder.newFolder(), ".images" + outputExt + ".123.tmp");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		return epub;
	}

	private static void assertLegacy(File epub, boolean expected) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			String opf = new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(opf, expected, opf.contains("<meta name=\"cover\""));
			//XML として読む（テンプレートの崩れで属性がくっついても、文字列の検索では分からない）
			Element spine = (Element) DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(opf.getBytes(StandardCharsets.UTF_8)))
				.getElementsByTagName("spine").item(0);
			assertEquals(opf, expected ? "ncx" : "", spine.getAttribute("toc"));
			assertEquals(opf, expected, opf.contains("href=\"toc.ncx\""));
			assertEquals("toc.ncx の有無", expected, zf.getEntry("OPS/toc.ncx") != null);
			//外しても表紙の指定と目次は残る
			assertTrue(opf, opf.contains("cover-image"));
			assertTrue(opf, opf.contains("properties=\"nav\""));
			assertTrue(opf, opf.contains("<guide>"));
		}
	}

	@Test
	public void onWritesLegacyElements() throws Exception {
		assertLegacy(textBook(true, false, ".epub"), true);
	}

	@Test
	public void offLeavesThemOut() throws Exception {
		assertLegacy(textBook(false, false, ".epub"), false);
	}

	/** 縦書きと横書きで spine の行が別れている */
	@Test
	public void horizontalBookFollowsTheSetting() throws Exception {
		assertLegacy(textBook(true, false, ".epub", false), true);
		assertLegacy(textBook(false, false, ".epub", false), false);
	}

	@Test
	public void imageBookFollowsTheSetting() throws Exception {
		assertLegacy(imageBook(true, ".epub"), true);
		assertLegacy(imageBook(false, ".epub"), false);
	}

	@Test
	public void koboExtensionsAlwaysWriteThem() throws Exception {
		assertLegacy(textBook(false, false, ".kepub.epub"), true);
		assertLegacy(textBook(false, false, ".KEPUB.EPUB"), true);
		assertLegacy(imageBook(false, ".fxl.kepub.epub"), true);
	}

	/** .mobi は目次が NCX 頼り（isKindle が届かない画像の本でも） */
	@Test
	public void mobiExtensionsAlwaysWriteThem() throws Exception {
		assertLegacy(imageBook(false, ".mobi"), true);
		assertLegacy(textBook(false, false, ".mobi+.epub"), true);
	}

	/** 決めるのは拡張子で、書名や入力の名前ではない（a.kepub.txt を .epub で出した本は Kobo ではない） */
	@Test
	public void outputFileNameDoesNotDecide() {
		assertFalse(Epub3Writer.usesLegacyOutput(false, false, ".epub"));
		assertFalse(Epub3Writer.usesLegacyOutput(false, false, ".kepub"));
		assertTrue(Epub3Writer.usesLegacyOutput(false, false, " .kepub.epub "));
	}

	@Test
	public void kindleModeAlwaysWritesThem() throws Exception {
		assertLegacy(textBook(false, true, ".epub"), true);
	}

	@Test
	public void defaultOmitsThem() {
		assertFalse(SettingDefaults.getBoolean(new Properties(), "LegacyOutput"));
	}

	/** Sony Reader は toc.ncx を読むので、そのプリセットではオンにする。ほかのプリセットはキーを持たず、切り替えると既定へ戻る */
	@Test
	public void onlySonyReaderPresetsTurnThemOn() throws Exception {
		Path root = VelocityTestUtils.templateDir().getParent();
		try (java.util.stream.Stream<Path> presets = Files.list(root.resolve("presets"))) {
			for (Path path : (Iterable<Path>) presets.filter(p -> p.toString().endsWith(".ini"))::iterator) {
				Properties preset = new Properties();
				try (InputStream in = Files.newInputStream(path)) {
					preset.load(in);
				}
				boolean sonyReader = path.getFileName().toString().startsWith("reader");
				assertEquals(path.toString(), sonyReader ? "1" : null, preset.getProperty("LegacyOutput"));
			}
		}
	}

	/** 前の版の package.vm を使い続けている場合。OPF が指す toc.ncx を書かないと、本が壊れる */
	@Test
	public void customTemplateThatListsNcxStillGetsTheFile() throws Exception {
		Path templates = tempFolder.newFolder("template").toPath();
		Path source = VelocityTestUtils.templateDir();
		try (java.util.stream.Stream<Path> tree = Files.walk(source)) {
			for (Path from : (Iterable<Path>) tree::iterator) {
				Path to = templates.resolve(source.relativize(from).toString());
				if (Files.isDirectory(from)) Files.createDirectories(to);
				else Files.copy(from, to);
			}
		}
		Path packageVm = templates.resolve(Paths.get("OPS", "package.vm"));
		String vm = new String(Files.readAllBytes(packageVm), StandardCharsets.UTF_8);
		String old = vm.replaceAll("#if \\(\\$\\{legacyOutput\\}\\) toc=\"ncx\"#end", " toc=\"ncx\"")
			.replaceAll("#if \\(\\$\\{legacyOutput\\}\\)(\\R\\s*<item href=\"toc\\.ncx\"[^\\r\\n]*\\R)#end", "$1");
		old = old.replace(" && ${legacyOutput}", "");
		assertFalse("書き換えられていない:\n" + old, old.contains("legacyOutput"));
		Files.write(packageVm, old.getBytes(StandardCharsets.UTF_8));
		templateOverride = templates;
		File epub = textBook(false, false, ".epub");
		try (ZipFile zf = new ZipFile(epub)) {
			String opf = new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(opf, opf.contains("href=\"toc.ncx\""));
			assertTrue("OPF が指す toc.ncx が無い", zf.getEntry("OPS/toc.ncx") != null);
		}
	}

	@Test
	public void recognizesTheNcxItemInTheOpf() {
		assertTrue(Epub3Writer.opfListsNcx("<item href=\"toc.ncx\" id=\"ncx\"/>"));
		assertTrue(Epub3Writer.opfListsNcx("<item id='ncx' href = './toc.ncx'/>"));
		assertFalse(Epub3Writer.opfListsNcx("<item href=\"xhtml/nav.xhtml\" properties=\"nav\"/>"));
		assertFalse(Epub3Writer.opfListsNcx("<item href=\"xhtml/toc.ncx.xhtml\"/>"));
	}

	/** INI の値が文字の本と画像の本の両方の writer に届く */
	@Test
	public void settingReachesBothWriters() throws Exception {
		Properties props = new Properties();
		props.setProperty("LegacyOutput", "");
		Epub3Writer text = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		Epub3ImageWriter image = new Epub3ImageWriter(VelocityTestUtils.templateDir() + File.separator);
		WriterConfigurator.apply(props, text, image);
		assertFalse(text.legacyOutput);
		assertFalse(image.legacyOutput);
		props.setProperty("LegacyOutput", "1");
		WriterConfigurator.apply(props, text, image);
		assertTrue(text.legacyOutput);
		assertTrue(image.legacyOutput);
	}

	/** GUI の設定の読み取りでも、両方の writer に届く */
	@Test
	public void guiSettingsReachBothWriters() throws Exception {
		Properties props = new Properties();
		props.setProperty("LegacyOutput", "");
		Epub3Writer text = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		Epub3ImageWriter image = new Epub3ImageWriter(VelocityTestUtils.templateDir() + File.separator);
		GuiConversionSettings.applyTo(props, text, image);
		assertFalse(text.legacyOutput);
		assertFalse(image.legacyOutput);
		props.setProperty("LegacyOutput", "1");
		props.setProperty("Ext", ".kepub.epub");
		GuiConversionSettings.applyTo(props, text, image);
		assertTrue(text.legacyOutput);
		assertTrue(image.legacyOutput);
		//本棚の更新は一時ファイルへ書くので、拡張子はここで渡る
		assertEquals(".kepub.epub", text.outputExt);
		assertEquals(".kepub.epub", image.outputExt);
	}

	/** ini の拡張子の読み方。本棚のファイル名と writer の判定が同じ値を見る */
	@Test
	public void outExtIsReadOneWay() {
		Properties props = new Properties();
		assertEquals(".epub", GuiConversionSettings.outExt(props));
		props.setProperty("Ext", "  ");
		assertEquals(".epub", GuiConversionSettings.outExt(props));
		props.setProperty("Ext", " .kepub.epub ");
		assertEquals(".kepub.epub", GuiConversionSettings.outExt(props));
	}

	/** Kobo のプリセットの拡張子で効く（設定のキーは持たない。プリセットを切り替えて値が残らないように） */
	@Test
	public void koboPresetsUseKoboExtensions() throws Exception {
		Path root = VelocityTestUtils.templateDir().getParent();
		for (String name : new String[]{"kobo_glo.ini", "kobo_touch.ini", "kobo__full.ini"}) {
			Properties preset = new Properties();
			try (InputStream in = Files.newInputStream(root.resolve(Paths.get("presets", name)))) {
				preset.load(in);
			}
			assertTrue(name, Epub3Writer.usesLegacyOutput(false, false, preset.getProperty("Ext")));
			assertFalse(name, preset.containsKey("LegacyOutput"));
		}
	}
}
