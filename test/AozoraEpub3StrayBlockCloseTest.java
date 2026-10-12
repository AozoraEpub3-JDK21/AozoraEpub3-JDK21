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

import com.github.hmdev.converter.AozoraEpub3Converter;

/**
 * 開いていないタグの閉じ（改ページで終えたブロックの後から来た閉じの注記など）は書かない（internal #41）。
 * 字下げの「字下げ終わり重複」と同じ扱いを、ほかのブロックにも当てる
 */
public class AozoraEpub3StrayBlockCloseTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String DROPPED = "閉じる注記の重複";

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

	private static int count(String s, String part) {
		return s.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	/** ページごとに div の開きと閉じの数が同じ */
	private static void assertBalanced(Map<String, String> pages) {
		for (Map.Entry<String, String> page : pages.entrySet())
			assertEquals(page.getKey() + ": " + page.getValue(), count(page.getValue(), "<div"), count(page.getValue(), "</div>"));
	}

	/** 開いていない罫囲みの閉じは書かず、後の罫囲みは普通に開いて閉じる（master は余った閉じで XHTML が壊れた） */
	@Test
	public void aStrayCloseIsDropped() throws Exception {
		Map<String, String> pages = convert("作品\n著者\n\n本文一\n［＃ここで罫囲み終わり］\n本文二\n［＃ここから罫囲み］\n本文三\n［＃ここで罫囲み終わり］\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("本文一") && body.contains("本文二") && body.contains("本文三"));
		assertEquals(body, 1, count(body, "<div class=\"border\">"));
		assertBalanced(pages);
		assertTrue(log.toString(), log.indexOf(DROPPED) >= 0);
	}

	/** 改ページをまたぐ罫囲み: 改ページで閉じ、次のページの閉じの注記は書かない（字下げと同じ） */
	@Test
	public void aBlockAcrossAPageBreakIsClosedOnceLikeAnIndent() throws Exception {
		Map<String, String> frame = convert("作品\n著者\n\n［＃ここから罫囲み］\n本文一\n［＃改ページ］\n本文二\n［＃ここで罫囲み終わり］\n本文三\n", "");
		assertTrue("改ページが無い: " + frame.keySet(), frame.size() >= 2);
		assertBalanced(frame);
		assertTrue(log.toString(), log.indexOf(DROPPED) >= 0);
		//対照: 字下げは master から同じ形（改ページで閉じ、後の字下げ終わりは書かない）
		Map<String, String> indent = convert("作品\n著者\n\n［＃ここから２字下げ］\n本文一\n［＃改ページ］\n本文二\n［＃ここで字下げ終わり］\n本文三\n", "");
		assertBalanced(indent);
		//（改行の数だけは違うので、空白を除いて比べる）
		assertEquals(String.join("", indent.values()).replace("pt2", "X").replaceAll("\\s", ""), String.join("", frame.values()).replace("border", "X").replaceAll("\\s", ""));
	}

	/** 行の中の余った閉じ（太字終わりだけ）も外し、本文の文字は残す */
	@Test
	public void aStrayInlineCloseIsDropped() throws Exception {
		String body = String.join("", convert("作品\n著者\n\n本文［＃太字終わり］一\n", "").values());
		assertTrue(body, body.contains("本文一"));
		assertFalse(body, body.contains("</span>"));
	}

	/** 対照: 対になっている注記は外さない（ログも出ない） */
	@Test
	public void matchedClosesAreKept() throws Exception {
		Map<String, String> pages = convert("作品\n著者\n\n［＃ここから罫囲み］\n本文［＃太字］一［＃太字終わり］\n［＃ここで罫囲み終わり］\n本文二\n", "TitlePage=0\n");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("border") && body.contains("</span>"));
		assertBalanced(pages);
		assertFalse(log.toString(), log.indexOf(DROPPED) >= 0);
	}

	/** 本文と同じ行の余った閉じでタグの数が負にならない（負のままだと、次の改ページの注記まで大きさでの改ページが止まる） */
	@Test
	public void sizeBasedPageBreaksContinueAfterAStrayClose() throws Exception {
		StringBuilder text = new StringBuilder("作品\n著者\n\n本文［＃ここで罫囲み終わり］\n");
		for (int i=0; i<200; i++) text.append("あいうえおかきくけこさしすせそたちつてと").append(i).append("\n");
		Map<String, String> pages = convert(text.toString(), "PageBreak=1\nPageBreakSize=1\n");
		assertTrue("大きさで改ページしていない: " + pages.keySet(), pages.size() >= 3);
	}

	/** 改ページのすぐ次の行の閉じ: 改ページで閉じるので、次のページに書かない */
	@Test
	public void aCloseRightAfterAPageBreakIsDropped() throws Exception {
		Map<String, String> pages = convert("作品\n著者\n\n［＃ここから罫囲み］\n本文一\n［＃改ページ］\n［＃ここで罫囲み終わり］\n本文二\n", "");
		assertTrue("改ページが無い: " + pages.keySet(), pages.size() >= 2);
		assertBalanced(pages);
		assertTrue(log.toString(), log.indexOf(DROPPED) >= 0);
	}

	/** 表題の後に書き直す囲みを、本文の最初の行の中で閉じる: 閉じは外さない（改ページの後に書き直すので開いている） */
	@Test
	public void aReopenedBlockClosedOnTheFirstBodyLineKeepsItsClose() throws Exception {
		Map<String, String> pages = convert("［＃ここから罫囲み］\n作品\n著者\n\n本文一［＃ここで罫囲み終わり］\n本文二\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.indexOf("</div>") >= 0 && body.indexOf("</div>") < body.indexOf("本文二"));
		assertBalanced(pages);
		assertFalse(log.toString(), log.indexOf(DROPPED) >= 0);
	}

	/** 見出しの次の行が余った閉じだけでも、変換は止まらない（閉じを外して行が空になる） */
	@Test
	public void aStrayCloseOnAHeadingLineDoesNotAbort() throws Exception {
		Map<String, String> pages = convert("作品\n著者\n\n［＃ここから中見出し］\n［＃ここで罫囲み終わり］\n見出し\n［＃ここで中見出し終わり］\n本文\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("見出し") && body.contains("本文"));
		assertBalanced(pages);
	}

	/** 行の終わりで閉じる注記（２字下げ）の行の余った閉じ: 行の終わりの閉じは残し、余った閉じだけ外す */
	@Test
	public void aStrayCloseOnALineWithALineEndCloseIsDropped() throws Exception {
		Map<String, String> pages = convert("作品\n著者\n\n［＃２字下げ］本文［＃ここで罫囲み終わり］\n次\n", "");
		String body = String.join("", pages.values());
		assertTrue(body, body.contains("pt2") && body.contains("本文") && body.contains("次"));
		assertBalanced(pages);
	}

	/** 開いている数から、位置の順に対にして（trackOpenTags と同じ）、対の無い閉じだけを外す */
	@Test
	public void dropUnmatchedCloseTags() {
		StringBuilder kept = new StringBuilder();
		assertEquals(1, AozoraEpub3Converter.dropUnmatchedCloseTags("</div>", 0, kept));
		assertEquals("", kept.toString());
		kept.setLength(0);
		assertEquals(0, AozoraEpub3Converter.dropUnmatchedCloseTags("</div>", 1, kept));
		assertEquals("</div>", kept.toString());
		kept.setLength(0);
		assertEquals(1, AozoraEpub3Converter.dropUnmatchedCloseTags("本<span>文</span>一</div>", 0, kept));
		assertEquals("本<span>文</span>一", kept.toString());
		kept.setLength(0);
		assertEquals(1, AozoraEpub3Converter.dropUnmatchedCloseTags("</div></div>", 1, kept));
		assertEquals("</div>", kept.toString());
		kept.setLength(0);
		//行の終わりの閉じ（開きと同じ行）は対になる
		assertEquals(1, AozoraEpub3Converter.dropUnmatchedCloseTags("<div class=\"pt2\">本文</div></div>", 0, kept));
		assertEquals("<div class=\"pt2\">本文</div>", kept.toString());
		kept.setLength(0);
		//<…/> は開きに数えない
		assertEquals(1, AozoraEpub3Converter.dropUnmatchedCloseTags("<br/></div>", 0, kept));
		assertEquals("<br/>", kept.toString());
	}
}
