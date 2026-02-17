import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.hmdev.web.WebAozoraConverter;
import com.github.hmdev.web.NarouFormatSettings;

/**
 * narou.rb との出力比較テスト
 *
 * test_data/narou_compat/input_cases.txt のテストケースを
 * Java側 printText() で変換し、narou.rb の期待出力と比較する。
 *
 * 実行方法:
 *   gradlew test --tests NarouRbOutputComparisonTest
 *
 * 事前準備:
 *   ruby test_data/narou_compat/generate_expected.rb
 *   で expected_narou.txt を生成しておくこと。
 */
public class NarouRbOutputComparisonTest {

	private WebAozoraConverter converter;
	private Method printTextMethod;
	private NarouFormatSettings formatSettings;

	private static final String INPUT_FILE = "test_data/narou_compat/input_cases.txt";
	private static final String EXPECTED_FILE = "test_data/narou_compat/expected_narou.txt";
	private static final String ACTUAL_FILE = "test_data/narou_compat/actual_java.txt";

	@Before
	public void setUp() throws Exception {
		File webConfigPath = new File("web");
		String testUrl = "https://ncode.syosetu.com/n0000xx/";

		converter = WebAozoraConverter.createWebAozoraConverter(testUrl, webConfigPath);
		assertNotNull("Converter作成失敗", converter);

		// リフレクションで printText にアクセス
		printTextMethod = WebAozoraConverter.class.getDeclaredMethod(
			"printText", BufferedWriter.class, String.class);
		printTextMethod.setAccessible(true);

		// formatSettings を取得して narou.rb デフォルト値に合わせる
		Field settingsField = WebAozoraConverter.class.getDeclaredField("formatSettings");
		settingsField.setAccessible(true);
		formatSettings = (NarouFormatSettings) settingsField.get(converter);

		// narou.rb 3.8.2 デフォルト値に合わせる
		formatSettings.setEnableConvertNumToKanji(true);
		formatSettings.setEnableKanjiNumWithUnits(true);
		formatSettings.setKanjiNumWithUnitsLowerDigitZero(3); // narou.rb default
		formatSettings.setEnableConvertSymbolsToZenkaku(true);
		formatSettings.setEnableAutoJoinInBrackets(true);
		formatSettings.setEnableAutoJoinLine(true);
		formatSettings.setEnableHalfIndentBracket(true);
		formatSettings.setEnableConvertHorizontalEllipsis(true);
		formatSettings.setEnableNarouTag(true);
		formatSettings.setEnableTransformFraction(false);
		formatSettings.setEnableTransformDate(false);
		formatSettings.setEnableDakutenFont(false);
		formatSettings.setEnableProlongedSoundMarkToDash(false);
	}

	/**
	 * テストケースファイルをパースする
	 */
	private Map<String, String> parseCases(String filePath) throws Exception {
		Map<String, String> cases = new LinkedHashMap<>();
		String currentName = null;
		List<String> currentLines = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.matches("^===CASE:\\s*(.+?)\\s*===$")) {
					if (currentName != null) {
						cases.put(currentName, String.join("\n", currentLines));
					}
					currentName = line.replaceAll("^===CASE:\\s*(.+?)\\s*===$", "$1");
					currentLines = new ArrayList<>();
				} else if (line.equals("===END===")) {
					if (currentName != null) {
						cases.put(currentName, String.join("\n", currentLines));
					}
				} else {
					currentLines.add(line);
				}
			}
		}
		return cases;
	}

	/**
	 * printText でテキストを変換する
	 * 実際のHTML処理と同様に、行ごとに printText を呼び出す。
	 * （HTML解析では各テキストノード/brタグ単位で printText が呼ばれるため）
	 */
	private String convertText(String input) throws Exception {
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			String[] lines = input.split("\n", -1);
			for (int i = 0; i < lines.length; i++) {
				printTextMethod.invoke(converter, bw, lines[i]);
				if (i < lines.length - 1) {
					bw.write("\n");
				}
			}
			bw.flush();
		}
		return sw.toString();
	}

	/**
	 * 全テストケースを変換して actual_java.txt に出力し、
	 * expected_narou.txt と比較する
	 */
	@Test
	public void compareWithNarouRb() throws Exception {
		// 入力ケースを読み込み
		Map<String, String> inputCases = parseCases(INPUT_FILE);
		assertFalse("入力テストケースが空", inputCases.isEmpty());

		// 期待出力が存在するか確認
		File expectedFile = new File(EXPECTED_FILE);
		assertTrue("expected_narou.txt が見つかりません。先に generate_expected.rb を実行してください。",
			expectedFile.exists());

		Map<String, String> expectedCases = parseCases(EXPECTED_FILE);

		// Java側の変換結果を生成・保存
		Map<String, String> actualCases = new LinkedHashMap<>();
		try (BufferedWriter fw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(ACTUAL_FILE), StandardCharsets.UTF_8))) {
			for (Map.Entry<String, String> entry : inputCases.entrySet()) {
				String name = entry.getKey();
				String input = entry.getValue();
				String actual = convertText(input);
				actualCases.put(name, actual);

				fw.write("===CASE: " + name + "===\n");
				fw.write(actual + "\n");
			}
			fw.write("===END===\n");
		}

		// 比較
		StringBuilder diffReport = new StringBuilder();
		int matchCount = 0;
		int diffCount = 0;

		for (String name : inputCases.keySet()) {
			String expected = expectedCases.get(name);
			String actual = actualCases.get(name);

			if (expected == null) {
				diffReport.append("\n[SKIP] ").append(name).append(": narou.rb出力なし\n");
				continue;
			}

			if (expected.equals(actual)) {
				matchCount++;
			} else {
				diffCount++;
				diffReport.append("\n[DIFF] ").append(name).append(":\n");
				diffReport.append("  input:    ").append(inputCases.get(name).replace("\n", "\\n")).append("\n");
				diffReport.append("  expected: ").append(expected.replace("\n", "\\n")).append("\n");
				diffReport.append("  actual:   ").append(actual.replace("\n", "\\n")).append("\n");
			}
		}

		// 結果のサマリを出力
		System.out.println("\n========================================");
		System.out.println("narou.rb 出力比較結果");
		System.out.println("========================================");
		System.out.println("一致: " + matchCount + " / " + inputCases.size());
		System.out.println("差異: " + diffCount + " / " + inputCases.size());

		if (diffReport.length() > 0) {
			System.out.println("\n--- 差異の詳細 ---");
			System.out.println(diffReport.toString());
		}

		System.out.println("========================================");
		System.out.println("Java出力: " + ACTUAL_FILE);
		System.out.println("narou.rb出力: " + EXPECTED_FILE);

		// 差異レポートをファイルにも出力
		try (BufferedWriter dw = new BufferedWriter(
				new OutputStreamWriter(
					new FileOutputStream("test_data/narou_compat/diff_report.txt"),
					StandardCharsets.UTF_8))) {
			dw.write("narou.rb 出力比較レポート\n");
			dw.write("========================\n\n");
			dw.write("一致: " + matchCount + " / " + inputCases.size() + "\n");
			dw.write("差異: " + diffCount + " / " + inputCases.size() + "\n");
			dw.write(diffReport.toString());
		}

		// テスト結果 - 差異がある場合はレポートを表示して失敗
		if (diffCount > 0) {
			fail(diffCount + " 件の差異があります。詳細は test_data/narou_compat/diff_report.txt を参照。\n"
				+ diffReport.toString());
		}
	}
}
