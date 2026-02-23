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

		// 2. 前書き・後書きの自動検出
		if (settings.isEnableAuthorComments()) {
			content = detectAndMarkAuthorComments(content);
		}

		// 3. 自動行頭字下げ
		if (settings.isEnableAutoIndent()) {
			content = applyAutoIndent(content);
		}

		// 4. 改ページ直後の見出し化
		if (settings.isEnableEnchantMidashi()) {
			content = enchantMidashi(content);
		}

		// 5. かぎ括弧内の自動連結（<br>タグ由来の改行にも対応）
		if (settings.isEnableAutoJoinInBrackets()) {
			content = autoJoinInBrackets(content);
		}

		// 6. 行末読点での自動連結（<br>タグ由来の改行にも対応）
		if (settings.isEnableAutoJoinLine()) {
			content = autoJoinLine(content);
		}

		// 7. かぎ括弧の開閉チェック（警告のみ）
		if (settings.isEnableInspectInvalidOpenCloseBrackets()) {
			inspectBrackets(content);
		}

		// 8. replace.txt によるテキスト置換（最後に適用）
		if (!settings.getTextReplacePatterns().isEmpty()) {
			content = applyReplacePatterns(content);
		}

		// 9. ファイルに書き戻す
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
	 * 自動行頭字下げ
	 *
	 * narou.rb互換: converterbase.rb:1031-1094
	 *
	 * 段落の開始を検出し、字下げを適用する。
	 * ただし、既に字下げされている場合や特殊な行（見出し、注記など）は対象外。
	 */
	private String applyAutoIndent(String text) {
		String[] lines = text.split("\n", -1);
		List<String> result = new ArrayList<>();

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			// 空行はそのまま
			if (line.isEmpty()) {
				result.add(line);
				continue;
			}

			// 既に字下げされている行はスキップ
			if (line.startsWith("　") || line.startsWith(" ")) {
				result.add(line);
				continue;
			}

			// 注記行はスキップ（［＃...］で始まる行）
			if (line.startsWith("［＃")) {
				result.add(line);
				continue;
			}

			// かぎ括弧で始まる行はスキップ（二分アキが入る可能性があるため）
			char firstChar = line.charAt(0);
			if (OPEN_BRACKETS.indexOf(firstChar) >= 0) {
				result.add(line);
				continue;
			}

			// 見出し・区切り線・挿絵などの特殊行はスキップ
			if (line.contains("［＃中見出し］") || line.contains("［＃大見出し］")
				|| line.contains("［＃区切り線］") || line.contains("［＃挿絵")
				|| line.contains("［＃改ページ］")) {
				result.add(line);
				continue;
			}

			// 前の行が空行（段落の開始）の場合のみ字下げ
			boolean isParagraphStart = (i == 0) || (i > 0 && lines[i - 1].isEmpty());
			if (isParagraphStart) {
				result.add("　" + line);
			} else {
				result.add(line);
			}
		}

		return String.join("\n", result);
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
