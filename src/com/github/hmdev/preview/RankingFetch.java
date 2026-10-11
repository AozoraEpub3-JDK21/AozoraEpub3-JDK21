package com.github.hmdev.preview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

/**
 * 本棚のランキングを取りに行く足回り（なろう・カクヨムで共有する）。
 *
 * <p>同じ問い合わせは {@link #CACHE_TTL} の間は取り直さない。サイトへ同時に出すのは
 * {@link #MAX_UPSTREAM} 本まで（サイトをまたいで数える。掴むのはプレビューの同じスレッドなので）。</p>
 */
final class RankingFetch
{
	/** 同じ問い合わせを取り直さない間 */
	static final Duration CACHE_TTL = Duration.ofMinutes(10);

	/** 覚えておく問い合わせの数（サイトごと。使う組だけ残れば足りる） */
	static final int CACHE_SIZE = 32;

	/**
	 * サイトへ同時に出す問い合わせの数。プレビューの HTTP のスレッドは 4 本で、問い合わせの間は 1 本を掴む。
	 * 2 本を残して、本・仕事の状態・heartbeat が待たないようにする（PR #127 の codex）
	 */
	static final int MAX_UPSTREAM = 2;

	/** 受け取る応答の上限（なろうの 100 作で 200KB、カクヨムのページで 1.3MB ほど） */
	static final int MAX_BYTES = 4 * 1024 * 1024;

	/** 頼んでから本文を受け取り終えるまでの上限 */
	static final Duration DEADLINE = Duration.ofSeconds(30);

	/** 同時に出せる数を超えた（覚えている答えも無い） */
	static final class BusyException extends IOException
	{
		BusyException()
		{
			super("ランキングの問い合わせが混んでいます。少ししてからもう一度選んでください");
		}
	}

	/** サイトから本文を取るもの（試験では差し替える） */
	interface Fetcher
	{
		/** gzip を解いた本文。200 以外は IOException */
		byte[] get(URI uri) throws IOException, InterruptedException;
	}

	/** 取ってきた本文を確かめ、ビューアーへ渡す形にする。渡せなければ IOException */
	interface Check
	{
		byte[] apply(byte[] body) throws IOException;
	}

	/** 取り直さないで返す応答 */
	private record Cached(long fetchedNanos, byte[] body) { }

	private final Semaphore upstream;

	/** 古い順に捨てる。同期は自分で取る */
	private final LinkedHashMap<Object, Cached> cache = new LinkedHashMap<>(16, 0.75f, true);

	/** @param upstream サイトへ同時に出す数の枠（サイトどうしで同じものを渡す） */
	RankingFetch(Semaphore upstream)
	{
		this.upstream = upstream;
	}

	static Semaphore newUpstream()
	{
		return new Semaphore(MAX_UPSTREAM);
	}

	/**
	 * 覚えている答えか、取ってきて check を通したもの。
	 * check を通らなかった本文は覚えない（次に選んだときに取り直す）
	 */
	byte[] fetch(Object key, URI uri, Fetcher fetcher, Check check, long nowNanos) throws IOException, InterruptedException
	{
		synchronized (this.cache) {
			Cached hit = this.cache.get(key);
			if (hit != null && nowNanos - hit.fetchedNanos < CACHE_TTL.toNanos()) return hit.body;
		}
		//覚えている答えは混んでいても返す。取りに行くのだけを数で抑える
		if (!this.upstream.tryAcquire()) throw new BusyException();
		byte[] raw;
		try {
			raw = fetcher.get(uri);
		} finally {
			this.upstream.release();
		}
		byte[] body = check.apply(raw);
		synchronized (this.cache) {
			this.cache.put(key, new Cached(nowNanos, body));
			while (this.cache.size() > CACHE_SIZE) {
				this.cache.remove(this.cache.keySet().iterator().next());
			}
		}
		return body;
	}

	/**
	 * 本文まで受け取る。deadline は試験で短くする。
	 * @param site 文言に出すサイトの名前（「なろうの API」など）
	 * @param acceptGzip Accept-Encoding: gzip を付ける（なろうは gzip=5 で gzip のファイルを返すので付けない）
	 */
	static byte[] httpGet(URI uri, Duration deadline, String site, boolean acceptGzip) throws IOException, InterruptedException
	{
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			//既定ではプロキシを見ない。-Dhttp.proxyHost 等が効くように渡す
			.proxy(ProxySelector.getDefault())
			.build();
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.header("User-Agent", "AozoraEpub3")
				.timeout(deadline)
				.GET();
			if (acceptGzip) builder.header("Accept-Encoding", "gzip");
			HttpRequest request = builder.build();
			//request の timeout は見出しが届くまでしか効かない。本文の途中で止まると、
			//プレビューの 4 本しかないスレッドを掴んだままになる（PR #127 の codex P2）。
			//本文を受け取り終えるまでを 1 つの期限で囲む
			//長さを名乗る応答は、上限を超えていたら本文を読まずにその場で断る（ofByteArray は全部を溜めてから返す）。
			//長さを名乗らない応答は、期限までしか受け取らない
			HttpResponse.BodyHandler<byte[]> handler = info -> {
				long length = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
				if (length > MAX_BYTES) throw new java.io.UncheckedIOException(new IOException(site + "の応答が大きすぎます"));
				return HttpResponse.BodySubscribers.ofByteArray();
			};
			CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, handler);
			HttpResponse<byte[]> response;
			try {
				response = pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				pending.cancel(true);
				throw new IOException(site + "が " + deadline.toSeconds() + " 秒で応えませんでした");
			} catch (ExecutionException e) {
				//つながらないときの ConnectException は文言が null のことがある。画面に「null」と出さない
				Throwable cause = e.getCause();
				if (cause instanceof java.io.UncheckedIOException unchecked) throw unchecked.getCause();
				String why = (cause != null && cause.getMessage() != null) ? cause.getMessage()
					: (cause != null ? cause.getClass().getSimpleName() : "不明");
				throw new IOException(site + "につながりません (" + why + ")", cause);
			}
			if (response.statusCode() != 200) {
				throw new IOException(site + "が HTTP " + response.statusCode() + " を返しました");
			}
			byte[] raw = response.body();
			if (raw.length > MAX_BYTES) throw new IOException(site + "の応答が大きすぎます");
			return gunzip(raw, site);
		} finally {
			//close() は走っている要求の終わりを待つので、期限切れのときに止まってしまう。待たずに閉じる
			client.shutdownNow();
		}
	}

	/** gzip なら解く。gzip でなければそのまま */
	static byte[] gunzip(byte[] raw, String site) throws IOException
	{
		if (raw.length < 2 || (raw[0] & 0xff) != 0x1f || (raw[1] & 0xff) != 0x8b) return raw;
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
			byte[] body = in.readNBytes(MAX_BYTES + 1);
			if (body.length > MAX_BYTES) throw new IOException(site + "の応答が大きすぎます");
			return body;
		}
	}
}
