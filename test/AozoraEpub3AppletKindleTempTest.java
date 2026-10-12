import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.BookLedger;

/**
 * kindlegen の後の、一時ファイルの EPUB（kindle*.epub）の後始末（internal #39）。
 * mobi ができなかった・中止したときも、出力のフォルダに一時ファイルを残さない
 */
public class AozoraEpub3AppletKindleTempTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String URL = "https://ncode.syosetu.com/n0000aa/";

	private File tmpEpub(File dir) throws Exception {
		File tmp = File.createTempFile("kindle", ".epub", dir);
		Files.write(tmp.toPath(), "epub".getBytes(StandardCharsets.UTF_8));
		return tmp;
	}

	/** EPUB を残す設定: 本当の名前へ動かし、その名前で本の話数を台帳に書く（mobi ができなかったときも） */
	@Test
	public void keepsTheEpubUnderItsRealNameAndRecordsIt() throws Exception {
		File out = tempFolder.newFolder();
		File ledgerDir = tempFolder.newFolder();
		BookLedger.create(URL, "in").withEpisodes(5).save(ledgerDir);
		BookInfo bookInfo = new BookInfo(new File(ledgerDir, "in.txt"));
		bookInfo.ledgerDir = ledgerDir;
		bookInfo.ledgerEpisodes = 5;
		File tmp = tmpEpub(out);
		File real = new File(out, "[著者] 題.epub");
		assertEquals(real, AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, true, bookInfo));
		assertFalse("一時ファイルが残った", tmp.exists());
		assertTrue(real.exists());
		assertEquals(5, BookLedger.load(ledgerDir).ownEpisodesFor(real));
		assertEquals(java.util.Arrays.toString(out.list()), 1, out.list().length);
	}

	/** 前の版の EPUB があれば置き換える */
	@Test
	public void replacesAnOlderEpub() throws Exception {
		File out = tempFolder.newFolder();
		File real = new File(out, "題.epub");
		Files.write(real.toPath(), "old".getBytes(StandardCharsets.UTF_8));
		File tmp = tmpEpub(out);
		assertEquals(real, AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, true, new BookInfo(tmp)));
		assertEquals("epub", new String(Files.readAllBytes(real.toPath()), StandardCharsets.UTF_8));
		assertFalse(tmp.exists());
	}

	/** EPUB を残さない設定（mobi のみ）: 一時ファイルを消す */
	@Test
	public void deletesTheTempEpubWhenNotKept() throws Exception {
		File out = tempFolder.newFolder();
		File tmp = tmpEpub(out);
		File real = new File(out, "題.epub");
		assertNull(AozoraEpub3Applet.finishKindleTempEpub(tmp, real, false, false, new BookInfo(tmp)));
		assertFalse("一時ファイルが残った", tmp.exists());
		assertFalse(real.exists());
	}

	/** 一時ファイルが無い（変換が失敗して消した後など）: 何もしない */
	@Test
	public void doesNothingWithoutTheTempEpub() throws Exception {
		File out = tempFolder.newFolder();
		File tmp = new File(out, "kindle123.epub");
		File real = new File(out, "題.epub");
		assertNull(AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, true, new BookInfo(tmp)));
		assertEquals(0, out.list().length);
	}

	/** 一時ファイルが無いとき、前の回の EPUB を返さない（古い本をプレビューの対象にしない） */
	@Test
	public void doesNotReturnAnOlderEpubWithoutTheTempEpub() throws Exception {
		File out = tempFolder.newFolder();
		File tmp = new File(out, "kindle123.epub");
		File real = new File(out, "題.epub");
		Files.write(real.toPath(), "old".getBytes(StandardCharsets.UTF_8));
		assertNull(AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, true, new BookInfo(tmp)));
		assertTrue(real.exists());
	}

	/** mobi ができなかった回（record=false）: EPUB は本当の名前で残すが、台帳には書かない */
	@Test
	public void keepsWithoutRecordingWhenTheMobiFailed() throws Exception {
		File out = tempFolder.newFolder();
		File ledgerDir = tempFolder.newFolder();
		BookLedger.create(URL, "in").withEpisodes(5).save(ledgerDir);
		BookInfo bookInfo = new BookInfo(new File(ledgerDir, "in.txt"));
		bookInfo.ledgerDir = ledgerDir;
		bookInfo.ledgerEpisodes = 5;
		File tmp = tmpEpub(out);
		File real = new File(out, "題.epub");
		assertEquals(real, AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, false, bookInfo));
		assertTrue(real.exists());
		assertEquals(-1, BookLedger.load(ledgerDir).ownEpisodesFor(real));
	}

	/** 本当の名前にできない（その名前の空でないフォルダがある、など）: 一時ファイルを消さずに返す（手動のプレビュー用） */
	@Test
	public void keepsTheTempEpubWhenTheMoveFails() throws Exception {
		File out = tempFolder.newFolder();
		File real = new File(out, "題.epub");
		assertTrue(real.mkdir());
		Files.write(new File(real, "x").toPath(), "x".getBytes(StandardCharsets.UTF_8));
		File tmp = tmpEpub(out);
		assertEquals(tmp, AozoraEpub3Applet.finishKindleTempEpub(tmp, real, true, true, new BookInfo(tmp)));
		assertTrue("ただ 1 つの写しを消した", tmp.exists());
	}

	/** 残すか: 中止した回は残さない。EPUB も出す設定なら残す。mobi だけでも mobi ができなかったら残す */
	@Test
	public void keepKindleEpub() {
		assertTrue(AozoraEpub3Applet.keepKindleEpub(true, true, false));
		assertTrue(AozoraEpub3Applet.keepKindleEpub(true, false, false));
		assertFalse(AozoraEpub3Applet.keepKindleEpub(false, true, false));
		assertTrue(AozoraEpub3Applet.keepKindleEpub(false, false, false));
		assertFalse(AozoraEpub3Applet.keepKindleEpub(true, false, true));
		assertFalse(AozoraEpub3Applet.keepKindleEpub(false, false, true));
	}
}
