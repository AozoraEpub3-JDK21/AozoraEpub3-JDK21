package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.update.UpdateChecker;

/**
 * 本棚の「新しい版があります」（internal #48）。利用者の決定（2026-10-15）: 本棚を開いたときに確かめ、24 時間に 1 回まで。
 * 閉じたらその版ではもう出さない。確かめに行くのは切れる（既定はオン）
 */
public class UpdateNoticeTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static final long HOUR = 60L * 60 * 1000;

	private final AtomicLong now = new AtomicLong(1_000_000_000_000L);
	private final AtomicInteger calls = new AtomicInteger();
	/** GitHub が答える最新の版。null なら確かめに失敗する */
	private volatile String upstream = "v1.8.0-jdk21";

	private UpdateNotice notice() throws Exception
	{
		Path file = temp.getRoot().toPath().resolve("home/.aozoraepub3/update-check.properties");
		return new UpdateNotice(file, "1.7.3-jdk21", () -> {
			calls.incrementAndGet();
			return upstream == null
				? new UpdateChecker.Result("1.7.3-jdk21", null, false, UpdateChecker.DOWNLOAD_PAGE_URL, "offline")
				: new UpdateChecker.Result("1.7.3-jdk21", upstream, UpdateChecker.compareVersions(upstream, "1.7.3-jdk21") > 0,
					UpdateChecker.DOWNLOAD_PAGE_URL, null);
		}, now::get);
	}

	@Test
	public void showsANewerVersionAndChecksAtMostOncePerDay() throws Exception
	{
		UpdateNotice n = notice();
		String first = n.json();
		assertTrue(first, first.contains("\"show\":true") && first.contains("\"latest\":\"v1.8.0-jdk21\""));
		assertTrue(first, first.contains("\"available\":true") && first.contains("\"current\":\"1.7.3-jdk21\""));
		assertEquals(1, calls.get());
		now.addAndGet(23 * HOUR);
		assertTrue("24 時間たつまでは覚えている版で出す", n.json().contains("\"show\":true"));
		assertEquals("通信しない", 1, calls.get());
		now.addAndGet(HOUR);
		upstream = "v1.8.1-jdk21";
		assertTrue(n.json().contains("\"latest\":\"v1.8.1-jdk21\""));
		assertEquals("24 時間たったら確かめ直す", 2, calls.get());
	}

	@Test
	public void remembersAcrossInstances() throws Exception
	{
		notice().json();
		now.addAndGet(HOUR);
		String again = notice().json();
		assertEquals("別の起動でも 1 日 1 回", 1, calls.get());
		assertTrue(again, again.contains("\"show\":true"));
	}

	@Test
	public void anOlderOrSameVersionIsNotShown() throws Exception
	{
		upstream = "v1.7.3-jdk21";
		assertTrue(notice().json().contains("\"show\":false"));
	}

	@Test
	public void aFailedCheckWaitsADayAndKeepsWhatItKnew() throws Exception
	{
		UpdateNotice n = notice();
		n.json();
		now.addAndGet(25 * HOUR);
		upstream = null;
		String offline = n.json();
		assertEquals(2, calls.get());
		assertTrue("失敗しても覚えている版で出す: " + offline, offline.contains("\"latest\":\"v1.8.0-jdk21\"") && offline.contains("\"show\":true"));
		now.addAndGet(HOUR);
		n.json();
		assertEquals("つながらなくても、開くたびに待たせない", 2, calls.get());
	}

	@Test
	public void aDismissedVersionIsNotShownUntilANewerOne() throws Exception
	{
		UpdateNotice n = notice();
		n.json();
		assertTrue(n.dismiss("v1.8.0-jdk21"));
		assertTrue(n.json().contains("\"show\":false"));
		now.addAndGet(25 * HOUR);
		upstream = "v1.8.1-jdk21";
		assertTrue("もっと新しい版が出たらまた出す", n.json().contains("\"show\":true"));
	}

	@Test
	public void turningItOffStopsCheckingAndTurningItOnChecksAgain() throws Exception
	{
		UpdateNotice n = notice();
		n.json();
		assertTrue(n.setEnabled(false));
		now.addAndGet(48 * HOUR);
		String off = n.json();
		assertTrue(off, off.contains("\"enabled\":false") && off.contains("\"show\":false"));
		assertEquals("切っていれば確かめに行かない", 1, calls.get());
		assertTrue(n.setEnabled(true));
		n.json();
		assertEquals("入れ直したら確かめ直す", 2, calls.get());
		//24 時間たっていなくても、入れ直したときは確かめ直す（48 時間後の上の升では、時間で確かめ直すのと見分けられない）
		now.addAndGet(HOUR);
		assertTrue(n.setEnabled(false));
		assertTrue(n.setEnabled(true));
		n.json();
		assertEquals("1 時間後に入れ直しても確かめ直す", 3, calls.get());
	}

	@Test
	public void aPeekNeverGoesOut() throws Exception
	{
		UpdateNotice n = notice();
		String peek = n.json(false);
		assertEquals(0, calls.get());
		assertTrue(peek, peek.contains("\"enabled\":true") && peek.contains("\"show\":false") && peek.contains("\"latest\":null"));
	}

	/**
	 * 問い合わせは鍵の外。問い合わせ中でも、覗く・閉じる・切り替えは待たない。2 つ目の問い合わせは出さない。
	 * 問い合わせ中に閉じた版は、結果を書いた後も残る（PR のゲート2）
	 */
	@Test
	public void aSlowCheckBlocksNothingElseAndKeepsWhatWasWrittenMeanwhile() throws Exception
	{
		java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Path file = temp.getRoot().toPath().resolve("home/.aozoraepub3/update-check.properties");
		UpdateNotice n = new UpdateNotice(file, "1.7.3-jdk21", () -> {
			calls.incrementAndGet();
			entered.countDown();
			try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			return new UpdateChecker.Result("1.7.3-jdk21", "v1.8.0-jdk21", true, UpdateChecker.DOWNLOAD_PAGE_URL, null);
		}, now::get);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			java.util.concurrent.Future<String> slow = pool.submit(() -> n.json());
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			long t0 = System.nanoTime();
			n.json(false);
			assertTrue(n.dismiss("v1.8.0-jdk21"));
			String second = n.json();
			long ms = (System.nanoTime() - t0) / 1_000_000;
			assertTrue("問い合わせ中でも待たない: " + ms + " ms", ms < 2000);
			assertEquals("2 つ目の問い合わせは出さない", 1, calls.get());
			assertTrue(second, second.contains("\"show\":false"));
			release.countDown();
			String done = slow.get(5, java.util.concurrent.TimeUnit.SECONDS);
			assertTrue("閉じた版は消えない: " + done, done.contains("\"latest\":\"v1.8.0-jdk21\"") && done.contains("\"show\":false"));
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	public void aCheckerThatThrowsIsAFailedCheck() throws Exception
	{
		Path file = temp.getRoot().toPath().resolve("home/.aozoraepub3/update-check.properties");
		UpdateNotice n = new UpdateNotice(file, "1.7.3-jdk21", () -> { calls.incrementAndGet(); throw new IllegalStateException("x"); }, now::get);
		assertTrue(n.json().contains("\"show\":false"));
		n.json();
		assertEquals("投げても時刻は進む", 1, calls.get());
	}

	@Test
	public void aClockThatWentBackChecksAgain() throws Exception
	{
		UpdateNotice n = notice();
		n.json();
		now.addAndGet(-HOUR);
		n.json();
		assertEquals(2, calls.get());
	}

	@Test
	public void refusesWhatIsNotAVersion() throws Exception
	{
		UpdateNotice n = notice();
		assertFalse(n.dismiss(""));
		assertFalse(n.dismiss("v1\nenabled=false"));
		assertFalse(n.dismiss(null));
	}

	/**
	 * 読めない記録（壊れた・フォルダ）は、自動の確認では確かめにも行かず上書きもしない（止めた設定を消さない。PR の検証）。
	 * 利用者が切り替えたときだけ新しく書き直す
	 */
	@Test
	public void anUnreadableRecordIsNeitherCheckedNorOverwrittenUntilTheUserActs() throws Exception
	{
		Path file = temp.getRoot().toPath().resolve("home/.aozoraepub3/update-check.properties");
		Files.createDirectories(file.getParent());
		String broken = "enabled=false\nlatest=\\uZZZZ\n";
		Files.writeString(file, broken);
		UpdateNotice n = notice();
		String answer = n.json();
		assertEquals("確かめに行かない", 0, calls.get());
		assertTrue(answer, answer.contains("\"show\":false"));
		assertEquals("上書きしない", broken, Files.readString(file));
		assertTrue("切り替えたときは書き直す", n.setEnabled(false));
		assertTrue(Files.readString(file).contains("enabled=false"));

		Path dir = temp.getRoot().toPath().resolve("home2/.aozoraepub3/update-check.properties");
		Files.createDirectories(dir);
		UpdateNotice d = new UpdateNotice(dir, "1.7.3-jdk21", () -> { calls.incrementAndGet(); return null; }, now::get);
		d.json();
		d.json();
		assertEquals("フォルダになっていても、開くたびに確かめに行かない", 0, calls.get());
	}

	/** 閉じた版より古い版を閉じても、閉じた版は戻らない（古い画面から届いても、新しい版の帯を出し直さない。PR の検証） */
	@Test
	public void dismissingAnOlderVersionDoesNotBringTheBandBack() throws Exception
	{
		upstream = "v1.8.1-jdk21";
		UpdateNotice n = notice();
		n.json();
		assertTrue(n.dismiss("v1.8.1-jdk21"));
		assertTrue(n.dismiss("v1.8.0-jdk21"));
		assertTrue(n.json().contains("\"show\":false"));
	}}
