import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * GUI のログ欄に貼り付けた文字列を、URL とファイルのパスの候補に分けるテスト（splitPastedText）。
 *
 * 修正前は文字列を改行と空白で区切り、パスは末尾が .txt のものしか受け付けなかったので、
 * .zip などのパスや、空白を含むパスを貼っても何も言わずに捨てていた（internal #7）。
 */
public class AozoraEpub3AppletPasteTest {

	@Test
	public void pathWithSpacesStaysOneItem() {
		assertEquals(Arrays.asList("/Users/a/My Books/走れメロス.zip"),
			AozoraEpub3Applet.splitPastedText("/Users/a/My Books/走れメロス.zip"));
	}

	@Test
	public void windowsCopyAsPathQuotesAreRemoved() {
		// エクスプローラーの「パスのコピー」は引用符で囲む。複数選ぶと CRLF で並ぶ
		assertEquals(Arrays.asList("C:\\Users\\a\\My Books\\a.zip", "C:\\b.txt"),
			AozoraEpub3Applet.splitPastedText("\"C:\\Users\\a\\My Books\\a.zip\"\r\n\"C:\\b.txt\"\r\n"));
	}

	@Test
	public void urlsOnOneLineAreSplitOnSpaces() {
		assertEquals(Arrays.asList("https://ncode.syosetu.com/n1/", "https://kakuyomu.jp/works/2"),
			AozoraEpub3Applet.splitPastedText("  https://ncode.syosetu.com/n1/ https://kakuyomu.jp/works/2 \n"));
	}

	@Test
	public void mixedLinesKeepTheirOrder() {
		assertEquals(Arrays.asList("https://example.com/a", "/tmp/x y.txt"),
			AozoraEpub3Applet.splitPastedText("https://example.com/a\n\n/tmp/x y.txt"));
	}

	@Test
	public void blankTextGivesNothing() {
		assertEquals(Collections.emptyList(), AozoraEpub3Applet.splitPastedText(" \n\r\n\"\"\n"));
	}
}
