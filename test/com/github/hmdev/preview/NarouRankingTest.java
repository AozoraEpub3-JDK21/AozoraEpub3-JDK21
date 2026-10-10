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
		assertTrue(daily, daily.contains("lim=" + NarouRanking.LIMIT));

		String all = NarouRanking.Query.parse("period=all&genre=0&serial=0").uri().toString();
		assertTrue(all, all.contains("&order=hyoka&"));
		assertTrue(all, all.contains("-gp"));
		assertFalse("総合はジャンルで絞らない: " + all, all.contains("genre="));
		assertFalse("短編も出す: " + all, all.contains("type="));

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
		assertArrayEquals(LIST, ranking.fetch(daily, t0 + NarouRanking.CACHE_TTL.toNanos() - 1));
		assertEquals(1, asked.size());
		ranking.fetch(NarouRanking.Query.parse("period=w"), t0);
		assertEquals("別の問い合わせは取る", 2, asked.size());
		ranking.fetch(daily, t0 + NarouRanking.CACHE_TTL.toNanos());
		assertEquals("古くなったら取り直す", 3, asked.size());
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
				NarouRanking.httpGet(uri, java.time.Duration.ofSeconds(1));
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

	@Test
	public void gzipBodiesAreUnpackedAndPlainOnesPassThrough() throws Exception
	{
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(packed)) {
			out.write(LIST);
		}
		assertArrayEquals(LIST, NarouRanking.gunzip(packed.toByteArray()));
		assertArrayEquals(LIST, NarouRanking.gunzip(LIST));
		assertArrayEquals(new byte[0], NarouRanking.gunzip(new byte[0]));
	}
}
