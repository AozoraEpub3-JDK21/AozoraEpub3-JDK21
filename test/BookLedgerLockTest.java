import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.BookLedger;
import com.github.hmdev.web.WebAozoraConverter;

/**
 * 台帳の話数の記録は、本棚の更新が台帳を書くのと同じ鍵（WEB_LOCK）の下で読んで書く（internal #39）。
 * GUI のファイル変換は、台帳のある txt（Web 変換のキャッシュ）を鍵の下で変換する
 */
public class BookLedgerLockTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String URL = "https://ncode.syosetu.com/n0000aa/";

	/** 台帳の txt だけを Web 変換のキャッシュと見る */
	@Test
	public void recognisesTheLedgerText() throws Exception {
		File dir = tempFolder.newFolder();
		BookLedger.create(URL, "作品").save(dir);
		File txt = new File(dir, "作品.txt");
		Files.write(txt.toPath(), "x".getBytes(StandardCharsets.UTF_8));
		assertTrue(BookLedger.isLedgerText(txt));
		assertFalse("台帳の txt ではない", BookLedger.isLedgerText(new File(dir, "別.txt")));
		assertFalse("台帳が無い", BookLedger.isLedgerText(new File(tempFolder.newFolder(), "作品.txt")));
		assertFalse(BookLedger.isLedgerText(null));
	}

	/** 別のスレッド（本棚の更新）が鍵を持っている間は書かず、鍵が外れてから書く */
	@Test(timeout = 10000)
	public void recordingWaitsForTheWebLock() throws Exception {
		File dir = tempFolder.newFolder();
		BookLedger.create(URL, "作品").withEpisodes(5).save(dir);
		BookInfo bookInfo = new BookInfo(new File(dir, "作品.txt"));
		bookInfo.ledgerDir = dir;
		bookInfo.ledgerEpisodes = 5;
		File epub = new File(tempFolder.newFolder(), "作品.epub");

		CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread shelf = new Thread(() -> {
			WebAozoraConverter.WEB_LOCK.lock();
			try {
				held.countDown();
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				WebAozoraConverter.WEB_LOCK.unlock();
			}
		});
		shelf.start();
		held.await();
		CountDownLatch done = new CountDownLatch(1);
		Thread gui = new Thread(() -> { BookLedger.recordBookEpisodes(bookInfo, epub); done.countDown(); });
		gui.start();
		try {
			assertFalse("鍵を持たれている間に書いた", done.await(500, TimeUnit.MILLISECONDS));
			assertEquals(-1, BookLedger.load(dir).ownEpisodesFor(epub));
		} finally {
			//落ちても鍵を残さない（ほかの試験が止まる）
			release.countDown();
			shelf.join();
		}
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(5, BookLedger.load(dir).ownEpisodesFor(epub));
		gui.join();
		assertFalse(WebAozoraConverter.WEB_LOCK.isLocked());
	}
}
