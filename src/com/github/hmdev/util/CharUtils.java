package com.github.hmdev.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文字変換と判別関連の関数定義クラス
 */
public class CharUtils
{
	/** 全角英数字を半角に変換
	 * @param src 全角文字列
	 * @return 半角文字列 */
	static public String fullToHalf(String src)
	{
		char[] c = src.toCharArray();
		for (int i=c.length-1; i>=0; i--) {
			if (c[i] >= '０' && c[i] <= '９') c[i] = (char)(c[i]-'０'+'0');
			else if (c[i] >= 'Ａ' && c[i] <= 'ｚ') c[i] = (char)(c[i]-'ａ'+'a');
		}
		return new String(c);
	}
	
	/** すべて同じ文字かチェック */
	static public boolean isSameChars(char[] ch, int begin, int end)
	{
		for (int i=begin+1; i<end; i++) {
			if (ch[begin] != ch[i]) return false;
		}
		return true;
	}
	
	/** 半角数字かチェック */
	static public boolean isNum(char ch)
	{
		switch (ch) {
			case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9': return true;
		}
		return false;
	}
	
	/** 英字かどうかをチェック 拡張ラテン文字含む
	 * 半角スペースは含まない */
	static public boolean isHalf(char ch)
	{
		return (0x21 <= ch && ch <= 0x02AF);
	}
	/** 英字かどうかをチェック 拡張ラテン文字含む
	 * 半角スペースは含まない */
	static public boolean isHalf(char[] chars)
	{
		for (char ch : chars) {
			if (!isHalf(ch)) return false;
		}
		return true;
	}
	
	/** 英字かどうかをチェック 拡張ラテン文字含む
	 * 半角スペースを含む */
	static public boolean isHalfSpace(char ch)
	{
		return (0x20 <= ch && ch <= 0x02AF);
	}
	/** 英字かどうかをチェック 拡張ラテン文字含む
	 * 半角スペースを含む */
	static public boolean isHalfSpace(char[] chars)
	{
		for (char ch : chars) {
			if (!isHalfSpace(ch)) return false;
		}
		return true;
	}
	
	static public boolean isFullAlpha(char ch)
	{
		return ('Ａ' <= ch && ch <= 'Ｚ') || ('ａ' <= ch && ch <= 'ｚ') || ('０' <= ch && ch <= '９') || '＠' == ch || '＿' == ch;
	}
	/** 半角数字かチェック */
	static public boolean isFullNum(char ch)
	{
		switch (ch) {
			case '０': case '１': case '２': case '３': case '４': case '５': case '６': case '７': case '８': case '９': return true;
		}
		return false;
	}
	
	/** ひらがなかチェック 半角濁点半濁点は全角に変換済 */
	static public boolean isHiragana(char ch)
	{
		return ('ぁ'<=ch && ch<='ん') || 'ゕ'==ch || 'ゖ'==ch || 'ー'==ch || 'ゝ'==ch || 'ゞ'==ch || 'ヽ'==ch || 'ヾ'==ch || '゛'==ch || '゜'==ch
				|| 'ι'==ch; //濁点処理用の例外
				// || 'ﾞ'==ch || 'ﾟ'== ch;
	}
	/** カタカナかチェック 半角濁点半濁点は全角に変換済 */
	static public boolean isKatakana(char ch)
	{
		if ('ァ'<=ch && ch<='ヶ') return true;
		switch (ch) {
		case 'ァ': case 'ィ': case 'ゥ': case 'ェ': case 'ォ': case 'ヵ': case 'ㇰ': case 'ヶ': case 'ㇱ': case 'ㇲ': case 'ッ': case 'ㇳ': case 'ㇴ':
		case 'ㇵ': case 'ㇶ': case 'ㇷ': case 'ㇸ': case 'ㇹ': case 'ㇺ': case 'ャ': case 'ュ': case 'ョ': case 'ㇻ': case 'ㇼ': case 'ㇽ': case 'ㇾ': case 'ㇿ': case 'ヮ':
		case 'ー': case 'ゝ': case 'ゞ': case 'ヽ': case 'ヾ': case '゛': case '゜':
		//case 'ﾞ': case 'ﾟ':
			return true;
		}
		return false;
	}
	
	static public boolean isSpace(String line)
	{
		char c;
		for (int i=line.length()-1; i>=0; i--) {
			c = line.charAt(i);
			if (c != ' ' && c != '　' && c != ' ') return false;
		}
		return true;
	}
	
	/** 英字かどうかをチェック 拡張ラテン文字含む */
	static public boolean isAlpha(char ch)
	{
		return ('A' <= ch  && ch <= 'Z') || ('a' <= ch && ch <= 'z') || (0x80 <= ch && ch <= 0x02AF);
	}
	
	/** 漢字かどうかをチェック
	 * 4バイト文字のも対応
	 * 漢字の間の「ノカケヵヶ」も漢字扱い
	 * IVS文字 U+e0100-e01efも漢字扱い */
	static public boolean isKanji(char[] ch, int i)
	{
		switch (ch[i]) {
		case '゛': case '゜':
			//二の字点は濁点付きも漢字
			return (i>0 && ch[i-1]=='〻');
		/*case 'ノ': case 'カ': case 'ケ': case 'ヵ': case 'ヶ':
			//漢字の間にある場合だけ漢字扱い
			if (i==0 || i+1==ch.length) return false;
			return _isKanji(ch, i-1) && _isKanji(ch, i+1);
		*/
		}
		return _isKanji(ch, i);
	}
	/** カタカナ以外の漢字チェック */
	static private boolean _isKanji(char[] ch, int i)
	{
		int pre = i==0?-1:ch[i-1];
		int c = ch[i];
		int suf = i+1>=ch.length?-1:ch[i+1];
		switch (c) {
		case '〓': case '〆': case '々': case '〻':
			return true;
		}
		if (0x4E00 <= c && c <= 0x9FFF) return true;//'一' <= ch && ch <= '龠'
		if (0xF900 <= c && c <= 0xFAFF) return true;//CJK互換漢字
		if (0xFE00 <= c && c <= 0xFE0D) return true;//VS1-14 (15,16は絵文字用なので除外)
		//0x20000-0x2A6DF UTF16({d840,dc00}-{d869,dedf})
		//0x2A700-0x2B81F UTF16({d869,df00}-{d86e,dc1f})
		//0x2F800-0x2FA1F UTF16({d87e,dc00}-{d87e,de1f})
		if (pre >= 0) {
			if (0xDB40 == pre && 0xDD00 <= c && c <= 0xDDEF) return true; //IVS e0100-e01ef
			if (0xD87E == pre && 0xDc00 <= c && c <= 0xDE1F) return true;
			int code = pre<<16|c&0xFFFF;
			if (0xD840DC00 <= code && code <= 0xD869DEDF) return true;
			if (0xD869DF00 <= code && code <= 0xD86EDC1F) return true;
		}
		if (suf >= 0) {
			if (0xDB40 == c && 0xDD00 <= suf && suf <= 0xDDEF) return true; //IVS e0100-e01ef
			if (0xD87E == c && 0xDc00 <= suf && suf <= 0xDE1F) return true;
			int code = c<<16|suf&0xFFFF;
			if (0xD840DC00 <= code && code <= 0xD869DEDF) return true;
			if (0xD869DF00 <= code && code <= 0xD86EDC1F) return true;
		}
		return false;
	}
	
	////////////////////////////////////////////////////////////////
	/** 制御文字 (0x00-0x1F)。Windows のファイル API は位置を問わずこれを含む名前を受け付けず、
	 * Path 解決では InvalidPathException になる。
	 * escapeUrlToFile と replaceInvalidFileChars の両方で使うため共有する
	 * （2 つの関数で対策範囲が食い違うのを防ぐ。経緯は docs/code-audit-followups.md の「### 13.」「### 14.」） */
	static private final String CONTROL_CHARS = "[\\x00-\\x1F]";

	/** Windows の予約デバイス名（大文字小文字は区別しない）。
	 * これらと完全一致するパスセグメントは実ファイルではなくデバイスとして解決され得るため、
	 * escapeUrlToFile で末尾に '_' を付けて無害化する。 */
	static private final Set<String> WINDOWS_RESERVED_NAMES;
	static {
		Set<String> names = new HashSet<>(Arrays.asList("CON", "PRN", "AUX", "NUL"));
		// COM0〜COM9 / LPT0〜LPT9 と、上付き数字を使う別名（COM¹ COM² COM³ / LPT¹ LPT² LPT³）
		for (String prefix : new String[]{"COM", "LPT"}) {
			for (char c : "0123456789¹²³".toCharArray()) names.add(prefix+c);
		}
		WINDOWS_RESERVED_NAMES = Set.copyOf(names);
	}

	/** ファイル名に使えない文字を'_'に置換
	 * パストラバーサル対策として「..」のみのパスセグメントは「__」に無害化する（多層防御）。
	 * Windows の予約デバイス名と完全一致するパスセグメントは末尾に '_' を付けて無害化する。
	 * 制御文字とセグメント末尾の半角スペースも '_' に置換する（Windows で InvalidPathException になるため）。
	 * いずれにも該当しない入力の出力は従来と完全に同一（キャッシュファイル名互換維持） */
	static public String escapeUrlToFile(String str)
	{
		String escaped = str.replaceAll("(\\?|\\&)", "/").replaceAll("(:|\\*|\\||\\<|\\>|\"|\\\\)", "_");
		// 制御文字 (0x00-0x1F) は Windows のファイル名に使えず、位置を問わず InvalidPathException になる
		escaped = escaped.replaceAll(CONTROL_CHARS, "_");
		// 「?」「&」→「/」置換後に現れる「..」セグメントも対象にするため、置換後に無害化する
		escaped = escaped.replaceAll("(^|/)\\.\\.(?=/|$)", "$1__");
		escaped = escapeReservedDeviceNames(escaped);
		// セグメント末尾の半角スペースも Windows では許されず InvalidPathException になる。
		// 予約デバイス名の判定後に行う（"COM1 " は先に "COM1 _" になり、この時点では末尾スペースではなくなる）。
		// 末尾ドットは Windows が黙って切り詰めるだけで例外にはならないため対象外。
		// 入力末尾は '$' ではなく '\z' で判定する。'$' は行終端文字 (U+0085 / U+2028 / U+2029) の
		// 直前にもマッチするため、"foo " のような Windows で有効な名前まで書き換えてしまう
		return escaped.replaceAll(" (?= *(?:/|\\z))", "_");
	}

	/** Windows 予約デバイス名と一致するパスセグメントの末尾に '_' を付けて無害化する。
	 * 該当セグメントが無い場合は引数をそのまま返す */
	static private String escapeReservedDeviceNames(String path)
	{
		String[] segments = path.split("/", -1);
		boolean modified = false;
		for (int i=0; i<segments.length; i++) {
			if (isWindowsReservedName(segments[i])) {
				segments[i] = segments[i]+"_";
				modified = true;
			}
		}
		return modified ? String.join("/", segments) : path;
	}

	/** Windows がデバイスとして解決するセグメント名か判定する。
	 * Windows は名前末尾の '.' と ' ' を無視するため、それらを除いてから予約名と比較する
	 * （"NUL." は "NUL" と同じくデバイスに解決され、"NUL" と同じ症状になる）。
	 * 一方 "NUL.txt" のように予約名の後に拡張子が続くものは Windows 11 実機では実ファイルとして作成できるため対象外
	 * （Microsoft のドキュメント上は予約扱いだが、リネームすると動作しているキャッシュを無効化するだけになる。
	 *   判断根拠と残存リスクは docs/code-audit-followups.md の「### 12.」を参照） */
	static private boolean isWindowsReservedName(String segment)
	{
		int end = segment.length();
		while (end > 0 && (segment.charAt(end-1) == '.' || segment.charAt(end-1) == ' ')) end--;
		if (end == 0) return false;
		String name = end == segment.length() ? segment : segment.substring(0, end);
		return WINDOWS_RESERVED_NAMES.contains(name.toUpperCase(Locale.ROOT));
	}
	
	////////////////////////////////////////////////////////////////
	/** ファイル名に使えない文字を'_'に置換（URL をそのままファイル名にする用途）。
	 * escapeUrlToFile と違い '?' '&' もディレクトリ区切りにせず '_' にする。
	 * ':' と制御文字 (0x00-0x1F) も '_' にする（Windows のファイル API が受け付けないため）。
	 *
	 * escapeUrlToFile と違い、セグメント末尾の半角スペース・Windows 予約デバイス名・
	 * 「..」セグメントは扱わない。唯一の呼び出し元 AozoraEpub3Applet の青空 zip 直接
	 * ダウンロード経路が、(a) URL 末尾が .zip / .txtz / .rar の場合しか通らず末尾スペースが
	 * 構造上あり得ない、(b) new File(...).getName() で最終セグメントしか使わないため、
	 * という前提に依存している。**別の用途で呼ぶ場合はこの前提が崩れる**ので、
	 * その場合は escapeUrlToFile 相当の処理が必要か検討すること
	 * （経緯は docs/code-audit-followups.md の「### 14.」）。 */
	static public String replaceInvalidFileChars(String str)
	{
		return str.replaceAll("[?*&|<>\":\\\\]", "_").replaceAll(CONTROL_CHARS, "_");
	}

	////////////////////////////////////////////////////////////////
	/** 前後の空白を除外 */
	static public String removeSpace(String text)
	{
		return text.replaceFirst("^[ |　]+", "").replaceFirst("[ |　]+$", "");
	}
	/** タグを除外 */
	static public String removeTag(String text)
	{
		return text.replaceAll("［＃.+?］", "").replaceAll("<[^>]+>", "");
	}
	
	/** ルビを除去 内部エスケープ（{@link #ESCAPE} + 特殊文字）は印ごと残す
	 * （印を外すと ［※［＃井げた］文字］ が ［＃文字］ になり、後の注記の除去で消える。印は getChapterName が注記を外した後に外す） */
	static public String removeRuby(String text)
	{
		StringBuilder buf = new StringBuilder();
		char[] ch = text.toCharArray();
		boolean inRuby = false;
		for (int i=0; i<ch.length; i++) {
			if (ch[i] == ESCAPE) {
				//印の次の 1 文字は文字そのもの（ルビの読みの中なら読みと一緒に捨てる）
				if (i+1 < ch.length && !inRuby) buf.append(ch[i]).append(ch[i+1]);
				i++;
				continue;
			}
			//エスケープ済みの文字は上の印の分岐で読み終えているので、ここに来る ｜《》 は記号
			if (inRuby) {
				if (ch[i] == '》') inRuby = false;
			} else {
				switch (ch[i]) {
				case '｜':
					break;
				case '《':
					inRuby = true;
					break;
				default:
					if (!inRuby) buf.append(ch[i]);
				}
			}
		}
		return buf.toString();
	}
	
	/**
	 * 内部エスケープの印（監査項目 32）。外字注記などから出た特殊文字（{@link #isEscapableChar}）の前に付け、
	 * 「次の 1 文字は文字そのもの（ルビ・注記の記号ではない）」を表す。文字の出力（convertReplacedChar）で捨てる。
	 * 以前は ※ を付けて ※ の個数の偶奇で判定していたので、本文に書かれた ※ が混ざると偶奇がずれ、
	 * 《 以降が行末まで消えた（upstream kyukyunyorituryo/AozoraEpub3#34。あちらも \u0001 に分けた）。
	 * 本文にこの文字があれば、外字注記の変換（convertGaijiChuki）の入口で落とす
	 */
	public static final char ESCAPE = '\u0001';

	/** begin から end の手前までに内部エスケープの印があれば true */
	static public boolean hasEscape(char[] ch, int begin, int end)
	{
		for (int i=Math.max(begin, 0); i<end && i<ch.length; i++) if (ch[i] == ESCAPE) return true;
		return false;
	}

	/**
	 * 外字から出た［ ］の代わりに置く文字（監査項目 36）。注記の解析は ［＃…］ を正規表現で探すので、
	 * 印（{@link #ESCAPE}）を前に置いても ［＃ の並びは崩れず、外字の［が注記を開いてしまう。
	 * 1 字を 1 字に置き換えるので、字数の数え方に例外は要らない。文字の出力（convertReplacedChar）と {@link #removeEscape} で［］に戻す。
	 * U+FDD0・U+FDD1 は Unicode の非文字（永久に文字を割り当てない、内部で使ってよい値）
	 */
	public static final char GAIJI_OPEN_BRACKET = '\uFDD0';
	/** 外字から出た］の代わり（{@link #GAIJI_OPEN_BRACKET}） */
	public static final char GAIJI_CLOSE_BRACKET = '\uFDD1';

	/** 内部の文字（印・外字の［］の代わり）を含むなら true */
	static private boolean hasInternalChars(String text)
	{
		for (int i=0; i<text.length(); i++) {
			char c = text.charAt(i);
			if (c == ESCAPE || c == GAIJI_OPEN_BRACKET || c == GAIJI_CLOSE_BRACKET) return true;
		}
		return false;
	}

	/** 内部エスケープの印を外し、外字の［］の代わりの文字を［］に戻す（名前・ログなど、出力に向ける文字列） */
	static public String removeEscape(String text)
	{
		if (!hasInternalChars(text)) return text;
		StringBuilder buf = new StringBuilder(text.length());
		for (int i=0; i<text.length(); i++) {
			char c = text.charAt(i);
			if (c == ESCAPE) continue;
			buf.append(c == GAIJI_OPEN_BRACKET ? '［' : c == GAIJI_CLOSE_BRACKET ? '］' : c);
		}
		return buf.toString();
	}

	/** 本文に紛れた内部の文字（印・外字の［］の代わり）を落とす。残すと印や括弧として読まれる */
	static public String stripInternalChars(String text)
	{
		if (!hasInternalChars(text)) return text;
		StringBuilder buf = new StringBuilder(text.length());
		for (int i=0; i<text.length(); i++) {
			char c = text.charAt(i);
			if (c != ESCAPE && c != GAIJI_OPEN_BRACKET && c != GAIJI_CLOSE_BRACKET) buf.append(c);
		}
		return buf.toString();
	}

	/** 出力の直前で、外字の［］の代わりの文字をその場で［］に戻す */
	static public void restoreGaijiBracket(char[] ch, int idx)
	{
		if (ch[idx] == GAIJI_OPEN_BRACKET) ch[idx] = '［';
		else if (ch[idx] == GAIJI_CLOSE_BRACKET) ch[idx] = '］';
	}

	/** getChapterName を通した名前に、連続する記号を 1 つにまとめて長さを切る（注記の除去はしない。もう一度 getChapterName を通すと、外字の［］に戻した ［＃…］ が消える） */
	static public String truncateName(String name, int maxLength)
	{
		if (name == null) return null;
		return cutName(reduceSymbols(name), maxLength);
	}

	/** 前に内部エスケープの印（{@link #ESCAPE}）を付ける特殊文字ならtrue
	 * 外字注記変換で付ける側と convertReplacedChar で外す側はこれを使う */
	static public boolean isEscapableChar(char c)
	{
		switch (c) {
		case '※': case '《': case '》': case '｜': case '＃':
			return true;
		}
		return false;
	}
	
	/** 文字がエスケープされた特殊文字（直前が {@link #ESCAPE}）ならtrue。※ の個数は数えない */
	static public boolean isEscapedChar(char[] ch, int idx)
	{
		return idx > 0 && ch[idx-1] == ESCAPE;
	}
	/** 文字がエスケープされた特殊文字（直前が {@link #ESCAPE}）ならtrue。※ の個数は数えない */
	static public boolean isEscapedChar(StringBuilder ch, int idx)
	{
		return idx > 0 && ch.charAt(idx-1) == ESCAPE;
	}
	
	
	/** HTML特殊文字をエスケープ */
	static public String escapeHtml(String text)
	{
		return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}
	
	/** 1 行の中で閉じた柱（中に柱の印を含まない） */
	static final Pattern HASHIRA_SPAN = Pattern.compile("［＃ここから柱］((?:(?!［＃ここ(?:から柱|で柱終わり)］).)*)［＃ここで柱終わり］", Pattern.DOTALL);
	static final Pattern HASHIRA_MARK = Pattern.compile("［＃ここ(?:から柱|で柱終わり)］");

	/**
	 * 柱（narou.rb の章の扉ページが作品名を置く独自の注記。作る側はどれも 1 行で閉じて書く）の印を、1 行の中で対にする。
	 * 対にならない開始・閉じの印は外し、中の文字は残す（注記の表で柱を div にしているので、残すと XHTML の開閉が崩れる。監査項目 20）
	 */
	static public String balanceHashira(String line)
	{
		if (line.indexOf("柱］") < 0 && line.indexOf("柱終わり］") < 0) return line;
		StringBuilder out = new StringBuilder();
		Matcher m = HASHIRA_SPAN.matcher(line);
		int last = 0;
		while (m.find()) {
			out.append(HASHIRA_MARK.matcher(line.substring(last, m.start())).replaceAll(""));
			out.append(m.group());
			last = m.end();
		}
		out.append(HASHIRA_MARK.matcher(line.substring(last)).replaceAll(""));
		return out.toString();
	}

	/** 柱を中身ごと外した行（目次の章の名前・表題・自動の章の判定に柱を使わない。監査項目 20）。対にならない印は印だけ外す */
	static public String removeHashira(String line)
	{
		//「対になっているか」は balanceHashira だけが決める
		return HASHIRA_SPAN.matcher(balanceHashira(line)).replaceAll("");
	}

	/** 目次やタイトル用の文字列を取得 ルビ関連の文字 ｜《》 は除外済で他の特殊文字は内部エスケープ（{@link #ESCAPE}）
	 * @param maxLength 文字制限 これより大きい文字は短くして...をつける
	 * @prram 記号文字を短縮する */
	static public String getChapterName(String line, int maxLength, boolean reduce)
	{
		//柱（narou.rb の章の扉ページの作品名）は中身ごと外す。本文には見えるが、章の名前・表題にはしない（監査項目 20）
		//内部エスケープの印は注記を外した後で外す（先に外すと ［※［＃井げた］…］ が注記に見える）
		String name = removeEscape(removeHashira(line).replaceAll("［＃.+?］", ""))//注記除去
				.replaceAll("\t", " ").replaceFirst("^[ |　]+", "").replaceFirst("[ |　]+$",""); //前後の不要な文字所除去
		if (reduce) name = reduceSymbols(name);//連続する記号は1つに
		//タグはimgとaを削除
		name = chapterTagOpenPattern.matcher(name).replaceAll("");
		name = chapterTagClosePattern.matcher(name).replaceAll("");
		
		return cutName(name, maxLength);
	}

	/** 連続する記号は1つに（getChapterName の reduce） */
	static public String reduceSymbols(String name)
	{
		return name.replaceAll("(=|＝|-|―|─)+", "$1");
	}

	/** maxLength より長い名前は短くして...をつける。0 なら切らない（getChapterName の最後） */
	static public String cutName(String name, int maxLength)
	{
		if (name == null || maxLength == 0) return name;
		return name.length()>maxLength ? name.substring(0, maxLength)+"..." : name;
	}
	/** imgとaタグ除去用のパターン */
	static Pattern chapterTagOpenPattern = Pattern.compile("< *(img|a) [^>]*>", Pattern.CASE_INSENSITIVE);
	static Pattern chapterTagClosePattern = Pattern.compile("< */ *(img|a)(>| [^>]*>)", Pattern.CASE_INSENSITIVE);
	
	/** 指定されたタグを削除
	 * @param single 単独または開始タグ 属性無し 複数のタグは|でOR条件を指定
	 * @param open 開始タグ 属性値有り 複数のタグは|でOR条件を指定
	 * @param close 終了タグ 複数のタグは|でOR条件を指定 */
	static String removeTag(String str, String single, String open, String close)
	{
		if (str.indexOf('<') == -1) return str;
		
		if (single != null) str = Pattern.compile("< *("+single+") */? *>", Pattern.CASE_INSENSITIVE).matcher(str).replaceAll("");
		if (open != null) str = Pattern.compile("< *("+open+") [^>]*>", Pattern.CASE_INSENSITIVE).matcher(str).replaceAll("");
		if (close != null) str = Pattern.compile("< */ *("+close+")(>| [^>]*>)", Pattern.CASE_INSENSITIVE).matcher(str).replaceAll("");
		return str;
	}
	
	static public String getChapterName(String line, int maxLength)
	{
		return getChapterName(line, maxLength, true);
	}
	
	/** BOMが文字列の先頭にある場合は除去 */
	static public String removeBOM(String str)
	{
		if (str != null && str.length() > 0 && str.charAt(0) == 0xFEFF) return str.substring(1);
		//空文字列は空文字列のまま返す（null を返すと、1 行目が空のテキストが表題の読み取りと本文の変換で落ちていた）
		return str;
	}
	
	////////////////////////////////////////////////////////////////
	/** Test用 */
	/*public static void main(String[] args)
	{
		try {
			System.out.println(removeTag("あ<IMG>あ<IMG1>あ<img src=\"\"/>い<A>い<A1>い<AB>う<ab href=\"\">うう<a href=\"\">え<br>え<br/>え</a>お</ab>おお", "br", "img|a", "a"));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}*/
}
