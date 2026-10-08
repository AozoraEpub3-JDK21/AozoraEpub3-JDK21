import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
 * GUI のログ欄に貼り付けた文字列から、URL とファイルを拾うテスト（collectPasted ほか）。
 *
 * 修正前は文字列を改行と空白で区切り、パスは末尾が .txt のものしか受け付けなかったので、
 * .zip などのパスや、空白を含むパスを貼っても何も言わずに捨てていた（internal #7）。
 * 一方で、語ごとにフォルダを拾うと「作品 / 作者」の / でディスク全体を変換しにかかるので、
 * 語で拾うのはファイルだけにする。
 */
public class AozoraEpub3AppletPasteTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File spaced;
	private File parens;
	private File plain;
	private File folder;
	private final List<File> files = new ArrayList<File>();
	private final List<String> urls = new ArrayList<String>();

	@Before
	public void setUp() throws Exception {
		File dir = tempFolder.newFolder("My Books");
		spaced = new File(dir, "走れメロス.zip");
		spaced.createNewFile();
		parens = new File(dir, "作品 (1).zip");
		parens.createNewFile();
		plain = tempFolder.newFile("b.txt");
		folder = tempFolder.newFolder("books");
	}

	private void collect(String text) {
		AozoraEpub3Applet.collectPasted(text, files, urls);
	}

	/** ターミナルの書き方（空白と記号の前にバックスラッシュ） */
	private static String escaped(File file) {
		return file.getAbsolutePath().replaceAll("([ ()])", "\\\\$1");
	}

	@Test
	public void pathWithSpacesIsOneFile() {
		collect(spaced.getAbsolutePath());
		assertEquals(Arrays.asList(spaced), files);
		assertEquals(Collections.emptyList(), urls);
	}

	@Test
	public void quotedPathsAndCrlf() {
		// エクスプローラーの「パスのコピー」は引用符で囲む。複数選ぶと CRLF で並ぶ
		collect("\"" + spaced.getAbsolutePath() + "\"\r\n\"" + plain.getAbsolutePath() + "\"\r\n");
		assertEquals(Arrays.asList(spaced, plain), files);
	}

	@Test
	public void terminalEscapesAndSingleQuotes() {
		Assume_notWindows();
		collect(escaped(spaced));
		collect(escaped(parens));
		collect("'" + plain.getAbsolutePath() + "'");
		assertEquals(Arrays.asList(spaced, parens, plain), files);
	}

	@Test
	public void severalPathsOnOneLineEvenWithSpaces() {
		Assume_notWindows();
		// 2 つのファイルをターミナルに落としてコピー／引用符つきで並べる
		collect(escaped(spaced) + " " + plain.getAbsolutePath());
		collect("\"" + parens.getAbsolutePath() + "\" " + plain.getAbsolutePath());
		// 引用符つきのパスが 1 行に 2 つ（行の頭と終わりの引用符を 1 組と取り違えない）
		collect("\"" + spaced.getAbsolutePath() + "\" \"" + parens.getAbsolutePath() + "\"");
		assertEquals(Arrays.asList(spaced, plain, parens, plain, spaced, parens), files);
	}

	@Test
	public void urlsAnywhereInTheLine() {
		// 共有ボタンの文字列のように、題の後に URL が続く
		collect("走れメロス｜小説家になろう https://ncode.syosetu.com/n1/\n　https://kakuyomu.jp/works/2　https://example.com/3 ");
		// 語の途中のアポストロフィで引用を始めない
		collect("Here's the link https://example.com/4");
		assertEquals(Arrays.asList("https://ncode.syosetu.com/n1/", "https://kakuyomu.jp/works/2", "https://example.com/3", "https://example.com/4"), urls);
		assertEquals(Collections.emptyList(), files);
	}

	@Test
	public void foldersOnlyAsAWholeLine() {
		// 行全体がフォルダなら受け付ける（フォルダのパスのコピー）
		collect(folder.getAbsolutePath());
		assertEquals(Arrays.asList(folder), files);
		files.clear();
		// 語の中のフォルダは拾わない（「作品 / 作者」の /、ログ欄に出たフォルダのパス）
		collect("走れメロス / 太宰治 https://ncode.syosetu.com/n1/");
		collect("キャッシュパスを作成します : " + folder.getAbsolutePath());
		assertEquals(Collections.emptyList(), files);
	}

	@Test
	public void rootFolderIsNeverTaken() {
		// ディスク全体の変換になる
		for (File root : File.listRoots()) collect(root.getAbsolutePath());
		collect("/");
		// .. でルートに行き着くフォルダ
		StringBuilder dots = new StringBuilder(tempFolder.getRoot().getAbsolutePath());
		for (File f = tempFolder.getRoot(); f != null; f = f.getParentFile()) dots.append(File.separator).append("..");
		collect(dots.toString());
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

	@Test
	public void finderCopyTextIsJustFileNames() {
		List<File> list = Arrays.asList(spaced, plain);
		assertTrue(AozoraEpub3Applet.textIsFileNames("走れメロス.zip\nb.txt", list));
		// ブラウザからのドラッグの文字列（代替テキストなど）はファイル名ではない
		assertFalse(AozoraEpub3Applet.textIsFileNames("きれいな画像", list));
		assertFalse(AozoraEpub3Applet.textIsFileNames("  \n", list));
	}

	/** ファイルの一覧（Finder のコピー・ドロップ）の経路でも、ルートへのリンクはフォルダとして受け付けない */
	@Test
	public void aLinkToTheRootIsNotAnAcceptableFolder() throws Exception {
		File link = new File(tempFolder.getRoot(), "root-link");
		try {
			java.nio.file.Files.createSymbolicLink(link.toPath(), File.listRoots()[0].toPath());
		} catch (java.io.IOException | UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
		}
		assertFalse(AozoraEpub3Applet.isAcceptableFolder(link));
		assertTrue(AozoraEpub3Applet.isAcceptableFolder(folder));
	}

	/** 名前が .url で終わるフォルダは、ショートカットではなくフォルダ */
	@Test
	public void aFolderNamedDotUrlIsNotAShortcut() throws Exception {
		File dotUrlFolder = tempFolder.newFolder("books.url");
		File shortcut = tempFolder.newFile("site.url");
		assertFalse(AozoraEpub3Applet.isInternetShortcut(dotUrlFolder));
		assertTrue(AozoraEpub3Applet.isInternetShortcut(shortcut));
	}

	private static void Assume_notWindows() {
		org.junit.Assume.assumeFalse("ターミナルの書き方は mac・Linux のもの",
			System.getProperty("os.name").toLowerCase().startsWith("windows"));
	}
}
