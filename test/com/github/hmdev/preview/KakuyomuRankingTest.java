package com.github.hmdev.preview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class KakuyomuRankingTest
{
	private static final String JSON = "{\"props\":{\"pageProps\":{\"__APOLLO_STATE__\":{}}}}";

	private static final byte[] PAGE = ("<!DOCTYPE html><html><head><script src=\"/a.js\"></script></head><body><div id=\"__next\"></div>"
		+ "<script id=\"__NEXT_DATA__\" type=\"application/json\">" + JSON + "</script>"
		+ "<script>var after = 1;</script></body></html>").getBytes(StandardCharsets.UTF_8);

	@Test
	public void parsesTheQueryTheViewerSends()
	{
		assertEquals(new KakuyomuRanking.Query("d", "all", true), KakuyomuRanking.Query.parse(null));
		assertEquals(new KakuyomuRanking.Query("d", "all", true), KakuyomuRanking.Query.parse("site=kakuyomu"));
		//総合はなろうと同じく 0 で来る
		assertEquals(new KakuyomuRanking.Query("w", "all", false),
			KakuyomuRanking.Query.parse("site=kakuyomu&period=w&genre=0&serial=0&tab=t1"));
		assertEquals(new KakuyomuRanking.Query("all", "fantasy", true),
			KakuyomuRanking.Query.parse("site=kakuyomu&period=all&genre=fantasy&serial=1"));
	}

	@Test
	public void refusesWhatTheSiteDoesNotHave()
	{
		//四半期はカクヨムに無い。なろうのジャンルの番号も通さない。ジャンルは URL の道になるので / や .. を通さない
		for (String bad : new String[]{ "period=q", "period=daily", "genre=201", "genre=comedy",
			"genre=../works", "genre=fantasy%2F..", "serial=2" }) {
			assertNull(bad, KakuyomuRanking.Query.parse(bad));
		}
	}

	@Test
	public void buildsTheRankingPageUrl()
	{
		assertEquals("https://kakuyomu.jp/rankings/all/daily?work_variation=long",
			KakuyomuRanking.Query.parse("period=d").uri().toString());
		assertEquals("https://kakuyomu.jp/rankings/love_story/entire?work_variation=all",
			KakuyomuRanking.Query.parse("period=all&genre=love_story&serial=0").uri().toString());
		String[][] periods = { { "w", "weekly" }, { "m", "monthly" }, { "y", "yearly" } };
		for (String[] p : periods) {
			assertTrue(p[0], KakuyomuRanking.Query.parse("period=" + p[0]).uri().getPath().endsWith("/" + p[1]));
		}
	}

	@Test
	public void takesOnlyTheNextDataJson() throws IOException
	{
		assertEquals(JSON, new String(KakuyomuRanking.nextData(PAGE), StandardCharsets.UTF_8));
	}

	/** ページの作りが変わったら、空の一覧ではなく理由で止まる */
	@Test
	public void saysSoWhenThePageChanged()
	{
		String[] pages = {
			"<html><body>メンテナンス中です</body></html>",
			"<script id=\"__NEXT_DATA__\" type=\"application/json\">{\"a\":1}",
			"<script id=\"__NEXT_DATA__\" type=\"application/json\">null</script>",
		};
		for (String page : pages) {
			try {
				KakuyomuRanking.nextData(page.getBytes(StandardCharsets.UTF_8));
				fail(page);
			} catch (IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("ページの作りが変わった"));
			}
		}
	}

	@Test
	public void remembersAPageForAWhile() throws Exception
	{
		List<URI> asked = new ArrayList<>();
		KakuyomuRanking ranking = new KakuyomuRanking(uri -> { asked.add(uri); return PAGE; });
		KakuyomuRanking.Query daily = KakuyomuRanking.Query.parse("period=d");
		long t0 = 1_000_000_000L;
		assertArrayEquals(JSON.getBytes(StandardCharsets.UTF_8), ranking.fetch(daily, t0));
		ranking.fetch(daily, t0 + RankingFetch.CACHE_TTL.toNanos() - 1);
		assertEquals(1, asked.size());
		ranking.fetch(daily, t0 + RankingFetch.CACHE_TTL.toNanos());
		assertEquals(2, asked.size());
	}

	/** 読めなかったページは覚えない（次に選んだときに取り直す） */
	@Test
	public void doesNotRememberAPageItCouldNotRead() throws Exception
	{
		List<URI> asked = new ArrayList<>();
		KakuyomuRanking ranking = new KakuyomuRanking(uri -> {
			asked.add(uri);
			return asked.size() == 1 ? "<html>エラー</html>".getBytes(StandardCharsets.UTF_8) : PAGE;
		});
		KakuyomuRanking.Query daily = KakuyomuRanking.Query.parse("period=d");
		try {
			ranking.fetch(daily, 1L);
			fail();
		} catch (IOException e) {
			//1 回目は読めない
		}
		assertArrayEquals(JSON.getBytes(StandardCharsets.UTF_8), ranking.fetch(daily, 2L));
		assertEquals(2, asked.size());
	}

	/** 同時に出す数の枠は、なろうとカクヨムで 1 つ（どちらもプレビューの同じスレッドを掴む） */
	@Test(timeout = 20_000)
	public void sharesTheUpstreamLimitWithNarou() throws Exception
	{
		java.util.concurrent.Semaphore upstream = RankingFetch.newUpstream();
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger inside = new java.util.concurrent.atomic.AtomicInteger();
		NarouRanking narou = new NarouRanking(uri -> {
			inside.incrementAndGet();
			release.await(15, java.util.concurrent.TimeUnit.SECONDS);
			return "[]".getBytes(StandardCharsets.UTF_8);
		}, upstream);
		KakuyomuRanking kakuyomu = new KakuyomuRanking(uri -> PAGE, upstream);
		List<Thread> waiting = new ArrayList<>();
		for (String q : new String[]{ "period=d", "period=w" }) {
			Thread t = new Thread(() -> {
				try {
					narou.fetch(NarouRanking.Query.parse(q));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			t.start();
			waiting.add(t);
		}
		while (inside.get() < RankingFetch.MAX_UPSTREAM) Thread.sleep(10);
		try {
			kakuyomu.fetch(KakuyomuRanking.Query.parse("period=d"));
			fail("なろうが枠を使い切っている間は、カクヨムも断る");
		} catch (RankingFetch.BusyException e) {
			//待たずに断る
		}
		release.countDown();
		for (Thread t : waiting) t.join();
		assertArrayEquals(JSON.getBytes(StandardCharsets.UTF_8), kakuyomu.fetch(KakuyomuRanking.Query.parse("period=d")));
	}
}
