package com.github.hmdev.update;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitHub の最新リリースを確認して、更新の有無だけを知らせるユーティリティ。
 *
 * <p>自動更新は行わない。更新があった場合は紹介ページ（GitHub Pages）の URL を返すだけで、
 * ダウンロードと差し替えは利用者に任せる。</p>
 *
 * <p>ネットワーク失敗・タイムアウト・レスポンス異常はすべて例外にせず
 * {@link Result#error()} に文言を詰めて返す。更新確認が落ちても本体の動作を妨げないため。</p>
 */
public final class UpdateChecker
{
	private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);

	/** 最新リリース取得 API。認証なしで叩けるが IP あたり 60 req/h の制限がある（手動操作前提なので十分） */
	public static final String LATEST_RELEASE_API =
		"https://api.github.com/repos/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest";

	/** 案内先。リリース一覧ではなく紹介ページ（導入手順つき）を出す */
	public static final String DOWNLOAD_PAGE_URL =
		"https://aozoraepub3-jdk21.github.io/AozoraEpub3-JDK21/";

	/** 接続タイムアウト */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	/** リクエスト全体のタイムアウト */
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	/** {@code "tag_name": "v1.5.2-jdk21"} を拾う。GitHub の JSON は改行位置が変わりうるので空白を緩く見る */
	private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

	/** 先頭から数字の並びを取り出す。"2b1" → 2 のように接尾辞つきでも比較できるようにする */
	private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

	private UpdateChecker() {}

	/**
	 * 更新確認の結果。
	 *
	 * @param currentVersion 実行中のバージョン
	 * @param latestVersion 取得できた最新タグ（失敗時は null）
	 * @param updateAvailable 最新の方が新しければ true
	 * @param downloadPageUrl 案内する紹介ページ URL
	 * @param error 確認に失敗した理由（成功時は null）
	 */
	public record Result(
		String currentVersion,
		String latestVersion,
		boolean updateAvailable,
		String downloadPageUrl,
		String error)
	{
		/** 確認自体に成功したか（更新の有無とは別） */
		public boolean isSuccess() { return this.error == null; }
	}

	/**
	 * 最新リリースを問い合わせる。呼び出しスレッドをブロックするので、
	 * GUI からは EDT 以外（SwingWorker など）で呼ぶこと。
	 *
	 * @param currentVersion 実行中のバージョン（例 {@code "1.5.2-jdk21"}）
	 */
	public static Result check(String currentVersion)
	{
		return check(currentVersion, LATEST_RELEASE_API);
	}

	/** テストから任意のエンドポイントを差せるようにした版 */
	static Result check(String currentVersion, String apiUrl)
	{
		//毎回作って毎回閉じる。閉じないとセレクタスレッドが残り、ボタンを押すたびに増える
		try (HttpClient client = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL)
				//HttpClient は既定でプロキシを見ない。社内プロキシ配下でも
				//-Dhttp.proxyHost 等が効くように既定の ProxySelector を渡す
				.proxy(ProxySelector.getDefault())
				.build()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
				.header("Accept", "application/vnd.github+json")
				//GitHub API は User-Agent 必須。無いと 403 で弾かれる
				.header("User-Agent", "AozoraEpub3/" + currentVersion)
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return failure(currentVersion, describeHttpFailure(response));
			}
			String latest = parseTagName(response.body());
			if (latest == null) {
				return failure(currentVersion, "tag_name not found");
			}
			boolean newer = compareVersions(latest, currentVersion) > 0;
			return new Result(currentVersion, latest, newer, DOWNLOAD_PAGE_URL, null);
		} catch (IOException e) {
			logger.debug("更新確認の通信に失敗", e);
			return failure(currentVersion, e.getClass().getSimpleName() + ": " + e.getMessage());
		} catch (InterruptedException e) {
			//割り込みステータスを復元してから失敗として返す
			Thread.currentThread().interrupt();
			return failure(currentVersion, "interrupted");
		} catch (RuntimeException e) {
			logger.debug("更新確認に失敗", e);
			return failure(currentVersion, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static Result failure(String currentVersion, String error)
	{
		return new Result(currentVersion, null, false, DOWNLOAD_PAGE_URL, error);
	}

	/** レスポンスから最新タグを取り出す。見つからなければ null */
	static String parseTagName(String body)
	{
		if (body == null) return null;
		Matcher matcher = TAG_NAME.matcher(body);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * 200 以外のときの理由。レート制限は「時間を置けば直る」ので通信障害と区別できるようにする。
	 * 未認証の GitHub API は IP あたり 1 時間 60 回で、超えると残数 0 の 403 が返る。
	 */
	static String describeHttpFailure(HttpResponse<String> response)
	{
		int status = response.statusCode();
		String remaining = response.headers().firstValue("x-ratelimit-remaining").orElse(null);
		boolean rateLimited = (status == 403 || status == 429) && "0".equals(remaining);
		return rateLimited
			? "HTTP " + status + " (GitHub API rate limit exceeded)"
			: "HTTP " + status;
	}

	/**
	 * バージョン文字列を比較する。
	 * {@code "v1.5.2-jdk21"} のような接頭辞 {@code v} とビルド接尾辞は落としてから
	 * ドット区切りの数値として比較し、桁数が違う場合は足りない側を 0 とみなす。
	 *
	 * @return a が新しければ正、同じなら 0、a が古ければ負
	 */
	public static int compareVersions(String a, String b)
	{
		String[] left = normalize(a).split("\\.");
		String[] right = normalize(b).split("\\.");
		int length = Math.max(left.length, right.length);
		for (int i = 0; i < length; i++) {
			int l = i < left.length ? toNumber(left[i]) : 0;
			int r = i < right.length ? toNumber(right[i]) : 0;
			if (l != r) return l < r ? -1 : 1;
		}
		return 0;
	}

	/**
	 * {@code "v1.5.2-jdk21"} → {@code "1.5.2"}。null / 空文字は {@code "0"} 扱い。
	 *
	 * <p>最初の {@code -} 以降を落とすので {@code "1.6.0-rc1"} は {@code "1.6.0"} と同値になる。
	 * {@code /releases/latest} は prerelease を返さないため現状は問題にならないが、
	 * prerelease を比較対象にするなら別の扱いが要る。</p>
	 */
	static String normalize(String version)
	{
		if (version == null) return "0";
		String v = version.trim().toLowerCase(Locale.ROOT);
		if (v.startsWith("v")) v = v.substring(1);
		//"-jdk21" や "-beta1" のようなビルド識別子は比較対象から外す
		int dash = v.indexOf('-');
		if (dash >= 0) v = v.substring(0, dash);
		return v.isEmpty() ? "0" : v;
	}

	/** 数値部分だけを取り出す。数字で始まらない要素は 0 */
	private static int toNumber(String part)
	{
		Matcher matcher = LEADING_DIGITS.matcher(part.trim());
		if (!matcher.find()) return 0;
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			//桁あふれ。実在しない値だが 0 に倒して比較を続ける
			return 0;
		}
	}
}
