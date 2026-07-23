package com.github.hmdev.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * URL からのストリーム取得ユーティリティ。
 *
 * <p>{@code URL.openStream()} は接続・読み込みともタイムアウト無制限のため、
 * 応答しないサーバに当たると変換スレッドが永久にブロックする。
 * このクラス経由で開くことでタイムアウトを一律に適用する。
 *
 * <p>HttpClient に移行済みの {@code WebAozoraConverter} / {@code NarouApiClient} と
 * 異なり {@link URLConnection} を使うのは以下の理由による。
 * <ul>
 * <li>{@code file:} スキームでも動作する（.url ショートカット経由で file:// が渡り得る）</li>
 * <li>読み込みタイムアウトが「1 回の read のブロック上限」なので、
 *     低速でもデータが流れ続ける限り大きなファイルの取得が打ち切られない
 *     （HttpClient の request timeout はリクエスト全体の上限）</li>
 * <li>writer / image パッケージから web パッケージへの依存を作らずに済む</li>
 * </ul>
 */
public final class NetUtils
{
	private NetUtils() {}

	/** 接続タイムアウト。WebAozoraConverter / NarouApiClient の connectTimeout と同値 */
	public static final int CONNECT_TIMEOUT_MILLIS = 10_000;

	/** 読み込みタイムアウト。上記の request timeout と同値だが、
	 * こちらは read 1 回あたりのブロック上限である点が異なる */
	public static final int READ_TIMEOUT_MILLIS = 30_000;

	/** タイムアウトを設定して URL のストリームを開く */
	public static InputStream openStream(URL url) throws IOException
	{
		return openStream(url, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
	}

	/** タイムアウト値を指定して URL のストリームを開く（テスト用） */
	static InputStream openStream(URL url, int connectMillis, int readMillis) throws IOException
	{
		URLConnection conn = url.openConnection();
		conn.setConnectTimeout(connectMillis);
		conn.setReadTimeout(readMillis);
		return conn.getInputStream();
	}
}
