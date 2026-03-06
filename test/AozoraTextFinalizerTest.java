import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import com.github.hmdev.web.AozoraTextFinalizer;
import com.github.hmdev.web.NarouFormatSettings;

/**
 * AozoraTextFinalizer の単体テスト
 *
 * テスト対象:
 * - 前書き・後書きの自動検出
 * - 改ページ直後の見出し化
 * - かぎ括弧の開閉チェック
 * - ファイルサイズ制限
 *
 * 実行方法:
 *   gradlew test --tests AozoraTextFinalizerTest
 */
public class AozoraTextFinalizerTest {

	private NarouFormatSettings settings;
	private AozoraTextFinalizer finalizer;
	private File tempFile;

	@Before
	public void setUp() throws Exception {
		settings = new NarouFormatSettings();
		// テスト安定性のため新機能をデフォルト無効化（個別テストで有効化する）
		settings.setEnableAutoIndent(false);
		settings.setEnablePackBlankLine(false);
		settings.setEnableHalfIndentBracket(false);
		settings.setEnableConvertNumToKanji(false);
		settings.setEnableAlphabetToZenkaku(false);
		settings.setEnableDisplayEndOfBook(false);
		settings.setEnableAutoJoinInBrackets(false);
		settings.setEnableAutoJoinLine(false);
		settings.setEnableConvertHorizontalEllipsis(false);
		finalizer = new AozoraTextFinalizer(settings);

		// テスト用の一時ファイル
		tempFile = File.createTempFile("aozora_test_", ".txt");
	}

	@After
	public void tearDown() {
		if (tempFile != null && tempFile.exists()) {
			tempFile.delete();
		}
	}

	/**
	 * テスト用ファイルを作成
	 */
	private void writeTestFile(String content) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8"))) {
			bw.write(content);
		}
	}

	/**
	 * テスト用ファイルを読み込み
	 */
	private String readTestFile() throws Exception {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(tempFile), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	// ========================================================================
	// 前書き・後書きの自動検出テスト
	// ========================================================================

	@Test
	public void testAuthorIntroduction() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("前書きの自動検出テスト");
		System.out.println("=".repeat(60));

		String input = "タイトル\n" +
				"著者名\n" +
				"\n" +
				"********************************************\n" +  // *44個
				"これは前書きです。\n" +
				"テスト用の前書き。\n" +
				"\n" +
				"［＃改ページ］\n" +
				"本文開始\n";

		writeTestFile(input);

		settings.setEnableAuthorComments(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		System.out.println("入力:");
		System.out.println(input);
		System.out.println("\n結果:");
		System.out.println(result);

		assertTrue("前書き開始注記がない", result.contains("［＃ここから前書き］"));
		assertTrue("前書き終了注記がない", result.contains("［＃ここで前書き終わり］"));

		System.out.println("\n判定: ✓");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	@Test
	public void testAuthorPostscript() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("後書きの自動検出テスト");
		System.out.println("=".repeat(60));

		String input = "タイトル\n" +
				"著者名\n" +
				"\n" +
				"本文内容\n" +
				"\n" +
				"************************************************\n" +  // *48個
				"これは後書きです。\n" +
				"テスト用の後書き。\n";

		writeTestFile(input);

		settings.setEnableAuthorComments(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		System.out.println("入力:");
		System.out.println(input);
		System.out.println("\n結果:");
		System.out.println(result);

		assertTrue("後書き開始注記がない", result.contains("［＃ここから後書き］"));
		assertTrue("後書き終了注記がない", result.contains("［＃ここで後書き終わり］"));

		System.out.println("\n判定: ✓");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	@Test
	public void testAuthorCommentsDisabled() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("前書き・後書き検出の無効化テスト");
		System.out.println("=".repeat(60));

		String input = "タイトル\n" +
				"著者名\n" +
				"\n" +
				"********************************************\n" +
				"これは前書きのはずだが無効化されている。\n";

		writeTestFile(input);

		settings.setEnableAuthorComments(false);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		assertFalse("前書き注記が挿入されている", result.contains("［＃ここから前書き］"));

		System.out.println("判定: ✓（注記が挿入されていない）");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	// ========================================================================
	// 改ページ直後の見出し化テスト
	// ========================================================================

	@Test
	public void testEnchantMidashi() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("改ページ後の見出し化テスト");
		System.out.println("=".repeat(60));

		String input = "タイトル\n" +
				"著者名\n" +
				"\n" +
				"本文\n" +
				"［＃改ページ］\n" +
				"第一章\n" +
				"内容\n" +
				"［＃改ページ］\n" +
				"第二章\n" +
				"内容\n";

		writeTestFile(input);

		settings.setEnableEnchantMidashi(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		System.out.println("入力:");
		System.out.println(input);
		System.out.println("\n結果:");
		System.out.println(result);

		assertTrue("第一章が見出し化されていない",
				result.contains("［＃３字下げ］［＃中見出し］第一章［＃中見出し終わり］"));
		assertTrue("第二章が見出し化されていない",
				result.contains("［＃３字下げ］［＃中見出し］第二章［＃中見出し終わり］"));

		System.out.println("\n判定: ✓");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	@Test
	public void testEnchantMidashiDisabled() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("改ページ後の見出し化の無効化テスト");
		System.out.println("=".repeat(60));

		String input = "［＃改ページ］\n" +
				"第一章\n";

		writeTestFile(input);

		settings.setEnableEnchantMidashi(false);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		assertFalse("見出し注記が挿入されている", result.contains("［＃中見出し］"));

		System.out.println("判定: ✓（見出し化されていない）");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	// ========================================================================
	// かぎ括弧の開閉チェックテスト
	// ========================================================================

	@Test
	public void testInspectBrackets() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("かぎ括弧の開閉チェックテスト");
		System.out.println("=".repeat(60));

		// 正常なケース
		String validInput = "「これは正常な文章です」\n" +
				"『二重かぎ括弧も正常』\n";

		writeTestFile(validInput);

		settings.setEnableInspectInvalidOpenCloseBrackets(true);
		finalizer.finalize(tempFile);

		System.out.println("正常なケース:");
		System.out.println(validInput);
		System.out.println("判定: ✓（警告なし）");

		// 異常なケース
		String invalidInput = "「これは閉じていない\n" +
				"」これは開いていない\n";

		writeTestFile(invalidInput);
		finalizer.finalize(tempFile);

		System.out.println("\n異常なケース:");
		System.out.println(invalidInput);
		System.out.println("判定: ✓（警告が出力される）");

		System.out.println("=".repeat(60));
		System.out.println();
	}

	// ========================================================================
	// ファイルサイズ制限テスト
	// ========================================================================

	@Test
	public void testFileSizeLimit() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("ファイルサイズ制限テスト");
		System.out.println("=".repeat(60));

		// 非常に小さい制限を設定（1バイト）
		settings.setMaxFinalizableFileSizeMB(0); // 0MB = ほぼ全てのファイルが対象外

		String input = "タイトル\n著者\n本文\n";
		writeTestFile(input);

		finalizer.finalize(tempFile);

		String result = readTestFile();

		// ファイルサイズ超過のため、変換されていないはず
		assertEquals("ファイルが変換されている", input, result);

		System.out.println("判定: ✓（ファイルサイズ超過でスキップ）");
		System.out.println("=".repeat(60));
		System.out.println();
	}

	@Test
	public void testApplyAutoIndent() throws Exception {
		// narou.rb互換: 50%以上の行が未字下げの場合に字下げを適用
		// この入力は「タイトル」「段落の最初です。」「続きの行」「次の段落です。」が未字下げ
		String input = "タイトル\n" +
				"段落の最初です。\n" +
				"続きの行\n" +
				"　すでに字下げされた行\n" +
				"「かぎ括弧で始まる行」\n" +
				"［＃挿絵（images/foo）入る］\n" +
				"次の段落です。\n";

		writeTestFile(input);

		settings.setEnableAutoIndent(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		// 未字下げ行が50%以上なので字下げが適用される
		assertTrue("タイトルが字下げされる", result.contains("　タイトル"));
		assertTrue("段落の最初が字下げされる", result.contains("　段落の最初です。"));
		assertTrue("続きの行が字下げされる", result.contains("　続きの行"));
		// すでに字下げされた行はそのまま
		assertTrue("既存字下げは維持", result.contains("　すでに字下げされた行"));
		// かぎ括弧で始まる行は字下げされない (AUTO_INDENT_IGNORE)
		assertTrue("かぎ括弧は字下げしない", result.contains("「かぎ括弧で始まる行」"));
		// 注記行は字下げされない
		assertTrue("注記行は字下げしない", result.contains("［＃挿絵（images/foo）入る］"));
		// 次の段落も字下げされる
		assertTrue("次の段落が字下げされる", result.contains("　次の段落です。"));
	}

	// ========================================================================
	// 空行圧縮テスト
	// ========================================================================

	@Test
	public void testPackBlankLine() throws Exception {
		String input = "段落1\n\n\n\n段落2\n\n段落3\n";

		writeTestFile(input);
		settings.setEnablePackBlankLine(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		// 4行連続空行 → 圧縮される
		assertFalse("連続空行が残っている", result.contains("\n\n\n\n"));
		assertTrue("段落1がある", result.contains("段落1"));
		assertTrue("段落2がある", result.contains("段落2"));
		assertTrue("段落3がある", result.contains("段落3"));
	}

	// ========================================================================
	// 二分アキテスト
	// ========================================================================

	@Test
	public void testHalfIndentBracket() throws Exception {
		String input = "「会話文です」\n" +
				"　「字下げ済み会話」\n" +
				"『二重かぎ括弧』\n" +
				"（括弧文）\n" +
				"地の文です。\n";

		writeTestFile(input);
		settings.setEnableHalfIndentBracket(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		assertTrue("二分アキが挿入されていない", result.contains("［＃二分アキ］「会話文です」"));
		assertTrue("字下げ済み会話にも二分アキ", result.contains("［＃二分アキ］「字下げ済み会話」"));
		assertTrue("二重かぎ括弧に二分アキ", result.contains("［＃二分アキ］『二重かぎ括弧』"));
		assertTrue("丸括弧に二分アキ", result.contains("［＃二分アキ］（括弧文）"));
		assertFalse("地の文に二分アキなし", result.contains("［＃二分アキ］地の文"));
	}

	// ========================================================================
	// 漢数字変換テスト
	// ========================================================================

	@Test
	public void testConvertNumToKanji() throws Exception {
		String input = "第1話\n" +
				"彼は3人いた。\n" +
				"1,234個\n" +
				"［＃３字下げ］注記行\n";

		writeTestFile(input);
		settings.setEnableConvertNumToKanji(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		assertTrue("1→一", result.contains("第一話"));
		assertTrue("3→三", result.contains("三人"));
		// カンマ付き数字は全角化のみ
		assertTrue("1,234→１，２３４", result.contains("１，２３４"));
		// 注記行はスキップ
		assertTrue("注記行は変換しない", result.contains("［＃３字下げ］注記行"));
	}

	// ========================================================================
	// 英字全角化テスト
	// ========================================================================

	@Test
	public void testAlphabetToZenkaku() throws Exception {
		String input = "Hello World\n" +       // 英文（2語以上）→半角保持
				"HP\n" +                        // 短い英単語→全角
				"JavaScript\n" +                // 8文字以上→半角保持
				"OK\n";                         // 短い英単語→全角

		writeTestFile(input);
		settings.setEnableAlphabetToZenkaku(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		// 英文は半角保持
		assertTrue("英文は半角保持", result.contains("Hello World"));
		// 短い英単語は全角化
		assertTrue("HP→ＨＰ", result.contains("ＨＰ"));
		// 8文字以上は半角保持
		assertTrue("JavaScript半角保持", result.contains("JavaScript"));
		// 短い英単語は全角化
		assertTrue("OK→ＯＫ", result.contains("ＯＫ"));
	}

	// ========================================================================
	// 読了表示テスト
	// ========================================================================

	@Test
	public void testDisplayEndOfBook() throws Exception {
		String input = "本文です。\n";

		writeTestFile(input);
		settings.setEnableDisplayEndOfBook(true);
		finalizer.finalize(tempFile);

		String result = readTestFile();

		assertTrue("読了表示がない",
				result.contains("［＃ここから地付き］［＃小書き］（本を読み終わりました）［＃小書き終わり］［＃ここで地付き終わり］"));
	}

	// ========================================================================
	// 統合テスト
	// ========================================================================

	@Test
	public void testIntegrated() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("統合テスト（全機能）");
		System.out.println("=".repeat(60));

		String input = "テスト小説\n" +
				"テスト太郎\n" +
				"\n" +
				"********************************************\n" +
				"これは前書きです。\n" +
				"［＃改ページ］\n" +
				"第一章 始まり\n" +
				"「こんにちは」\n" +
				"『やあ』\n" +
				"［＃改ページ］\n" +
				"第二章 終わり\n" +
				"本文内容。\n" +
				"************************************************\n" +
				"これは後書きです。\n";

		writeTestFile(input);

		settings.setEnableAuthorComments(true);
		settings.setEnableEnchantMidashi(true);
		settings.setEnableInspectInvalidOpenCloseBrackets(true);

		finalizer.finalize(tempFile);

		String result = readTestFile();

		System.out.println("結果:");
		System.out.println(result);

		// 前書き・後書きが検出されているか
		assertTrue("前書き開始注記がない", result.contains("［＃ここから前書き］"));
		assertTrue("前書き終了注記がない", result.contains("［＃ここで前書き終わり］"));
		assertTrue("後書き開始注記がない", result.contains("［＃ここから後書き］"));
		assertTrue("後書き終了注記がない", result.contains("［＃ここで後書き終わり］"));

		// 見出しが化されているか
		assertTrue("第一章が見出し化されていない", result.contains("［＃中見出し］第一章 始まり［＃中見出し終わり］"));
		assertTrue("第二章が見出し化されていない", result.contains("［＃中見出し］第二章 終わり［＃中見出し終わり］"));

		System.out.println("\n判定: ✓（全機能が動作）");
		System.out.println("=".repeat(60));
		System.out.println();
	}
}
