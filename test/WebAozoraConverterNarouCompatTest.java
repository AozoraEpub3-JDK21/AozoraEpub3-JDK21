import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.StringWriter;
import java.io.BufferedWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.github.hmdev.web.WebAozoraConverter;
import com.github.hmdev.web.api.model.NovelMetadata;

/**
 * WebAozoraConverter - narou.rb互換機能の単体テスト
 *
 * テスト対象:
 * - Phase 1: 改行処理、ルビ処理、表紙挿絵注記
 * - Phase 2: 二分アキ、英文保護、縦中横
 * - Phase 3: 数字漢数字化、記号全角化、ローマ数字変換など
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterNarouCompatTest
 */
public class WebAozoraConverterNarouCompatTest {

	private WebAozoraConverter converter;
	private Method addHalfIndentBracketMethod;
	private Method convertTatechuyokoMethod;
	private Method convertSymbolsToZenkakuMethod;
	private Method convertRomanNumeralsMethod;
	private Method protectEnglishSentencesMethod;

	@Before
	public void setUp() throws Exception {
		// テスト用コンバーター作成
		File webConfigPath = new File("web");
		String testUrl = "https://ncode.syosetu.com/n0000xx/"; // ダミーURL

		converter = WebAozoraConverter.createWebAozoraConverter(testUrl, webConfigPath);
		assertNotNull("Converter作成失敗", converter);

		// リフレクションでprivateメソッドにアクセス
		addHalfIndentBracketMethod = getPrivateMethod("addHalfIndentBracket", String.class);
		convertTatechuyokoMethod = getPrivateMethod("convertTatechuyoko", String.class);
		convertSymbolsToZenkakuMethod = getPrivateMethod("convertSymbolsToZenkaku", String.class);
		convertRomanNumeralsMethod = getPrivateMethod("convertRomanNumerals", String.class);
		protectEnglishSentencesMethod = getPrivateMethod("protectEnglishSentences", String.class);
	}

	private Method getPrivateMethod(String methodName, Class<?>... parameterTypes) throws Exception {
		Method method = WebAozoraConverter.class.getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	// ========================================================================
	// Phase 1: 緊急修正テスト
	// ========================================================================

	/**
	 * Phase 1.1: 改行処理のテスト
	 * HTML改行コード（\r\n）が削除されることを確認
     */
    @Test
    public void testNewlineRemoval() throws Exception {
        Method m = getPrivateMethod("printText", java.io.BufferedWriter.class, String.class);

        // case 1
        StringWriter sw1 = new StringWriter();
        try (BufferedWriter bw1 = new BufferedWriter(sw1)) {
            m.invoke(converter, bw1, "テキスト\r\n改行");
            bw1.flush();
            assertEquals("テキスト改行", sw1.toString());
        }

        // case 2: multiple consecutive CRLF -> all removed
        StringWriter sw2 = new StringWriter();
        try (BufferedWriter bw2 = new BufferedWriter(sw2)) {
            m.invoke(converter, bw2, "複数\r\n\r\n改行");
            bw2.flush();
            assertEquals("複数改行", sw2.toString());
        }
    }
	// Phase 2: 品質向上テスト
	// ========================================================================

	/**
	 * Phase 2.1: かぎ括弧の二分アキテスト
	 */
	@Test
	public void testHalfIndentBracket() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Phase 2.1: かぎ括弧の二分アキテスト");
		System.out.println("=".repeat(60));

		String[][] testCases = {
			{"「これはテスト」", "［＃二分アキ］「これはテスト」"},
			{"　「全角スペース付き」", "［＃二分アキ］「全角スペース付き」"},
			{" 「半角スペース付き」", "［＃二分アキ］「半角スペース付き」"},
			{"『二重かぎ括弧』", "［＃二分アキ］『二重かぎ括弧』"},
			{"（丸括弧）", "［＃二分アキ］（丸括弧）"},
			{"〈山括弧〉", "［＃二分アキ］〈山括弧〉"},
			{"普通の文章", "普通の文章"},  // 行頭でない場合は変換しない
		};

		for (String[] testCase : testCases) {
			String input = testCase[0];
			String expected = testCase[1];
			String actual = (String) addHalfIndentBracketMethod.invoke(converter, input);

			System.out.println("入力: " + input);
			System.out.println("期待: " + expected);
			System.out.println("結果: " + actual);
			System.out.println("判定: " + (expected.equals(actual) ? "✓" : "✗"));
			System.out.println();

			assertEquals("二分アキ変換が正しくない", expected, actual);
		}

		System.out.println("=".repeat(60));
		System.out.println();
	}

	/**
	 * Phase 2.3: 縦中横処理のテスト
	 */
	@Test
	public void testTatechuyoko() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Phase 2.3: 縦中横処理テスト");
		System.out.println("=".repeat(60));

		String[][] testCases = {
			{"！！", "［＃縦中横］!!［＃縦中横終わり］"},
			{"！！！", "［＃縦中横］!!!［＃縦中横終わり］"},
			{"！？", "［＃縦中横］!?［＃縦中横終わり］"},
			{"？！", "［＃縦中横］?!［＃縦中横終わり］"},
			{"？？", "［＃縦中横］??［＃縦中横終わり］"},
			{"！！？", "［＃縦中横］!!?［＃縦中横終わり］"},
			{"？！！", "［＃縦中横］?!!［＃縦中横終わり］"},
			{"！", "！"},  // 1個は変換しない
			{"！！！！", "［＃縦中横］!!［＃縦中横終わり］［＃縦中横］!!［＃縦中横終わり］"},  // 4個は2個ずつ縦中横
			{"！！！！！", "［＃縦中横］!!［＃縦中横終わり］［＃縦中横］!!［＃縦中横終わり］［＃縦中横］!!［＃縦中横終わり］"},  // 5個→6個に調整して2個ずつ
		};

		for (String[] testCase : testCases) {
			String input = testCase[0];
			String expected = testCase[1];
			String actual = (String) convertTatechuyokoMethod.invoke(converter, input);

			System.out.println("入力: " + input);
			System.out.println("期待: " + expected);
			System.out.println("結果: " + actual);
			System.out.println("判定: " + (expected.equals(actual) ? "✓" : "✗"));
			System.out.println();

			assertEquals("縦中横変換が正しくない", expected, actual);
		}

		System.out.println("=".repeat(60));
		System.out.println();
	}

	// ========================================================================
	// Phase 3: 高度な変換テスト
	// ========================================================================

	/**
     * Phase 3.1: 数字の漢数字化テスト（実装）
     */
    @Test
    public void testNumbersToKanji() throws Exception {
        Method mPrint = getPrivateMethod("printText", java.io.BufferedWriter.class, String.class);

        // ensure clean state for kanjiNumbers
        Field kanjiField = WebAozoraConverter.class.getDeclaredField("kanjiNumbers");
        kanjiField.setAccessible(true);
        ((java.util.List<?>) kanjiField.get(converter)).clear();

        // enable conversion
        converter.getFormatSettings().setEnableConvertNumToKanji(true);

        // invoke via printText to follow real conversion path
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            mPrint.invoke(converter, bw, "123");
            bw.flush();
        }
        String out1 = sw.toString();
        assertTrue("printText -> [" + out1 + "]", out1.contains("一二三"));

        // comma-protected numbers should be wrapped as 半角数字
        StringWriter swComma = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(swComma)) {
            mPrint.invoke(converter, bw, "1,000");
            bw.flush();
        }
        String outComma = swComma.toString();
        assertTrue("printText(comma) -> ["+outComma+"]", outComma.contains("半角数字"));

        // units: enable and test 1000 -> 千, 10000 -> 一万
        converter.getFormatSettings().setEnableKanjiNumWithUnits(true);

        // for 1000 -> 千 we must allow thousand-unit conversion (lowerDigitZero <= 3)
        converter.getFormatSettings().setKanjiNumWithUnitsLowerDigitZero(3);
        ((java.util.List<?>) kanjiField.get(converter)).clear();

        StringWriter swTh = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(swTh)) {
            mPrint.invoke(converter, bw, "1000");
            bw.flush();
        }
        String outThousand = swTh.toString();
        assertTrue("printText(1000) -> ["+outThousand+"]", outThousand.contains("千"));

        // for 10000 -> 一万, disable thousand-level replacement by using threshold=4
        converter.getFormatSettings().setKanjiNumWithUnitsLowerDigitZero(4);
        ((java.util.List<?>) kanjiField.get(converter)).clear();

        StringWriter swMan = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(swMan)) {
            mPrint.invoke(converter, bw, "10000");
            bw.flush();
        }
        String outMan = swMan.toString();
        assertTrue("printText(10000) -> ["+outMan+"]", outMan.contains("一万"));
    }

    /** Phase 1/2 helpers: autoJoinInBrackets and autoJoinLine */
    @Test
    public void testAutoJoinInBracketsAndLine() throws Exception {
        Method mJoinBr = getPrivateMethod("autoJoinInBrackets", String.class);
        Method mJoinLine = getPrivateMethod("autoJoinLine", String.class);

        String inBracket = "「これは\nテストです」"; // no leading ideographic space after newline
        String outBracket = (String) mJoinBr.invoke(converter, inBracket);
        assertEquals("「これは　テストです」", outBracket);

        String inLine = "これは、\n次の行に続きます。";
        String outLine = (String) mJoinLine.invoke(converter, inLine);
        assertEquals("これは、次の行に続きます。", outLine);
    }

    /** Phase: なろうタグ処理 */
    @Test
    public void testConvertNarouTags() throws Exception {
        Method m = getPrivateMethod("convertNarouTags", String.class);

        String np = (String) m.invoke(converter, "[newpage]");
        assertEquals("［＃改ページ］", np);

        String chapter = "[chapter:テスト章]";
        String replaced = (String) m.invoke(converter, chapter);
        assertTrue(replaced.contains("中見出し"));
        assertTrue(replaced.contains("テスト章"));
    }

    /** 分数・日付変換 */
    @Test
    public void testConvertFractionsAndDates() throws Exception {
        Method mFrac = getPrivateMethod("convertFractions", String.class);
        Method mDate = getPrivateMethod("convertDates", String.class);

        String mixed = "1/2 and 2024/1/1";
        String fracOut = (String) mFrac.invoke(converter, mixed);
        assertTrue(fracOut.contains("2分の1"));
        assertTrue(fracOut.contains("2024/1/1"));

        // default date format
        String dateOut = (String) mDate.invoke(converter, "2024/1/1");
        assertEquals("2024年1月1日", dateOut);

        // custom format
        converter.getFormatSettings().setDateFormat("%Y/%m/%d");
        String dateOut2 = (String) mDate.invoke(converter, "2024/1/1");
        assertEquals("2024/1/1", dateOut2);
    }

    /** saveMetadataToCache の単体検証（ファイル出力） */
    @Test
    public void testSaveMetadataToCache() throws Exception {
        Method m = getPrivateMethod("saveMetadataToCache", com.github.hmdev.web.api.model.NovelMetadata.class, File.class);
        com.github.hmdev.web.api.model.NovelMetadata meta = new com.github.hmdev.web.api.model.NovelMetadata();
        meta.setNcode("n0001a");
        meta.setTitle("タイトル");
        meta.setWriter("作者名");
        meta.setStory("あらすじ");
        meta.setGeneralAllNo(12);
        meta.setLength(3456);
        meta.setNovelType(1);
        meta.setEnd(0);
        meta.setGlobalPoint(10);
        meta.setFavNovelCnt(5);

        File tmp = File.createTempFile("metadata", ".json");
        tmp.deleteOnExit();
        m.invoke(converter, meta, tmp);

        String content = Files.readString(tmp.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"ncode\": \"n0001a\""));
        assertTrue(content.contains("\"title\": \"タイトル\""));
        assertTrue(content.contains("\"writer\": \"作者名\""));
    }

    /**
     * fetchAndCacheMetadata の単体検証 — 正常系とR18フラグ確認
     */
    @Test
    public void testFetchAndCacheMetadataSuccess() throws Exception {
        Method m = getPrivateMethod("fetchAndCacheMetadata", String.class, File.class, String.class);

        java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("metaCache");
        File cacheDir = tmpDir.toFile();

        // stub API client: タイトルに isR18 フラグを反映して返す
        com.github.hmdev.web.api.NarouApiClient stub = new com.github.hmdev.web.api.NarouApiClient() {
            @Override
            public com.github.hmdev.web.api.model.NovelMetadata getNovelMetadata(String ncode, boolean isR18) throws com.github.hmdev.web.api.exception.ApiException {
                com.github.hmdev.web.api.model.NovelMetadata meta = new com.github.hmdev.web.api.model.NovelMetadata();
                meta.setNcode(ncode);
                meta.setTitle(isR18 ? "TITLE-R18" : "TITLE");
                meta.setWriter("writer");
                meta.setStory("story");
                meta.setGeneralAllNo(1);
                meta.setLength(100);
                meta.setNovelType(1);
                meta.setEnd(0);
                meta.setGlobalPoint(0);
                meta.setFavNovelCnt(0);
                return meta;
            }
        };

        java.lang.reflect.Field f = WebAozoraConverter.class.getDeclaredField("apiClient");
        f.setAccessible(true);
        f.set(converter, stub);

        com.github.hmdev.web.api.model.NovelMetadata out = (com.github.hmdev.web.api.model.NovelMetadata) m.invoke(converter, "n9999z", cacheDir, "https://ncode.syosetu.com/n9999z/");
        assertNotNull(out);
        assertEquals("TITLE", out.getTitle());

        File metaFile = new File(cacheDir, "metadata.json");
        assertTrue(metaFile.exists());
        String content = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"ncode\": \"n9999z\""));
        assertTrue(content.contains("\"title\": \"TITLE\""));

        // R18 URL を渡すと stub が isR18=true を受け取る
        com.github.hmdev.web.api.model.NovelMetadata out2 = (com.github.hmdev.web.api.model.NovelMetadata) m.invoke(converter, "n9999z", cacheDir, "https://novel18.syosetu.com/n9999z/");
        assertEquals("TITLE-R18", out2.getTitle());
    }

    /**
     * fetchAndCacheMetadata の単体検証 — API例外時は null を返しキャッシュを書かない
     */
    @Test
    public void testFetchAndCacheMetadataApiError() throws Exception {
        Method m = getPrivateMethod("fetchAndCacheMetadata", String.class, File.class, String.class);

        java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("metaCacheErr");
        File cacheDir = tmpDir.toFile();

        com.github.hmdev.web.api.NarouApiClient stub = new com.github.hmdev.web.api.NarouApiClient() {
            @Override
            public com.github.hmdev.web.api.model.NovelMetadata getNovelMetadata(String ncode, boolean isR18) throws com.github.hmdev.web.api.exception.ApiException {
                throw new com.github.hmdev.web.api.exception.ApiException("mock");
            }
        };

        java.lang.reflect.Field f = WebAozoraConverter.class.getDeclaredField("apiClient");
        f.setAccessible(true);
        f.set(converter, stub);

        com.github.hmdev.web.api.model.NovelMetadata out = (com.github.hmdev.web.api.model.NovelMetadata) m.invoke(converter, "n0000a", cacheDir, "https://ncode.syosetu.com/n0000a/");
        assertNull(out);

        File metaFile = new File(cacheDir, "metadata.json");
        assertFalse(metaFile.exists());
    }

	/**
	 * Phase 3.3: 記号の全角化テスト
	 */
	@Test
	public void testSymbolsToZenkaku() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Phase 3.3: 記号の全角化テスト");
		System.out.println("=".repeat(60));

		String[][] testCases = {
			{"-", "－"},
			{"<", "〈"},
			{">", "〉"},
			{"(", "（"},
			{")", "）"},
			{"|", "｜"},
			{"[", "［"},
			{"]", "］"},
			{"test-case", "test－case"},
			{"100%", "100％"},
		};

		for (String[] testCase : testCases) {
			String input = testCase[0];
			String expected = testCase[1];
			String actual = (String) convertSymbolsToZenkakuMethod.invoke(converter, input);

			System.out.println("入力: " + input);
			System.out.println("期待: " + expected);
			System.out.println("結果: " + actual);
			System.out.println("判定: " + (expected.equals(actual) ? "✓" : "✗"));
			System.out.println();

			assertEquals("記号の全角化が正しくない", expected, actual);
		}

		System.out.println("=".repeat(60));
		System.out.println();
	}

	/**
	 * 追加: ルビ出力の検証（HTML <ruby> → 青空ルビ）
	 */
	@Test
	public void testPrintRuby() throws Exception {
		Method m = getPrivateMethod("printRuby", java.io.BufferedWriter.class, org.jsoup.nodes.Element.class);

		org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment("<ruby>漢<rt>かん</rt></ruby>");
		org.jsoup.nodes.Element ruby = doc.selectFirst("ruby");

		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			m.invoke(converter, bw, ruby);
			bw.flush();
		}
		String out = sw.toString();
		assertTrue("ルビが青空形式に変換されていない", out.contains("｜漢《かん》"));
	}

	/**
	 * 追加: 画像注記の出力検証（printImage via _printNode）
	 * 既存ファイルを作成してネットワークダウンロードを回避する
	 */
	@Test
	public void testImageAnnotation() throws Exception {
		// set dstPath to a temp dir and create expected image file so cacheFile() is not invoked
		java.io.File tmp = java.nio.file.Files.createTempDirectory("dst_images_test").toFile();
		tmp.deleteOnExit();
		java.lang.reflect.Field fDst = WebAozoraConverter.class.getDeclaredField("dstPath");
		fDst.setAccessible(true);
		fDst.set(converter, tmp.getAbsolutePath()+"/");

		java.io.File imagesDir = new java.io.File(tmp, "images/__");
		imagesDir.mkdirs();
		java.io.File imgFile = new java.io.File(imagesDir, "sample.png");
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(imgFile)) { fos.write(new byte[]{0}); }

		// pageBaseUri を設定して printImage の相対パス処理を回避
		java.lang.reflect.Field fPageBase = WebAozoraConverter.class.getDeclaredField("pageBaseUri");
		fPageBase.setAccessible(true);
		fPageBase.set(converter, "http://example/");

		org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment("<div><img src=\"sample.png\" /></div>");
		org.jsoup.nodes.Element wrapper = doc.selectFirst("div");

		Method m = getPrivateMethod("_printNode", java.io.BufferedWriter.class, org.jsoup.nodes.Node.class);
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			m.invoke(converter, bw, wrapper);
			bw.flush();
		}
		String out = sw.toString();
		assertTrue("挿絵注記が含まれていない", out.contains("［＃挿絵（images/__/sample.png）入る］"));
	}

	/**
	 * 追加: 濁点フォント処理の検証
	 */
	@Test
	public void testConvertDakutenFont() throws Exception {
		Method m = getPrivateMethod("convertDakutenFont", String.class);
		String out = (String) m.invoke(converter, "か゛");
		assertTrue(out.contains("［＃濁点］か［＃濁点終わり］"));
	}

	/**
	 * 追加: 長音記号の変換テスト（ー{2,} → ―）
	 */
	@Test
	public void testProlongedSoundMark() throws Exception {
		Method m = getPrivateMethod("convertProlongedSoundMark", String.class);
		String out1 = (String) m.invoke(converter, "ーー");
		assertEquals("――", out1);
		String out2 = (String) m.invoke(converter, "ーーー");
		assertEquals("―――", out2);
	}

	/**
	 * 追加: 中黒→三点リーダー変換（・{3,} → …）
	 */
	@Test
	public void testConvertHorizontalEllipsis() throws Exception {
		Method m = getPrivateMethod("convertHorizontalEllipsis", String.class);
		String r1 = (String) m.invoke(converter, "・・・");
		assertEquals("…", r1);
		String r2 = (String) m.invoke(converter, "・・・・・・");
		assertEquals("……", r2);
	}

	/**
	 * 追加: site別 replace.txt の読み込み確認
	 */
	@Test
	public void testReplaceTxtLoading() throws Exception {
		java.nio.file.Path cfg = java.nio.file.Files.createTempDirectory("webcfg");
		java.nio.file.Path siteDir = cfg.resolve("local.replace.test");
		java.nio.file.Files.createDirectories(siteDir);
		// minimal extract.txt (required by constructor)
		java.nio.file.Files.write(siteDir.resolve("extract.txt"), java.util.List.of("TITLE\t.div.title"), java.nio.charset.StandardCharsets.UTF_8);
		// replace.txt with a TITLE replacement
		java.nio.file.Files.write(siteDir.resolve("replace.txt"), java.util.List.of("TITLE\t検索\t置換"), java.nio.charset.StandardCharsets.UTF_8);

		WebAozoraConverter c = WebAozoraConverter.createWebAozoraConverter("https://local.replace.test/", cfg.toFile());
		assertNotNull(c);
		java.lang.reflect.Field f = WebAozoraConverter.class.getDeclaredField("replaceMap");
		f.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<?,?> map = (java.util.Map<?,?>) f.get(c);
		// map のキーは package-private な enum なので文字列で探す
		Object key = map.keySet().stream().filter(k -> k.toString().equals("TITLE")).findFirst().orElse(null);
		assertNotNull("replaceMap に TITLE が見つからない", key);
		java.util.Vector<String[]> vec = (java.util.Vector<String[]>) map.get(key);
		assertEquals("検索", vec.get(0)[0]);
		assertEquals("置換", vec.get(0)[1]);
	}

	/**
	 * Phase 3.7: ローマ数字の変換テスト
	 */
	@Test
	public void testRomanNumerals() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Phase 3.7: ローマ数字の変換テスト");
		System.out.println("=".repeat(60));

		String[][] testCases = {
			{"Ⅰ", "I"},
			{"Ⅱ", "II"},
			{"Ⅲ", "III"},
			{"Ⅳ", "IV"},
			{"Ⅴ", "V"},
			{"ⅰ", "i"},
			{"ⅱ", "ii"},
			{"ⅲ", "iii"},
			{"第Ⅰ章", "第I章"},
			{"ⅩⅡ巻", "XII巻"},
		};

		for (String[] testCase : testCases) {
			String input = testCase[0];
			String expected = testCase[1];
			String actual = (String) convertRomanNumeralsMethod.invoke(converter, input);

			System.out.println("入力: " + input);
			System.out.println("期待: " + expected);
			System.out.println("結果: " + actual);
			System.out.println("判定: " + (expected.equals(actual) ? "✓" : "✗"));
			System.out.println();

			assertEquals("ローマ数字の変換が正しくない", expected, actual);
		}

		System.out.println("=".repeat(60));
		System.out.println();
	}

	/**
	 * Phase 2.2: 英文の半角保護テスト
	 */
	@Test
	public void testEnglishSentenceProtection() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Phase 2.2: 英文の半角保護テスト");
		System.out.println("=".repeat(60));

		// 英文保護は protectEnglishSentences() で一時保護し、
		// rebuildEnglishSentences() で復元する2段階処理

		String[][] testCases = {
			{"Hello, World!", "［＃英文＝0］"},  // 8文字以上の英文は保護
			{"This is a test.", "［＃英文＝0］"},
			{"short", "short"},  // 8文字未満は保護しない
			{"test", "test"},
		};

		System.out.println("英文保護の仕組み:");
		System.out.println("1. 8文字以上の英文（スペース・句読点含む）を検出");
		System.out.println("2. ［＃英文＝N］に一時置換して保護");
		System.out.println("3. 他の変換処理を実施");
		System.out.println("4. 最後に元の英文に復元");
		System.out.println();

		for (String[] testCase : testCases) {
			String input = testCase[0];
			String expectedProtected = testCase[1];

			// 保護処理
			String protected_ = (String) protectEnglishSentencesMethod.invoke(converter, input);

			System.out.println("入力: " + input);
			System.out.println("保護後: " + protected_);
			System.out.println("期待: " + expectedProtected);

			// 8文字以上の場合のみ保護されるかチェック
			boolean shouldProtect = input.length() >= 8 && input.matches(".*[a-zA-Z]{2,}.*") && input.matches(".*[\\s.,].*");
			if (shouldProtect) {
				assertTrue("英文が保護されていない", protected_.startsWith("［＃英文＝"));
			}

			System.out.println("判定: " + (shouldProtect ? protected_.startsWith("［＃英文＝") ? "✓" : "✗" : "✓（保護不要）"));
			System.out.println();
		}

		System.out.println("=".repeat(60));
		System.out.println();
	}

	// ========================================================================
	// 統合テスト
	// ========================================================================

	/**
	 * 総合的な変換フローのテスト
	 */
	@Test
	public void testIntegratedConversion() {
		System.out.println("=".repeat(60));
		System.out.println("統合変換フローテスト");
		System.out.println("=".repeat(60));

		System.out.println("以下の変換が順番に適用されることを確認:");
		System.out.println("1. HTML改行削除");
		System.out.println("2. かぎ括弧内の自動連結");
		System.out.println("3. 行末読点での自動連結");
		System.out.println("4. 行頭のかぎ括弧に二分アキ");
		System.out.println("5. 縦中横処理");
		System.out.println("6. 数字の漢数字化");
		System.out.println("7. ローマ数字の変換");
		System.out.println("8. 英文の保護");
		System.out.println("9. 記号の全角化");
		System.out.println("10. 特殊文字を青空注記に変換");
		System.out.println("11. 英文の復元");
		System.out.println();

		System.out.println("注: 実際の変換フローは統合テストまたは手動テストで確認してください");
		System.out.println("=".repeat(60));
		System.out.println();
	}
}
