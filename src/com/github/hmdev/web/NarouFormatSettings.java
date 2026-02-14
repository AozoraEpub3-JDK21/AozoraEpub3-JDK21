package com.github.hmdev.web;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * narou.rb互換のフォーマット設定を管理するクラス。
 * INI形式の設定ファイル(setting_narourb.ini)を読み書きする。
 * narou.rbのsetting.iniとキー互換性を持つ。
 */
public class NarouFormatSettings {

	/** 前書き・後書きスタイル: "css" | "simple" | "plain" */
	private String authorCommentStyle = "css";

	/** 横書きモード (字下げが3字→1字に変わる) */
	private boolean enableYokogaki = false;

	/** 本の終了マーカー表示 */
	private boolean enableDisplayEndOfBook = false;

	/** 章中表紙で「ページの左右中央」を使用 */
	private boolean chapterUseCenterPage = true;

	/** 章中表紙で「柱」にタイトルを表示 */
	private boolean chapterUseHashira = true;

	/** あらすじを表紙ページに含める */
	private boolean includeStory = true;

	/** 掲載URLを表紙ページに含める */
	private boolean includeTocUrl = true;

	/** 更新日時を各話に表示 */
	private boolean showPostDate = true;

	/** 行頭のかぎ括弧に二分アキを挿入 */
	private boolean enableHalfIndentBracket = true;

	/** 数字の漢数字化 (123 → 一二三) */
	private boolean enableConvertNumToKanji = false;

	/** 漢数字の単位化 (1000 → 千、10000 → 一万) */
	private boolean enableKanjiNumWithUnits = false;

	/** 単位化する際の下位桁ゼロ数 (デフォルト2: 100以上で単位化) */
	private int kanjiNumWithUnitsLowerDigitZero = 2;

	/** 記号の全角化 (- → －、< → 〈など) */
	private boolean enableConvertSymbolsToZenkaku = false;

	/** かぎ括弧内の自動連結 (改行を全角スペースに) */
	private boolean enableAutoJoinInBrackets = false;

	/** 行末読点での自動連結 */
	private boolean enableAutoJoinLine = false;

	// === Getters / Setters ===

	public String getAuthorCommentStyle() { return authorCommentStyle; }
	public void setAuthorCommentStyle(String style) {
		if ("css".equals(style) || "simple".equals(style) || "plain".equals(style)) {
			this.authorCommentStyle = style;
		}
	}

	public boolean isEnableYokogaki() { return enableYokogaki; }
	public void setEnableYokogaki(boolean v) { this.enableYokogaki = v; }

	public boolean isEnableDisplayEndOfBook() { return enableDisplayEndOfBook; }
	public void setEnableDisplayEndOfBook(boolean v) { this.enableDisplayEndOfBook = v; }

	public boolean isChapterUseCenterPage() { return chapterUseCenterPage; }
	public void setChapterUseCenterPage(boolean v) { this.chapterUseCenterPage = v; }

	public boolean isChapterUseHashira() { return chapterUseHashira; }
	public void setChapterUseHashira(boolean v) { this.chapterUseHashira = v; }

	public boolean isIncludeStory() { return includeStory; }
	public void setIncludeStory(boolean v) { this.includeStory = v; }

	public boolean isIncludeTocUrl() { return includeTocUrl; }
	public void setIncludeTocUrl(boolean v) { this.includeTocUrl = v; }

	public boolean isShowPostDate() { return showPostDate; }
	public void setShowPostDate(boolean v) { this.showPostDate = v; }

	public boolean isEnableHalfIndentBracket() { return enableHalfIndentBracket; }
	public void setEnableHalfIndentBracket(boolean v) { this.enableHalfIndentBracket = v; }

	public boolean isEnableConvertNumToKanji() { return enableConvertNumToKanji; }
	public void setEnableConvertNumToKanji(boolean v) { this.enableConvertNumToKanji = v; }

	public boolean isEnableKanjiNumWithUnits() { return enableKanjiNumWithUnits; }
	public void setEnableKanjiNumWithUnits(boolean v) { this.enableKanjiNumWithUnits = v; }

	public int getKanjiNumWithUnitsLowerDigitZero() { return kanjiNumWithUnitsLowerDigitZero; }
	public void setKanjiNumWithUnitsLowerDigitZero(int v) { this.kanjiNumWithUnitsLowerDigitZero = v; }

	public boolean isEnableConvertSymbolsToZenkaku() { return enableConvertSymbolsToZenkaku; }
	public void setEnableConvertSymbolsToZenkaku(boolean v) { this.enableConvertSymbolsToZenkaku = v; }

	public boolean isEnableAutoJoinInBrackets() { return enableAutoJoinInBrackets; }
	public void setEnableAutoJoinInBrackets(boolean v) { this.enableAutoJoinInBrackets = v; }

	public boolean isEnableAutoJoinLine() { return enableAutoJoinLine; }
	public void setEnableAutoJoinLine(boolean v) { this.enableAutoJoinLine = v; }

	/** 字下げの文字数を返す (横書き:1字、縦書き:3字) */
	public String getIndent() {
		return enableYokogaki ? "１" : "３";
	}

	/**
	 * INI形式の設定ファイルから読み込み。
	 * narou.rbのsetting.iniと互換性のあるキーを使用。
	 */
	public void load(File file) throws IOException {
		if (!file.exists()) return;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
				int eq = line.indexOf('=');
				if (eq < 0) continue;
				String key = line.substring(0, eq).trim();
				String value = line.substring(eq + 1).trim();
				// 引用符を除去
				if (value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				applyKey(key, value);
			}
		}
	}

	private void applyKey(String key, String value) {
		switch (key) {
			case "author_comment_style":
				setAuthorCommentStyle(value);
				break;
			case "enable_yokogaki":
				enableYokogaki = toBoolean(value);
				break;
			case "enable_display_end_of_book":
				enableDisplayEndOfBook = toBoolean(value);
				break;
			case "chapter_use_center_page":
				chapterUseCenterPage = toBoolean(value);
				break;
			case "chapter_use_hashira":
				chapterUseHashira = toBoolean(value);
				break;
			case "include_story":
				includeStory = toBoolean(value);
				break;
			case "include_toc_url":
				includeTocUrl = toBoolean(value);
				break;
			case "show_post_date":
				showPostDate = toBoolean(value);
				break;
			case "enable_half_indent_bracket":
				enableHalfIndentBracket = toBoolean(value);
				break;
			case "enable_convert_num_to_kanji":
				enableConvertNumToKanji = toBoolean(value);
				break;
			case "enable_kanji_num_with_units":
				enableKanjiNumWithUnits = toBoolean(value);
				break;
			case "kanji_num_with_units_lower_digit_zero":
				try {
					kanjiNumWithUnitsLowerDigitZero = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					// デフォルト値を維持
				}
				break;
			case "enable_convert_symbols_to_zenkaku":
				enableConvertSymbolsToZenkaku = toBoolean(value);
				break;
			case "enable_auto_join_in_brackets":
				enableAutoJoinInBrackets = toBoolean(value);
				break;
			case "enable_auto_join_line":
				enableAutoJoinLine = toBoolean(value);
				break;
		}
	}

	private static boolean toBoolean(String value) {
		return "true".equalsIgnoreCase(value) || "1".equals(value);
	}

	/**
	 * デフォルト設定ファイルを生成する。
	 * ファイルが存在しない場合のみ作成。
	 */
	public static void generateDefaultIfMissing(File file) throws IOException {
		if (file.exists()) return;
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
			bw.write("; AozoraEpub3-JDK21 narou.rb互換フォーマット設定\n");
			bw.write("; narou.rbから呼び出される場合はnarou.rbのsetting.iniが優先されます\n");
			bw.write("; この設定はAozoraEpub3-JDK21が直接ダウンロード時に適用されます\n");
			bw.write("\n");
			bw.write("; 前書き・後書きのスタイル: \"css\" / \"simple\" / \"plain\"\n");
			bw.write("; css    = ［＃ここから前書き/後書き］マーカーで囲む (CSSで装飾)\n");
			bw.write("; simple = 8字下げ＋2段階小さな文字 (Kobo等の互換性が高い)\n");
			bw.write("; plain  = 区切り線で本文と分離するだけ\n");
			bw.write("author_comment_style = \"css\"\n");
			bw.write("\n");
			bw.write("; 横書きモード (true: 字下げ1字、false: 字下げ3字)\n");
			bw.write("enable_yokogaki = false\n");
			bw.write("\n");
			bw.write("; 本の終了マーカー表示\n");
			bw.write("enable_display_end_of_book = false\n");
			bw.write("\n");
			bw.write("; 章中表紙のレイアウト\n");
			bw.write("chapter_use_center_page = true\n");
			bw.write("chapter_use_hashira = true\n");
			bw.write("\n");
			bw.write("; あらすじを表紙ページに含める\n");
			bw.write("include_story = true\n");
			bw.write("\n");
			bw.write("; 掲載URLを表紙ページに含める\n");
			bw.write("include_toc_url = true\n");
			bw.write("\n");
			bw.write("; 更新日時を各話に表示\n");
			bw.write("show_post_date = true\n");
			bw.write("\n");
			bw.write("; 行頭のかぎ括弧に二分アキを挿入 (縦書き時の見た目改善)\n");
			bw.write("enable_half_indent_bracket = true\n");
			bw.write("\n");
			bw.write("; 数字の漢数字化 (123 → 一二三)\n");
			bw.write("enable_convert_num_to_kanji = false\n");
			bw.write("\n");
			bw.write("; 漢数字の単位化 (1000 → 千、10000 → 一万)\n");
			bw.write("enable_kanji_num_with_units = false\n");
			bw.write("\n");
			bw.write("; 単位化する際の下位桁ゼロ数 (2: 100以上で単位化)\n");
			bw.write("kanji_num_with_units_lower_digit_zero = 2\n");
			bw.write("\n");
			bw.write("; 記号の全角化 (- → －、< → 〈など)\n");
			bw.write("enable_convert_symbols_to_zenkaku = false\n");
			bw.write("\n");
			bw.write("; かぎ括弧内の自動連結 (改行を全角スペースに)\n");
			bw.write("enable_auto_join_in_brackets = false\n");
			bw.write("\n");
			bw.write("; 行末読点での自動連結\n");
			bw.write("enable_auto_join_line = false\n");
		}
	}
}
