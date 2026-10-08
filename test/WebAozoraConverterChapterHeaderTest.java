import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.github.hmdev.web.NarouFormatSettings;
import com.github.hmdev.web.WebAozoraConverter;

/**
 * 章が変わったときの章中表紙（printChapterHeader）のテスト
 *
 * 左右中央の章中表紙の後に改ページが無いと、続く第1話まで左右中央の節に入る
 * （narou.rb は章題の直後に［＃改ページ］を出す）。
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterChapterHeaderTest
 */
public class WebAozoraConverterChapterHeaderTest {

	private WebAozoraConverter converter;
	private NarouFormatSettings settings;
	private Method printChapterHeader;

	@Before
	public void setUp() throws Exception {
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://kakuyomu.jp/works/1234567890", new File("web"));
		assertNotNull(converter);

		Field fs = WebAozoraConverter.class.getDeclaredField("formatSettings");
		fs.setAccessible(true);
		settings = (NarouFormatSettings) fs.get(converter);

		Field bt = WebAozoraConverter.class.getDeclaredField("bookTitle");
		bt.setAccessible(true);
		bt.set(converter, "本の題");

		printChapterHeader = WebAozoraConverter.class.getDeclaredMethod(
			"printChapterHeader", BufferedWriter.class, String.class);
		printChapterHeader.setAccessible(true);
	}

	private String header(String chapterTitle) throws Exception {
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			printChapterHeader.invoke(converter, bw, chapterTitle);
		}
		return sw.toString();
	}

	private static int count(String s, String sub) {
		int n = 0;
		for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
		return n;
	}

	@Test
	public void centerPageEndsWithPageBreakAfterChapterTitle() throws Exception {
		settings.setChapterUseCenterPage(true);
		String out = header("第一章");

		assertTrue("左右中央の注記がある: " + out, out.contains("［＃ページの左右中央］"));
		assertEquals("改ページは章中表紙の前と後の 2 つ: " + out, 2, count(out, "［＃改ページ］"));
		// 後ろの改ページは大見出しより後ろ＝第1話は次のページから
		assertTrue("大見出しの後で改ページして終わる: " + out,
			out.endsWith("［＃大見出し終わり］\n\n［＃改ページ］\n"));
		assertTrue("先頭は改ページ: " + out, out.startsWith("\n［＃改ページ］\n［＃ページの左右中央］\n"));
	}

	@Test
	public void withoutCenterPageChapterTitleFlowsIntoFirstEpisode() throws Exception {
		settings.setChapterUseCenterPage(false);
		String out = header("第一章");

		assertFalse("左右中央の注記は無い: " + out, out.contains("［＃ページの左右中央］"));
		assertEquals("改ページは章題の前の 1 つだけ: " + out, 1, count(out, "［＃改ページ］"));
		assertTrue("大見出しの後は空行だけ: " + out, out.endsWith("［＃大見出し終わり］\n\n"));
	}

	@Test
	public void hashiraAndTitleAreWrittenBetweenTheBreaks() throws Exception {
		settings.setChapterUseCenterPage(true);
		settings.setChapterUseHashira(true);
		String out = header("第二章 展開");

		int hashira = out.indexOf("［＃ここから柱］");
		int title = out.indexOf("第二章");
		int lastBreak = out.lastIndexOf("［＃改ページ］");
		assertTrue("柱がある: " + out, hashira > 0);
		assertTrue("本の題が柱に出る: " + out, out.contains("本の題"));
		assertTrue("柱 → 章題 → 改ページの順: " + out, hashira < title && title < lastBreak);
	}
}
