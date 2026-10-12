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
 * internal #38: Web 変換は、サイトの文字の「［＃」を逃がさなかったので、注記の始まりとして読まれていた。
 * 作品名が柱に入ると閉じの注記を飲み込んで柱が閉じない。
 * - 題名（作品名・章題・話の題・著者名・なろうの [chapter:] の見出し）: 「［」を外字の注記（※［＃始め角括弧、1-1-46］）で書いて、ただの文字にする
 * - 本文: 逃がさない（narou.rb と同じ）。作者が書いた注記（［＃改ページ］など）・返り点（［＃一］〜［＃四］）・
 *   青空文庫の HTML が span.notes に置く注記（［＃「…」はママ］など）は注記として効く
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
	private String toc;

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

	private String episode(String bookTitle, String chapterTitle, String bodyHtml) throws Exception {
		return episode(bookTitle, chapterTitle, "第1話 はじまり", bodyHtml);
	}

	/** 変換の本体と同じ入口で 1 話を出力する。chapterTitle が null なら章の途中の話 */
	private String episode(String bookTitle, String chapterTitle, String subtitle, String bodyHtml) throws Exception {
		bookTitleField.set(converter, bookTitle);
		String html = "<html><body>"
			+ "<h1 class=\"p-novel__title p-novel__title--rensai\">" + subtitle + "</h1>"
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
	public void chapterAndEpisodeTitlesAreEscaped() throws Exception {
		String out = episode("作品", "章［＃題", "［＃改ページ］の話", "<p id=\"L1\">本文。</p>");
		assertTrue("章題: " + out, out.contains("［＃大見出し］章" + ESCAPED + "題［＃大見出し終わり］"));
		assertTrue("話の題（注記の形でも題名なら逃がす）: " + out, out.contains(ESCAPED + "改ページ］の話"));
	}

	@Test
	public void titleWithHalfWidthBracketIsEscapedOnlyWhenSymbolsGoFullWidth() throws Exception {
		//題名の [#1] は記号を全角にする設定で［＃１］になる。全角にしない設定なら注記にならないので、そのまま
		boolean saved = settings.isEnableConvertSymbolsToZenkaku();
		try {
			settings.setEnableConvertSymbolsToZenkaku(true);
			String out = episode("作品", null, "[#1] 出会い", "<p id=\"L1\">本文。</p>");
			assertTrue(out, out.matches("(?s).*" + java.util.regex.Pattern.quote(ESCAPED) + "[1１一]］ 出会い.*"));
			settings.setEnableConvertSymbolsToZenkaku(false);
			out = episode("作品", null, "[#1] 出会い ［#2］", "<p id=\"L1\">本文。</p>");
			assertFalse(out, out.contains("始め角括弧"));
		} finally {
			settings.setEnableConvertSymbolsToZenkaku(saved);
		}
	}

	@Test
	public void bodyIsNotEscaped() throws Exception {
		//本文は narou.rb と同じく逃がさない。作者が書いた注記・返り点は効く（番号の［＃１］も注記に読まれる。narou.rb と同じ）
		String out = episode("作品", null, "<p id=\"L1\">前［＃改ページ］後</p><p id=\"L2\">［＃３字下げ］字下げ</p>"
			+ "<p id=\"L3\">学［＃二］而時習［＃一］之</p><p id=\"L4\">［＃１］と作［＃品</p>");
		assertTrue(out, out.contains("前［＃改ページ］後"));
		assertTrue(out, out.contains("［＃３字下げ］字下げ"));
		assertTrue(out, out.contains("学［＃二］而時習［＃一］之"));
		assertTrue(out, out.matches("(?s).*［＃[１一]］と作［＃品.*"));
		assertFalse(out, out.contains("始め角括弧"));
	}

	@Test
	public void onlyTheOpeningBracketBeforeHashIsEscaped() throws Exception {
		//「＃」の付かない「［」・閉じの「］」・「＃」だけは注記にならないので、題名でもそのまま
		String out = episode("作品", "［注］と＃と］と［", "<p id=\"L1\">本文。</p>");
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
		//青空文庫の HTML: <span class="notes">［＃…］</span> は注記として読ませる（本文に出さない）。本文は逃がさないので、そのまま通る
		String html = "<p>措しい<span class=\"notes\">［＃「措しい」はママ］</span>事をする<span class=\"notes\">［＃ルビの「か」は底本では「け」］</span></p>";
		String out = printNode("https://www.aozora.gr.jp/cards/000148/files/789_14547.html", html);
		assertTrue("span.notes の中は注記のまま: " + out, out.contains("措しい［＃「措しい」はママ］事を"));
		assertTrue("span.notes の中は注記のまま: " + out, out.contains("する［＃ルビの「か」は底本では「け」］"));
	}

	@Test
	public void noExtraSpaceBeforeAnEscapedBracketAfterAnExclamation() throws Exception {
		//！？ の後は空けるが、「［」の前は空けない（narou.rb）。題名で逃がした「［」も同じ
		String out = episode("作品", null, "すごい！［＃２］ と なに？［注］", "<p id=\"L1\">本文。</p>");
		assertTrue(out, out.contains("すごい！" + ESCAPED + "２］"));
		assertTrue(out, out.contains("なに？［注］"));
	}

	@Test
	public void narouChapterTagHeadingIsEscaped() throws Exception {
		//本文の [chapter:…] は中見出しになる。見出しの中の「［＃」は題名と同じく全部逃がす（閉じを飲み込まないように）
		boolean saved = settings.isEnableNarouTag();
		try {
			settings.setEnableNarouTag(true);
			String out = episode("作品", null, "<p id=\"L1\">[chapter:章［＃題]</p>");
			assertTrue(out, out.contains("［＃中見出し］章" + ESCAPED + "題［＃中見出し終わり］"));
		} finally {
			settings.setEnableNarouTag(saved);
		}
	}

	/** そのサイトの converter で、本文の要素を出力する（printNode） */
	private String printNode(String url, String bodyHtml) throws Exception {
		WebAozoraConverter site = WebAozoraConverter.createWebAozoraConverter(url, new File("web"));
		assertNotNull(site);
		Method m = WebAozoraConverter.class.getDeclaredMethod("printNode", BufferedWriter.class, org.jsoup.nodes.Node.class, boolean.class);
		m.setAccessible(true);
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			m.invoke(site, bw, Jsoup.parseBodyFragment(bodyHtml, url).body(), false);
		}
		return sw.toString();
	}

	@Test
	public void escapedTextConvertsToTheLiteralCharacters() throws Exception {
		//中間テキストを変換して、柱が閉じ、題名に「［＃」がそのまま出て、目次の章の名前にも残り、本文の返り点は効くことを見る
		String text = "作品\n作者\n\n" + episode("作［＃品", "章［＃題", "<p id=\"L1\">学［＃二］而時習［＃一］之</p>");
		String xhtml = convert(text);
		assertTrue("柱が閉じていない・作品名が欠けた:\n" + xhtml, xhtml.contains("<div class=\"running_head yoko\">作［＃品</div>"));
		assertTrue("章題の「［＃」が消えた:\n" + xhtml, xhtml.contains("章［＃題"));
		assertTrue("返り点が注記として効いていない:\n" + xhtml, xhtml.contains("学<span class=\"sub\">二</span>而時習<span class=\"sub\">一</span>之"));
		assertTrue("目次の章の名前の「［＃」が消えた:\n" + toc, toc.contains("<text>章［＃題</text>"));
	}

	/** AozoraEpub3 で変換し、表題ページと目次を除く XHTML をつなげて返す。toc.ncx は toc に入れる */
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
				String body = new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
				if (name.endsWith("toc.ncx")) toc = body;
				if (name.endsWith(".xhtml") && !name.endsWith("nav.xhtml") && !name.endsWith("title.xhtml")) xhtml.append(body);
			}
		}
		return xhtml.toString();
	}
}
