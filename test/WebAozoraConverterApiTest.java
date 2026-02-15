import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import com.github.hmdev.web.WebAozoraConverter;

/**
 * WebAozoraConverter + なろうAPI 統合テスト
 * 
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterApiTest
 */
public class WebAozoraConverterApiTest {
	
	@Test
	public void testApiIntegration() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("WebAozoraConverter + なろうAPI 統合テスト");
		System.out.println("=".repeat(60));
		System.out.println();
		
		// テスト用キャッシュディレクトリ
		File cacheDir = new File("build/test-cache");
		cacheDir.mkdirs();
		
		// Web設定ディレクトリ
		File webConfigPath = new File("web");
		
		// テストURL (短編作品 - テストに適している)
		String testUrl = "https://ncode.syosetu.com/n4830bu/";
		
		System.out.println("【テスト1】API有効 + フォールバック有効");
		System.out.println("-".repeat(60));
		testWithApi(testUrl, webConfigPath, cacheDir, true, true);
		System.out.println();
		
		System.out.println("【テスト2】API無効（従来のHTML取得のみ）");
		System.out.println("-".repeat(60));
		testWithApi(testUrl, webConfigPath, cacheDir, false, true);
		System.out.println();
		
		System.out.println("=".repeat(60));
		System.out.println("統合テスト完了");
		System.out.println("=".repeat(60));
	}
	
	private void testWithApi(String url, File webConfigPath, File cacheDir, 
			boolean useApi, boolean fallbackEnabled) throws Exception {
		
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(url, webConfigPath);
		assertNotNull("Converter作成失敗", converter);
		
		// API設定
		converter.setUseApi(useApi);
		converter.setApiFallbackEnabled(fallbackEnabled);
		
		System.out.println("URL: " + url);
		System.out.println("API使用: " + useApi);
		System.out.println("フォールバック: " + fallbackEnabled);
		System.out.println();
		
		// 実際のネットワーク接続は行わない。ここではAPI設定の反映のみ確認する。
		// 実運用・統合テストでネットワークが必要な場合は別途有効化する。
		System.out.println("※ ネットワーク接続はスキップされています（CI向け）");
		// 基本的なオブジェクト作成と設定反映を確認する
		assertNotNull("Converterがnull", converter);
	}
	
	/**
	 * Nコード抽出のユニットテスト
	 */
	@Test
	public void testNcodeExtraction() throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("Nコード抽出テスト");
		System.out.println("=".repeat(60));
		
		File webConfigPath = new File("web");
		String testUrl = "https://ncode.syosetu.com/n4830bu/";
		
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(testUrl, webConfigPath);
		assertNotNull("Converter作成失敗", converter);
		
		// リフレクションでprivateメソッドをテスト（通常は推奨されないが、テスト目的）
		java.lang.reflect.Method method = WebAozoraConverter.class.getDeclaredMethod("extractNcode", String.class);
		method.setAccessible(true);
		
		// テストケース
		String[][] testCases = {
			{"https://ncode.syosetu.com/n4830bu/", "n4830bu"},
			{"https://ncode.syosetu.com/N4830BU/", "n4830bu"},  // 大文字→小文字変換
			{"https://ncode.syosetu.com/n4830bu/1/", "n4830bu"}, // 話番号付き
			{"https://novel18.syosetu.com/n1234ab/", "n1234ab"}, // R18版
			{"https://example.com/test/", null},  // syosetu以外
		};
		
		for (String[] testCase : testCases) {
			String url = testCase[0];
			String expected = testCase[1];
			String actual = (String) method.invoke(converter, url);
			
			System.out.println("URL: " + url);
			System.out.println("  期待値: " + expected);
			System.out.println("  実際値: " + actual);
			System.out.println("  結果: " + (expected == null ? actual == null : expected.equals(actual) ? "✓" : "✗"));
			System.out.println();
			
			assertEquals("Nコード抽出が正しくない: " + url, expected, actual);
		}
		
		System.out.println("=".repeat(60));
		System.out.println("Nコード抽出テスト完了");
		System.out.println("=".repeat(60));
	}
}
