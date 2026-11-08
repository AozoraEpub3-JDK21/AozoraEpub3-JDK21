import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.velocity.app.Velocity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Kindle 向けの本の nav にも epub:type="toc" を付け、ほかの出力と同じく章の下に節を入れ子にする（internal #64 項目 4）。
 * 前は kindlegen に toc.ncx を使わせるため Kindle だけ外していて、epubcheck が RSC-005 を出し、目次も平らだった。
 * Kindle Previewer 4 は入れ子の nav を目次パネルの階層にする（2026-11-08 win で確認）。
 * kindlegen で .mobi にする本（拡張子 .mobi）だけは前のまま。
 */
public class AozoraEpub3KindleNavTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String nav(String... extra) throws Exception {
		StringBuilder text = new StringBuilder("目次の階層の試験\n試験著者\n\n");
		for (int c = 1; c <= 2; c++) {
			text.append("［＃大見出し］第").append(c).append("章［＃大見出し終わり］\n");
			for (int s = 1; s <= 2; s++) text.append("［＃中見出し］").append(c).append("-").append(s).append("節［＃中見出し終わり］\n本文です。\n\n");
			text.append("［＃改ページ］\n");
		}
		File txt = new File(tempFolder.newFolder(), "navtest.txt");
		Files.write(txt.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
		File dst = tempFolder.newFolder();
		File ini = tempFolder.newFile();
		List<String> args = new ArrayList<>(List.of("-i", ini.getPath(), "-enc", "UTF-8", "-d", dst.getPath()));
		args.addAll(List.of(extra));
		args.add(txt.getPath());
		String orgClassPath = System.getProperty("java.class.path");
		PrintStream orgOut = System.out;
		try {
			//Gradle のテスト JVM では template/ を解決できないので、プロジェクト直下の jar から起動したことにする（AozoraEpub3TextBmpTest と同じ）
			System.setProperty("java.class.path", new File("AozoraEpub3.jar").getAbsolutePath());
			Velocity.reset();
			System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			assertEquals(0, AozoraEpub3.run(args.toArray(new String[0])));
		} finally {
			System.setOut(orgOut);
			Velocity.reset();
			if (orgClassPath == null) System.clearProperty("java.class.path"); else System.setProperty("java.class.path", orgClassPath);
		}
		File[] epubs = dst.listFiles((d, n) -> n.endsWith(".epub") || n.endsWith(".mobi"));
		assertEquals(1, epubs.length);
		try (ZipFile zf = new ZipFile(epubs[0])) {
			return new String(zf.getInputStream(zf.getEntry("OPS/xhtml/nav.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** toc の nav は 1 つだけで、各章の節が章の li の中の ol に入る */
	private static void assertNestedToc(String nav) {
		Matcher toc = Pattern.compile("<nav epub:type=\"toc\"").matcher(nav);
		int count = 0;
		while (toc.find()) count++;
		assertEquals("toc の nav の数:\n" + nav, 1, count);
		String tocNav = nav.substring(nav.indexOf("<nav epub:type=\"toc\""), nav.indexOf("</nav>", nav.indexOf("<nav epub:type=\"toc\"")));
		for (int c = 1; c <= 2; c++) {
			Matcher chapter = Pattern.compile(">第" + c + "章</a>\\s*<ol>\\s*<li[^>]*><a[^>]*>" + c + "-1節</a></li>\\s*<li[^>]*><a[^>]*>" + c + "-2節</a></li></ol></li>").matcher(tocNav);
			assertTrue("第" + c + "章の下に節が入れ子になっていない:\n" + tocNav, chapter.find());
		}
	}

	@Test
	public void kindleNavIsATocAndNested() throws Exception {
		assertNestedToc(nav("-device", "kindle"));
	}

	/** kindlegen で .mobi にする本（拡張子 .mobi の Kindle 向け）は前のまま。toc の印の無い平らな nav を kindlegen に渡し、toc.ncx を使わせる */
	@Test
	public void navForKindlegenStaysAsBefore() throws Exception {
		String nav = nav("-device", "kindle", "-ext", ".mobi");
		assertTrue("toc の印が付いた:\n" + nav, !nav.contains("epub:type=\"toc\""));
		assertTrue(nav, nav.contains("<nav id=\"toc\">"));
		String tocNav = nav.substring(nav.indexOf("<nav id=\"toc\">"));
		assertEquals("入れ子になった:\n" + tocNav, 1, tocNav.substring(0, tocNav.indexOf("</nav>")).split("<ol>", -1).length - 1);
	}

	/** Kindle でない本も同じ形（Kindle だけ違う形にしない） */
	@Test
	public void defaultNavIsTheSame() throws Exception {
		assertNestedToc(nav());
	}
}
