package com.github.hmdev.preview;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * プレビュー機能のテスト用に EPUB を組み立てるヘルパ。
 *
 * <p>テストクラスではないため build.gradle のテスト検出から除外している。</p>
 */
final class EpubFixture
{
	/** 本プロジェクトが出力する container.xml は BOM 付き */
	static final String CONTAINER_XML =
		"﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		+ "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">\n"
		+ "<rootfiles>\n"
		+ "<rootfile full-path=\"OPS/package.opf\" media-type=\"application/oebps-package+xml\"/>\n"
		+ "</rootfiles>\n"
		+ "</container>\n";

	private final Map<String, byte[]> entries = new LinkedHashMap<>();

	private EpubFixture() {}

	/** 本プロジェクトの出力に近い最小構成の EPUB を作る */
	static EpubFixture standard()
	{
		EpubFixture fixture = new EpubFixture();
		fixture.put("mimetype", "application/epub+zip");
		fixture.put("META-INF/container.xml", CONTAINER_XML);
		fixture.put("OPS/package.opf", packageOpf());
		fixture.put("OPS/toc.ncx", tocNcx());
		fixture.put("OPS/xhtml/nav.xhtml", navXhtml(true));
		fixture.put("OPS/xhtml/cover.xhtml", xhtml("表紙", "<p>cover</p>"));
		fixture.put("OPS/xhtml/text00001.xhtml",
			xhtml("第一章", "<h3 id=\"chapter1\">第一章</h3><p>本文<ruby>漢字<rt>かんじ</rt></ruby></p>"));
		fixture.put("OPS/xhtml/text00002.xhtml",
			xhtml("第二章", "<h3 id=\"chapter2\">第二章</h3><p>本文</p>"));
		fixture.put("OPS/css/vertical_text.css",
			"html { writing-mode: vertical-rl; }\nbody { font-family: \"@ＭＳ 明朝\", serif; }\n");
		fixture.put("OPS/gaiji/u3042-u3099.ttf", "dummy-font");
		return fixture;
	}

	EpubFixture put(String path, String content)
	{
		this.entries.put(path, content.getBytes(StandardCharsets.UTF_8));
		return this;
	}

	EpubFixture remove(String path)
	{
		this.entries.remove(path);
		return this;
	}

	/** EPUB (ZIP) として書き出す */
	Path writeTo(Path file) throws IOException
	{
		Files.createDirectories(file.getParent());
		try (OutputStream out = Files.newOutputStream(file);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			for (Map.Entry<String, byte[]> entry : this.entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
		return file;
	}

	/** 展開済みのディレクトリとして書き出す (展開処理を経ないテスト用) */
	Path explodeTo(Path dir) throws IOException
	{
		for (Map.Entry<String, byte[]> entry : this.entries.entrySet()) {
			Path target = dir.resolve(entry.getKey());
			Files.createDirectories(target.getParent());
			Files.write(target, entry.getValue());
		}
		return dir;
	}

	// ------------------------------------------------------------------

	static String packageOpf()
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"pub-id\" version=\"3.0\">\n"
			+ "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
			+ "    <dc:title id=\"title\">テスト書籍</dc:title>\n"
			+ "    <meta refines=\"#title\" property=\"file-as\">てすとしょせき</meta>\n"
			+ "    <dc:creator id=\"creator\">テスト著者</dc:creator>\n"
			+ "    <dc:language id=\"pub-lang\">ja</dc:language>\n"
			+ "    <dc:identifier id=\"pub-id\">urn:uuid:test-0001</dc:identifier>\n"
			+ "    <meta property=\"dcterms:modified\">2026-08-08T00:00:00Z</meta>\n"
			+ "    <meta property=\"rendition:layout\">reflowable</meta>\n"
			+ "    <meta name=\"primary-writing-mode\" content=\"horizontal-rl\"/>\n"
			+ "  </metadata>\n"
			+ "  <manifest>\n"
			+ "    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n"
			+ "    <item id=\"nav\" properties=\"nav\" href=\"xhtml/nav.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
			+ "    <item id=\"cover-page\" href=\"xhtml/cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
			+ "    <item id=\"sectext00001\" href=\"xhtml/text00001.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
			+ "    <item id=\"sectext00002\" href=\"xhtml/text00002.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
			+ "    <item id=\"css-vertical\" href=\"css/vertical_text.css\" media-type=\"text/css\"/>\n"
			+ "    <item id=\"font-dakuten\" href=\"gaiji/u3042-u3099.ttf\" media-type=\"font/ttf\"/>\n"
			+ "  </manifest>\n"
			+ "  <spine page-progression-direction=\"rtl\" toc=\"ncx\">\n"
			+ "    <itemref idref=\"cover-page\" linear=\"no\"/>\n"
			+ "    <itemref idref=\"nav\" linear=\"yes\"/>\n"
			+ "    <itemref idref=\"sectext00001\" linear=\"yes\"/>\n"
			+ "    <itemref idref=\"sectext00002\" linear=\"yes\"/>\n"
			+ "  </spine>\n"
			+ "</package>\n";
	}

	/** navPoint を入れ子にした toc.ncx (本プロジェクトの toc.ncx.vm と同じ構造) */
	static String tocNcx()
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n"
			+ "<docTitle><text>テスト書籍</text></docTitle>\n"
			+ "<navMap>\n"
			+ "  <navPoint id=\"toc1\" playOrder=\"1\">\n"
			+ "    <navLabel><text>第一章</text></navLabel>\n"
			+ "    <content src=\"xhtml/text00001.xhtml#chapter1\"/>\n"
			+ "    <navPoint id=\"toc2\" playOrder=\"2\">\n"
			+ "      <navLabel><text>第二章</text></navLabel>\n"
			+ "      <content src=\"xhtml/text00002.xhtml#chapter2\"/>\n"
			+ "    </navPoint>\n"
			+ "  </navPoint>\n"
			+ "</navMap>\n"
			+ "</ncx>\n";
	}

	/**
	 * nav.xhtml。landmarks nav を必ず含める。
	 * @param epubType true なら toc nav に epub:type="toc" を付ける (kindle モードでは付かない)
	 */
	static String navXhtml(boolean epubType)
	{
		String tocOpen = epubType ? "<nav epub:type=\"toc\" id=\"toc\">" : "<nav id=\"toc\">";
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<!DOCTYPE html>\n"
			+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ja\">\n"
			+ "<head><title>目次</title></head>\n"
			+ "<body>\n"
			+ "  <nav epub:type=\"landmarks\" id=\"landmarks\" hidden=\"\">\n"
			+ "    <h2>Guide</h2>\n"
			+ "    <ol><li><a epub:type=\"toc\" href=\"nav.xhtml\">ランドマーク</a></li></ol>\n"
			+ "  </nav>\n"
			+ "  " + tocOpen + "\n"
			+ "    <h1>目　次</h1>\n"
			+ "    <ol>\n"
			+ "      <li class=\"chapter\"><a href=\"text00001.xhtml#chapter1\">第一章</a></li>\n"
			+ "      <li class=\"chapter\"><a href=\"text00002.xhtml#chapter2\">第二章</a></li>\n"
			+ "    </ol>\n"
			+ "  </nav>\n"
			+ "</body>\n"
			+ "</html>\n";
	}

	static String xhtml(String title, String body)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ja\">\n"
			+ "<head><title>" + title + "</title>\n"
			+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"../css/vertical_text.css\"/></head>\n"
			+ "<body>" + body + "</body>\n"
			+ "</html>\n";
	}
}
