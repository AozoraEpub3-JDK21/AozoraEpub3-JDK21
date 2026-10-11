import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * ini を、変わったキーの行だけ書き換える（internal #30）。
 *
 * <p>{@link Properties#store} で書き直すと、配布の ini のコメント（各設定の説明）が消え、キーの順も変わる。
 * CLI だけで使う人にはコメントが唯一の説明なので、CLI（{@code IniWebShelf}）も GUI（{@link #writeInPlace}）も
 * こちらで書く。書く設定は前と同じ（書き手の設定がファイルに勝つ）で、変わるのはコメントと行の順が残ることだけ。</p>
 *
 * <p>ファイルはバイトのまま扱う（{@link Properties#load(java.io.InputStream)} と同じ ISO-8859-1 で読む）。
 * 触らない行は 1 バイトも変えない（UTF-8 のコメントもそのまま残る）。</p>
 */
final class IniLines
{
	private IniLines() {}

	/**
	 * @param original 今のファイルの中身（before はこれを読んだもの）
	 * @param after 書きたい設定の全部
	 * @return 書く中身。値の変わったキーはその行を書き換え、無くなったキーは行を消し、新しいキーは末尾に足す
	 */
	static byte[] update(byte[] original, Properties before, Properties after) throws IOException
	{
		return update(original, before, after, "AozoraEpub3 Parameters");
	}

	/** @param comment 行を残せず store で全部を書くときの見出し */
	static byte[] update(byte[] original, Properties before, Properties after, String comment) throws IOException
	{
		//足す順は名前順。ただし末尾の番号は数で比べる（PreviewLibraryDir.2 を .10 より前に）
		Map<String, String> set = new TreeMap<>(IniLines::compareKeys);
		for (String key : after.stringPropertyNames()) {
			if (!Objects.equals(before.getProperty(key), after.getProperty(key))) set.put(key, after.getProperty(key));
		}
		Set<String> remove = new HashSet<>(before.stringPropertyNames());
		remove.removeAll(after.stringPropertyNames());

		String text = new String(original, StandardCharsets.ISO_8859_1);
		String eol = firstLineEnd(text);
		StringBuilder out = new StringBuilder(text.length() + 256);
		Set<String> written = new HashSet<>();
		List<String> lines = logicalLines(text);
		for (String line : lines) {
			String key = keyOf(line);
			if (key != null && remove.contains(key)) continue;
			if (key != null && set.containsKey(key)) {
				//同じキーが 2 行あれば、load は後の行を使う。1 行目を書き換え、2 行目以降は消す
				if (written.add(key)) out.append(entry(key, set.get(key))).append(lineEnd(line));
				continue;
			}
			out.append(line);
		}
		boolean appended = false;
		for (Map.Entry<String, String> e : set.entrySet()) {
			if (written.contains(e.getKey())) continue;
			if (!appended && out.length() > 0) {
				if (out.charAt(out.length() - 1) != '\n' && out.charAt(out.length() - 1) != '\r') out.append(eol);
				//空の行を 1 つ挟む。配布の ini では最後の節（GUI の既定の説明）の続きに見えないように。
				//最後の行が \ で終わって続きを待っていても、続きはこの空の行で終わる（足す行がその値に入らない。PR の手元の codex）
				out.append(eol);
			}
			appended = true;
			out.append(entry(e.getKey(), e.getValue())).append(eol);
		}
		byte[] result = out.toString().getBytes(StandardCharsets.ISO_8859_1);
		//読み直して書きたい設定にならなければ、行を残すのをあきらめて全部を書く（コメントより設定が大事）
		if (!after.equals(load(result))) {
			ByteArrayOutputStream all = new ByteArrayOutputStream();
			after.store(all, comment);
			return all.toByteArray();
		}
		return result;
	}

	/**
	 * 設定をファイルへ、その場で書く（一時ファイルから置き換えない。GUI の保存。PR #125 の codex）。
	 * 今のファイルが読めれば、変わったキーの行だけを書き換え、何も変わらなければ書かない。
	 * 読めない・無いときは store で全部を書く（前からの GUI の書き方）
	 */
	static void writeInPlace(Path file, Properties props, String comment) throws IOException
	{
		byte[] content = null;
		byte[] original = null;
		if (Files.isRegularFile(file)) {
			try {
				original = Files.readAllBytes(file);
			} catch (IOException e) {
				//書けるが読めない ini（権限・ほかのプロセスの共有の禁止）。前からの書き方（store で全部）で書く（PR の手元の codex）
				original = null;
			}
		}
		if (original != null) {
			Properties before = load(original);
			//GUI は窓が手前から外れるたびに保存する（#130）。ファイルと同じ設定なら、行を見ずに抜ける
			if (props.equals(before)) return;
			if (before != null) {
				content = update(original, before, props, comment);
				//GUI は窓が手前から外れるたびに保存する（#130）。変わらなければファイルに触らない
				if (Arrays.equals(content, original)) return;
			}
		}
		if (content == null) {
			ByteArrayOutputStream all = new ByteArrayOutputStream();
			props.store(all, comment);
			content = all.toByteArray();
		}
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(content);
		}
	}

	/** 最初に使われている改行（無ければ \n） */
	private static String firstLineEnd(String text)
	{
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') return "\n";
			if (c == '\r') return (i + 1 < text.length() && text.charAt(i + 1) == '\n') ? "\r\n" : "\r";
		}
		return "\n";
	}

	/**
	 * 名前で比べる。末尾が「.数」のキーは、前置きで比べてから数で比べる（PreviewLibraryDir.2 を .10 より前に）。
	 * (前置き, 数) の組で比べるので推移的（TreeMap の比べ方に使える。PR のゲート2）
	 */
	static int compareKeys(String a, String b)
	{
		int c = numberedBase(a).compareTo(numberedBase(b));
		if (c != 0) return c;
		c = Long.compare(numberOf(a), numberOf(b));
		return c != 0 ? c : a.compareTo(b);
	}

	/** 末尾が「.数」なら「.」までの前置き、そうでなければキーそのもの */
	private static String numberedBase(String key)
	{
		return numberOf(key) < 0 ? key : key.substring(0, key.lastIndexOf('.') + 1);
	}

	/** 末尾の「.数」の数（18 桁まで）。無ければ -1 */
	private static long numberOf(String key)
	{
		int dot = key.lastIndexOf('.');
		if (dot < 0 || dot == key.length() - 1 || key.length() - dot - 1 > 18) return -1;
		for (int i = dot + 1; i < key.length(); i++) {
			char ch = key.charAt(i);
			if (ch < '0' || ch > '9') return -1;
		}
		return Long.parseLong(key.substring(dot + 1));
	}

	static Properties load(byte[] bytes)
	{
		Properties p = new Properties();
		try {
			p.load(new ByteArrayInputStream(bytes));
		} catch (IOException | IllegalArgumentException e) {
			return null;
		}
		return p;
	}


	/** 論理行（続きの行を含む）に切る。行末の改行も含めて返す（つなげると元に戻る） */
	static List<String> logicalLines(String text)
	{
		List<String> lines = new ArrayList<>();
		int start = 0;
		int i = 0;
		while (i < text.length()) {
			int end = i;
			while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
			int next = end;
			if (next < text.length() && text.charAt(next) == '\r') next++;
			if (next < text.length() && text.charAt(next) == '\n' && (next == end || text.charAt(next - 1) == '\r')) next++;
			//コメントの行は続かない。それ以外は、行末の \ が奇数個なら次の行へ続く
			boolean comment = isComment(text.substring(start, end));
			if (!comment && next < text.length() && endsWithOddBackslashes(text, i, end)) {
				i = next;
				continue;
			}
			lines.add(text.substring(start, next));
			start = next;
			i = next;
		}
		if (start < text.length()) lines.add(text.substring(start));
		return lines;
	}

	private static boolean endsWithOddBackslashes(String text, int from, int end)
	{
		int n = 0;
		for (int j = end - 1; j >= from && text.charAt(j) == '\\'; j--) n++;
		return n % 2 == 1;
	}

	/** Properties が行頭で飛ばす空白（空白・タブ・改ページだけ。String.strip より狭い。PR のゲート2） */
	private static int skipSpaces(String line)
	{
		int i = 0;
		while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t' || line.charAt(i) == '\f')) i++;
		return i;
	}

	private static boolean isComment(String physicalLine)
	{
		int i = skipSpaces(physicalLine);
		return i < physicalLine.length() && (physicalLine.charAt(i) == '#' || physicalLine.charAt(i) == '!');
	}

	/** 空白だけの行（Properties が飛ばす行） */
	private static boolean isBlankLine(String logicalLine)
	{
		int i = skipSpaces(logicalLine);
		return i == logicalLine.length() || logicalLine.charAt(i) == '\n' || logicalLine.charAt(i) == '\r';
	}

	/** その論理行のキー（Properties が読むとおり）。コメント・空行は null */
	static String keyOf(String logicalLine)
	{
		if (isBlankLine(logicalLine) || isComment(logicalLine)) return null;
		Properties one = new Properties();
		try {
			one.load(new ByteArrayInputStream(logicalLine.getBytes(StandardCharsets.ISO_8859_1)));
		} catch (IOException | IllegalArgumentException e) {
			return null;
		}
		Set<String> keys = one.stringPropertyNames();
		return keys.size() == 1 ? keys.iterator().next() : null;
	}

	/** 1 つのキーを、store と同じ書き方（ISO-8859-1、それ以外は \\uXXXX）で 1 行にする。改行は付けない */
	static String entry(String key, String value) throws IOException
	{
		Properties one = new Properties();
		one.setProperty(key, value);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		one.store(buf, null);
		for (String line : buf.toString(StandardCharsets.ISO_8859_1).split("\r\n|\n|\r")) {
			if (!line.isEmpty() && !line.startsWith("#")) return line;
		}
		throw new IOException("設定の行を作れませんでした: " + key);
	}

	private static String lineEnd(String line)
	{
		if (line.endsWith("\r\n")) return "\r\n";
		if (line.endsWith("\n")) return "\n";
		if (line.endsWith("\r")) return "\r";
		return "";
	}
}
