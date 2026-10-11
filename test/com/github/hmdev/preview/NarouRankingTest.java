package com.github.hmdev.preview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class NarouRankingTest
{
	private static final byte[] LIST = "[{\"allcount\":1},{\"ncode\":\"N1234AB\",\"title\":\"t\"}]".getBytes(StandardCharsets.UTF_8);

	@Test
	public void theDefaultIsTheDailyOverallRankingOfSerials()
	{
		assertEquals(new NarouRanking.Query("d", 0, true), NarouRanking.Query.parse(null));
		assertEquals(new NarouRanking.Query("d", 0, true), NarouRanking.Query.parse(""));
		//ビューアーが付けるタブの印は見ない
		assertEquals(new NarouRanking.Query("w", 201, false), NarouRanking.Query.parse("period=w&genre=201&serial=0&tab=t1"));
	}

	@Test
	public void unknownPeriodsAndGenresAreRefused()
	{
		for (String bad : new String[]{ "period=x", "period=", "genre=5", "genre=abc", "genre=", "serial=2", "serial=" }) {
			assertNull(bad, NarouRanking.Query.parse(bad));
		}
	}

	@Test
	public void theUrlAsksForThePeriodsPointsAndOnlySerialsWhenAsked()
	{
		String daily = NarouRanking.Query.parse("period=d&genre=201&serial=1").uri().toString();
		assertTrue(daily, daily.startsWith(NarouRanking.ENDPOINT + "?"));
		assertTrue(daily, daily.contains("&order=dailypoint&"));
		assertTrue(daily, daily.contains("&of=t-n-w-s-g-nt-e-ga-dp"));
		assertTrue(daily, daily.contains("&genre=201"));
		//r だと連載中だけになり、完結した連載が抜ける
		assertTrue(daily, daily.endsWith("&type=re"));
		assertTrue(daily, daily.contains("gzip=5"));
		assertTrue("数字だけの文字列も引用符付きで: " + daily, daily.contains("libtype=2"));
		assertTrue(daily, daily.contains("lim=" + NarouRanking.LIMIT));

		String all = NarouRanking.Query.parse("period=all&genre=0&serial=0").uri().toString();
		assertTrue(all, all.contains("&order=hyoka&"));
		assertTrue(all, all.contains("-gp"));
		assertFalse("総合はジャンルで絞らない: " + all, all.contains("&genre="));
		//libtype= と取り違えないよう & から見る
		assertFalse("短編も出す: " + all, all.contains("&type="));

		for (String[] p : new String[][]{ {"w", "weeklypoint", "wp"}, {"m", "monthlypoint", "mp"},
				{"q", "quarterpoint", "qp"}, {"y", "yearlypoint", "yp"} }) {
			String url = NarouRanking.Query.parse("period=" + p[0]).uri().toString();
			assertTrue(url, url.contains("&order=" + p[1] + "&"));
			assertTrue(url, url.contains("-" + p[2]));
		}
	}

	@Test
	public void theSameQueryIsNotFetchedAgainUntilTheCacheExpires() throws Exception
	{
		List<URI> asked = new CopyOnWriteArrayList<>();
		NarouRanking ranking = new NarouRanking(uri -> { asked.add(uri); return LIST; });
		NarouRanking.Query daily = NarouRanking.Query.parse("period=d");
		long t0 = 1_000_000_000L;
		assertArrayEquals(LIST, ranking.fetch(daily, t0));
		assertArrayEquals(LIST, ranking.fetch(daily, t0 + RankingFetch.CACHE_TTL.toNanos() - 1));
		assertEquals(1, asked.size());
		ranking.fetch(NarouRanking.Query.parse("period=w"), t0);
		assertEquals("別の問い合わせは取る", 2, asked.size());
		ranking.fetch(daily, t0 + RankingFetch.CACHE_TTL.toNanos());
		assertEquals("古くなったら取り直す", 3, asked.size());
	}

	/** なろうへ同時に出すのは MAX_UPSTREAM 本まで。超えたら待たずに断る。覚えている答えは混んでいても返す */
	@Test(timeout = 20_000)
	public void onlyAFewQueriesGoToNarouAtOnce() throws Exception
	{
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger inside = new java.util.concurrent.atomic.AtomicInteger();
		NarouRanking ranking = new NarouRanking(uri -> {
			inside.incrementAndGet();
			if (!uri.toString().contains("order=dailypoint")) release.await(15, java.util.concurrent.TimeUnit.SECONDS);
			return LIST;
		});
		//覚えさせておく
		ranking.fetch(NarouRanking.Query.parse("period=d"));
		java.util.List<Thread> waiting = new java.util.ArrayList<>();
		String[] slow = { "period=w", "period=m" };
		for (String q : slow) {
			Thread t = new Thread(() -> {
				try {
					ranking.fetch(NarouRanking.Query.parse(q));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			t.start();
			waiting.add(t);
		}
		while (inside.get() < 1 + slow.length) Thread.sleep(10);
		try {
			ranking.fetch(NarouRanking.Query.parse("period=q"));
			fail("3 本目は断る");
		} catch (RankingFetch.BusyException e) {
			//待たずに断る
		}
		assertArrayEquals("覚えている答えは返す", LIST, ranking.fetch(NarouRanking.Query.parse("period=d")));
		release.countDown();
		for (Thread t : waiting) t.join();
		assertArrayEquals("空いたら取れる", LIST, ranking.fetch(NarouRanking.Query.parse("period=q")));
	}

	@Test
	public void anErrorBodyIsReportedAndNotCached() throws Exception
	{
		List<URI> asked = new CopyOnWriteArrayList<>();
		NarouRanking ranking = new NarouRanking(uri -> {
			asked.add(uri);
			//API はエラーも 200 で返す
			return "Error: Invalid ranking type.".getBytes(StandardCharsets.UTF_8);
		});
		NarouRanking.Query daily = NarouRanking.Query.parse("period=d");
		for (int i = 0; i < 2; i++) {
			try {
				ranking.fetch(daily, 0L);
				fail("一覧でない本文は断る");
			} catch (IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("Error: Invalid ranking type."));
			}
		}
		assertEquals("断った本文は覚えない", 2, asked.size());
	}

	/** 見出しの後で本文が止まっても、期限で戻る（PR #127 の codex P2）。見出しまでの timeout では戻らない形 */
	@Test(timeout = 20_000)
	public void aBodyThatStallsAfterTheHeadersHitsTheDeadline() throws Exception
	{
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		com.sun.net.httpserver.HttpServer stall = com.sun.net.httpserver.HttpServer.create(
			new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
		stall.createContext("/", exchange -> {
			//長さを約束して頭だけ送り、残りは送らない
			exchange.sendResponseHeaders(200, 1000);
			exchange.getResponseBody().write('[');
			exchange.getResponseBody().flush();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		stall.start();
		try {
			URI uri = URI.create("http://" + PreviewServer.urlHost(java.net.InetAddress.getLoopbackAddress()) + ":" + stall.getAddress().getPort() + "/");
			long t0 = System.nanoTime();
			try {
				RankingFetch.httpGet(uri, java.time.Duration.ofSeconds(1), NarouRanking.SITE, false);
				fail("本文が届かないまま返ってはいけない");
			} catch (IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("応えませんでした"));
			}
			long millis = (System.nanoTime() - t0) / 1_000_000L;
			assertTrue("期限で戻る: " + millis + "ms", millis < 10_000);
		} finally {
			release.countDown();
			stall.stop(0);
		}
	}

	/** 上限を超える長さを名乗る応答は、本文を待たずにその場で断る（溜めてから断る形だと、止まった本文で期限まで待つ） */
	@Test(timeout = 20_000)
	public void aBodyThatDeclaresMoreThanTheLimitIsRefused() throws Exception
	{
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		com.sun.net.httpserver.HttpServer big = com.sun.net.httpserver.HttpServer.create(
			new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
		big.createContext("/", exchange -> {
			exchange.sendResponseHeaders(200, RankingFetch.MAX_BYTES + 1L);
			try {
				//長さだけ名乗って 1 バイト送り、止まる
				exchange.getResponseBody().write('[');
				exchange.getResponseBody().flush();
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (IOException e) {
				//受け取る側が切った
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		big.start();
		try {
			URI uri = URI.create("http://" + PreviewServer.urlHost(java.net.InetAddress.getLoopbackAddress()) + ":" + big.getAddress().getPort() + "/");
			long t0 = System.nanoTime();
			try {
				RankingFetch.httpGet(uri, java.time.Duration.ofSeconds(10), NarouRanking.SITE, false);
				fail("上限を超える応答は断る");
			} catch (IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("大きすぎます"));
			}
			long millis = (System.nanoTime() - t0) / 1_000_000L;
			assertTrue("本文を待たない: " + millis + "ms", millis < 5_000);
		} finally {
			release.countDown();
			big.stop(0);
		}
	}

	/** つながらないときに「null」と出さない */
	@Test(timeout = 20_000)
	public void aRefusedConnectionSaysSoInsteadOfNull() throws Exception
	{
		int port;
		try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			port = probe.getLocalPort();
		}
		URI uri = URI.create("http://" + PreviewServer.urlHost(java.net.InetAddress.getLoopbackAddress()) + ":" + port + "/");
		try {
			RankingFetch.httpGet(uri, java.time.Duration.ofSeconds(5), NarouRanking.SITE, false);
			fail("閉じたポートからは取れない");
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("つながりません"));
			assertFalse(e.getMessage(), e.getMessage().contains("null"));
		}
	}

	@Test
	public void gzipBodiesAreUnpackedAndPlainOnesPassThrough() throws Exception
	{
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(packed)) {
			out.write(LIST);
		}
		assertArrayEquals(LIST, RankingFetch.gunzip(packed.toByteArray(), NarouRanking.SITE));
		assertArrayEquals(LIST, RankingFetch.gunzip(LIST, NarouRanking.SITE));
		assertArrayEquals(new byte[0], RankingFetch.gunzip(new byte[0], NarouRanking.SITE));
	}
}
