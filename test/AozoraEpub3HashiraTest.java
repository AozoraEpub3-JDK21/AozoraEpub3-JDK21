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
	private final java.util.List<String> pages = new java.util.ArrayList<>();
	private String titlePage = "";

	private void convert(String text) throws Exception {
		convert(text, "");
	}

	private void convert(String text, String iniText) throws Exception {
		convert(text, iniText, new String[0]);
	}

	private void convert(String text, String iniText, String[] extraArgs) throws Exception {
		File src = tempFolder.newFolder();
		File dst = tempFolder.newFolder();
		File txt = new File(src, "book.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File ini = tempFolder.newFile();
		Files.write(ini.toPath(), iniText.getBytes(StandardCharsets.UTF_8));
		pages.clear();
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		try {
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			java.util.List<String> args = new java.util.ArrayList<>(java.util.List.of("-i", ini.getPath(), "-enc", "UTF-8"));
			args.addAll(java.util.List.of(extraArgs));
			args.addAll(java.util.List.of("-d", dst.getPath(), txt.getPath()));
			assertEquals(0, AozoraEpub3.run(args.toArray(new String[0])));
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
				if (e.getName().endsWith(".xhtml")) pages.add(body);
				if (e.getName().endsWith("title.xhtml")) titlePage = body;
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
		int found = 0;
		while (m.find()) { found++; assertFalse("目次のラベルが作品名:\n" + toc, m.group(1).contains("作品タイトル")); }
		assertTrue("本文のページを指す目次の項目が無い:\n" + toc, found >= 2);
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
		//対にならない印は外したので、XHTML の開閉は崩れない
		assertWellFormed();
	}

	/** 作品名が章の名前の形（1 始まり… のような数字と区切り）でも、自動の章の判定に使わない（目次に空の行を作らない） */
	@Test
	public void aTitleThatLooksLikeAChapterIsNotAnAutoChapter() throws Exception {
		convert("1 始まりの物語\n作者\n\n［＃改ページ］\n［＃ページの左右中央］\n［＃ここから柱］1 始まりの物語［＃ここで柱終わり］\n［＃３字下げ］［＃大見出し］日本編［＃大見出し終わり］\n［＃改ページ］\n本文です。\n", "ChapterNumTitle=1\nChapterNumOnly=1\n");
		assertFalse("目次に空の項目:\n" + toc, toc.contains("<text></text>"));
		assertTrue(toc, toc.contains("<text>日本編</text>"));
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("<text>([^<]*)</text>\\s*</navLabel>\\s*<content src=\"xhtml/0").matcher(toc);
		while (m.find()) assertFalse("柱の作品名が章になった:\n" + toc, m.group(1).contains("始まりの物語"));
		//章として拾うと、柱の div に章の目印（id）が付く
		assertTrue("柱が章として拾われた:\n" + allXhtml, allXhtml.contains("<div class=\"running_head yoko\">1 始まりの物語</div>"));
		assertWellFormed();
	}

	/** 外字の注記に飲まれた閉じ（※［＃ここで柱終わり］）は閉じに数えない。出力は外字の変換の後で対にする（前走査と同じ順） */
	@Test
	public void aCloseSwallowedByGaijiDoesNotLeaveAnOpenDiv() throws Exception {
		convert("題\n作者\n\n［＃ここから柱］注※［＃ここで柱終わり］\n［＃改ページ］\n本文です。\n");
		assertTrue(allXhtml, allXhtml.contains("本文です。"));
		assertWellFormed();
	}

	/** 表題の行の柱は、OPF の表題と同じく扉ページの表題にも出さない */
	@Test
	public void aHashiraOnTheTitleLineIsNotOnTheTitlePage() throws Exception {
		convert("題名［＃ここから柱］作品名［＃ここで柱終わり］\n作者\n\n本文です。\n");
		assertTrue(titlePage, titlePage.contains("題名"));
		assertFalse("扉ページの表題に柱が出た:\n" + titlePage, titlePage.contains("作品名"));
	}

	/** 見出しのブロックの最初の行が柱だけでも、次の行を章の名前にする（目次から章が落ちない） */
	@Test
	public void aHashiraOnlyLineInAHeadingBlockIsSkippedForTheName() throws Exception {
		convert("題\n作者\n\n［＃改ページ］\n［＃ここから中見出し］\n［＃ここから柱］作品名［＃ここで柱終わり］\n章一\n［＃ここで中見出し終わり］\n本文です。\n");
		assertTrue("章が目次に無い:\n" + toc, toc.contains("<text>章一</text>"));
	}

	private void assertWellFormed() throws Exception {
		javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
		f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		for (String page : pages) f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8)));
	}
}
