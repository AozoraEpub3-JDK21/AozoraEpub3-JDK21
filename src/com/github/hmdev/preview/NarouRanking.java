package com.github.hmdev.preview;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本棚のランキング（小説家になろう）。internal #11
 *
 * <p>なろう小説 API（{@code novelapi}）を期間のポイント順で引く。公式のランキング API
 * （{@code rankget}）は年間・累計が無く、ジャンルでも連載かどうかでも絞れないので使わない
 * （2026-10-11 の spike）。代わりに順番は、サイトのランキングのページ（集計した時点の写し）と
 * 少し違う（今のポイントで並ぶ）。</p>
 *
 * <p>並びはブックマーク数・新着更新・文字数でも選べ、作品の種類（完結済みだけ など）・文字数・
 * 長期連載停止中・単語・R15／異世界転生／異世界転移の除外でも絞れる（internal #62）。</p>
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

	/**
	 * 並び → 並び順（order）と、並びの理由になる項目（of）。d〜all は期間のポイント、
	 * fav・new・len はブックマーク数・新着更新・文字数の多い順（internal #62）
	 */
	private static final Map<String, String[]> PERIODS = Map.of(
		"d", new String[]{ "dailypoint", "dp" },
		"w", new String[]{ "weeklypoint", "wp" },
		"m", new String[]{ "monthlypoint", "mp" },
		"q", new String[]{ "quarterpoint", "qp" },
		"y", new String[]{ "yearlypoint", "yp" },
		"all", new String[]{ "hyoka", "gp" },
		"fav", new String[]{ "favnovelcnt", "f" },
		"new", new String[]{ "new", "gl" },
		"len", new String[]{ "lengthdesc", "l" });

	/** API のジャンルの番号（0 は総合として扱う。API の「0 未選択」は使わない） */
	static final Set<Integer> GENRES = Set.of(
		101, 102, 201, 202, 301, 302, 303, 304, 305, 306, 307,
		401, 402, 403, 404, 9901, 9902, 9903, 9904, 9999, 9801);

	/** API の大ジャンルの番号（ビューアーは b1 のように頼む）。98 はノンジャンル、99 はその他 */
	static final Set<Integer> BIG_GENRES = Set.of(1, 2, 3, 4, 98, 99);

	/**
	 * 作品の種類（API の type）。空は短編も連載も。
	 * re＝連載中と完結済みの連載（r は連載中だけで、完結した連載が抜ける）
	 */
	static final Set<String> TYPES = Set.of("", "re", "er", "r", "t", "ter");

	/** 文字数（API の length。最小-最大、どちらかは省ける） */
	private static final Pattern LENGTH = Pattern.compile("(0|[1-9][0-9]{0,8})?-(0|[1-9][0-9]{0,8})?");

	/** 単語の長さの上限（コードポイント）。API に上限の定めは無いが、URL を長くしすぎない */
	static final int WORD_MAX = 100;

	/**
	 * 問い合わせ 1 つ。
	 * @param period 並び（d・w・m・q・y・all・fav・new・len）
	 * @param genre 0 は総合、b1 のような b＋番号は大ジャンル
	 * @param type 作品の種類（{@link #TYPES}）
	 * @param length 文字数（{@code 30000-100000} の形）、空は指定なし
	 * @param noStopped 長期連載停止中を除く
	 * @param word 単語（空白で区切ると全部を含むもの。題・あらすじ・キーワード・作者名から探す）、空は指定なし
	 * @param notR15 R15 を除く
	 * @param notTensei 異世界転生を除く
	 * @param notTenni 異世界転移を除く
	 */
	record Query(String period, String genre, String type, String length, boolean noStopped, String word,
		boolean notR15, boolean notTensei, boolean notTenni)
	{
		/** 期間・ジャンル・連載だけかの 3 つだけの問い合わせ（#62 より前の形） */
		Query(String period, int genre, boolean serialOnly)
		{
			this(period, String.valueOf(genre), serialOnly ? "re" : "", "", false, "", false, false, false);
		}

		/**
		 * {@code period=d&genre=201&type=er&length=-30000&stop=1&word=…&notr15=1} を読む。読めなければ null。
		 * {@code serial=1}（連載だけ）・{@code serial=0} も読む。{@code type} があればそちらを使う
		 */
		static Query parse(String rawQuery)
		{
			String period = "d";
			String genre = "0";
			String serialType = "re";
			String type = null;
			String length = "";
			String word = "";
			boolean stop = false, notR15 = false, notTensei = false, notTenni = false;
			if (rawQuery != null && !rawQuery.isEmpty()) {
				for (String part : rawQuery.split("&")) {
					int eq = part.indexOf('=');
					String key = eq < 0 ? part : part.substring(0, eq);
					String value = eq < 0 ? "" : part.substring(eq + 1);
					switch (key) {
						case "period" -> period = value;
						case "genre" -> genre = value;
						case "serial" -> {
							if (!value.equals("0") && !value.equals("1")) return null;
							serialType = value.equals("1") ? "re" : "";
						}
						case "type" -> type = value;
						case "length" -> length = value;
						case "word" -> {
							word = decodeStrictly(value);
							if (word == null) return null;
						}
						case "stop", "notr15", "nottensei", "nottenni" -> {
							if (!value.equals("0") && !value.equals("1")) return null;
							boolean on = value.equals("1");
							switch (key) {
								case "stop" -> stop = on;
								case "notr15" -> notR15 = on;
								case "nottensei" -> notTensei = on;
								default -> notTenni = on;
							}
						}
						//tab=…（ビューアーのタブの印）・site=… などは見ない
						default -> { }
					}
				}
			}
			if (!PERIODS.containsKey(period)) return null;
			if (!genre.equals("0") && !knownGenre(genre)) return null;
			if (type == null) type = serialType;
			if (!TYPES.contains(type)) return null;
			if (!length.isEmpty() && !knownLength(length)) return null;
			//幅の無い空白・ソフトハイフン（Web から貼ると混ざる。語の中に入ると、その語が当たらない）は消す
			word = word.replaceAll("[\\u200B\\u2060\\uFEFF\\u00AD]", "");
			//空白（全角・ノーブレークスペースなどの Unicode の空白、タブ・改行も）を 1 つにそろえる。
			//同じ単語の問い合わせを 1 つに数え（取り直さない）、API の「空白で区切ると全部を含む」に乗せる
			word = word.replaceAll("[\\p{Z}\\s]+", " ").strip();
			if (word.codePointCount(0, word.length()) > WORD_MAX) return null;
			//空白に寄せたあとに残る制御文字は断る
			if (word.chars().anyMatch(Character::isISOControl)) return null;
			return new Query(period, genre, type, length, stop, word, notR15, notTensei, notTenni);
		}

		/** 最小-最大（どちらかは省ける・両方は省けない）。先頭の 0 は通さない（同じ絞り込みを別の問い合わせに数える） */
		private static boolean knownLength(String length)
		{
			if (!LENGTH.matcher(length).matches()) return false;
			int dash = length.indexOf('-');
			String min = length.substring(0, dash), max = length.substring(dash + 1);
			if (min.isEmpty() && max.isEmpty()) return false;
			//0- は指定なしと同じ（同じ絞り込みを別の問い合わせに数える）、-0 は何にも当たらない
			if (max.isEmpty()) return !min.equals("0");
			if (Long.parseLong(max) == 0) return false;
			return min.isEmpty() || Long.parseLong(min) <= Long.parseLong(max);
		}

		/**
		 * %XX と + を UTF-8 として読む。壊れた UTF-8・壊れた %XX は null（URLDecoder は壊れたバイトを U+FFFD に
		 * 置き換えて通す。U+FFFD そのもの（%EF%BF%BD）は正しい UTF-8 なので通す）
		 */
		static String decodeStrictly(String value)
		{
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				if (c == '%') {
					if (i + 2 >= value.length()) return null;
					int hi = Character.digit(value.charAt(i + 1), 16), lo = Character.digit(value.charAt(i + 2), 16);
					if (hi < 0 || lo < 0) return null;
					bytes.write(hi * 16 + lo);
					i += 2;
				} else if (c == '+') {
					bytes.write(' ');
				} else if (c < 0x80) {
					bytes.write(c);
				} else {
					//生の非 ASCII は来ない（ブラウザは %XX にする）
					return null;
				}
			}
			try {
				return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
			} catch (CharacterCodingException e) {
				return null;
			}
		}

		private static boolean knownGenre(String genre)
		{
			boolean big = genre.startsWith("b");
			String digits = big ? genre.substring(1) : genre;
			//+ や先頭の 0 を通さない（同じジャンルが別の問い合わせとして数えられる）
			if (!digits.matches("[1-9][0-9]{0,4}")) return false;
			int code = Integer.parseInt(digits);
			return big ? BIG_GENRES.contains(code) : GENRES.contains(code);
		}

		/** API の URL。gzip で受け取る（API の推奨） */
		URI uri()
		{
			String[] p = PERIODS.get(this.period);
			StringBuilder url = new StringBuilder(ENDPOINT);
			//libtype=2: 数字だけの題・あらすじも文字列で返す（従来のライブラリは引用符を付けない）
			url.append("?out=json&libtype=2&gzip=5&lim=").append(LIMIT);
			url.append("&order=").append(p[0]);
			//題・作品コード・作者・あらすじ・ジャンル・連載か・完結か・話数・並びの理由の項目
			url.append("&of=t-n-w-s-g-nt-e-ga-").append(p[1]);
			if (this.genre.startsWith("b")) url.append("&biggenre=").append(this.genre.substring(1));
			else if (!this.genre.equals("0")) url.append("&genre=").append(this.genre);
			if (!this.length.isEmpty()) url.append("&length=").append(this.length);
			if (this.noStopped) url.append("&stop=1");
			if (this.notR15) url.append("&notr15=1");
			if (this.notTensei) url.append("&nottensei=1");
			if (this.notTenni) url.append("&nottenni=1");
			//題・あらすじ・キーワード・作者名のどれも指定しないと、API は 4 つ全部から探す
			if (!this.word.isEmpty()) url.append("&word=").append(URLEncoder.encode(this.word, StandardCharsets.UTF_8));
			if (!this.type.isEmpty()) url.append("&type=").append(this.type);
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
