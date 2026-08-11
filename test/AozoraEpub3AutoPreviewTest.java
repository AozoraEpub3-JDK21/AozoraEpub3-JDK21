import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

/**
 * AozoraEpub3 - ini の AutoPreview で自分自身を -preview 付きで再起動する
 * コマンド組み立てのテスト (docs/epub-preview-plan.md の起票セクション)。
 *
 * プロセスの実起動はテストしない (Gradle テスト JVM では CLI e2e が書けないため、
 * コマンド列の構造だけを固定する)。
 *
 * 実行方法:
 *   gradlew test --tests AozoraEpub3AutoPreviewTest
 */
public class AozoraEpub3AutoPreviewTest {

	@Test
	public void testCommandStructure() {
		File epub = new File("build/test_autopreview.epub");
		List<String> command = AozoraEpub3.buildAutoPreviewCommand(epub);

		assertEquals("コマンド長", 6, command.size());
		//java 実行ファイルは実行中の JVM (java.home) から取る
		assertTrue("java バイナリのパスが java.home 配下であること",
			command.get(0).startsWith(System.getProperty("java.home")));
		//開発時のクラスディレクトリ実行でも動くよう -cp + main クラスで起動する
		assertEquals("-cp", command.get(1));
		assertEquals("クラスパスは現プロセスのものをそのまま渡す",
			System.getProperty("java.class.path"), command.get(2));
		assertEquals("main クラス", "AozoraEpub3", command.get(3));
		//子プロセスは既存の -preview 経路 (変換なしプレビュー) に入る
		assertEquals("-preview", command.get(4));
		assertEquals("EPUB は絶対パスで渡す", epub.getAbsolutePath(), command.get(5));
	}

	@Test
	public void testCommandUsesAbsolutePathForRelativeInput() {
		File relative = new File("out.epub");
		List<String> command = AozoraEpub3.buildAutoPreviewCommand(relative);
		//子プロセスの作業ディレクトリ解釈に依存しないこと
		assertTrue("相対パス入力でも絶対パスに解決されること",
			new File(command.get(command.size() - 1)).isAbsolute());
	}
}
