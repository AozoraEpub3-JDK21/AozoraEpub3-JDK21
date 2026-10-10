package com.github.hmdev.preview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

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
 * 同じ問い合わせは {@link #CACHE_TTL} の間は取り直さない。</p>
 */
final class NarouRanking
{
	static final String ENDPOINT = "https://api.syosetu.com/novelapi/api/";

	/** 1 回に出す作品の数（API の上限は 500） */
	static final int LIMIT = 100;

	/** 同じ問い合わせを取り直さない間 */
	static final Duration CACHE_TTL = Duration.ofMinutes(10);

	/** 覚えておく問い合わせの数（期間 6 × ジャンル 22 × 2 より少なく、使う組だけ残れば足りる） */
	static final int CACHE_SIZE = 32;

	/** 受け取る応答の上限（100 作のあらすじで 200KB ほど） */
	static final int MAX_BYTES = 4 * 1024 * 1024;

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
			url.append("?out=json&gzip=5&lim=").append(LIMIT);
			url.append("&order=").append(p[0]);
			//題・作品コード・作者・あらすじ・ジャンル・連載か・完結か・話数・期間の点数
			url.append("&of=t-n-w-s-g-nt-e-ga-").append(p[1]);
			if (this.genre != 0) url.append("&genre=").append(this.genre);
			//re＝連載中と完結済みの連載（r は連載中だけで、完結した連載が抜ける）
			if (this.serialOnly) url.append("&type=re");
			return URI.create(url.toString());
		}
	}

	/** API から本文を取るもの（試験では差し替える） */
	interface Fetcher
	{
		/** gzip を解いた本文。200 以外は IOException */
		byte[] get(URI uri) throws IOException, InterruptedException;
	}

	/** 取り直さないで返す応答 */
	private record Cached(long fetchedNanos, byte[] body) { }

	private final Fetcher fetcher;

	/** 古い順に捨てる。同期は自分で取る */
	private final LinkedHashMap<Query, Cached> cache = new LinkedHashMap<>(16, 0.75f, true);

	NarouRanking()
	{
		this(NarouRanking::httpGet);
	}

	NarouRanking(Fetcher fetcher)
	{
		this.fetcher = fetcher;
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
		synchronized (this.cache) {
			Cached hit = this.cache.get(query);
			if (hit != null && nowNanos - hit.fetchedNanos < CACHE_TTL.toNanos()) return hit.body;
		}
		byte[] body = this.fetcher.get(query.uri());
		String head = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).strip();
		if (!head.startsWith("[")) {
			//「Error: …」のような本文。そのまま伝える（直すのはこちらの問い合わせの組み立て）
			throw new IOException("なろうの API が一覧を返しませんでした: " + head);
		}
		synchronized (this.cache) {
			this.cache.put(query, new Cached(nowNanos, body));
			while (this.cache.size() > CACHE_SIZE) {
				this.cache.remove(this.cache.keySet().iterator().next());
			}
		}
		return body;
	}

	private static byte[] httpGet(URI uri) throws IOException, InterruptedException
	{
		//毎回作って閉じる（UpdateChecker と同じ。閉じないとセレクタのスレッドが残る）
		try (HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				//既定ではプロキシを見ない。-Dhttp.proxyHost 等が効くように渡す
				.proxy(ProxySelector.getDefault())
				.build()) {
			HttpRequest request = HttpRequest.newBuilder(uri)
				.header("User-Agent", "AozoraEpub3")
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				byte[] raw = in.readNBytes(MAX_BYTES + 1);
				if (response.statusCode() != 200) {
					throw new IOException("なろうの API が HTTP " + response.statusCode() + " を返しました");
				}
				if (raw.length > MAX_BYTES) throw new IOException("なろうの API の応答が大きすぎます");
				return gunzip(raw);
			}
		}
	}

	/** gzip なら解く（gzip=5 で頼むと gzip のファイルが返る）。gzip でなければそのまま */
	static byte[] gunzip(byte[] raw) throws IOException
	{
		if (raw.length < 2 || (raw[0] & 0xff) != 0x1f || (raw[1] & 0xff) != 0x8b) return raw;
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
			byte[] body = in.readNBytes(MAX_BYTES + 1);
			if (body.length > MAX_BYTES) throw new IOException("なろうの API の応答が大きすぎます");
			return body;
		}
	}
}
