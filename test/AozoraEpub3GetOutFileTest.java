import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.info.BookInfo;

/**
 * 出力ファイルの決定（AozoraEpub3.getOutFile）のパス検証のテスト。
 *
 * 修正前は、出力先を toRealPath で解決する一方、まだ無い出力ファイルは toAbsolutePath のまま比べていた。
 * symlink（macOS の /tmp → /private/tmp）・junction・8.3 形式の短い名前を通した出力先では、
 * 初めて変換する本で「出力パスが許可されたディレクトリ外です」になっていた。
 */
public class AozoraEpub3GetOutFileTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static BookInfo book(File src) {
		BookInfo info = new BookInfo(src);
		info.creator = "著者";
		info.title = "表題";
		return info;
	}

	/** symlink（Windows では junction）経由の出力先に、まだ無い本を書ける */
	@Test
	public void newBookIntoLinkedDestinationIsAllowed() throws Exception {
		Path realDst = tempFolder.newFolder("real").toPath();
		Path linkDst = tempFolder.getRoot().toPath().resolve("link");
		assumeSymlinkSupported(linkDst, realDst);
		File src = tempFolder.newFile("in.txt");

		File out = AozoraEpub3.getOutFile(src, linkDst.toFile(), book(src), true, ".epub");

		//返すのは渡された出力先の形（ログ・プレビューの照合がこの形を前提にしている）
		assertEquals(linkDst.toAbsolutePath().normalize(), out.toPath().getParent());
		//実体は実パスの出力先
		assertEquals(realDst.toRealPath(), out.toPath().getParent().toRealPath());
		assertEquals("[著者] 表題.epub", out.getName());
	}

	@Test
	public void newBookIntoRealDestinationIsAllowed() throws Exception {
		Path realDst = tempFolder.newFolder("real").toPath();
		File src = tempFolder.newFile("in.txt");

		File out = AozoraEpub3.getOutFile(src, realDst.toFile(), book(src), true, ".epub");

		assertEquals(realDst.toAbsolutePath().normalize(), out.toPath().getParent());
	}

	/** 出力ファイルの名前が壊れたリンクなら、理由の分かる文言で断る */
	@Test
	public void danglingOutputLinkIsRejectedWithReason() throws Exception {
		Path realDst = tempFolder.newFolder("real").toPath();
		File src = tempFolder.newFile("in.txt");
		Path link = realDst.resolve("[著者] 表題.epub");
		try {
			Files.createSymbolicLink(link, realDst.resolve("old").resolve("gone.epub"));
		} catch (IOException | UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
		}

		IOException e = assertThrows(IOException.class,
			() -> AozoraEpub3.getOutFile(src, realDst.toFile(), book(src), true, ".epub"));
		assertTrue(e.getMessage(), e.getMessage().contains("出力パスを解決できません"));
	}

	/** 出力ファイルの名前のリンクが出力先の外を指していれば、従来どおり断る */
	@Test
	public void existingOutputLinkPointingOutsideIsRejected() throws Exception {
		Path realDst = tempFolder.newFolder("real").toPath();
		Path outside = tempFolder.newFile("outside.epub").toPath();
		File src = tempFolder.newFile("in.txt");
		Path link = realDst.resolve("[著者] 表題.epub");
		try {
			Files.createSymbolicLink(link, outside);
		} catch (IOException | UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
		}

		assertThrows(IOException.class,
			() -> AozoraEpub3.getOutFile(src, realDst.toFile(), book(src), true, ".epub"));
	}

	/** ディレクトリへのリンクを作成する。作れない環境ではテストをスキップする。
	 * Windows では開発者モード / 管理者権限が無いと symlink を作成できないため、
	 * 権限不要の directory junction (mklink /J) にフォールバックする
	 * （WebAozoraConverterSafeResolveTest と同じ）。 */
	private static void assumeSymlinkSupported(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return;
		} catch (IOException | UnsupportedOperationException e) {
			if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
				org.junit.Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
				return;
			}
		}
		try {
			Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
				.redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			org.junit.Assume.assumeTrue("junction を作成できない環境のためスキップ",
				p.waitFor() == 0 && Files.exists(link));
		} catch (IOException | InterruptedException e) {
			org.junit.Assume.assumeNoException("junction を作成できない環境のためスキップ", e);
		}
	}
}
