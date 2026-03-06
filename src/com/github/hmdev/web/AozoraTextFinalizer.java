package com.github.hmdev.web;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.hmdev.util.LogAppender;

/**
 * 青空文庫テキストのファイナライズ処理を行うクラス。
 *
 * ストリーム変換後に文章全体を評価する後処理を実施:
 * - 前書き・後書きの自動検出
 * - 自動行頭字下げ
 * - 改ページ直後の見出し化
 * - かぎ括弧の開閉チェック
 *
 * narou.rb互換の処理を実装。
 * 参考: narou-3.8.2/lib/converterbase.rb
 */
public class AozoraTextFinalizer {

	private NarouFormatSettings settings;

	/** 前書きの区切りパターン（*が44個） */
	private static final Pattern AUTHOR_INTRODUCTION_SPLITTER = Pattern.compile("^[ 　]*[\\*＊]{44}$");

	/** 後書きの区切りパターン（*が48個） */
	private static final Pattern AUTHOR_POSTSCRIPT_SPLITTER = Pattern.compile("^[ 　]*[\\*＊]{48}$");

	/** かぎ括弧の種類（開き括弧） */
	private static final String OPEN_BRACKETS = "「『〔（【〈《≪";

	/** かぎ括弧の種類（閉じ括弧） */
	private static final String CLOSE_BRACKETS = "」』〕）】〉》≫";

	/** 二分アキ対象の行頭括弧 (narou.rb: HALF_INDENT_TARGET) */
	private static final Pattern HALF_INDENT_TARGET = Pattern.compile(
		"^[ \u3000\\t]*+([〔「『(（【〈《≪〝])");

	/** 字下げ判定の除外文字 (narou.rb: IGNORE_INDENT_CHAR) */
	private static final String IGNORE_INDENT_CHARS = "(（「『〈《≪【〔―・※［〝\n";

	/** 字下げ対象外文字 (・を除外) */
	private static final String AUTO_INDENT_IGNORE = "(（「『〈《≪【〔―※［〝\n";

	/** 英文判定の最小長 (narou.rb: ENGLISH_SENTENCES_MIN_LENGTH) */
	private static final int ENGLISH_SENTENCES_MIN_LENGTH = 8;

	/** 英文候補の正規表現 (narou.rb: ENGLISH_SENTENCES_CHARACTERS) */
	private static final Pattern ENGLISH_SENTENCES_PATTERN = Pattern.compile(
		"[\\w.,!?'\" &:;-]+");

	/** 漢数字テーブル */
	private static final char[] KANJI_NUM = "〇一二三四五六七八九".toCharArray();

	/**
	 * コンストラクタ
	 *
	 * @param settings フォーマット設定
	 */
	public AozoraTextFinalizer(NarouFormatSettings settings) {
		this.settings = settings;
	}

	/** 单一引数の後方互換オーバーロード。baseDir には txtFile の親ディレクトリを使用。 */
	public void finalize(File txtFile) throws IOException {
		finalize(txtFile, txtFile.getParentFile());
	}

	/**
	 * メイン処理: converted.txt を読み込み、後処理を適用して書き戻す
	 *
	 * @param txtFile 変換済みテキストファイル
	 * @throws IOException ファイル読み書きエラー
	 */
	public void finalize(File txtFile, File baseDir) throws IOException {
		// パストラバーサル対策: baseDir 配下にあることを検証
		File canonicalBase = baseDir.getCanonicalFile();
		File canonicalTxt = txtFile.getCanonicalFile();
		if (!canonicalTxt.getPath().startsWith(canonicalBase.getPath() + File.separator)) {
			throw new IOException("ファイルパスが許可されたディレクトリ外です: " + canonicalTxt);
		}
		txtFile = canonicalTxt;
		// ファイルサイズチェック
		long fileSizeBytes = txtFile.length();
		long maxSizeBytes = settings.getMaxFinalizableFileSizeMB() * 1024L * 1024L;

		if (fileSizeBytes > maxSizeBytes) {
			LogAppender.println("警告: ファイルサイズが上限を超えているため、ファイナライズ処理をスキップします");
			LogAppender.println("  ファイルサイズ: " + (fileSizeBytes / 1024 / 1024) + "MB");
			LogAppender.println("  上限: " + settings.getMaxFinalizableFileSizeMB() + "MB");
			LogAppender.println("  (setting_narourb.ini の max_finalizable_file_size_mb で変更可能)");
			return;
		}

		LogAppender.println("ファイナライズ処理を開始: " + txtFile.getName());

		// 1. ファイル全体を読み込み
		String content = readFile(txtFile);

		// 2. 空行圧縮
		if (settings.isEnablePackBlankLine()) {
			content = packBlankLine(content);
		}

		// 3. 前書き・後書きの自動検出
		if (settings.isEnableAuthorComments()) {
			content = detectAndMarkAuthorComments(content);
		}

		// 4. 漢数字変換
		if (settings.isEnableConvertNumToKanji()) {
			content = convertNumToKanji(content);
		}

		// 5. 英字全角化
		if (settings.isEnableAlphabetToZenkaku()) {
			content = alphabetToZenkaku(content, settings.isEnableAlphabetForceZenkaku());
		}

		// 6. 二分アキ挿入 + 自動行頭字下げ
		if (settings.isEnableHalfIndentBracket() || settings.isEnableAutoIndent()) {
			content = halfIndentBracketAndAutoIndent(content);
		}

		// 7. 改ページ直後の見出し化
		if (settings.isEnableEnchantMidashi()) {
			content = enchantMidashi(content);
		}

		// 8. かぎ括弧内の自動連結（<br>タグ由来の改行にも対応）
		if (settings.isEnableAutoJoinInBrackets()) {
			content = autoJoinInBrackets(content);
		}

		// 9. 行末読点での自動連結（<br>タグ由来の改行にも対応）
		if (settings.isEnableAutoJoinLine()) {
			content = autoJoinLine(content);
		}

		// 10. かぎ括弧の開閉チェック（警告のみ）
		if (settings.isEnableInspectInvalidOpenCloseBrackets()) {
			inspectBrackets(content);
		}

		// 11. replace.txt によるテキスト置換（最後に適用）
		if (!settings.getTextReplacePatterns().isEmpty()) {
			content = applyReplacePatterns(content);
		}

		// 12. 読了表示
		if (settings.isEnableDisplayEndOfBook()) {
			content = appendEndOfBook(content);
		}

		// 13. ファイルに書き戻す
		writeFile(txtFile, content);

		LogAppender.println("ファイナライズ処理が完了しました");
	}

	/**
	 * ファイル全体を読み込む
	 */
	private String readFile(File file) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	/**
	 * ファイルに書き戻す
	 */
	private void writeFile(File file, String content) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
			bw.write(content);
		}
	}

	/**
	 * 前書き・後書きの自動検出と注記挿入
	 *
	 * narou.rb互換: converterbase.rb:731-844
	 *
	 * パターン:
	 * - *が44個の行の後 → 前書き
	 * - *が48個の行の後 → 後書き
	 */
	private String detectAndMarkAuthorComments(String text) {
		String[] lines = text.split("\n", -1);
		List<String> result = new ArrayList<>();

		boolean inIntroduction = false;
		boolean inPostscript = false;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			// 前書きの開始パターン
			if (!inIntroduction && !inPostscript && AUTHOR_INTRODUCTION_SPLITTER.matcher(line).matches()) {
				result.add(line); // *44個の行は残す
				result.add("［＃ここから前書き］");
				inIntroduction = true;
				continue;
			}

			// 後書きの開始パターン
			if (!inIntroduction && !inPostscript && AUTHOR_POSTSCRIPT_SPLITTER.matcher(line).matches()) {
				result.add(line); // *48個の行は残す
				result.add("［＃ここから後書き］");
				inPostscript = true;
				continue;
			}

			// 前書きの終了（次の章や改ページで終わる）
			if (inIntroduction && (line.startsWith("［＃改ページ］") || line.matches("^第.+章.*$"))) {
				result.add("［＃ここで前書き終わり］");
				inIntroduction = false;
			}

			// 後書きの終了（改ページまたはファイル末尾）
			if (inPostscript && line.startsWith("［＃改ページ］")) {
				result.add("［＃ここで後書き終わり］");
				inPostscript = false;
			}

			result.add(line);
		}

		// ファイル末尾で前書き・後書きが終了していない場合
		if (inIntroduction) {
			result.add("［＃ここで前書き終わり］");
		}
		if (inPostscript) {
			result.add("［＃ここで後書き終わり］");
		}

		return String.join("\n", result);
	}

	/**
	 * 空行圧縮
	 *
	 * narou.rb互換: converterbase.rb:28-31
	 * 連続する空行を削減して縦書きの読みやすさを改善する。
	 */
	private String packBlankLine(String text) {
		// \n\n → \n (連続空行を1行に)
		text = text.replace("\n\n", "\n");
		// 3行以上の連続改行のみ行を2行に削減
		text = text.replaceAll("(?m)(^\\n){3,}", "\n\n");
		return text;
	}

	/**
	 * 二分アキ挿入 + 自動行頭字下げ（統合処理）
	 *
	 * narou.rb互換: converterbase.rb:605-636
	 *
	 * 1. 行頭の開き括弧の前に ［＃二分アキ］ を挿入
	 * 2. 字下げが必要な行に全角スペースを挿入
	 */
	private String halfIndentBracketAndAutoIndent(String text) {
		String[] lines = text.split("\n", -1);
		boolean doHalfIndent = settings.isEnableHalfIndentBracket();
		boolean doAutoIndent = settings.isEnableAutoIndent();

		// 字下げ判定: 50%以上の行が未字下げなら字下げを適用 (narou.rb: inspect_indent)
		boolean shouldIndent = false;
		if (doAutoIndent) {
			shouldIndent = inspectIndent(lines);
		}

		List<String> result = new ArrayList<>();
		for (String line : lines) {
			if (line.isEmpty()) {
				result.add(line);
				continue;
			}

			// 二分アキ: 行頭空白を除去し開き括弧の前に ［＃二分アキ］ を挿入
			if (doHalfIndent) {
				Matcher m = HALF_INDENT_TARGET.matcher(line);
				if (m.find()) {
					line = "［＃二分アキ］" + m.group(1) + line.substring(m.end());
					result.add(line);
					continue;
				}
			}

			// 自動字下げ
			if (shouldIndent) {
				char firstChar = line.charAt(0);
				// ダッシュ行: ――で始まる行は「　――」にする
				if (line.startsWith("――")) {
					line = "　" + line;
				}
				// 注記行・字下げ対象外文字で始まる行はスキップ
				else if (AUTO_INDENT_IGNORE.indexOf(firstChar) < 0
						&& firstChar != '　' && firstChar != ' ' && firstChar != '\t'
						&& firstChar != '［') {
					// 中黒1文字のみの場合はスキップ (三点リーダー代替対策)
					if (firstChar == '・' && (line.length() < 2 || line.charAt(1) != '・')) {
						// スキップ
					} else {
						line = "　" + line;
					}
				}
			}

			result.add(line);
		}

		return String.join("\n", result);
	}

	/**
	 * 字下げ判定 (narou.rb: Inspector#inspect_indent)
	 *
	 * 判定除外文字で始まる行を除き、50%以上が未字下げなら true を返す。
	 */
	private boolean inspectIndent(String[] lines) {
		int targetCount = 0;
		int noIndentCount = 0;
		for (String line : lines) {
			if (line.isEmpty()) continue;
			char first = line.charAt(0);
			if (IGNORE_INDENT_CHARS.indexOf(first) >= 0) continue;
			if (first == '［') continue; // 注記行
			targetCount++;
			if (first != '　' && first != ' ' && first != '\t') {
				noIndentCount++;
			}
		}
		return targetCount > 0 && (double) noIndentCount / targetCount > 0.5;
	}

	/**
	 * 漢数字変換
	 *
	 * narou.rb互換: converterbase.rb:104-133
	 * 半角・全角数字を漢数字に変換する。カンマ含有数字列はそのまま全角化。
	 */
	private String convertNumToKanji(String text) {
		String[] lines = text.split("\n", -1);
		List<String> result = new ArrayList<>();
		for (String line : lines) {
			// 注記行はスキップ
			if (line.startsWith("［＃")) {
				result.add(line);
				continue;
			}
			// URL含有行・変換日時行はスキップ (漢数字化すると不自然)
			if (line.contains("://") || line.startsWith("変換日時")) {
				result.add(line);
				continue;
			}
			result.add(convertNumToKanjiLine(line));
		}
		return String.join("\n", result);
	}

	private String convertNumToKanjiLine(String line) {
		// 注記 ［＃...］ 内の数字は変換しない
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while (pos < line.length()) {
			// 注記の開始を検出
			int chukiStart = line.indexOf("［＃", pos);
			if (chukiStart < 0) {
				// 残り全体を変換
				sb.append(convertNumsInSegment(line.substring(pos)));
				break;
			}
			// 注記の前を変換
			if (chukiStart > pos) {
				sb.append(convertNumsInSegment(line.substring(pos, chukiStart)));
			}
			// 注記の終了を検出
			int chukiEnd = line.indexOf("］", chukiStart);
			if (chukiEnd < 0) {
				// 閉じ注記がない場合、残り全体をそのまま
				sb.append(line.substring(chukiStart));
				pos = line.length();
				break;
			}
			// 注記部分をそのまま出力
			sb.append(line, chukiStart, chukiEnd + 1);
			pos = chukiEnd + 1;
		}
		return sb.toString();
	}

	private String convertNumsInSegment(String segment) {
		StringBuilder sb = new StringBuilder();
		Matcher m = Pattern.compile("[\\d０-９,，]+").matcher(segment);
		int lastEnd = 0;
		while (m.find()) {
			sb.append(segment, lastEnd, m.start());
			String match = m.group();
			match = zenkakuNumToHankaku(match);
			if (match.contains(",") || match.contains("，")) {
				sb.append(hankakuNumToZenkaku(match.replace("，", ",")));
			} else {
				sb.append(hankakuToKanji(match));
			}
			lastEnd = m.end();
		}
		sb.append(segment, lastEnd, segment.length());
		return sb.toString();
	}

	private String zenkakuNumToHankaku(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c >= '０' && c <= '９') {
				sb.append((char) (c - '０' + '0'));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private String hankakuNumToZenkaku(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c >= '0' && c <= '9') {
				sb.append((char) (c - '0' + '０'));
			} else if (c == ',') {
				sb.append('，');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private String hankakuToKanji(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c >= '0' && c <= '9') {
				sb.append(KANJI_NUM[c - '0']);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * 英字全角化
	 *
	 * narou.rb互換: converterbase.rb:521-536
	 * 短い英単語は全角化、英文(2語以上)と長い英単語(8文字以上)は半角保持。
	 */
	private String alphabetToZenkaku(String text, boolean force) {
		String[] lines = text.split("\n", -1);
		List<String> result = new ArrayList<>();
		for (String line : lines) {
			// 注記行はスキップ
			if (line.startsWith("［＃")) {
				result.add(line);
				continue;
			}
			// URL含有行はスキップ (全角化するとリンクが壊れる)
			if (line.contains("://")) {
				result.add(line);
				continue;
			}
			result.add(alphabetToZenkakuLine(line, force));
		}
		return String.join("\n", result);
	}

	private String alphabetToZenkakuLine(String line, boolean force) {
		// 注記 ［＃...］ 内の英字は変換しない
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while (pos < line.length()) {
			int chukiStart = line.indexOf("［＃", pos);
			if (chukiStart < 0) {
				sb.append(convertAlphaInSegment(line.substring(pos), force));
				break;
			}
			if (chukiStart > pos) {
				sb.append(convertAlphaInSegment(line.substring(pos, chukiStart), force));
			}
			int chukiEnd = line.indexOf("］", chukiStart);
			if (chukiEnd < 0) {
				sb.append(line.substring(chukiStart));
				pos = line.length();
				break;
			}
			sb.append(line, chukiStart, chukiEnd + 1);
			pos = chukiEnd + 1;
		}
		return sb.toString();
	}

	private String convertAlphaInSegment(String segment, boolean force) {
		Matcher m = ENGLISH_SENTENCES_PATTERN.matcher(segment);
		StringBuilder sb = new StringBuilder();
		int lastEnd = 0;
		while (m.find()) {
			sb.append(segment, lastEnd, m.start());
			String match = m.group();
			if (force) {
				sb.append(alphaToZenkaku(match));
			} else if (isSentence(match) || shouldWordBeHankaku(match)) {
				sb.append(match);
			} else {
				sb.append(alphaToZenkaku(match));
			}
			lastEnd = m.end();
		}
		sb.append(segment, lastEnd, segment.length());
		return sb.toString();
	}

	private boolean isSentence(String match) {
		return match.split(" ").length >= 2;
	}

	private boolean shouldWordBeHankaku(String word) {
		return word.length() >= ENGLISH_SENTENCES_MIN_LENGTH && word.matches(".*[a-zA-Z].*");
	}

	private String alphaToZenkaku(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c >= 'a' && c <= 'z') {
				sb.append((char) (c - 'a' + 'ａ'));
			} else if (c >= 'A' && c <= 'Z') {
				sb.append((char) (c - 'A' + 'Ａ'));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * 読了表示の追加
	 *
	 * narou.rb互換: template/novel.txt.erb:90-92
	 * テキスト末尾に「（本を読み終わりました）」を追加する。
	 */
	private String appendEndOfBook(String text) {
		// 末尾の改行を保持しつつ追加
		if (!text.endsWith("\n")) {
			text += "\n";
		}
		text += "\n［＃ここから地付き］［＃小書き］（本を読み終わりました）［＃小書き終わり］［＃ここで地付き終わり］\n";
		return text;
	}

	/**
	 * 改ページ直後の見出し化
	 *
	 * narou.rb互換: converterbase.rb:1248-1283
	 *
	 * ［＃改ページ］の直後の行を見出しとして検出し、
	 * ［＃３字下げ］［＃中見出し］...［＃中見出し終わり］で囲む。
	 */
	private String enchantMidashi(String text) {
		String[] lines = text.split("\n", -1);
		List<String> result = new ArrayList<>();

		boolean nextLineIsMidashi = false;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			// 改ページの直後
			if (line.equals("［＃改ページ］")) {
				result.add(line);
				nextLineIsMidashi = true;
				continue;
			}

			// 改ページ直後の行を見出し化
			if (nextLineIsMidashi && !line.isEmpty() && !line.startsWith("［＃")) {
				// 既に見出し注記がある場合はスキップ
				if (!line.contains("［＃中見出し］") && !line.contains("［＃大見出し］")) {
					line = "［＃３字下げ］［＃中見出し］" + line + "［＃中見出し終わり］";
				}
				nextLineIsMidashi = false;
			} else if (nextLineIsMidashi && (line.isEmpty() || line.startsWith("［＃"))) {
				// 空行または注記の場合は見出し化をスキップ
				nextLineIsMidashi = false;
			}

			result.add(line);
		}

		return String.join("\n", result);
	}

	/**
	 * かぎ括弧の開閉チェック
	 *
	 * narou.rb互換: converterbase.rb:669-729
	 *
	 * かぎ括弧のネストが正しいかをチェックし、警告を出力する。
	 */
	private void inspectBrackets(String text) {
		int lineNumber = 1;
		for (String line : text.split("\n")) {
			int depth = 0;
			int maxDepth = 0;

			for (char ch : line.toCharArray()) {
				if (OPEN_BRACKETS.indexOf(ch) >= 0) {
					depth++;
					maxDepth = Math.max(maxDepth, depth);
				} else if (CLOSE_BRACKETS.indexOf(ch) >= 0) {
					depth--;
				}

				// 負の深度（閉じ括弧が多い）
				if (depth < 0) {
					LogAppender.println("警告: かぎ括弧の閉じが多すぎます（行 " + lineNumber + "）: " + line.trim());
					break;
				}
			}

			// 行末で括弧が閉じていない
			if (depth > 0) {
				LogAppender.println("警告: かぎ括弧が閉じていません（行 " + lineNumber + "）: " + line.trim());
			}

			lineNumber++;
		}
	}

	/**
	 * かぎ括弧内の自動連結（ファイナライズ版）
	 *
	 * narou.rb互換: converterbase.rb:520-609
	 *
	 * printText()のTextNodeレベル処理では対応できない
	 * &lt;br&gt;タグ由来の改行を含む、行をまたいだかぎ括弧内の連結を行う。
	 */
	private String autoJoinInBrackets(String text) {
		// 「」内と『』内の改行を全角スペースに置換（繰り返し適用）
		boolean changed = true;
		while (changed) {
			String before = text;
			// 「...」内の改行を検出して全角スペースに置換
			text = text.replaceAll("「([^「」]*)\n([^「」]*)」", "「$1　$2」");
			// 『...』内の改行も処理
			text = text.replaceAll("『([^『』]*)\n([^『』]*)』", "『$1　$2』");
			changed = !text.equals(before);
		}
		return text;
	}

	/**
	 * 行末読点での自動連結（ファイナライズ版）
	 *
	 * narou.rb互換: converterbase.rb:611-667
	 *
	 * printText()のTextNodeレベル処理では対応できない
	 * &lt;br&gt;タグ由来の改行を含む、行末読点後の改行を削除する。
	 */
	private String autoJoinLine(String text) {
		text = text.replace("、\n", "、");
		return text;
	}

	/**
	 * replace.txt によるテキスト置換
	 *
	 * narou.rb互換: converterbase.rb:1436-1445
	 *
	 * ユーザー定義の置換ルール（タブ区切りの検索文字列→置換文字列ペア）を
	 * テキスト全体に順次適用する。
	 */
	private String applyReplacePatterns(String text) {
		List<String[]> patterns = settings.getTextReplacePatterns();
		LogAppender.println("replace.txt: " + patterns.size() + "件の置換ルールを適用");
		for (String[] pair : patterns) {
			text = text.replace(pair[0], pair[1]);
		}
		return text;
	}
}
