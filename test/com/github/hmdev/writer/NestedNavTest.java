package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.ChapterInfo;
import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.util.VelocityTestUtils;

/**
 * EPUB 3 リーダーが読むのは NAV だけで、NCX の階層は届かない（2026-11-01 に 4 リーダーで実測）。
 * NAV に階層を出す。
 */
public class NestedNavTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 目次の項目を見出しの深さの列から作り、&lt;ol&gt; と &lt;li&gt; の並びを 1 行の文字にする */
	private static String nest(int... levels) {
		List<ChapterInfo> chapters = new ArrayList<>();
		for (int i = 0; i < levels.length; i++) {
			chapters.add(new ChapterInfo("s", null, "c" + i, levels[i]));
		}
		Epub3Writer.nestNavChapters(chapters);
		StringBuilder out = new StringBuilder();
		for (ChapterInfo chapter : chapters) {
			out.append(chapter.getChapterName()).append(chapter.getNavAfter().replaceAll("\\s", ""));
			out.append(' ');
		}
		return out.toString().trim();
	}

	@Test
	public void sameLevelStaysFlat() {
		assertEquals("c0</li> c1</li> c2</li>", nest(1, 1, 1));
	}

	@Test
	public void deeperLevelOpensAList() {
		assertEquals("c0<ol> c1<ol> c2</li></ol></li></ol></li>", nest(1, 2, 3));
	}

	@Test
	public void shallowerLevelClosesEveryOpenList() {
		assertEquals("c0<ol> c1<ol> c2</li></ol></li></ol></li> c3</li>", nest(1, 2, 3, 1));
	}

	/** 深さが飛んでも &lt;ol&gt; は 1 段ずつ。3 の次の 2 は 3 の兄弟になる（親の 1 より深い） */
	@Test
	public void skippedLevelNestsOneList() {
		assertEquals("c0<ol> c1</li> c2</li></ol></li>", nest(1, 3, 2));
	}

	/** 先頭が深くても、次に浅くなったときに閉じすぎない */
	@Test
	public void firstChapterDeeperThanLater() {
		assertEquals("c0</li> c1</li>", nest(2, 1));
	}

	private static String nestWithPageBreakLines(int[] levels, boolean[] lines) {
		List<ChapterInfo> chapters = new ArrayList<>();
		for (int i = 0; i < levels.length; i++) {
			ChapterInfo chapter = new ChapterInfo("s", null, "c" + i, levels[i]);
			chapter.pageBreakLine = lines[i];
			chapters.add(chapter);
		}
		Epub3Writer.nestNavChapters(chapters);
		StringBuilder out = new StringBuilder();
		for (ChapterInfo chapter : chapters) {
			out.append(chapter.getChapterName()).append(chapter.getNavAfter().replaceAll("\\s", "")).append(' ');
		}
		return out.toString().trim();
	}

	/** 改ページの後の本文の行は、後ろの章の親にならない。第一部 > 第一章・本文の行・第二章・第三章 */
	@Test
	public void pageBreakLineIsNotAParent() {
		assertEquals("c0<ol> c1</li> c2</li> c3</li> c4</li></ol></li>",
			nestWithPageBreakLines(new int[]{0, 1, 0, 1, 1}, new boolean[]{false, false, true, false, false}));
	}

	/** 大見出しの直後の本文の行は、次の章と同じ深さに並ぶ */
	@Test
	public void pageBreakLineTakesTheDepthOfTheNextHeading() {
		assertEquals("c0<ol> c1</li> c2</li></ol></li>",
			nestWithPageBreakLines(new int[]{0, 0, 1}, new boolean[]{false, true, false}));
	}

	/** 見出しの無い本は、本文の行だけが平らに並ぶ */
	@Test
	public void onlyPageBreakLinesStayFlat() {
		assertEquals("c0</li> c1</li> c2</li>",
			nestWithPageBreakLines(new int[]{0, 0, 0}, new boolean[]{true, true, true}));
	}

	/** 画像だけの本は全部レベル 0 */
	@Test
	public void allLevelZeroStaysFlat() {
		assertEquals("c0</li> c1</li>", nest(0, 0));
	}

	@Test
	public void emptyListIsFine() {
		Epub3Writer.nestNavChapters(new ArrayList<>());
	}

	private File writeBook(boolean navNest, boolean kindle) throws Exception {
		return writeBook(navNest, kindle, BookInfo.TitleType.TITLE_AUTHOR);
	}

	private File writeBook(boolean navNest, boolean kindle, BookInfo.TitleType titleType) throws Exception {
		String text = "表題\n著者\n\n"
			+ "［＃大見出し］第一部［＃大見出し終わり］\n"
			+ "［＃中見出し］第一章［＃中見出し終わり］\n本文。\n"
			+ "［＃小見出し］その一［＃小見出し終わり］\n本文。\n"
			+ "［＃中見出し］第二章［＃中見出し終わり］\n本文。\n"
			+ "［＃改ページ］\n［＃大見出し］第二部［＃大見出し終わり］\n本文。\n"
			+ "［＃中見出し］第三章［＃中見出し終わり］\n本文。\n";
		return writeBook(navNest, kindle, titleType, text);
	}

	private File writeBook(boolean navNest, boolean kindle, BookInfo.TitleType titleType, String text) throws Exception {
		java.nio.file.Path txt = tempFolder.newFile().toPath();
		Files.write(txt, text.getBytes(StandardCharsets.UTF_8));
		Epub3Writer writer = new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600, SectionInfo.IMAGE_SIZE_TYPE_ASPECT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(navNest, false);
		writer.setIsKindle(kindle);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		converter.setChapterLevel(64, false, false, true, true, true, true, true, false, false, false, false, false, false, null);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt.toFile());
		BookInfo bookInfo;
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			bookInfo = converter.getBookInfo(txt.toFile(), src, imageInfoReader, titleType, false);
		}
		File epub = tempFolder.newFile();
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			writer.write(converter, src, txt.toFile(), "txt", epub, bookInfo, imageInfoReader);
		}
		return epub;
	}

	private static String nav(File epub) throws Exception {
		try (ZipFile zf = new ZipFile(epub)) {
			String nav = new String(zf.getInputStream(zf.getEntry("OPS/xhtml/nav.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
			return nav.substring(nav.indexOf("<h1>"));
		}
	}

	private static int count(String text, String part) {
		return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	@Test
	public void navIsNestedInTheBook() throws Exception {
		String nav = nav(writeBook(true, false));
		//表題と第一部は同じ深さ。第一部 > 第一章 > その一、第一部 > 第二章、第二部 > 第三章
		assertTrue(nav, nav.contains("表題</a></li>"));
		assertTrue(nav, nav.matches("(?s).*第一部</a>\\s*<ol>\\s*<li[^>]*><a[^>]*>第一章</a>\\s*<ol>\\s*<li[^>]*><a[^>]*>その一</a></li></ol></li>\\s*<li[^>]*><a[^>]*>第二章</a></li></ol></li>\\s*<li[^>]*><a[^>]*>第二部</a>\\s*<ol>\\s*<li[^>]*><a[^>]*>第三章</a></li></ol></li>\\s*</ol>.*"));
		//改ページの直後の見出しは a に見出しの ID が付かない。この経路でも入れ子にする
		assertTrue(nav, nav.matches("(?s).*href=\"[0-9]+\\.xhtml\">第二部</a>\\s*<ol>.*"));
		assertEquals("ol と li が閉じていない: " + nav, count(nav, "<ol>"), count(nav, "</ol>"));
		assertEquals("ol と li が閉じていない: " + nav, count(nav, "<li "), count(nav, "</li>"));
	}

	/** 表題の無い本の先頭は本物の見出しなので、次の見出しのレベルに揃えない */
	@Test
	public void firstHeadingKeepsItsChildrenWithoutATitle() throws Exception {
		String nav = nav(writeBook(true, false, BookInfo.TitleType.NONE));
		assertTrue(nav, nav.matches("(?s).*第一部</a>\\s*<ol>\\s*<li[^>]*><a[^>]*>第一章</a>.*"));
	}

	/** 章の間の改ページの後の本文の行が、次の章を子に取らない */
	@Test
	public void bodyLineAfterPageBreakDoesNotTakeChapters() throws Exception {
		String text = "表題\n著者\n\n［＃大見出し］第一部［＃大見出し終わり］\n［＃中見出し］第一章［＃中見出し終わり］\n本文。\n"
			+ "［＃改ページ］\nその日の朝。\n［＃中見出し］第二章［＃中見出し終わり］\n本文。\n［＃中見出し］第三章［＃中見出し終わり］\n本文。\n";
		String nav = nav(writeBook(true, false, BookInfo.TitleType.TITLE_AUTHOR, text));
		assertTrue(nav, nav.contains("その日の朝。</a></li>"));
		assertTrue(nav, nav.matches("(?s).*第一部</a>\\s*<ol>.*第三章</a></li></ol></li>\\s*</ol>.*"));
	}

	@Test
	public void navStaysFlatWhenTurnedOff() throws Exception {
		String nav = nav(writeBook(false, false));
		assertEquals(nav, 1, count(nav, "<ol>"));
		assertTrue(nav, nav.contains("第一章</a></li>"));
	}

	/** .mobi は追わないので、Kindle モードの出力は変えない */
	@Test
	public void kindleOutputStaysFlat() throws Exception {
		String nav = nav(writeBook(true, true));
		assertEquals(nav, 1, count(nav, "<ol>"));
		assertFalse(nav, nav.contains("</ol></li>"));
	}
}
