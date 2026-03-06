import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Java版リファレンスEPUB比較テスト（.NET JavaComparisonTests の並列テスト）
 *
 * reference/input.txt を Java fat JAR で EPUB に変換し、
 * reference/reference.epub とタイムスタンプ・UUID 正規化後に完全一致を検証する。
 *
 * テストデータ: D:/git/aozoraepub3-dotnet/tests/integration/reference/{id}/
 *   - input.txt      : narou.rb が生成した青空文庫形式テキスト
 *   - reference.epub  : Java AozoraEpub3 で生成したリファレンス EPUB
 *
 * スキップ条件:
 *   - fat JAR (build/libs/AozoraEpub3.jar) が存在しない → gradlew jar を先に実行
 *   - reference ディレクトリが存在しない → aozoraepub3-dotnet リポジトリが必要
 *
 * 実行方法:
 *   gradlew jar
 *   gradlew test --tests "JavaAozoraVsReferenceTest"
 */
@RunWith(Parameterized.class)
public class JavaAozoraVsReferenceTest {

	private static final Path REFERENCE_BASE =
		Paths.get("D:/git/aozoraepub3-dotnet/tests/integration/reference");
	private static final Path JAR_PATH =
		Paths.get("build/libs/AozoraEpub3.jar");
	private static final Path PROJECT_ROOT = Paths.get(".").toAbsolutePath().normalize();

	// テキストファイル拡張子（正規化比較対象）
	private static final Set<String> TEXT_EXTENSIONS =
		Set.of(".xhtml", ".html", ".opf", ".ncx", ".css", ".xml");

	// package.opf 正規化パターン
	private static final Pattern PAT_DC_DATE =
		Pattern.compile("<dc:date>[^<]*+</dc:date>");
	private static final Pattern PAT_DCTERMS_MODIFIED =
		Pattern.compile("<meta\\s++property=\"dcterms:modified\">[^<]*+</meta>");
	private static final Pattern PAT_DC_IDENTIFIER =
		Pattern.compile("<dc:identifier[^>]*+>urn:uuid:[0-9a-f\\-]++</dc:identifier>");
	private static final Pattern PAT_DCTERMS_IDENTIFIER =
		Pattern.compile("property=\"dcterms:identifier\">urn:uuid:[0-9a-f\\-]++</meta>");

	// toc.ncx 正規化パターン
	private static final Pattern PAT_DTB_UID =
		Pattern.compile("<meta\\s++name=\"dtb:uid\"\\s++content=\"[^\"]*+\"\\s*+/?>");

	private final String testId;
	private final String inputEncoding;

	public JavaAozoraVsReferenceTest(String testId, String inputEncoding) {
		this.testId = testId;
		this.inputEncoding = inputEncoding;
	}

	@Parameters(name = "{0}")
	public static Collection<Object[]> testCases() {
		return Arrays.asList(new Object[][] {
			{ "n8005ls",                       "UTF-8" },
			{ "n0063lr",                       "UTF-8" },
			{ "n9623lp",                       "UTF-8" },
			{ "kakuyomu_822139840468926025",   "UTF-8" },
			{ "aozora_1567_14913",             "MS932" },
		});
	}

	@Test
	public void compareWithReference() throws Exception {
		Path jarFile = PROJECT_ROOT.resolve(JAR_PATH);
		Assume.assumeTrue(
			"fat JAR が見つかりません: " + jarFile + "（先に gradlew jar を実行）",
			Files.exists(jarFile));

		Path refDir = REFERENCE_BASE.resolve(testId);
		Assume.assumeTrue(
			"reference ディレクトリが見つかりません: " + refDir,
			Files.isDirectory(refDir));

		Path inputTxt = refDir.resolve("input.txt");
		Path referenceEpub = refDir.resolve("reference.epub");
		Assume.assumeTrue("input.txt が見つかりません: " + inputTxt, Files.exists(inputTxt));
		Assume.assumeTrue("reference.epub が見つかりません: " + referenceEpub, Files.exists(referenceEpub));

		// 一時出力ディレクトリ
		Path outDir = PROJECT_ROOT.resolve("build/reference_test/" + testId);
		if (Files.exists(outDir)) deleteDir(outDir);
		Files.createDirectories(outDir);

		try {
			// Java fat JAR で EPUB 変換
			Path generatedEpub = convertToEpub(jarFile, inputTxt, outDir);
			assertNotNull("[" + testId + "] EPUB 生成失敗（出力ファイルなし）", generatedEpub);
			assertTrue("[" + testId + "] EPUB サイズが小さすぎる",
				Files.size(generatedEpub) > 100);

			// 両 EPUB を読み込み
			Map<String, byte[]> refEntries = readZipEntries(referenceEpub);
			Map<String, byte[]> genEntries = readZipEntries(generatedEpub);

			// 比較実行
			ComparisonResult result = compareEpubs(refEntries, genEntries);

			// レポート出力
			String report = result.buildReport(testId);
			System.out.println(report);
			writeReport(outDir.resolve("comparison_report.txt"), report);

			// アサーション
			assertTrue("[" + testId + "] エントリ一覧が不一致:\n" + result.entryDiffSummary(),
				result.entryListMatch);
			assertTrue("[" + testId + "] 内容に差異あり:\n" + result.contentDiffSummary(),
				result.contentDiffs.isEmpty());
		} finally {
			// テスト後クリーンアップ（失敗時はレポートを残す）
		}
	}

	// ================================================================
	// EPUB 変換
	// ================================================================

	private Path convertToEpub(Path jarFile, Path inputTxt, Path outDir) throws Exception {
		List<String> cmd = new ArrayList<>(Arrays.asList(
			"java", "-Dfile.encoding=UTF-8",
			"-cp", jarFile.toAbsolutePath().toString(),
			"AozoraEpub3",
			"-enc", inputEncoding,
			"-of",
			"-d", outDir.toAbsolutePath().toString(),
			inputTxt.toAbsolutePath().toString()
		));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		// プロジェクトルート（template/, gaiji/, AozoraEpub3.ini 等の解決用）
		pb.directory(PROJECT_ROOT.toFile());
		pb.redirectErrorStream(true);

		System.out.println("[" + testId + "] 変換開始: " + inputTxt.getFileName());
		Process proc = pb.start();
		String output;
		try (InputStream is = proc.getInputStream()) {
			output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		int exitCode = proc.waitFor();
		System.out.println("[" + testId + "] exit=" + exitCode
			+ " output=" + output.length() + " chars");
		if (!output.isEmpty()) {
			System.out.println(output.substring(0, Math.min(output.length(), 800)));
		}

		// 生成された EPUB を探す
		try (var stream = Files.list(outDir)) {
			return stream
				.filter(p -> p.toString().endsWith(".epub"))
				.findFirst()
				.orElse(null);
		}
	}

	// ================================================================
	// ZIP 読み込み
	// ================================================================

	private Map<String, byte[]> readZipEntries(Path zipPath) throws IOException {
		Map<String, byte[]> entries = new TreeMap<>();
		try (ZipInputStream zis = new ZipInputStream(
				Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) continue;
				entries.put(entry.getName(), zis.readAllBytes());
			}
		}
		return entries;
	}

	// ================================================================
	// EPUB 比較
	// ================================================================

	static class ComparisonResult {
		boolean entryListMatch = true;
		List<String> onlyInRef = new ArrayList<>();
		List<String> onlyInGen = new ArrayList<>();
		/** entryName -> diff description */
		Map<String, String> contentDiffs = new LinkedHashMap<>();
		int totalTextFiles = 0;
		int totalBinaryFiles = 0;

		String entryDiffSummary() {
			StringBuilder sb = new StringBuilder();
			if (!onlyInRef.isEmpty())
				sb.append("  reference のみ: ").append(onlyInRef).append("\n");
			if (!onlyInGen.isEmpty())
				sb.append("  generated のみ: ").append(onlyInGen).append("\n");
			return sb.toString();
		}

		String contentDiffSummary() {
			StringBuilder sb = new StringBuilder();
			int shown = 0;
			for (var e : contentDiffs.entrySet()) {
				if (shown++ >= 5) {
					sb.append("  ... 他 ").append(contentDiffs.size() - 5).append(" ファイル\n");
					break;
				}
				sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
			}
			return sb.toString();
		}

		String buildReport(String testId) {
			StringBuilder sb = new StringBuilder();
			sb.append("\n========================================\n");
			sb.append("リファレンス比較レポート: ").append(testId).append("\n");
			sb.append("========================================\n");
			sb.append("エントリ一覧一致: ").append(entryListMatch).append("\n");
			sb.append("テキストファイル数: ").append(totalTextFiles).append("\n");
			sb.append("バイナリファイル数: ").append(totalBinaryFiles).append("\n");
			if (!onlyInRef.isEmpty())
				sb.append("reference のみ: ").append(onlyInRef).append("\n");
			if (!onlyInGen.isEmpty())
				sb.append("generated のみ: ").append(onlyInGen).append("\n");
			if (contentDiffs.isEmpty()) {
				sb.append("結果: 完全一致 ✓\n");
			} else {
				sb.append("差異ファイル数: ").append(contentDiffs.size()).append("\n");
				for (var e : contentDiffs.entrySet()) {
					sb.append("\n--- ").append(e.getKey()).append(" ---\n");
					sb.append(e.getValue()).append("\n");
				}
			}
			sb.append("========================================\n");
			return sb.toString();
		}
	}

	private ComparisonResult compareEpubs(Map<String, byte[]> ref, Map<String, byte[]> gen) {
		ComparisonResult result = new ComparisonResult();

		// エントリ一覧比較
		Set<String> refKeys = ref.keySet();
		Set<String> genKeys = gen.keySet();
		for (String k : refKeys) {
			if (!genKeys.contains(k)) result.onlyInRef.add(k);
		}
		for (String k : genKeys) {
			if (!refKeys.contains(k)) result.onlyInGen.add(k);
		}
		if (!result.onlyInRef.isEmpty() || !result.onlyInGen.isEmpty()) {
			result.entryListMatch = false;
		}

		// 共通エントリの内容比較
		Set<String> common = new TreeSet<>(refKeys);
		common.retainAll(genKeys);

		for (String name : common) {
			byte[] refBytes = ref.get(name);
			byte[] genBytes = gen.get(name);

			if (isTextFile(name)) {
				result.totalTextFiles++;
				String refText = normalizeText(name,
					new String(refBytes, StandardCharsets.UTF_8));
				String genText = normalizeText(name,
					new String(genBytes, StandardCharsets.UTF_8));
				if (!refText.equals(genText)) {
					result.contentDiffs.put(name, buildLineDiff(refText, genText));
				}
			} else {
				result.totalBinaryFiles++;
				if (!Arrays.equals(refBytes, genBytes)) {
					result.contentDiffs.put(name,
						"バイナリ不一致 (ref=" + refBytes.length
						+ " bytes, gen=" + genBytes.length + " bytes)");
				}
			}
		}

		return result;
	}

	// ================================================================
	// テキスト正規化
	// ================================================================

	private boolean isTextFile(String name) {
		String lower = name.toLowerCase();
		for (String ext : TEXT_EXTENSIONS) {
			if (lower.endsWith(ext)) return true;
		}
		return false;
	}

	private String normalizeText(String entryName, String content) {
		// CRLF → LF
		content = content.replace("\r\n", "\n");
		// 末尾改行除去
		content = content.replaceAll("\\n+$", "");

		String lower = entryName.toLowerCase();

		if (lower.endsWith(".opf")) {
			content = PAT_DC_DATE.matcher(content)
				.replaceAll("<dc:date>NORMALIZED_DATE</dc:date>");
			content = PAT_DCTERMS_MODIFIED.matcher(content)
				.replaceAll("<meta property=\"dcterms:modified\">NORMALIZED_DATE</meta>");
			content = PAT_DC_IDENTIFIER.matcher(content)
				.replaceAll("<dc:identifier id=\"unique-id\">urn:uuid:NORMALIZED_UUID</dc:identifier>");
			content = PAT_DCTERMS_IDENTIFIER.matcher(content)
				.replaceAll("property=\"dcterms:identifier\">urn:uuid:NORMALIZED_UUID</meta>");
		}

		if (lower.endsWith(".ncx")) {
			content = PAT_DTB_UID.matcher(content)
				.replaceAll("<meta name=\"dtb:uid\" content=\"NORMALIZED_UUID\"/>");
		}

		return content;
	}

	// ================================================================
	// 行単位 diff
	// ================================================================

	private String buildLineDiff(String refText, String genText) {
		String[] refLines = refText.split("\n", -1);
		String[] genLines = genText.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		int maxLines = Math.max(refLines.length, genLines.length);
		int diffCount = 0;
		int shownCount = 0;

		for (int i = 0; i < maxLines; i++) {
			String refLine = i < refLines.length ? refLines[i] : "<EOF>";
			String genLine = i < genLines.length ? genLines[i] : "<EOF>";
			if (!refLine.equals(genLine)) {
				diffCount++;
				if (shownCount < 30) {
					shownCount++;
					sb.append("  L").append(i + 1).append(":\n");
					sb.append("    REF: ").append(truncate(refLine, 200)).append("\n");
					sb.append("    GEN: ").append(truncate(genLine, 200)).append("\n");
				}
			}
		}

		if (refLines.length != genLines.length) {
			sb.append("  行数: ref=").append(refLines.length)
				.append(" gen=").append(genLines.length).append("\n");
		}
		if (diffCount > shownCount) {
			sb.append("  ... 他 ").append(diffCount - shownCount).append(" 行の差異\n");
		}
		sb.append("  差異行数合計: ").append(diffCount);
		return sb.toString();
	}

	private String truncate(String s, int maxLen) {
		return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
	}

	// ================================================================
	// ユーティリティ
	// ================================================================

	private void deleteDir(Path dir) throws IOException {
		Files.walk(dir)
			.sorted(Comparator.reverseOrder())
			.map(Path::toFile)
			.forEach(File::delete);
	}

	private void writeReport(Path path, String content) {
		try {
			Files.writeString(path, content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("レポート書き込み失敗: " + e.getMessage());
		}
	}
}
