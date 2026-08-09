package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本棚のインデックスを永続化する。
 *
 * <p>本棚を開くたびに全冊の ZIP を開き直すのは無駄なので、
 * パス・サイズ・更新時刻・書誌・表紙の位置を保存しておき、
 * <b>サイズと更新時刻が一致する本は再パースを省く</b>。
 * あくまでキャッシュであり、壊れていても捨てて作り直せばよい。</p>
 *
 * <h2>なぜ JSON ではなく行指向のテキストなのか</h2>
 *
 * <p>計画書 (docs/epub-preview-plan.md) では {@code preview-library.json} としていたが、
 * <b>このプロジェクトには JSON パーサが無い</b>。{@link Json} は
 * 「追加依存ゼロ」の方針のもとで書かれた<b>出力専用</b>のヘルパで、読む側は持っていない。
 * 読み書き両方が要るキャッシュのために JSON ライブラリを足すのは方針と衝突し、
 * 自前パーサを書けば「壊れた入力で誤解釈する」リスクを新たに抱える。</p>
 *
 * <p>キャッシュは再生成できるので、曖昧さの無い行指向の形式を選んだ。
 * 1 行 1 冊のタブ区切りで、値に含まれうるタブ・改行・バックスラッシュだけを
 * エスケープする。列数が合わない行は黙って捨てる。</p>
 */
public class LibraryIndexCache
{
	private static final Logger logger = LoggerFactory.getLogger(LibraryIndexCache.class);

	/** 形式が変わったら上げる。一致しない世代のファイルは読まずに捨てる */
	static final String HEADER = "#aozoraepub3-preview-library\t1";

	/** 1 行あたりの列数 (path / size / modified / title / creator / coverEntry) */
	private static final int COLUMNS = 6;

	/**
	 * 保存する上限行数。使わなくなった本の記録が無限に積み上がらないようにする。
	 * {@link LibraryScanner#MAX_BOOKS} より大きく取り、複数フォルダを行き来しても
	 * 直近のものが落ちないようにしている。
	 */
	static final int MAX_ENTRIES = 5000;

	/**
	 * キャッシュファイルの上限バイト数。<b>読み書きの両方に効かせる。</b>
	 *
	 * <p>読み: これを超えるファイルは (手で壊された・別物が置かれた等) 丸ごと捨てる。
	 * 読んでから件数で切るのでは、その前に巨大なファイルを全部メモリに載せてしまう。</p>
	 *
	 * <p>書き: 件数だけで縛ると
	 * {@link #MAX_ENTRIES} × {@link LibraryScanner#MAX_FIELD_CHARS} × 3 フィールドで
	 * 最悪 20MB を超え、<b>書いた直後の自分のファイルを読み捨てる</b>ことになる。
	 * 予算を超える分は古い方から落として、書ける形にしてから保存する。</p>
	 */
	static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

	private final Path file;
	/** キーは絶対パスの文字列。読み込み順を保って保存する */
	private final Map<String, LibraryEntry> entries = new LinkedHashMap<>();

	public LibraryIndexCache()
	{
		// アプリの配置先が読み取り専用でも書けるようホーム配下に置く
		// (PreviewSettingsStore と同じ考え方)
		this(Path.of(System.getProperty("user.home", "."), ".aozoraepub3", "preview-library.tsv"));
	}

	LibraryIndexCache(Path file)
	{
		this.file = file;
	}

	Path getFile() { return this.file; }

	/**
	 * 保存済みインデックスを読み込む。読めなければ空のまま始める。
	 *
	 * <p>プレビューサーバは複数スレッドで動く ({@code PreviewServer} は 4 本の
	 * プールを持つ) ため、本クラスのメソッドはすべて {@code synchronized} にしてある。</p>
	 */
	public synchronized void load()
	{
		this.entries.clear();
		try {
			if (!Files.isRegularFile(this.file)) return;
			if (Files.size(this.file) > MAX_FILE_BYTES) {
				logger.debug("本棚キャッシュが大きすぎるため読み捨てます: {}", this.file);
				return;
			}
			List<String> lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
			if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
				logger.debug("本棚キャッシュの形式が古いため読み捨てます: {}", this.file);
				return;
			}
			for (String line : lines.subList(1, lines.size())) {
				LibraryEntry entry = parseLine(line);
				if (entry != null) this.entries.put(key(entry.file()), entry);
			}
			// 上限を超える行数のファイルを読んだ場合もメモリ上の件数を守る
			// (save 側だけで切ると、読み込み直後だけ上限を超えた状態になる)
			evictOverflow();
		} catch (IOException | RuntimeException e) {
			/* 意図的: キャッシュは再生成できる。読めなければ空で続行する */
			logger.debug("本棚キャッシュを読み込めませんでした: {}", this.file, e);
			this.entries.clear();
		}
	}

	/** 指定パスの記録を返す。無ければ null (呼び出し側でサイズ・更新時刻を照合すること) */
	public synchronized LibraryEntry get(Path file)
	{
		return this.entries.get(key(file));
	}

	/**
	 * スキャン結果でインデックスを置き換えて保存する。
	 *
	 * <p>他フォルダの記録は残す (本棚を切り替えるたびに互いのキャッシュを
	 * 捨て合うことになるため)。</p>
	 */
	public synchronized void update(Collection<LibraryEntry> scanned)
	{
		for (LibraryEntry entry : scanned) {
			// 再挿入で末尾に移すため、いったん外す。
			// 上限で溢れさせるのは「最近見ていないもの」からにしたい
			String key = key(entry.file());
			this.entries.remove(key);
			this.entries.put(key, entry);
		}
		save();
	}

	/**
	 * 上限を超えた分を古い方 (LinkedHashMap の先頭側 = 最近見ていないもの) から捨てる。
	 *
	 * <p><b>ファイルに書く分だけでなくメモリ上の {@link #entries} からも消す。</b>
	 * 片方だけ切り詰めると、GUI を起動したまま本棚をいくつも渡り歩いたときに
	 * マップだけが際限なく育ち、「捨てたはずの記録」が再利用され続ける。</p>
	 */
	private void evictOverflow()
	{
		if (this.entries.size() <= MAX_ENTRIES) return;
		int excess = this.entries.size() - MAX_ENTRIES;
		var iterator = this.entries.keySet().iterator();
		for (int i = 0; i < excess && iterator.hasNext(); i++) {
			iterator.next();
			iterator.remove();
		}
	}

	/** 現在の内容をファイルへ書き出す */
	synchronized void save()
	{
		evictOverflow();
		evictOverBudget();
		try {
			Path parent = this.file.getParent();
			if (parent != null) Files.createDirectories(parent);

			StringBuilder buf = new StringBuilder(this.entries.size() * 128 + 64);
			buf.append(HEADER).append('\n');
			for (LibraryEntry entry : this.entries.values()) buf.append(formatLine(entry)).append('\n');
			Files.writeString(this.file, buf.toString(), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			/* 意図的: 保存できなくても本棚は毎回スキャンすれば動く */
			logger.debug("本棚キャッシュを保存できませんでした: {}", this.file, e);
		}
	}

	/**
	 * ファイルサイズの予算を超える分を古い方から捨てる。
	 * 件数の上限だけでは、長い書名が並んだときに
	 * 「書いた直後の自分のファイルを次回 {@link #load} で読み捨てる」ことになる。
	 */
	private void evictOverBudget()
	{
		long budget = MAX_FILE_BYTES - HEADER.getBytes(StandardCharsets.UTF_8).length - 1;
		long total = 0;
		// 新しい方 (末尾) から詰めて、予算に収まる件数を決める
		List<LibraryEntry> values = new ArrayList<>(this.entries.values());
		int keepFrom = values.size();
		for (int i = values.size() - 1; i >= 0; i--) {
			long line = formatLine(values.get(i)).getBytes(StandardCharsets.UTF_8).length + 1L;
			if (total + line > budget) break;
			total += line;
			keepFrom = i;
		}
		if (keepFrom == 0) return;
		logger.debug("本棚キャッシュがサイズ上限に達したため古い {} 件を捨てます", keepFrom);
		for (int i = 0; i < keepFrom; i++) this.entries.remove(key(values.get(i).file()));
	}

	/** 現在メモリに保持している件数 (上限の検証用) */
	synchronized int size() { return this.entries.size(); }

	// ------------------------------------------------------------------

	private static String key(Path file)
	{
		return file.toAbsolutePath().normalize().toString();
	}

	static String formatLine(LibraryEntry entry)
	{
		return String.join("\t",
			escape(key(entry.file())),
			Long.toString(entry.size()),
			Long.toString(entry.modifiedMillis()),
			escape(entry.title()),
			escape(entry.creator()),
			escape(entry.coverEntry()));
	}

	/**
	 * 1 行を復元する。列数不足・数値でない等はキャッシュの破損とみなして null。
	 *
	 * <p><b>復元した値はスキャン経路と同じ制約を通し直す。</b>
	 * キャッシュファイルはユーザーのホーム配下の平文で、手で書き換えられる。
	 * 通さないと、512 文字上限も「表紙はルート相対で {@code ..} を含まない」という
	 * 性質も、キャッシュ再利用時だけすり抜けてしまう。</p>
	 */
	static LibraryEntry parseLine(String line)
	{
		if (line == null || line.isEmpty()) return null;
		// split の既定は末尾の空文字列を落とすため、-1 が要る。
		// 最後の列 (表紙) が空文字だと列数が 5 になり、行ごと捨てられてしまう
		String[] columns = line.split("\t", -1);
		if (columns.length != COLUMNS) return null;
		try {
			String path = unescape(columns[0]);
			if (path == null || path.isEmpty()) return null;
			return new LibraryEntry(
				Path.of(path),
				Long.parseLong(columns[1]),
				Long.parseLong(columns[2]),
				LibraryScanner.truncate(unescape(columns[3])),
				LibraryScanner.truncate(unescape(columns[4])),
				LibraryScanner.sanitizeCoverEntry(unescape(columns[5])));
		} catch (RuntimeException e) {
			/* 意図的: 壊れた行はその 1 行だけ捨てる */
			logger.debug("本棚キャッシュの行を解釈できませんでした: {}", line, e);
			return null;
		}
	}

	/**
	 * 区切りと行境界を壊す文字だけを退避する。
	 * null と空文字を区別する必要があるので、null は {@code \0} 1 文字で表す
	 * (書名が空文字の EPUB と、書名が無い EPUB を混同しないため)。
	 */
	static String escape(String value)
	{
		if (value == null) return "\\0";
		StringBuilder buf = new StringBuilder(value.length() + 8);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\': buf.append("\\\\"); break;
			case '\t': buf.append("\\t"); break;
			case '\n': buf.append("\\n"); break;
			case '\r': buf.append("\\r"); break;
			default: buf.append(c);
			}
		}
		return buf.toString();
	}

	/** {@link #escape} の逆。{@code \0} 単独は null に戻す */
	static String unescape(String value)
	{
		if (value == null) return null;
		if (value.equals("\\0")) return null;
		StringBuilder buf = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c != '\\' || i + 1 >= value.length()) {
				buf.append(c);
				continue;
			}
			char next = value.charAt(++i);
			switch (next) {
			case '\\': buf.append('\\'); break;
			case 't': buf.append('\t'); break;
			case 'n': buf.append('\n'); break;
			case 'r': buf.append('\r'); break;
			// 知らないエスケープはバックスラッシュごと元に戻す (取りこぼしても壊さない)
			default: buf.append('\\').append(next);
			}
		}
		return buf.toString();
	}
}
