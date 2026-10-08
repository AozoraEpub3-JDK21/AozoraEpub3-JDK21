import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * GUI のログ欄に貼り付けた文字列から、URL とファイルを拾うテスト（collectPasted）。
 *
 * 修正前は文字列を改行と空白で区切り、パスは末尾が .txt のものしか受け付けなかったので、
 * .zip などのパスや、空白を含むパスを貼っても何も言わずに捨てていた（internal #7）。
 */
public class AozoraEpub3AppletPasteTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File spaced;
	private File plain;
	private File folder;
	private final List<File> files = new ArrayList<File>();
	private final List<String> urls = new ArrayList<String>();

	@Before
	public void setUp() throws Exception {
		File dir = tempFolder.newFolder("My Books");
		spaced = new File(dir, "走れメロス.zip");
		spaced.createNewFile();
		plain = tempFolder.newFile("b.txt");
		folder = tempFolder.newFolder("books");
	}

	private void collect(String text) {
		AozoraEpub3Applet.collectPasted(text, files, urls);
	}

	@Test
	public void pathWithSpacesIsOneFile() {
		collect(spaced.getAbsolutePath());
		assertEquals(Arrays.asList(spaced), files);
		assertEquals(Collections.emptyList(), urls);
	}

	@Test
	public void windowsCopyAsPathQuotesAndCrlf() {
		// エクスプローラーの「パスのコピー」は引用符で囲む。複数選ぶと CRLF で並ぶ
		collect("\"" + spaced.getAbsolutePath() + "\"\r\n\"" + plain.getAbsolutePath() + "\"\r\n");
		assertEquals(Arrays.asList(spaced, plain), files);
	}

	@Test
	public void terminalBackslashSpaceAndSingleQuotes() {
		collect(spaced.getAbsolutePath().replace(" ", "\\ "));
		collect("'" + plain.getAbsolutePath() + "'");
		assertEquals(Arrays.asList(spaced, plain), files);
	}

	@Test
	public void severalPathsOnOneLine() {
		collect(plain.getAbsolutePath() + " " + folder.getAbsolutePath());
		assertEquals(Arrays.asList(plain, folder), files);
	}

	@Test
	public void urlsAnywhereInTheLine() {
		// 共有ボタンの文字列のように、題の後に URL が続く
		collect("走れメロス｜小説家になろう https://ncode.syosetu.com/n1/\n　https://kakuyomu.jp/works/2　https://example.com/3 ");
		assertEquals(Arrays.asList("https://ncode.syosetu.com/n1/", "https://kakuyomu.jp/works/2", "https://example.com/3"), urls);
		assertEquals(Collections.emptyList(), files);
	}

	@Test
	public void relativeAndMissingPathsAreNotTaken() {
		// 作業フォルダの何かにたまたま当たって変換が始まらないよう、相対パスは読まない
		collect("build.gradle\n" + new File(tempFolder.getRoot(), "no such.zip").getAbsolutePath() + "\nこれはパスではない文字列");
		assertEquals(Collections.emptyList(), files);
		assertEquals(Collections.emptyList(), urls);
	}

	@Test
	public void blankTextGivesNothing() {
		collect(" \n\r\n\"\"\n　\r");
		assertEquals(Collections.emptyList(), files);
		assertEquals(Collections.emptyList(), urls);
	}
}
