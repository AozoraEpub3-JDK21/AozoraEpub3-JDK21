package com.github.hmdev.writer;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
 * 監査項目 43・44: EPUB の中のテキストは、テンプレートのファイルの改行（git の中で CRLF と LF が混ざる）に関係なく LF で書く。
 * 画像だけの本も同じ道（mergeTemplate）を通り、テンプレートを見つけて変換できる。
 */
public class EpubLineEndingTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Path projectRoot;
	private String templatePath;

	@Before
	public void setUp() throws Exception {
		projectRoot = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(projectRoot.resolve("template"))) {
			Path testClasses = Paths.get(
				EpubLineEndingTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			projectRoot = testClasses.getParent().getParent().getParent();
		}
		templatePath = projectRoot.resolve("template").toString();
		if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) templatePath += "/";
	}

	private static void setParams(Epub3Writer writer) {
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600,
			SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
	}

	/** テキストのエントリ（xhtml・opf・ncx・css・xml）のうち CR を含むものの名前 */
	private static List<String> entriesWithCr(File epub) throws Exception {
		List<String> withCr = new ArrayList<>();
		try (ZipFile zf = new ZipFile(epub)) {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				if (!e.getName().matches(".*\\.(xhtml|opf|ncx|css|xml)$")) continue;
				try (InputStream is = zf.getInputStream(e)) {
					for (byte b : is.readAllBytes()) if (b == '\r') { withCr.add(e.getName()); break; }
				}
			}
		}
		return withCr;
	}

	private static boolean hasEntry(File epub, String name) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			return zf.getEntry(name) != null;
		}
	}

	@Test
	public void aTextBookHasNoCarriageReturnAnywhere() throws Exception {
		//縦書きの CSS テンプレートや固定の CSS・container.xml は git の中でも CRLF なので、どの台でも確かめになる
		Path txt = tempFolder.newFile("sample.txt").toPath();
		Files.write(txt, ("表題\n著者\n\n本文です。\n［＃改ページ］\n［＃ページの左右中央］\n［＃大見出し］第一章［＃大見出し終わり］\n").getBytes(StandardCharsets.UTF_8));
		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").toString());
		Epub3Writer writer = new Epub3Writer(templatePath, new VelocityEngine(vp));
		setParams(writer);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		bookInfo.vertical = true;
		converter.vertical = true;
		File epub = tempFolder.newFile("text.epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		assertTrue(hasEntry(epub, "META-INF/container.xml"));
		assertTrue(hasEntry(epub, "OPS/css/vertical_text.css"));
		assertTrue("CR を含むエントリ: " + entriesWithCr(epub), entriesWithCr(epub).isEmpty());
	}

	@Test
	public void anImageOnlyBookConvertsAndHasNoCarriageReturn() throws Exception {
		//CLI（AozoraEpub3.run）の画像だけの本と同じ手順。以前は Epub3ImageWriter がテンプレートを見つけられず失敗した
		File zip = projectRoot.resolve("test_data/test_png.zip").toFile();
		org.junit.Assume.assumeTrue("test_data/test_png.zip が無い", zip.isFile());
		Epub3ImageWriter writer = new Epub3ImageWriter(templatePath);
		setParams(writer);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		ImageInfoReader imageInfoReader = new ImageInfoReader(false, zip);
		imageInfoReader.loadZipImageInfos(zip, true);
		imageInfoReader.sortImageFileNames();
		BookInfo bookInfo = new BookInfo(zip);
		bookInfo.imageOnly = true;
		File epub = tempFolder.newFile("image.epub");
		writer.write(converter, null, zip, "zip", epub, bookInfo, imageInfoReader);
		assertTrue("EPUB が書かれていない", epub.length() > 0);
		assertTrue(hasEntry(epub, "OPS/xhtml/0001.xhtml"));
		List<String> withCr = entriesWithCr(epub);
		assertTrue("CR を含むエントリ: " + withCr, withCr.isEmpty());
	}
}
