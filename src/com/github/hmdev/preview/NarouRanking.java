package com.github.hmdev.preview;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 本棚のランキング（小説家になろう）。internal #11
 *
 * <p>なろう小説 API（{@code novelapi}）を期間のポイント順で引く。公式のランキング API
 * （{@code rankget}）は年間・累計が無く、ジャンルでも連載かどうかでも絞れないので使わない
 * （2026-10-11 の spike）。代わりに順番は、サイトのランキングのページ（集計した時点の写し）と
 * 少し違う（今のポイントで並ぶ）。</p>
 *
 * <p>応答の JSON は読まずにビューアーへ渡す（並べるのも本棚にあるかを見るのもビューアー）。
 * ここで確かめるのは、配列の形で返ったかだけ。API はエラーも 200 で「Error: …」の本文を返す。</p>
 *
 * <p>API の注意事項は「利用制限は今は無いがキャッシュを入れること・長くても 2 週間で更新」。
 * 同じ問い合わせは {@link RankingFetch#CACHE_TTL} の間は取り直さない。</p>
 */
final class NarouRanking
{
	static final String ENDPOINT = "https://api.syosetu.com/novelapi/api/";

	/** 1 回に出す作品の数（API の上限は 500） */
	static final int LIMIT = 100;

	/** 期間 → 並び順（order）と、点数の項目（of） */
	private static final Map<String, String[]> PERIODS = Map.of(
		"d", new String[]{ "dailypoint", "dp" },
		"w", new String[]{ "weeklypoint", "wp" },
		"m", new String[]{ "monthlypoint", "mp" },
		"q", new String[]{ "quarterpoint", "qp" },
		"y", new String[]{ "yearlypoint", "yp" },
		"all", new String[]{ "hyoka", "gp" });

	/** API のジャンルの番号（0 は総合として扱う。API の「0 未選択」は使わない） */
	static final Set<Integer> GENRES = Set.of(
		101, 102, 201, 202, 301, 302, 303, 304, 305, 306, 307,
		401, 402, 403, 404, 9901, 9902, 9903, 9904, 9999, 9801);

	/**
	 * 問い合わせ 1 つ。
	 * @param period d・w・m・q・y・all
	 * @param genre 0 は総合
	 * @param serialOnly 連載だけ（連載中と完結済み。短編を外す）
	 */
	record Query(String period, int genre, boolean serialOnly)
	{
		/** {@code period=d&genre=201&serial=1} を読む。読めなければ null */
		static Query parse(String rawQuery)
		{
			String period = "d";
			int genre = 0;
			boolean serial = true;
			if (rawQuery != null && !rawQuery.isEmpty()) {
				for (String part : rawQuery.split("&")) {
					int eq = part.indexOf('=');
					String key = eq < 0 ? part : part.substring(0, eq);
					String value = eq < 0 ? "" : part.substring(eq + 1);
					switch (key) {
						case "period" -> period = value;
						case "genre" -> {
							try {
								genre = Integer.parseInt(value);
							} catch (NumberFormatException e) {
								return null;
							}
						}
						case "serial" -> {
							if (!value.equals("0") && !value.equals("1")) return null;
							serial = value.equals("1");
						}
						//tab=…（ビューアーのタブの印）などは見ない
						default -> { }
					}
				}
			}
			if (!PERIODS.containsKey(period)) return null;
			if (genre != 0 && !GENRES.contains(genre)) return null;
			return new Query(period, genre, serial);
		}

		/** API の URL。gzip で受け取る（API の推奨） */
		URI uri()
		{
			String[] p = PERIODS.get(this.period);
			StringBuilder url = new StringBuilder(ENDPOINT);
			//libtype=2: 数字だけの題・あらすじも文字列で返す（従来のライブラリは引用符を付けない）
			url.append("?out=json&libtype=2&gzip=5&lim=").append(LIMIT);
			url.append("&order=").append(p[0]);
			//題・作品コード・作者・あらすじ・ジャンル・連載か・完結か・話数・期間の点数
			url.append("&of=t-n-w-s-g-nt-e-ga-").append(p[1]);
			if (this.genre != 0) url.append("&genre=").append(this.genre);
			//re＝連載中と完結済みの連載（r は連載中だけで、完結した連載が抜ける）
			if (this.serialOnly) url.append("&type=re");
			return URI.create(url.toString());
		}
	}

	/** API の名前（文言に出す） */
	static final String SITE = "なろうの API";

	private final RankingFetch.Fetcher fetcher;

	private final RankingFetch fetch;

	/** @param upstream サイトへ同時に出す数の枠（カクヨムと同じものを渡す） */
	NarouRanking(java.util.concurrent.Semaphore upstream)
	{
		this(uri -> RankingFetch.httpGet(uri, RankingFetch.DEADLINE, SITE, false), upstream);
	}

	NarouRanking(RankingFetch.Fetcher fetcher)
	{
		this(fetcher, RankingFetch.newUpstream());
	}

	/** @param upstream サイトへ同時に出す数の枠（カクヨムと同じものを渡す） */
	NarouRanking(RankingFetch.Fetcher fetcher, java.util.concurrent.Semaphore upstream)
	{
		this.fetcher = fetcher;
		this.fetch = new RankingFetch(upstream);
	}

	/**
	 * ランキングの JSON（API の応答のまま。先頭は {@code {"allcount":n}}）。
	 * @throws IOException 取れなかった、または配列の形で返らなかった（本文の頭を伝える）
	 */
	byte[] fetch(Query query) throws IOException, InterruptedException
	{
		return fetch(query, System.nanoTime());
	}

	/** 時刻を渡せる版（試験用） */
	byte[] fetch(Query query, long nowNanos) throws IOException, InterruptedException
	{
		return this.fetch.fetch(query, query.uri(), this.fetcher, NarouRanking::checkList, nowNanos);
	}

	/** 配列の形で返ったかだけを見る。API はエラーも 200 で「Error: …」の本文を返す */
	static byte[] checkList(byte[] body) throws IOException
	{
		String head = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).strip();
		if (!head.startsWith("[")) {
			//「Error: …」のような本文。そのまま伝える（直すのはこちらの問い合わせの組み立て）
			throw new IOException("なろうの API が一覧を返しませんでした: " + head);
		}
		return body;
	}
}
