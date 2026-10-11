package com.github.hmdev.preview;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * 本棚のランキング（カクヨム）。
 *
 * <p>カクヨムに公開の API は無いので、ランキングのページ（{@code /rankings/<ジャンル>/<期間>}）を読む。
 * ページは Next.js で、順位の並びの作品 100 件が {@code <script id="__NEXT_DATA__">} の JSON
 * （Apollo の控え）に入っている（2026-10-11 に確かめた）。順番はサイトのランキングと同じ。</p>
 *
 * <p>JSON は読まずにビューアーへ渡す（プレビューは JSON の読み手を持たない）。
 * ここで確かめるのは、その script が在って「{」で始まるかだけ。ページの作りが変われば、
 * 「ランキングのデータが見つかりません」で止まる（黙って空の一覧を出さない）。</p>
 *
 * <p>利用規約（/legal/tos）は機械での取得を禁じていない。禁じているのは「過剰に負荷をかける利用」。
 * robots.txt も /rankings を拒んでいない。同じページは {@link RankingFetch#CACHE_TTL} の間は取り直さない。</p>
 */
final class KakuyomuRanking
{
	static final String BASE = "https://kakuyomu.jp/rankings/";

	/** 文言に出すサイトの名前 */
	static final String SITE = "カクヨム";

	/** 期間（なろうと同じ印）→ URL の期間。カクヨムに四半期は無い */
	private static final Map<String, String> PERIODS = Map.of(
		"d", "daily", "w", "weekly", "m", "monthly", "y", "yearly", "all", "entire");

	/** URL のジャンル（ランキングのページの見出しにある 12 個。総合は all） */
	static final Set<String> GENRES = Set.of(
		"fantasy", "action", "sf", "love_story", "romance", "drama",
		"horror", "mystery", "nonfiction", "history", "criticism", "others");

	private static final byte[] SCRIPT_OPEN = "<script id=\"__NEXT_DATA__\"".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] SCRIPT_CLOSE = "</script>".getBytes(StandardCharsets.US_ASCII);

	/**
	 * 問い合わせ 1 つ。
	 * @param period d・w・m・y・all
	 * @param genre all は総合
	 * @param longOnly 長編だけ（短編を外す）。外さないときは長編と短編のすべて
	 */
	record Query(String period, String genre, boolean longOnly)
	{
		/** {@code site=kakuyomu&period=d&genre=fantasy&serial=1} を読む。読めなければ null */
		static Query parse(String rawQuery)
		{
			String period = "d";
			String genre = "all";
			boolean longOnly = true;
			if (rawQuery != null && !rawQuery.isEmpty()) {
				for (String part : rawQuery.split("&")) {
					int eq = part.indexOf('=');
					String key = eq < 0 ? part : part.substring(0, eq);
					String value = eq < 0 ? "" : part.substring(eq + 1);
					switch (key) {
						case "period" -> period = value;
						//ビューアーは総合を 0 で送る（なろうと同じ）
						case "genre" -> genre = value.equals("0") ? "all" : value;
						case "serial" -> {
							if (!value.equals("0") && !value.equals("1")) return null;
							longOnly = value.equals("1");
						}
						//site=…・tab=… などは見ない
						default -> { }
					}
				}
			}
			if (!PERIODS.containsKey(period)) return null;
			if (!genre.equals("all") && !GENRES.contains(genre)) return null;
			return new Query(period, genre, longOnly);
		}

		/** ランキングのページの URL */
		URI uri()
		{
			return URI.create(BASE + this.genre + "/" + PERIODS.get(this.period)
				+ "?work_variation=" + (this.longOnly ? "long" : "all"));
		}
	}

	private final RankingFetch.Fetcher fetcher;

	private final RankingFetch fetch;

	KakuyomuRanking()
	{
		this(RankingFetch.newUpstream());
	}

	/** @param upstream サイトへ同時に出す数の枠（なろうと同じものを渡す） */
	KakuyomuRanking(Semaphore upstream)
	{
		this(uri -> RankingFetch.httpGet(uri, RankingFetch.DEADLINE, SITE, true), upstream);
	}

	KakuyomuRanking(RankingFetch.Fetcher fetcher)
	{
		this(fetcher, RankingFetch.newUpstream());
	}

	KakuyomuRanking(RankingFetch.Fetcher fetcher, Semaphore upstream)
	{
		this.fetcher = fetcher;
		this.fetch = new RankingFetch(upstream);
	}

	/** ページの __NEXT_DATA__ の JSON（そのまま） */
	byte[] fetch(Query query) throws IOException, InterruptedException
	{
		return fetch(query, System.nanoTime());
	}

	/** 時刻を渡せる版（試験用） */
	byte[] fetch(Query query, long nowNanos) throws IOException, InterruptedException
	{
		return this.fetch.fetch(query, query.uri(), this.fetcher, KakuyomuRanking::nextData, nowNanos);
	}

	/** ページから __NEXT_DATA__ の script の中身を抜く。無ければ IOException */
	static byte[] nextData(byte[] html) throws IOException
	{
		int open = indexOf(html, SCRIPT_OPEN, 0);
		int start = open < 0 ? -1 : indexOf(html, new byte[]{ '>' }, open + SCRIPT_OPEN.length);
		int end = start < 0 ? -1 : indexOf(html, SCRIPT_CLOSE, start + 1);
		if (end < 0) throw new IOException("カクヨムのページにランキングのデータが見つかりません（ページの作りが変わったかもしれません）");
		byte[] json = Arrays.copyOfRange(html, start + 1, end);
		String head = new String(json, 0, Math.min(json.length, 16), StandardCharsets.UTF_8).strip();
		if (!head.startsWith("{")) throw new IOException("カクヨムのページのランキングのデータが読めません（ページの作りが変わったかもしれません）");
		return json;
	}

	private static int indexOf(byte[] haystack, byte[] needle, int from)
	{
		outer:
		for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) continue outer;
			}
			return i;
		}
		return -1;
	}
}
