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
	}

	@Test
	public void aPeekNeverGoesOut() throws Exception
	{
		UpdateNotice n = notice();
		String peek = n.json(false);
		assertEquals(0, calls.get());
		assertTrue(peek, peek.contains("\"enabled\":true") && peek.contains("\"show\":false") && peek.contains("\"latest\":null"));
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
	public void refusesWhatIsNotAVersionAndAnUnreadableFile() throws Exception
	{
		UpdateNotice n = notice();
		assertFalse(n.dismiss(""));
		assertFalse(n.dismiss("v1\nenabled=false"));
		assertFalse(n.dismiss(null));
		//壊れた記録は、何も覚えていないとして読む
		Path file = temp.getRoot().toPath().resolve("home/.aozoraepub3/update-check.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "checkedAt=\\uZZZZ\n");
		assertTrue(notice().json().contains("\"show\":true"));
	}
}
