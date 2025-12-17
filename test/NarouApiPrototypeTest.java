import org.junit.Test;
import com.github.hmdev.web.api.NarouApiClient;
import com.github.hmdev.web.api.exception.ApiException;
import com.github.hmdev.web.api.model.NovelMetadata;

/**
 * なろうAPIクライアント プロトタイプテスト
 * 
 * 実行方法:
 *   gradlew test --tests NarouApiPrototypeTest
 */
public class NarouApiPrototypeTest {
	
	@Test
	public void testPrototype() {
		main(null);
	}
	
	public static void main(String[] args) {
		NarouApiPrototypeTest test = new NarouApiPrototypeTest();
		
		System.out.println("=".repeat(60));
		System.out.println("なろうAPIクライアント プロトタイプテスト");
		System.out.println("=".repeat(60));
		System.out.println();
		
		// テスト1: 有名作品のメタデータ取得
		test.testGetMetadata("n9669bk");  // 無職転生
		System.out.println();
		
		// テスト2: 別の作品
		test.testGetMetadata("n2267be");  // 転生したらスライムだった件
		System.out.println();
		
		// テスト3: 短編作品
		test.testGetMetadata("n4830bu");  // 短編作品例
		System.out.println();
		
		// テスト4: 存在しないNコード
		test.testGetMetadata("n0000zz");
		System.out.println();
		
		System.out.println("=".repeat(60));
		System.out.println("テスト完了");
		System.out.println("=".repeat(60));
	}
	
	/**
	 * メタデータ取得テスト
	 */
	public void testGetMetadata(String ncode) {
		System.out.println("【テスト】Nコード: " + ncode);
		System.out.println("-".repeat(60));
		
		try {
			NarouApiClient client = new NarouApiClient();
			NovelMetadata metadata = client.getNovelMetadata(ncode);
			
			// 取得成功 - 詳細表示
			System.out.println("✓ 取得成功!");
			System.out.println();
			System.out.println("  Nコード     : " + metadata.getNcode());
			System.out.println("  タイトル    : " + metadata.getTitle());
			System.out.println("  作者        : " + metadata.getWriter());
			System.out.println("  作者ID      : " + metadata.getUserid());
			System.out.println("  作品タイプ  : " + 
				(metadata.getNovelType() == 1 ? "連載" : "短編"));
			System.out.println("  状態        : " + 
				(metadata.getEnd() == 0 ? "完結" : "連載中"));
			System.out.println("  全話数      : " + metadata.getGeneralAllNo() + "話");
			System.out.println("  文字数      : " + 
				String.format("%,d", metadata.getLength()) + "文字");
			System.out.println("  読了時間    : " + metadata.getTime() + "分");
			System.out.println("  ブックマーク: " + 
				String.format("%,d", metadata.getFavNovelCnt()));
			System.out.println("  評価ポイント: " + 
				String.format("%,d", metadata.getGlobalPoint()));
			
			if (metadata.getGeneralFirstup() != null) {
				System.out.println("  初回掲載日  : " + metadata.getGeneralFirstup());
			}
			if (metadata.getGeneralLastup() != null) {
				System.out.println("  最終掲載日  : " + metadata.getGeneralLastup());
			}
			
			// あらすじ表示 (最初の100文字)
			if (metadata.getStory() != null && !metadata.getStory().isEmpty()) {
				String story = metadata.getStory();
				if (story.length() > 100) {
					story = story.substring(0, 100) + "...";
				}
				System.out.println();
				System.out.println("  あらすじ:");
				System.out.println("    " + story.replace("\n", "\n    "));
			}
			
			// キーワード表示
			if (metadata.getKeyword() != null && !metadata.getKeyword().isEmpty()) {
				System.out.println();
				System.out.println("  キーワード  : " + metadata.getKeyword());
			}
			
			System.out.println();
			System.out.println("  " + client.getStatistics());
			
		} catch (ApiException e) {
			System.out.println("✗ エラー: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("  原因: " + e.getCause().getMessage());
			}
		}
	}
}
