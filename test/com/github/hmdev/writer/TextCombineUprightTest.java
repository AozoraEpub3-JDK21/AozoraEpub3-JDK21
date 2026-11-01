package com.github.hmdev.writer;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.velocity.app.VelocityEngine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.SectionInfo;

/**
 * 縦中横は標準の text-combine-upright を出す。古い指定は残す。
 * Google Play ブックスは -epub-text-combine がないと縦中横を組まない（2026-11-01 の実測）。
 */
public class TextCombineUprightTest {

	private static final Pattern TCY_RULE = Pattern.compile("\\.tcy span \\{(.*?)\\}", Pattern.DOTALL);

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File writeBook(Path root) throws Exception {
		String templatePath = root.resolve("template") + "/";
		Path txt = tempFolder.newFile("book.txt").toPath();
		Files.write(txt, "表題\n著者\n\n［＃中見出し］一［＃中見出し終わり］\n本文［＃縦中横］12［＃縦中横終わり］です。\n".getBytes(StandardCharsets.UTF_8));
		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", root.resolve("template").toString());
		Epub3Writer writer = new Epub3Writer(templatePath, new VelocityEngine(vp));
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, root + "/");
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, BookInfo.TitleType.TITLE_AUTHOR, false);
		}
		bookInfo.vertical = true;
		bookInfo.insertTocPage = true;
		bookInfo.setTocVertical(true);
		File epub = tempFolder.newFile("book.epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		return epub;
	}

	private static String read(ZipFile zf, String name) throws Exception {
		assertTrue(name + " が無い", zf.getEntry(name) != null);
		return new String(zf.getInputStream(zf.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
	}

	private static void assertAllProperties(String name, String text) {
		Matcher rule = TCY_RULE.matcher(text);
		assertTrue(name + " に .tcy span が無い", rule.find());
		String body = rule.group(1);
		assertTrue(name + ": " + body, body.contains("text-combine-upright: all;"));
		assertTrue(name + ": " + body, body.contains("-webkit-text-combine: horizontal;"));
		assertTrue(name + ": " + body, body.contains("-epub-text-combine: horizontal;"));
		assertTrue(name + ": " + body, Pattern.compile("(?m)^\\s*text-combine: horizontal;").matcher(body).find());
	}

	@Test
	public void standardAndOldPropertiesAreBothWritten() throws Exception {
		Path root = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(root.resolve("template"))) {
			root = Paths.get(TextCombineUprightTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
				.getParent().getParent().getParent();
		}
		try (ZipFile zf = new ZipFile(writeBook(root))) {
			assertAllProperties("vertical.css", read(zf, "OPS/css/vertical.css"));
			assertAllProperties("nav.xhtml", read(zf, "OPS/xhtml/nav.xhtml"));
			assertTrue("本文に縦中横が無い", read(zf, "OPS/xhtml/0001.xhtml").contains("<span class=\"tcy\"><span>12</span></span>"));
		}
	}
}
