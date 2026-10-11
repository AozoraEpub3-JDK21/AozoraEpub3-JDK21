import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 柱（［＃ここから柱］…［＃ここで柱終わり］。narou.rb の章の扉ページが作品名を置く独自の注記）は、
 * narou.rb と同じく running_head として出し、目次の章の名前・表題には使わない（監査項目 20）。
 * 前は注記の表に柱が無く、作品名が本文の段落になり、目次の章のラベルも章の名前ではなく作品名になった
 */
public class AozoraEpub3HashiraTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String allXhtml;
	private String toc;

	private void convert(String text) throws Exception {
		File src = tempFolder.newFolder();
		File dst = tempFolder.newFolder();
		File txt = new File(src, "book.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File ini = tempFolder.newFile();
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		try {
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			assertEquals(0, AozoraEpub3.run(new String[]{ "-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), txt.getPath() }));
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		StringBuilder xhtml = new StringBuilder();
		try (ZipFile zip = new ZipFile(dst.listFiles((d, n) -> n.endsWith(".epub"))[0])) {
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				String body = new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
				if (e.getName().endsWith("toc.ncx")) toc = body;
				else if (e.getName().endsWith(".xhtml") && !e.getName().endsWith("nav.xhtml") && !e.getName().endsWith("title.xhtml")) xhtml.append(body);
			}
		}
		//<head> の <title>（本の題名）は柱ではない
		allXhtml = xhtml.toString().replaceAll("<title>[^<]*</title>", "");
	}

	/** Web 変換の章の扉ページ（narou.rb 互換）の形。目次の章のラベルは章の名前、柱は running_head として出る */
	@Test
	public void aChapterTitlePageHashiraIsARunningHeadNotAChapterName() throws Exception {
		convert("作品タイトル\n作者\n\n"
			+ "［＃改ページ］\n［＃ページの左右中央］\n［＃ここから柱］作品タイトル［＃ここで柱終わり］\n［＃３字下げ］［＃大見出し］日本編［＃大見出し終わり］\n"
			+ "［＃改ページ］\n［＃中見出し］開幕一軍［＃中見出し終わり］\n本文です。\n");
		assertTrue("柱が running_head で出ていない:\n" + allXhtml, allXhtml.contains("<div class=\"running_head yoko\">作品タイトル</div>"));
		assertFalse("柱の作品名が段落になった:\n" + allXhtml, allXhtml.contains("<p>作品タイトル</p>"));
		assertTrue("目次の章のラベルが章の名前でない:\n" + toc, toc.contains("<text>日本編</text>"));
		//本文のページを指す目次の項目（表題ページの項目を除く）に作品名は出ない
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("<text>([^<]*)</text>\\s*</navLabel>\\s*<content src=\"xhtml/0").matcher(toc);
		while (m.find()) assertFalse("目次のラベルが作品名:\n" + toc, m.group(1).contains("作品タイトル"));
	}

	/** 柱の中の注記（Web 変換は外字・縦中横を入れうる）も、目次の章のラベルに出ない */
	@Test
	public void aHashiraWithAnnotationsInsideIsNotAChapterName() throws Exception {
		convert("題\n作者\n\n［＃改ページ］\n［＃ここから柱］作品［＃縦中横］12［＃縦中横終わり］※［＃始め二重山括弧］名［＃ここで柱終わり］\n［＃大見出し］第一章［＃大見出し終わり］\n本文。\n");
		assertTrue("目次の章のラベルが章の名前でない:\n" + toc, toc.contains("<text>第一章</text>"));
		assertFalse(toc, toc.contains("作品"));
	}

	/** 柱は 1 行で閉じる形しか作られない。それ以外の形でも本文の文字は消えない（master と同じく残る） */
	@Test
	public void otherShapesKeepTheirText() throws Exception {
		convert("題\n作者\n\n［＃ここから柱］\n二行目の柱\n［＃ここで柱終わり］\n本文一。\n本文二［＃ここで柱終わり］続き\n");
		assertTrue(allXhtml, allXhtml.contains("二行目の柱"));
		assertTrue(allXhtml, allXhtml.contains("本文一。"));
		assertTrue(allXhtml, allXhtml.contains("本文二") && allXhtml.contains("続き"));
	}
}
