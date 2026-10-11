import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.velocity.app.Velocity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.github.hmdev.web.NarouFormatSettings;
import com.github.hmdev.web.WebAozoraConverter;

/**
 * internal #38: Web 変換は、サイトの文字（作品名・章題・本文）の「［＃」を逃がさなかったので、注記の始まりとして読まれていた。
 * 作品名が柱に入ると閉じの注記を飲み込んで柱が閉じず、本文では字が消えた。
 * 「［」を外字の注記（※［＃始め角括弧、1-1-46］）で書いて、ただの文字にする。
 * 青空文庫の HTML の span.notes は中身が注記そのもの（底本との違いなど）なので、注記のまま通す。
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterChukiEscapeTest
 */
public class WebAozoraConverterChukiEscapeTest {

	private static final String ESCAPED = "※［＃始め角括弧、1-1-46］＃";

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private WebAozoraConverter converter;
	private NarouFormatSettings settings;
	private Field bookTitleField;
	private boolean savedCenterPage;
	private boolean savedHashira;
	private Object savedBookTitle;
	private Method printEpisode;

	@Before
	public void setUp() throws Exception {
		// converter はサイトごとに static に共有されるので、変えた設定は @After で戻す
		converter = WebAozoraConverter.createWebAozoraConverter(
			"https://ncode.syosetu.com/n0000xx/", new File("web"));
		assertNotNull(converter);
		settings = converter.getFormatSettings();
		savedCenterPage = settings.isChapterUseCenterPage();
		savedHashira = settings.isChapterUseHashira();
		settings.setChapterUseCenterPage(true);
		settings.setChapterUseHashira(true);

		bookTitleField = WebAozoraConverter.class.getDeclaredField("bookTitle");
		bookTitleField.setAccessible(true);
		savedBookTitle = bookTitleField.get(converter);

		printEpisode = WebAozoraConverter.class.getDeclaredMethod(
			"printEpisode", BufferedWriter.class, Document.class, String.class,
			String.class, String.class, String.class);
		printEpisode.setAccessible(true);
	}

	@After
	public void tearDown() throws Exception {
		if (settings != null) {
			settings.setChapterUseCenterPage(savedCenterPage);
			settings.setChapterUseHashira(savedHashira);
		}
		if (bookTitleField != null) bookTitleField.set(converter, savedBookTitle);
	}

	/** 変換の本体と同じ入口で、章の最初の 1 話を出力する */
	private String episode(String bookTitle, String chapterTitle, String bodyHtml) throws Exception {
		bookTitleField.set(converter, bookTitle);
		String html = "<html><body>"
			+ "<h1 class=\"p-novel__title p-novel__title--rensai\">第1話 はじまり</h1>"
			+ "<div class=\"js-novel-text p-novel__text\">" + bodyHtml + "</div>"
			+ "</body></html>";
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			Document doc = Jsoup.parse(html, "https://ncode.syosetu.com/n0000xx/1/");
			printEpisode.invoke(converter, bw, doc, chapterTitle, null, null, null);
		}
		return sw.toString();
	}

	@Test
	public void bookTitleInTheHashiraIsEscaped() throws Exception {
		String out = episode("作［＃品", "第一章", "<p id=\"L1\">本文。</p>");
		assertTrue("柱の作品名の「［＃」が逃がされていない: " + out,
			out.contains("［＃ここから柱］作" + ESCAPED + "品［＃ここで柱終わり］"));
	}

	@Test
	public void chapterTitleAndBodyAreEscaped() throws Exception {
		String out = episode("作品", "章［＃題", "<p id=\"L1\">本文の［＃中見出し］はそのまま。</p>");
		assertTrue("章題: " + out, out.contains("［＃大見出し］章" + ESCAPED + "題［＃大見出し終わり］"));
		assertTrue("本文: " + out, out.contains("本文の" + ESCAPED + "中見出し］はそのまま。"));
	}

	@Test
	public void onlyTheOpeningBracketBeforeHashIsEscaped() throws Exception {
		//「＃」の付かない「［」・閉じの「］」・「＃」だけは注記にならないので、そのまま
		String out = episode("作品", null, "<p id=\"L1\">［注］と＃と］と［</p>");
		assertTrue(out, out.contains("［注］と＃と］と［"));
		assertFalse(out, out.contains("始め角括弧"));
	}

	@Test
	public void annotationsTheConverterWritesAreNotEscaped() throws Exception {
		//変換が足す注記（太字・柱・見出し・改ページ）は逃がさない
		String out = episode("作品", "第一章", "<p id=\"L1\"><b>太字</b>の本文。</p>");
		assertTrue(out, out.contains("［＃ここから太字］太字［＃ここで太字終わり］"));
		assertTrue(out, out.contains("［＃ここから柱］作品［＃ここで柱終わり］"));
		assertTrue(out, out.contains("［＃改ページ］"));
		assertFalse(out, out.contains("始め角括弧"));
	}

	@Test
	public void aozoraNotesStayAnnotations() throws Exception {
		//青空文庫の HTML: <span class="notes">［＃…］</span> は注記として読ませる（本文に出さない）
		String out = episode("作品", null, "<p id=\"L1\">措しい<span class=\"notes\">［＃「措しい」はママ］</span>事を［＃する</p>");
		assertTrue("span.notes の中は注記のまま: " + out, out.contains("措しい［＃「措しい」はママ］事を"));
		assertTrue("span.notes を出た後は逃がす: " + out, out.contains("事を" + ESCAPED + "する"));
	}

	@Test
	public void escapedTextConvertsToTheLiteralCharacters() throws Exception {
		//中間テキストを変換して、柱が閉じ、本文に「［＃」がそのまま出ることを見る
		String text = "作品\n作者\n\n" + episode("作［＃品", "第一章", "<p id=\"L1\">本文の［＃中見出し］はそのまま。</p>");
		String xhtml = convert(text);
		assertTrue("柱が閉じていない・作品名が欠けた:\n" + xhtml, xhtml.contains("<div class=\"running_head yoko\">作［＃品</div>"));
		assertTrue("本文の「［＃」が消えた:\n" + xhtml, xhtml.contains("本文の［＃中見出し］はそのまま。"));
	}

	/** AozoraEpub3 で変換し、表題ページと目次を除く XHTML をつなげて返す */
	private String convert(String text) throws Exception {
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
			assertEquals(0, AozoraEpub3.run(new String[] { "-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath(), txt.getPath() }));
		} finally {
			System.setOut(orgOut);
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		StringBuilder xhtml = new StringBuilder();
		try (ZipFile zip = new ZipFile(dst.listFiles((d, n) -> n.endsWith(".epub"))[0])) {
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				String name = e.getName();
				if (name.endsWith(".xhtml") && !name.endsWith("nav.xhtml") && !name.endsWith("title.xhtml")) {
					xhtml.append(new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		}
		return xhtml.toString();
	}
}
