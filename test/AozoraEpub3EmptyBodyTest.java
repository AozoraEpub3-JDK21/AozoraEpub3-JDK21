import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本文の行が 1 つも残らない入力でも、変換全体が落ちずに EPUB ができるテスト（監査 35・internal #15）。
 *
 * 表題の後に空行が無いと、先頭の数行が表題の塊として読まれる。短い本文が全部そこに飲み込まれると、
 * 本文の節が 1 度も開かれず、最後に節を閉じるところで「No current entry」になって EPUB が残らなかった
 * （空行なしで本文 0〜4 行。5 行以上なら通っていた）。
 */
public class AozoraEpub3EmptyBodyTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private void convertsToEpub(String text) throws Exception {
		File txt = tempFolder.newFile("in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File out = tempFolder.newFolder("out");
		int exit = AozoraEpub3.run(new String[] {"-enc", "UTF-8", "-of", "-d", out.getPath(), txt.getPath()});
		assertEquals("終了コード: " + text, 0, exit);
		File[] epubs = out.listFiles((d, n) -> n.endsWith(".epub"));
		assertEquals("EPUB が 1 つできる: " + text, 1, epubs.length);
		try (ZipFile zf = new ZipFile(epubs[0])) {
			assertTrue("本文の節がある: " + text, zf.getEntry("OPS/xhtml/0001.xhtml") != null);
		}
	}

	@Test
	public void noBlankLineAndNoBody() throws Exception {
		convertsToEpub("テスト\n著者\n");
	}

	@Test
	public void noBlankLineAndShortBody() throws Exception {
		convertsToEpub("テスト\n著者\n本文1\n本文2\n本文3\n本文4\n");
	}

	@Test
	public void titleBlankAndOneLine() throws Exception {
		// internal #15 の入力（表題・空行・1 行）
		convertsToEpub("テスト\n\nあいう\n");
	}
}
