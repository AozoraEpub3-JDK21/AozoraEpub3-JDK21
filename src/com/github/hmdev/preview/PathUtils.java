package com.github.hmdev.preview;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * EPUB 内パス (常にスラッシュ区切りの相対パス) を扱うユーティリティ。
 *
 * <p>OS のファイルシステムに触れる前に文字列として正規化することで、
 * {@code ../} を含むパスがルート外へ抜けることを検知できるようにする。</p>
 *
 * <h2>プラットフォーム間の保証範囲</h2>
 * <p><b>「展開先ルートの外へ出さない」という保証はこのクラスが一次的に負い、Windows / macOS / Linux で
 * 同一に判定する</b> ({@code ..} とドライブ修飾を文字列段階で弾く)。OS のパス解決に判定を委ねない。</p>
 *
 * <p>一方、<b>ファイル名を表現できるかどうかは OS 依存で、意図的に揃えていない</b>。
 * W3C EPUB 3.3 OCF はファイル名に {@code " * : < > ? \ |}・末尾のドット・制御文字を
 * 使ってはならないと定めているが、Linux / macOS はそれらを実際には受け付けるため、
 * 仕様違反の EPUB でも Linux では表示できる。これを全 OS で一律に拒否すると
 * 「今まで読めていた本が読めなくなる」ため、次の扱いとする。</p>
 * <ul>
 *   <li>その OS で表現できない名前 &rarr; {@link #resolveInside} が null。該当ファイルのみ欠落 (Windows で発生)</li>
 *   <li>大文字小文字を区別しないファイルシステム (Windows / 既定の APFS) では
 *       {@code Text.xhtml} と {@code text.xhtml} が衝突する。検出は {@code EpubExtractor} 側で行う</li>
 *   <li>Unicode 正規化 (NFC/NFD) の揺れは吸収しない。href と ZIP エントリ名で正規化形が異なる EPUB は
 *       Linux で 404 になりうる (macOS は FS 側が正規化非依存で照合するため引ける)</li>
 * </ul>
 */
final class PathUtils
{
	private PathUtils() {}

	/**
	 * ルートより上へ抜けようとするパスか。
	 *
	 * <p>{@link #normalizeRelative} は「危険なパス」と「実体の無いパス ({@code ./} など)」の
	 * どちらも null にするため、拒否すべきかどうかの判断にはこちらを使う。</p>
	 */
	static boolean escapesRoot(String path)
	{
		if (path == null) return false;
		int depth = 0;
		for (String segment : path.replace('\\', '/').split("/")) {
			if (segment.isEmpty() || segment.equals(".")) continue;
			if (segment.equals("..")) {
				if (depth == 0) return true;
				depth--;
				continue;
			}
			// ドライブ修飾が意味を持つのは解決後の先頭に来る場合だけ。
			// ネストした "OPS/a:b.xhtml" はルート外に出られないので危険ではない
			// (ここで true にすると EPUB 全体を拒否してしまう)
			if (depth == 0 && isDriveQualified(segment)) return true;
			depth++;
		}
		return false;
	}

	/**
	 * {@code C:} や {@code C:Windows} のようなドライブ修飾セグメントか。
	 *
	 * <p>Windows では {@code Path#resolve} がこれを絶対パスとして扱うためルート外を指すが、
	 * Linux / macOS では {@code :} を含むだけの普通のファイル名として通ってしまう。
	 * 本アプリはマルチプラットフォーム対応なので、OS のパス解決に判定を委ねず
	 * 文字列の段階で弾いて挙動を揃える。</p>
	 */
	private static boolean isDriveQualified(String segment)
	{
		if (segment.length() < 2 || segment.charAt(1) != ':') return false;
		char drive = segment.charAt(0);
		return (drive >= 'A' && drive <= 'Z') || (drive >= 'a' && drive <= 'z');
	}

	/**
	 * 相対パスを正規化する。{@code .} を除去し {@code ..} を解決する。
	 * ルートより上に出る場合、ドライブ修飾を含む場合、解決結果が空になる場合は null を返す。
	 */
	static String normalizeRelative(String path)
	{
		if (path == null) return null;
		String cleaned = path.replace('\\', '/');
		if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
		Deque<String> stack = new ArrayDeque<>();
		for (String segment : cleaned.split("/")) {
			if (segment.isEmpty() || segment.equals(".")) continue;
			if (segment.equals("..")) {
				if (stack.isEmpty()) return null; // ルートより上へ抜けようとしている
				stack.removeLast();
				continue;
			}
			// "C:/Windows/win.ini" のように解決後の先頭へ来るものだけを弾く。
			// "OPS/../C:/x" も ".." の解決で先頭に来るのでここで捕まる
			if (stack.isEmpty() && isDriveQualified(segment)) return null;
			stack.addLast(segment);
		}
		if (stack.isEmpty()) return null;
		return String.join("/", stack);
	}

	/**
	 * EPUB 内の相対パスを展開先の実パスへ解決する。
	 * ルート外に出る場合と、この OS のファイル名として表現できない場合は null。
	 *
	 * <p>{@code C:/Windows/win.ini} のようなドライブ付きの文字列は
	 * {@link #normalizeRelative} が弾くため、Windows / macOS / Linux のいずれでも null になる
	 * (OS の {@code resolve} 任せにすると Windows だけルート外、他 OS ではただのファイル名、と挙動が割れる)。
	 * 細工した {@code container.xml} の {@code full-path} や manifest の href で
	 * ホスト上のファイルを読まされないよう、<b>ファイルを開く全ての箇所でこれを通す</b>。</p>
	 */
	static Path resolveInside(Path root, String relativePath)
	{
		if (root == null) return null;
		String normalized = normalizeRelative(relativePath);
		if (normalized == null) return null;
		Path base = root.normalize();
		Path target;
		try {
			target = base.resolve(normalized).normalize();
		} catch (java.nio.file.InvalidPathException e) {
			// Windows では ':' '?' '*' 等を含む名前が InvalidPathException になる (Linux/macOS では合法)。
			// 非チェック例外なので、ここで飲まないと呼び出し側の catch(IOException) をすり抜けて
			// プレビューが落ちる。この OS で開けないファイル = 見つからない、として扱う
			return null;
		}
		return target.startsWith(base) ? target : null;
	}

	/**
	 * href からクエリとフラグメントを落とす。
	 * URI では {@code ?} が {@code #} より前に来るので、先に現れた方で切る。
	 * ファイル名に含まれる {@code ?} は URI 上 {@code %3F} になるため誤爆しない。
	 */
	static String stripQueryAndFragment(String href)
	{
		if (href == null) return null;
		int cut = href.length();
		int hash = href.indexOf('#');
		if (hash >= 0) cut = hash;
		int query = href.indexOf('?');
		if (query >= 0 && query < cut) cut = query;
		return href.substring(0, cut);
	}

	/**
	 * base (ドキュメントのパス) を起点に相対 href を解決し、
	 * EPUB ルートからの相対パスを返す。解決できなければ null。
	 *
	 * <p>OPF / nav / ncx の href は URI なので、空白を含むファイル名は
	 * {@code images/cover%20art.jpg} のようにパーセントエスケープされている。
	 * ZIP エントリ名は {@code images/cover art.jpg} なので、
	 * デコードしないとファイルが見つからず 404 になる。</p>
	 */
	static String resolveAgainst(String basePath, String href)
	{
		if (href == null || href.isEmpty()) return null;
		// クエリ・フラグメントの区切りはエスケープされないので、デコード前に切り離す
		String cleaned = stripQueryAndFragment(href);
		if (cleaned.isEmpty()) return null;
		cleaned = decodeUri(cleaned);
		if (cleaned.startsWith("/")) return normalizeRelative(cleaned);
		int slash = (basePath == null) ? -1 : basePath.lastIndexOf('/');
		String baseDir = (slash < 0) ? "" : basePath.substring(0, slash);
		return normalizeRelative(baseDir.isEmpty() ? cleaned : baseDir + "/" + cleaned);
	}

	/**
	 * URI のパーセントエスケープをデコードする (EPUB 内 href 用の寛容版)。
	 * エスケープが壊れていた場合は、リテラルの {@code %} を含むファイル名とみなして
	 * 入力をそのまま返す。
	 */
	static String decodeUri(String value)
	{
		String decoded = decodeUriStrict(value);
		return (decoded == null) ? value : decoded;
	}

	/**
	 * URI のパーセントエスケープをデコードする (HTTP リクエストパス用の厳格版)。
	 *
	 * <p>{@code java.net.URLDecoder} は application/x-www-form-urlencoded 用で
	 * {@code +} を空白に変換してしまうため使わない。
	 * ファイル名に {@code +} を含む EPUB (Web 小説の画像で起こりうる) を壊さないこと。</p>
	 *
	 * @return デコード結果。エスケープが壊れていれば null
	 */
	static String decodeUriStrict(String value)
	{
		if (value == null) return null;
		if (value.indexOf('%') < 0) return value;
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(value.length());
		int i = 0;
		while (i < value.length()) {
			if (value.charAt(i) == '%') {
				if (i + 2 >= value.length()) return null;
				int hi = Character.digit(value.charAt(i + 1), 16);
				int lo = Character.digit(value.charAt(i + 2), 16);
				if (hi < 0 || lo < 0) return null;
				buf.write((hi << 4) + lo);
				i += 3;
				continue;
			}
			// リテラル部分は「まとめて」UTF-8 化する。
			// 1 文字ずつ変換すると、絵文字などのサロゲートペアが片割れずつ処理されて
			// '?' に化け、ZIP エントリ名と一致しなくなる
			int start = i;
			while (i < value.length() && value.charAt(i) != '%') i++;
			buf.writeBytes(value.substring(start, i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
	}

	/**
	 * href のフラグメント部分を返す。無ければ null。
	 * パス部と同様に URI エスケープを解く (HTML の id は生の文字列で照合されるため)。
	 */
	static String fragmentOf(String href)
	{
		if (href == null) return null;
		int hash = href.indexOf('#');
		if (hash < 0 || hash == href.length() - 1) return null;
		return decodeUri(href.substring(hash + 1));
	}

	/** 拡張子 (小文字、ドット無し) を返す。無ければ空文字 */
	static String extensionOf(String path)
	{
		if (path == null) return "";
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		if (dot < 0 || dot < slash) return "";
		return path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
	}
}
