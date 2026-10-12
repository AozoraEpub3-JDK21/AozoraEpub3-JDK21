import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

import com.github.hmdev.converter.AozoraEpub3Converter;

/**
 * 表題の前後で開いたブロックの注記が表題の後の本文まで閉じないとき（internal #37）:
 * 表題ページは前走査が表題の塊に数えない文字の無い行までしか延ばさず、本文の行で終える。表題の間に開いたタグは本文の頭に書き直し、
 * 閉じないまま改ページや本文の終わりに来たタグは閉じる（どのページも XHTML として読める）
 */
public class AozoraEpub3TitleBlockOpenAnnotationTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String ENDED = "表題をこの行の前で終え";
	private static final String UNCLOSED = "タグが閉じていません";

	private final StringBuilder log = new StringBuilder();

	/** text を CLI と同じ道で変換し（iniText が空なら表題ページは既定の別ページ）、本文の xhtml（名前→body の中身）を返す。ログは log に */
	private Map<String, String> convert(String text, String iniText) throws Exception {
		File src = tempFolder.newFolder();
		File dst = tempFolder.newFolder();
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1000, 1500, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(src, "fig.png"));
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(src, "small.png"));
		File txt = new File(src, "in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File ini = tempFolder.newFile();
		Files.write(ini.toPath(), iniText.getBytes(StandardCharsets.UTF_8));
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
			assertEquals(0, AozoraEpub3.run(new String[]{ "-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), txt.getPath() }));
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		log.append(baos.toString(StandardCharsets.UTF_8));
		File epub = dst.listFiles((d, n) -> n.endsWith(".epub"))[0];
		Map<String, String> pages = new TreeMap<>();
		try (ZipFile zip = new ZipFile(epub)) {
			for (ZipEntry e : Collections.list(zip.entries())) {
				if (!e.getName().endsWith(".xhtml")) continue;
				byte[] bytes = zip.getInputStream(e).readAllBytes();
				//XML として読めること（閉じタグが余る・足りないと例外）
				DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
				String s = new String(bytes, StandardCharsets.UTF_8);
				if (e.getName().endsWith("nav.xhtml") || e.getName().endsWith("title.xhtml")) continue;
				pages.put(e.getName(), s.substring(s.indexOf("<body"), s.indexOf("</body>")));
			}
		}
		return pages;
	}

	private String body(String text) throws Exception {
		return String.join("", convert(text, "").values());
	}

	private static int count(String s, String part) {
		return s.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	/** 本文に lines が全部あり、警告が 1 回だけ出る */
	private String assertBodyKept(String text, String... lines) throws Exception {
		String body = body(text);
		for (String line : lines) assertTrue(line + " が本文に無い:\n" + body + "\n" + log, body.contains(line));
		assertTrue("警告が無い:\n" + log, log.indexOf(ENDED) >= 0);
		assertEquals("1 回だけ:\n" + log, log.indexOf(ENDED), log.lastIndexOf(ENDED));
		return body;
	}

	/** 表題の前の行で開いた罫囲みが閉じない: 本文を全部残し、囲みは本文で続けて最後に閉じる（master は本文が空） */
	@Test
	public void anUnclosedBlockOnItsOwnLineKeepsTheBody() throws Exception {
		String body = assertBodyKept("［＃ここから罫囲み］\n作品\n著者\n\n本文一\n本文二\n", "本文一", "本文二");
		assertTrue(body, body.indexOf("border") >= 0 && body.indexOf("border") < body.indexOf("本文一"));
		assertTrue(log.toString(), log.indexOf(UNCLOSED) >= 0);
	}

	/** 表題と同じ行で開いた罫囲み: 表題の枠ではなく罫囲みを書き直す */
	@Test
	public void anUnclosedBlockOnTheTitleLineReopensTheBlockNotTheTitleFrame() throws Exception {
		String body = assertBodyKept("［＃ここから罫囲み］作品\n著者\n\n本文一\n", "本文一");
		assertTrue(body, body.contains("<div class=\"border\">"));
		assertFalse("表題の枠が本文に出た:\n" + body, body.contains("font5"));
	}

	/** 字下げでも同じ */
	@Test
	public void anUnclosedIndentKeepsTheBody() throws Exception {
		assertBodyKept("［＃ここから２字下げ］\n作品\n著者\n\n本文一\n", "本文一");
	}

	/** 改ページで閉じ、その後の本文も残す（master は改ページの後も表題を延ばした） */
	@Test
	public void anUnclosedBlockBeforeAPageBreakKeepsBothPages() throws Exception {
		Map<String, String> pages = convert("［＃ここから罫囲み］\n作品\n著者\n\n本文一\n［＃改ページ］\n本文二\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("本文一") && body.contains("本文二"));
		assertTrue("改ページが無い: " + pages.keySet(), pages.size() >= 2);
	}

	/** 本文の後で閉じる: 囲みは本文の頭から閉じる所まで（master は本文一を捨てた） */
	@Test
	public void aBlockClosedAfterBodyTextWrapsThatText() throws Exception {
		String body = assertBodyKept("［＃ここから罫囲み］\n作品\n著者\n\n本文一\n［＃ここで罫囲み終わり］\n本文二\n", "本文一", "本文二");
		assertTrue(body, body.indexOf("border") < body.indexOf("本文一") && body.indexOf("</div>") < body.indexOf("本文二"));
		assertFalse(log.toString(), log.indexOf(UNCLOSED) >= 0);
	}

	/** 本文の後で閉じる字下げの後に、別の字下げを開いて閉じても崩れない（字下げの状態が出力と食い違わない） */
	@Test
	public void aLaterIndentInTheBodyStaysBalanced() throws Exception {
		String body = assertBodyKept("［＃ここから２字下げ］\n作品\n著者\n\n本文一\n［＃ここから３字下げ］\n本文二\n［＃ここで字下げ終わり］\n本文三\n", "本文一", "本文二", "本文三");
		assertEquals(body, count(body, "<div"), count(body, "</div>"));
	}

	/** 表紙の画像と改ページの後で開いた罫囲み（表題の前の行を書き直す道）: 囲みは本文に 1 つだけ */
	@Test
	public void aBlockAfterACoverPageIsReopenedOnce() throws Exception {
		Map<String, String> pages = convert("［＃挿絵（small.png）入る］\n［＃改ページ］\n［＃ここから罫囲み］\n作品\n著者\n\n本文一\n", "");
		String body = String.join("", pages.values());
		assertTrue(body + log, body.contains("本文一") && log.indexOf(ENDED) >= 0);
		assertEquals(body, 1, count(body, "border"));
		for (Map.Entry<String, String> page : pages.entrySet()) {
			String b = page.getValue();
			assertEquals(page.getKey() + ": " + b, count(b, "<div"), count(b, "</div>"));
			//表題の後の改ページで、本文は表紙のページと分かれる
			assertFalse("本文が表紙のページに入った: " + b, b.contains("本文一") && b.contains("<img"));
			//待っている改ページ（改ページの注記）を指定し直さない。し直すと表題の後の空行が消える
			if (b.contains("本文一")) assertTrue("表題の後の空行が無い: " + b, b.indexOf("<br/>") >= 0 && b.indexOf("<br/>") < b.indexOf("本文一"));
		}
	}

	/** 表題の塊の後で字下げを入れ替える行（閉じて開く行）: 本文に書き直すのは後の字下げ */
	@Test
	public void aSwappedIndentReopensTheLaterOne() throws Exception {
		String body = assertBodyKept("［＃ここから２字下げ］\n作品\n著者\n［＃ここから３字下げ］\n本文一\n［＃ここで字下げ終わり］\n本文二\n", "本文一", "本文二");
		assertTrue(body, body.contains("pt3") && !body.contains("pt2"));
		assertEquals(body, count(body, "<div"), count(body, "</div>"));
	}

	/** 本文の頭の行の挿絵は、書き直した囲みの中なので単ページにしない（表題を終えた所でタグの数を 0 にしない） */
	@Test
	public void anImageOnTheFirstBodyLineStaysInsideTheBlock() throws Exception {
		Map<String, String> pages = convert("［＃ここから罫囲み］\n作品\n著者\n\n［＃挿絵（fig.png）入る］本文一\n［＃ここで罫囲み終わり］\n本文二\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("本文一") && body.contains("本文二") && body.contains("<img"));
		for (Map.Entry<String, String> page : pages.entrySet())
			assertEquals(page.getKey() + ": " + page.getValue(), count(page.getValue(), "<div"), count(page.getValue(), "</div>"));
		assertTrue("挿絵が単ページになった:\n" + log, log.indexOf("タグ内のため画像単ページ化できません") >= 0);
	}

	/** 対照: 表題の後のコメント（前走査が表題の塊に数えない行）は表題に含めたまま、本文の前で閉じれば警告しない */
	@Test
	public void aCommentInsideTheFrameDoesNotEndTheTitle() throws Exception {
		String body = body("［＃ここから罫囲み］\n作品\n著者\n\n-------------------------------------------------------\n説明\n-------------------------------------------------------\n［＃ここで罫囲み終わり］\n本文一\n");
		assertTrue(body, body.contains("本文一"));
		assertFalse(body, body.contains("border"));
		assertFalse(log.toString(), log.indexOf(ENDED) >= 0);
	}

	/** 表紙の画像のすぐ後で開いた罫囲み（改ページの注記なし）: 表題の後で改ページし、本文は表紙のページに入れない。表題の後の空行も残す */
	@Test
	public void aBlockRightAfterACoverImageStartsANewPage() throws Exception {
		Map<String, String> pages = convert("［＃挿絵（small.png）入る］\n［＃ここから罫囲み］\n作品\n著者\n\n本文一\n", "");
		boolean found = false;
		for (String b : pages.values()) {
			if (!b.contains("本文一")) continue;
			found = true;
			assertFalse("本文が表紙のページに入った: " + b, b.contains("<img"));
			assertTrue("表題の後の空行が無い: " + b, b.indexOf("<br/>") >= 0 && b.indexOf("<br/>") < b.indexOf("本文一"));
		}
		assertTrue(pages.toString(), found);
	}

	/** 表題の塊の中の空行（表題と著者の間）は本文に持ち越さない。残すのは表題の塊の後の空行だけ（閉じて終える道と同じ） */
	@Test
	public void blankLinesInsideTheTitleBlockAreNotCarriedToTheBody() throws Exception {
		Map<String, String> pages = convert("［＃挿絵（small.png）入る］\n［＃改ページ］\n［＃ここから罫囲み］\n作品\n\n著者\n\n本文一\n", "");
		for (String b : pages.values()) {
			if (!b.contains("本文一")) continue;
			assertEquals(b, 1, count(b.substring(0, b.indexOf("本文一")), "<br/>"));
		}
	}

	/** 1 行目の表題の行で開いた注記: 表題を終えた後の本文の挿絵を残す（表題の間は挿絵を出さない印を戻す） */
	@Test
	public void anImageAfterTheCutIsKept() throws Exception {
		String body = assertBodyKept("［＃ここから罫囲み］作品\n著者\n\n本文一\n［＃挿絵（small.png）入る］\n", "本文一");
		assertTrue("挿絵が無い:\n" + body, body.contains("<img"));
	}

	/** 表題の塊が 4 行（作品・副題・著者・訳者）でも、本文を残す（前走査は表題の窓の後まで文字の行を覚える） */
	@Test
	public void aFourLineTitleBlockKeepsTheBody() throws Exception {
		assertBodyKept("［＃ここから罫囲み］\n作品\n副題\n著者\n訳者訳\n\n本文一\n", "本文一");
	}

	/** 続けて変換すると、前の本で開いたままのタグが次の本に残らない */
	@Test
	public void openTagsDoNotLeakIntoTheNextBook() throws Exception {
		File src = tempFolder.newFolder();
		File dst = tempFolder.newFolder();
		File first = new File(src, "a.txt");
		Files.write(first.toPath(), "［＃ここから罫囲み］\n作品\n著者\n".getBytes(StandardCharsets.UTF_8));
		File second = new File(src, "b.txt");
		Files.write(second.toPath(), "題二\n著者二\n\n本文二\n".getBytes(StandardCharsets.UTF_8));
		File ini = tempFolder.newFile();
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		try {
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			AozoraEpub3.run(new String[]{ "-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), first.getPath(), second.getPath() });
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		File[] epubs = dst.listFiles((d, name) -> name.endsWith(".epub") && name.contains("題二"));
		assertEquals(java.util.Arrays.toString(dst.list()), 1, epubs.length);
		try (ZipFile zip = new ZipFile(epubs[0])) {
			for (ZipEntry e : Collections.list(zip.entries())) {
				if (!e.getName().endsWith(".xhtml")) continue;
				byte[] bytes = zip.getInputStream(e).readAllBytes();
				DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
			}
		}
	}

	/** 表紙の後の左右中央は表紙の節だけに掛かり、本文は普通のページ（表題を閉じて終える道と同じ。改ページを指定し直す） */
	@Test
	public void aMiddlePageBeforeTheTitleDoesNotSpreadToTheBody() throws Exception {
		Map<String, String> pages = convert("［＃挿絵（small.png）入る］\n［＃ページの左右中央］\n［＃ここから罫囲み］\n作品\n著者\n\n本文一\n", "");
		boolean found = false;
		for (String b : pages.values()) {
			if (!b.contains("本文一")) continue;
			found = true;
			assertFalse("本文が左右中央になった: " + b, b.contains("middle"));
		}
		assertTrue(pages.toString(), found);
	}

	/** 書き直した囲みの中の 2 行目以降の挿絵も、単ページにしない（改ページでタグの数を 0 にしない） */
	@Test
	public void aLaterImageInsideTheReopenedBlockStaysInline() throws Exception {
		Map<String, String> pages = convert("［＃ここから罫囲み］\n作品\n著者\n\n本文一\n［＃挿絵（fig.png）入る］\n［＃ここで罫囲み終わり］\n本文二\n", "");
		for (Map.Entry<String, String> page : pages.entrySet())
			assertEquals(page.getKey() + ": " + page.getValue(), count(page.getValue(), "<div"), count(page.getValue(), "</div>"));
		assertTrue("挿絵が単ページになった:\n" + log, log.indexOf("タグ内のため画像単ページ化できません") >= 0);
	}

	/** コメントを出力する設定でも、表題の後のコメントの中の行は表題を終えない */
	@Test
	public void aPrintedCommentInsideTheFrameDoesNotEndTheTitle() throws Exception {
		convert("［＃ここから罫囲み］\n作品\n著者\n\n-------------------------------------------------------\n説明\n-------------------------------------------------------\n［＃ここで罫囲み終わり］\n本文一\n", "CommentPrint=1\n");
		assertFalse(log.toString(), log.indexOf(ENDED) >= 0);
	}

	/** 対照: 本文の前に閉じれば、これまでどおり表題に含めて警告しない（囲みは本文に出ない） */
	@Test
	public void aBlockClosedBeforeTheBodyStaysInTheTitle() throws Exception {
		String body = body("［＃ここから罫囲み］\n作品\n著者\n［＃ここで罫囲み終わり］\n\n本文一\n");
		assertTrue(body, body.contains("本文一"));
		assertFalse(body, body.contains("border"));
		assertFalse(body, body.contains("作品"));
		assertFalse(log.toString(), log.indexOf(ENDED) >= 0);
	}

	/** 対照: 注記だけの行（閉じる前の空行）は表題に含めたまま */
	@Test
	public void annotationOnlyLinesDoNotEndTheTitle() throws Exception {
		String body = body("［＃ここから罫囲み］\n作品\n著者\n\n［＃ここで罫囲み終わり］\n本文一\n");
		assertTrue(body, body.contains("本文一"));
		assertFalse(body, body.contains("border"));
		assertFalse(log.toString(), log.indexOf(ENDED) >= 0);
	}

	/** 本文の中で閉じないブロックも、最後に閉じる（master は XHTML が壊れた） */
	@Test
	public void anUnclosedBlockInTheBodyIsClosedAtTheEnd() throws Exception {
		String body = body("作品\n著者\n\n［＃ここから罫囲み］\n本文一\n本文二\n");
		assertTrue(body, body.contains("本文一") && body.contains("本文二"));
		assertTrue(log.toString(), log.indexOf(UNCLOSED) >= 0);
		assertFalse(log.toString(), log.indexOf(ENDED) >= 0);
	}

	/** 表題を本文に出す種類でも、本文と囲みは同じ（master もここは本文を残していた） */
	@Test
	public void theNormalTitlePageKeepsTheBodyToo() throws Exception {
		String body = String.join("", convert("［＃ここから罫囲み］\n作品\n著者\n\n本文一\n", "TitlePage=0\n").values());
		assertTrue(body, body.contains("作品") && body.contains("本文一") && body.contains("border"));
	}

	/** 開いたタグを積み、閉じたら最後の開きを外す。&lt;…/&gt; は数えない */
	@Test
	public void trackOpenTags() {
		List<String> open = new ArrayList<>();
		AozoraEpub3Converter.trackOpenTags("<div class=\"a\"><span>x</span><br/>", open);
		assertEquals(List.of("<div class=\"a\">"), open);
		AozoraEpub3Converter.trackOpenTags("<div class=\"b\">", open);
		AozoraEpub3Converter.trackOpenTags("</div>", open);
		assertEquals(List.of("<div class=\"a\">"), open);
		AozoraEpub3Converter.trackOpenTags("</div></div>", open);
		assertTrue(open.isEmpty());
	}
}
