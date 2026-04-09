import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * ハーメルン E2E テスト — 複数パターンをダウンロードし narou.rb 互換仕様でコンテンツを検証する
 *
 * 検証マスター: narou.rb の syosetu.org.yaml 定義
 *   - 章タイトル regex: (?:<tr><td colspan=2><strong>(?<chapter>.+?)</strong></td></tr>)?
 *   - エピソードリンク: /novel/{ncode}/{index}.html
 *   - 前書き: <div id="maegaki">
 *   - 後書き: <div id="atogaki">
 *   - タイトル: span[itemprop=name]
 *   - 著者: span[itemprop=author]
 *
 * スキップ条件:
 *   - fat JAR が存在しない → gradlew jar を実行すること
 *   - ネットワーク到達不可
 *
 * 実行方法:
 *   gradlew jar
 *   gradlew test --tests HamelnE2ETest
 */
public class HamelnE2ETest {

	private static final String OUT_DIR = "build/hameln_e2e_test";

	/** テストパターン定義 */
	static class TestCase {
		final String id;
		final String url;
		final String description;
		final int minChapters;    // 期待する最低章数 (0=章なし)
		final boolean hasPreamble;  // 前書きあり
		final boolean hasPostscript; // 後書きあり

		TestCase(String id, String url, String description,
				int minChapters, boolean hasPreamble, boolean hasPostscript) {
			this.id = id;
			this.url = url;
			this.description = description;
			this.minChapters = minChapters;
			this.hasPreamble = hasPreamble;
			this.hasPostscript = hasPostscript;
		}
	}

	private static final List<TestCase> TEST_CASES = Arrays.asList(
		// 章ありの連載作品 (なろう同様のデモ用として小規模なものを選択)
		new TestCase("hameln_chapter", "https://novel.syosetu.org/402358/",
			"章ありハーメルン作品", 1, false, false),
		// 章なし短編 (isUrlExists で存在確認するため URL が削除されていてもテストはスキップされる)
		new TestCase("hameln_nochapter", "https://novel.syosetu.org/7/",
			"章なしハーメルン作品", 0, false, false)
	);

	private File jarFile;

	@Before
	public void setUp() {
		jarFile = new File("build/libs/AozoraEpub3.jar");
	}

	@Test
	public void testHamelnChapterWork() throws Exception {
		runTestCase(TEST_CASES.get(0));
	}

	@Test
	public void testHamelnNoChapterWork() throws Exception {
		runTestCase(TEST_CASES.get(1));
	}

	private void runTestCase(TestCase tc) throws Exception {
		// E2E テストはネットワークアクセスが必要なため、明示的な opt-in が必要
		// 実行方法: gradlew jar -DhamelnE2E=true test --tests HamelnE2ETest
		Assume.assumeTrue(
			"E2E テストをスキップ (有効化するには -DhamelnE2E=true を指定)",
			"true".equalsIgnoreCase(System.getProperty("hamelnE2E")));
		Assume.assumeTrue(
			"fat JAR が見つかりません。先に gradlew jar を実行してください。",
			jarFile.exists());
		Assume.assumeTrue(
			"ネットワーク到達不可 (ハーメルンへの接続が必要)",
			isNetworkAvailable("https://novel.syosetu.org/"));
		Assume.assumeTrue(
			"[" + tc.id + "] テスト対象 URL が存在しません (削除された作品の可能性): " + tc.url,
			isUrlExists(tc.url));

		Path outDir = Paths.get(OUT_DIR, tc.id);
		if (Files.exists(outDir)) deleteDir(outDir);
		Files.createDirectories(outDir);

		System.out.println("\n=== " + tc.description + " (" + tc.url + ") ===");

		// AozoraEpub3 で変換実行
		File epub = convertUrl(tc.url, outDir.toFile());

		assertNotNull("[" + tc.id + "] EPUB 生成失敗", epub);
		assertTrue("[" + tc.id + "] EPUB が小さすぎる (最低 1KB)", epub.length() > 1000);
		System.out.println("EPUB: " + epub.getName() + " (" + epub.length() + " bytes)");

		// EPUB 内容を解析
		EpubContent content = extractEpubContent(epub);
		System.out.println("タイトル: " + content.title);
		System.out.println("著者: " + content.creator);
		System.out.println("章数: " + content.chapterCount);
		System.out.println("ルビ数: " + content.rubyCount);
		System.out.println("読了表示あり: " + content.hasEndOfBook);
		System.out.println("前書きあり: " + content.hasPreamble);
		System.out.println("後書きあり: " + content.hasPostscript);

		// --- narou.rb マスター仕様に基づく検証 ---

		// タイトル・著者が取得できること
		assertFalse("[" + tc.id + "] タイトルが未取得", content.title.isEmpty());
		assertFalse("[" + tc.id + "] 著者が未取得", content.creator.isEmpty());

		// 章サポート検証
		if (tc.minChapters > 0) {
			assertTrue("[" + tc.id + "] 章が " + tc.minChapters + " 件以上あること",
				content.chapterCount >= tc.minChapters);
		}

		// テキスト処理 (narou.rb 互換): 読了表示が末尾に付くこと
		assertTrue("[" + tc.id + "] 読了表示 '（本を読み終わりました）' が存在すること",
			content.hasEndOfBook);

		// 前書き・後書き
		if (tc.hasPreamble) {
			assertTrue("[" + tc.id + "] 前書き (.introduction) が存在すること", content.hasPreamble);
		}
		if (tc.hasPostscript) {
			assertTrue("[" + tc.id + "] 後書き (.postscript) が存在すること", content.hasPostscript);
		}
	}

	// ================================================================
	// EPUB 変換
	// ================================================================

	private File convertUrl(String url, File outDir) throws Exception {
		List<String> cmd = Arrays.asList(
			"java", "-Dfile.encoding=UTF-8",
			"-jar", jarFile.getAbsolutePath(),
			"--url", url,
			"-of",
			"-d", outDir.getAbsolutePath()
		);
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(new File(".").getAbsoluteFile());
		pb.redirectErrorStream(true);

		System.out.println("[convert] " + url);
		Process proc = pb.start();
		String output;
		try (InputStream is = proc.getInputStream()) {
			output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		int exitCode = proc.waitFor();
		System.out.println("[convert] exit=" + exitCode + " (" + output.length() + " chars output)");
		if (output.length() > 0) {
			System.out.println(output.substring(0, Math.min(output.length(), 600)));
		}

		File[] epubs = outDir.listFiles((d, name) -> name.endsWith(".epub"));
		return (epubs != null && epubs.length > 0) ? epubs[0] : null;
	}

	// ================================================================
	// EPUB 内容解析
	// ================================================================

	static class EpubContent {
		String title   = "";
		String creator = "";
		int chapterCount = 0;
		int rubyCount    = 0;
		boolean hasEndOfBook  = false;
		boolean hasPreamble   = false;
		boolean hasPostscript = false;
	}

	private EpubContent extractEpubContent(File epub) throws Exception {
		EpubContent c = new EpubContent();
		try (ZipInputStream zis = new ZipInputStream(
				new FileInputStream(epub), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				String text = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

				if (name.endsWith(".opf")) {
					Matcher m = Pattern.compile("<dc:title[^>]*+>([^<]++)").matcher(text);
					if (m.find()) c.title = m.group(1).trim();
					m = Pattern.compile("<dc:creator[^>]*+>([^<]++)").matcher(text);
					if (m.find()) c.creator = m.group(1).trim();
				}

				if (name.endsWith(".xhtml") || name.endsWith(".html")) {
					// 章数 (class="chap" を持つ要素)
					c.chapterCount += countOccurrences(text, "class=\"chap");
					// ルビ数
					c.rubyCount += countOccurrences(text, "<ruby>");
					// 読了表示
					if (text.contains("本を読み終わりました")) c.hasEndOfBook = true;
					// 前書き
					if (text.contains("class=\"introduction\"")) c.hasPreamble = true;
					// 後書き
					if (text.contains("class=\"postscript\"")) c.hasPostscript = true;
				}
			}
		}
		return c;
	}

	private int countOccurrences(String text, String pattern) {
		int count = 0, idx = 0;
		while ((idx = text.indexOf(pattern, idx)) != -1) { count++; idx++; }
		return count;
	}

	// ================================================================
	// ユーティリティ
	// ================================================================

	/** サーバーに到達でき 2xx/3xx が返ること (5xx や接続エラーを除外するネットワーク疎通確認) */
	private boolean isNetworkAvailable(String urlStr) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setConnectTimeout(5000);
			conn.setRequestMethod("HEAD");
			conn.setRequestProperty("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			int code = conn.getResponseCode();
			return code < 500;
		} catch (Exception e) {
			return false;
		}
	}

	/** URL が存在すること (2xx/3xx のみ true、404 等は false でテストスキップ) */
	private boolean isUrlExists(String urlStr) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setConnectTimeout(5000);
			conn.setRequestMethod("HEAD");
			conn.setRequestProperty("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			int code = conn.getResponseCode();
			return code >= 200 && code < 400;
		} catch (Exception e) {
			return false;
		}
	}

	private void deleteDir(Path dir) throws IOException {
		Files.walk(dir)
			.sorted(Comparator.reverseOrder())
			.map(Path::toFile)
			.forEach(File::delete);
	}
}
