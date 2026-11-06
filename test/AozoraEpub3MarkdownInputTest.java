import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * internal #72: CLI に .md を渡すと、Markdown として読んで EPUB にする。
 * 文字コードは -enc（既定 MS932）によらず UTF-8。
 *
 * run() の呼び方は AozoraEpub3BatchPerBookTest と同じ。
 */
public class AozoraEpub3MarkdownInputTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String lastLog;

	private int run(String... args) {
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int exitCode;
		try {
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
			exitCode = AozoraEpub3.run(args);
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		lastLog = baos.toString(StandardCharsets.UTF_8);
		System.out.println(lastLog);
		return exitCode;
	}

	private static String entry(ZipFile zf, String name) throws Exception {
		ZipEntry e = zf.getEntry(name);
		assertNotNull(name + " が無い", e);
		try (InputStream is = zf.getInputStream(e)) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void aMarkdownFileBecomesABookWithTitleAuthorAndToc() throws Exception {
		File src = tempFolder.newFolder("src");
		File dst = tempFolder.newFolder("dst");
		File md = new File(src, "report.md");
		Files.write(md.toPath(), ("﻿---\nauthor: 春雨\n---\n# 調べた結果\n\n概要の**段落**です。\n\n"
			+ "## 背景\n\n- 項目《ルビではない》\n  - 入れ子\n\n```\na << b ［＃注記ではない］\n```\n\n"
			+ "## 結論\n\n| 名前 | 値 |\n|---|---|\n| 版 | 3.4 |\n").getBytes(StandardCharsets.UTF_8));
		File ini = new File(src, "test.ini");
		Files.write(ini.toPath(), "TocPage=1\n".getBytes(StandardCharsets.UTF_8));

		//-enc を付けない（既定 MS932）。Markdown は UTF-8 で読む
		int exitCode = run("-i", ini.getPath(), "-of", "-d", dst.getPath(), md.getPath());
		assertEquals("変換に失敗した:\n" + lastLog, 0, exitCode);
		File epub = new File(dst, "report.epub");
		assertTrue("EPUB が無い:\n" + lastLog, epub.isFile());
		assertFalse("注記のエラーが出た:\n" + lastLog, lastLog.contains("[ERROR]"));

		try (ZipFile zf = new ZipFile(epub)) {
			String opf = entry(zf, "OPS/package.opf");
			assertTrue(opf, opf.contains(">調べた結果</dc:title>"));
			assertTrue(opf, opf.contains(">春雨</dc:creator>"));
			String nav = entry(zf, "OPS/xhtml/nav.xhtml");
			assertTrue(nav, nav.contains("背景"));
			assertTrue(nav, nav.contains("結論"));
			StringBuilder body = new StringBuilder();
			for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements();) {
				ZipEntry e = en.nextElement();
				if (e.getName().matches("OPS/xhtml/\\d+\\.xhtml")) body.append(entry(zf, e.getName()));
			}
			String b = body.toString();
			//≪≫ は変換器が《》の字として出す（ルビにはしない）
			assertTrue(b, b.contains("・項目《ルビではない》"));
			assertFalse(b, b.contains("<ruby>"));
			assertTrue(b, b.contains("名前：版／値："));
			assertTrue("コードの ［＃ が注記として消えた:\n" + b, b.contains("注記ではない"));
			assertTrue("太字が出ていない:\n" + b, b.contains("class=\"b\""));
		}
	}
}
