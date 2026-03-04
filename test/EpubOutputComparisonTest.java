import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * EPUB出力比較テスト: direct URL変換 vs narou.rb+AozoraEpub3 パイプライン
 *
 * 検証データ: n1314hd「魔術師クノンは見えている」
 *   - narou.rb 生成 txt: D:/MyNovel/小説データ/小説家になろう/n1314hd.../
 *   - direct URL キャッシュ txt: .cache/ncode.syosetu.com/n1314hd/
 *
 * 両方の txt を AozoraEpub3 でEPUBに変換し、以下を比較:
 *   - タイトル・著者が一致
 *   - ルビ数 (direct >= narou.rb は版数差なので許容)
 *   - ルビが両方に存在する
 *
 * データが存在しない場合はテストをスキップ（assumeTrue）。
 *
 * 実行方法:
 *   gradlew test --tests EpubOutputComparisonTest
 */
public class EpubOutputComparisonTest {

	private static final String NCODE         = "n1314hd";
	private static final String NAROU_BASE    = "D:/MyNovel/小説データ/小説家になろう";
	private static final String CACHE_BASE    = ".cache/ncode.syosetu.com";
	private static final String OUT_DIR       = "build/epub_compare_test";

	private File jarFile;
	private File narouTxt;
	private File directTxt;

	@Before
	public void findTestData() {
		jarFile   = new File("build/libs/AozoraEpub3.jar");
		narouTxt  = findNarouRbTxt();
		directTxt = findDirectCacheTxt();
	}

	// ----------------------------------------------------------------
	// メインテスト
	// ----------------------------------------------------------------

	@Test
	public void compareEpubFromNarouRbVsDirectUrl() throws Exception {
		Assume.assumeTrue(
			"AozoraEpub3.jar が見つかりません。先に gradlew jar を実行してください。",
			jarFile.exists());
		Assume.assumeTrue(
			"narou.rb txt が見つかりません。narou.rb で " + NCODE + " を変換済みの環境が必要です。",
			narouTxt != null && narouTxt.exists());
		Assume.assumeTrue(
			"direct URL キャッシュ txt が見つかりません。先に -url " + NCODE + " を実行してください。",
			directTxt != null && directTxt.exists());

		File outDir = new File(OUT_DIR);
		outDir.mkdirs();

		// 両方を EPUB に変換（別プロセスで実行）
		File narouEpub  = convertTxtToEpub(narouTxt,  new File(outDir, "narou"));
		File directEpub = convertTxtToEpub(directTxt, new File(outDir, "direct"));

		assertNotNull("narou.rb → EPUB 変換失敗", narouEpub);
		assertNotNull("direct URL → EPUB 変換失敗", directEpub);
		assertTrue("narou.rb EPUB サイズが小さすぎる", narouEpub.length() > 1000);
		assertTrue("direct EPUB サイズが小さすぎる", directEpub.length() > 1000);

		// EPUB内容を抽出
		EpubStats narouStats  = extractEpubStats(narouEpub);
		EpubStats directStats = extractEpubStats(directEpub);

		// レポート出力
		String report = buildReport(narouStats, directStats);
		System.out.println(report);
		writeReport(new File(outDir, "report.txt"), report);

		// アサーション
		assertEquals("タイトル一致",  narouStats.title,   directStats.title);
		assertEquals("著者一致",      narouStats.creator, directStats.creator);
		assertTrue("narou.rb EPUB にルビがある",  narouStats.rubyCount  > 0);
		assertTrue("direct  EPUB にルビがある",   directStats.rubyCount > 0);
		// direct はより新しいバージョン（話数が多い）のでルビ数は >= を許容
		assertTrue("direct のルビ数 >= narou.rb のルビ数",
			directStats.rubyCount >= narouStats.rubyCount);
		// 章数も同様
		assertTrue("direct の章数 >= narou.rb の章数",
			directStats.chapterCount >= narouStats.chapterCount);

		// ルビ形式を比較（共通章の先頭ルビが一致するか）
		if (!narouStats.firstRubies.isEmpty() && !directStats.firstRubies.isEmpty()) {
			Set<String> narouSet = new HashSet<>(narouStats.firstRubies);
			Set<String> directSet = new HashSet<>(directStats.firstRubies);
			narouSet.retainAll(directSet);
			assertTrue("共通ルビが存在する（形式一致）", !narouSet.isEmpty());
		}
	}

	// ----------------------------------------------------------------
	// ファイル検索ヘルパー
	// ----------------------------------------------------------------

	private File findNarouRbTxt() {
		File base = new File(NAROU_BASE);
		if (!base.isDirectory()) return null;
		for (File dir : base.listFiles()) {
			if (!dir.isDirectory() || !dir.getName().startsWith(NCODE)) continue;
			for (File f : dir.listFiles()) {
				if (f.getName().endsWith(".txt")
						&& !f.getName().contains("前書")
						&& f.length() > 100_000) {
					return f;
				}
			}
		}
		return null;
	}

	private File findDirectCacheTxt() {
		File dir = new File(CACHE_BASE + "/" + NCODE);
		if (!dir.isDirectory()) return null;
		for (File f : dir.listFiles()) {
			if (f.getName().endsWith(".txt") && !f.getName().equals("update.txt")) {
				return f;
			}
		}
		return null;
	}

	// ----------------------------------------------------------------
	// EPUB変換（別プロセスで fat JAR を実行）
	// ----------------------------------------------------------------

	private File convertTxtToEpub(File txtFile, File outDir) throws Exception {
		if (outDir.exists()) deleteDir(outDir);
		outDir.mkdirs();

		ProcessBuilder pb = new ProcessBuilder(
			"java", "-cp", jarFile.getAbsolutePath(),
			"AozoraEpub3",
			"-enc", "UTF-8",
			"-d", outDir.getAbsolutePath(),
			txtFile.getAbsolutePath()
		);
		// JAR と同じディレクトリをワーキングディレクトリに（template/, chuki_*.txt 等の解決用）
		pb.directory(jarFile.getAbsoluteFile().getParentFile());
		pb.redirectErrorStream(true);

		System.out.println("[test] converting: " + txtFile.getName());
		Process proc = pb.start();
		String output;
		try (InputStream is = proc.getInputStream()) {
			output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		int exitCode = proc.waitFor();
		System.out.println("[test] exit=" + exitCode + " output(" + output.length() + " chars)");
		if (output.length() > 0) {
			System.out.println(output.substring(0, Math.min(output.length(), 500)));
		}

		File[] epubs = outDir.listFiles((d, name) -> name.endsWith(".epub"));
		return (epubs != null && epubs.length > 0) ? epubs[0] : null;
	}

	private void deleteDir(File dir) throws IOException {
		Files.walk(dir.toPath())
			.sorted(Comparator.reverseOrder())
			.map(Path::toFile)
			.forEach(File::delete);
	}

	// ----------------------------------------------------------------
	// EPUB内容抽出
	// ----------------------------------------------------------------

	static class EpubStats {
		String title   = "(不明)";
		String creator = "(不明)";
		int    rubyCount    = 0;
		int    chapterCount = 0;
		List<String> firstRubies = new ArrayList<>();
	}

	private EpubStats extractEpubStats(File epub) throws Exception {
		EpubStats s = new EpubStats();
		// AozoraEpub3 は <ruby>文字<rt>ルビ</rt>文字<rt>ルビ</rt></ruby> 形式
		// （1文字ずつ <rt> がつく場合がある）
		// <ruby>...</ruby> 全体を1ルビとしてカウントする
		Pattern rubyPat = Pattern.compile("<ruby>(.*?)</ruby>");
		Pattern rtPat = Pattern.compile("<rt>([^<]*+)</rt>");

		try (ZipInputStream zis = new ZipInputStream(
				new FileInputStream(epub), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

				if (name.endsWith(".opf")) {
					Matcher m = Pattern.compile("<dc:title[^>]*+>([^<]++)").matcher(content);
					if (m.find()) s.title = m.group(1).trim();
					m = Pattern.compile("<dc:creator[^>]*+>([^<]++)").matcher(content);
					if (m.find()) s.creator = m.group(1).trim();
				}

				if (name.endsWith(".xhtml") || name.endsWith(".html")) {
					Matcher m = rubyPat.matcher(content);
					while (m.find()) {
						s.rubyCount++;
						if (s.firstRubies.size() < 20) {
							// 内部の <rt> からルビテキストを抽出して正規化表現にする
							String inner = m.group(1);
							String base = inner.replaceAll("<r[pt][^>]*+>([^<]*+)</r[pt]>", "").trim();
							StringBuilder rt = new StringBuilder();
							Matcher rtm = rtPat.matcher(inner);
							while (rtm.find()) rt.append(rtm.group(1));
							s.firstRubies.add(base + "《" + rt + "》");
						}
					}
					// 章数（class="chap" を含む div を章と見なす）
					s.chapterCount += countMatches(content, "class=\"chap");
				}
			}
		}
		return s;
	}

	private int countMatches(String text, String pattern) {
		int count = 0, idx = 0;
		while ((idx = text.indexOf(pattern, idx)) != -1) { count++; idx++; }
		return count;
	}

	// ----------------------------------------------------------------
	// レポート生成
	// ----------------------------------------------------------------

	private String buildReport(EpubStats narou, EpubStats direct) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n========================================\n");
		sb.append("EPUB出力比較レポート: ").append(NCODE).append("\n");
		sb.append("========================================\n");
		sb.append(String.format("%-12s  %-30s  %-30s%n", "項目", "narou.rb+AozoraEpub3", "direct -url"));
		sb.append(String.format("%-12s  %-30s  %-30s%n", "title",   narou.title,   direct.title));
		sb.append(String.format("%-12s  %-30s  %-30s%n", "creator", narou.creator, direct.creator));
		sb.append(String.format("%-12s  %-30d  %-30d%n", "ruby数",  narou.rubyCount,  direct.rubyCount));
		sb.append(String.format("%-12s  %-30d  %-30d%n", "章数",    narou.chapterCount, direct.chapterCount));
		sb.append("\n--- タイトル一致: " + narou.title.equals(direct.title) + " ---\n");
		sb.append("--- 著者一致: "   + narou.creator.equals(direct.creator) + " ---\n");
		if (!narou.firstRubies.isEmpty()) {
			Set<String> common = new HashSet<>(narou.firstRubies);
			common.retainAll(new HashSet<>(direct.firstRubies));
			sb.append("--- 共通ルビ数(先頭20件中): " + common.size() + " ---\n");
			if (!common.isEmpty()) {
				sb.append("    例: ").append(common.iterator().next()).append("\n");
			}
		}
		sb.append("========================================\n");
		return sb.toString();
	}

	private void writeReport(File f, String content) {
		try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
			w.write(content);
		} catch (Exception e) {
			System.err.println("レポート書き込み失敗: " + e.getMessage());
		}
	}
}
