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

	// === ファイナライズ処理関連の設定 ===

	/** 前書き・後書きの自動検出 (*44個/*48個のパターン) */
	private boolean enableAuthorComments = true;

	/** 自動行頭字下げ */
	private boolean enableAutoIndent = false;

	/** 改ページ直後の見出し化 */
	private boolean enableEnchantMidashi = true;

	/** かぎ括弧の開閉チェック（警告のみ） */
	private boolean enableInspectInvalidOpenCloseBrackets = true;

	/** ファイナライズ処理の最大ファイルサイズ（MB単位、デフォルト100MB） */
	private int maxFinalizableFileSizeMB = 100;

	// === 追加変換処理の設定 ===

	/** 分数変換 (1/2 → 2分の1) */
	private boolean enableTransformFraction = false;

	/** 日付変換 (2024/1/1 → 2024年1月1日) */
	private boolean enableTransformDate = false;

	/** 日付フォーマット */
	private String dateFormat = "%Y年%m月%d日";

	/** 三点リーダー変換 (・・・ → ……) */
	private boolean enableConvertHorizontalEllipsis = false;

	/** 濁点フォント処理 (か゛ → ［＃濁点付き片仮名か、1-86-12］) */
	private boolean enableDakutenFont = false;

	/** 長音記号の変換 (ーー → ――) */
	private boolean enableProlongedSoundMarkToDash = false;

	/** なろう独自タグの処理 ([newpage] → ［＃改ページ］) */
	private boolean enableNarouTag = true;

	// === テキスト置換パターン (replace_narourb.txt) ===

	/** narou.rb互換のテキスト置換パターン（検索文字列→置換文字列のペア） */
	private List<String[]> textReplacePatterns = new ArrayList<>();

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

	public boolean isEnableAuthorComments() { return enableAuthorComments; }
	public void setEnableAuthorComments(boolean v) { this.enableAuthorComments = v; }

	public boolean isEnableAutoIndent() { return enableAutoIndent; }
	public void setEnableAutoIndent(boolean v) { this.enableAutoIndent = v; }

	public boolean isEnableEnchantMidashi() { return enableEnchantMidashi; }
	public void setEnableEnchantMidashi(boolean v) { this.enableEnchantMidashi = v; }

	public boolean isEnableInspectInvalidOpenCloseBrackets() { return enableInspectInvalidOpenCloseBrackets; }
	public void setEnableInspectInvalidOpenCloseBrackets(boolean v) { this.enableInspectInvalidOpenCloseBrackets = v; }

	public int getMaxFinalizableFileSizeMB() { return maxFinalizableFileSizeMB; }
	public void setMaxFinalizableFileSizeMB(int v) { this.maxFinalizableFileSizeMB = v; }

	public boolean isEnableTransformFraction() { return enableTransformFraction; }
	public void setEnableTransformFraction(boolean v) { this.enableTransformFraction = v; }

	public boolean isEnableTransformDate() { return enableTransformDate; }
	public void setEnableTransformDate(boolean v) { this.enableTransformDate = v; }

	public String getDateFormat() { return dateFormat; }
	public void setDateFormat(String v) { this.dateFormat = v; }

	public boolean isEnableConvertHorizontalEllipsis() { return enableConvertHorizontalEllipsis; }
	public void setEnableConvertHorizontalEllipsis(boolean v) { this.enableConvertHorizontalEllipsis = v; }

	public boolean isEnableDakutenFont() { return enableDakutenFont; }
	public void setEnableDakutenFont(boolean v) { this.enableDakutenFont = v; }

	public boolean isEnableProlongedSoundMarkToDash() { return enableProlongedSoundMarkToDash; }
	public void setEnableProlongedSoundMarkToDash(boolean v) { this.enableProlongedSoundMarkToDash = v; }

	public boolean isEnableNarouTag() { return enableNarouTag; }
	public void setEnableNarouTag(boolean v) { this.enableNarouTag = v; }

	public List<String[]> getTextReplacePatterns() { return textReplacePatterns; }

	/**
	 * narou.rb互換のreplace.txtを読み込む。
	 * フォーマット: タブ区切りで「検索文字列\t置換文字列」、`;`始まりはコメント。
	 */
	public void loadReplacePatterns(File file) throws IOException {
		textReplacePatterns.clear();
		if (!file.exists()) return;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.replaceAll("[\\r\\n]+$", "");
				if (line.isEmpty() || line.charAt(0) == ';' || line.charAt(0) == '#') continue;
				String[] pair = line.split("\t", 2);
				if (pair.length == 2 && pair[0].length() > 0) {
					textReplacePatterns.add(new String[]{pair[0], pair[1]});
				}
			}
		}
	}

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
			case "enable_author_comments":
				enableAuthorComments = toBoolean(value);
				break;
			case "enable_auto_indent":
				enableAutoIndent = toBoolean(value);
				break;
			case "enable_enchant_midashi":
				enableEnchantMidashi = toBoolean(value);
				break;
			case "enable_inspect_invalid_openclose_brackets":
				enableInspectInvalidOpenCloseBrackets = toBoolean(value);
				break;
			case "max_finalizable_file_size_mb":
				try {
					maxFinalizableFileSizeMB = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					// デフォルト値を維持
				}
				break;
			case "enable_transform_fraction":
				enableTransformFraction = toBoolean(value);
				break;
			case "enable_transform_date":
				enableTransformDate = toBoolean(value);
				break;
			case "date_format":
				dateFormat = value;
				break;
			case "enable_convert_horizontal_ellipsis":
				enableConvertHorizontalEllipsis = toBoolean(value);
				break;
			case "enable_dakuten_font":
				enableDakutenFont = toBoolean(value);
				break;
			case "enable_prolonged_sound_mark_to_dash":
				enableProlongedSoundMarkToDash = toBoolean(value);
				break;
			case "enable_narou_tag":
				enableNarouTag = toBoolean(value);
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
			bw.write("\n");
			bw.write("; === ファイナライズ処理 ===\n");
			bw.write("\n");
			bw.write("; 前書き・後書きの自動検出\n");
			bw.write("enable_author_comments = true\n");
			bw.write("\n");
			bw.write("; 自動行頭字下げ\n");
			bw.write("enable_auto_indent = false\n");
			bw.write("\n");
			bw.write("; 改ページ直後の見出し化\n");
			bw.write("enable_enchant_midashi = true\n");
			bw.write("\n");
			bw.write("; かぎ括弧の開閉チェック（警告のみ）\n");
			bw.write("enable_inspect_invalid_openclose_brackets = true\n");
			bw.write("\n");
			bw.write("; === 追加変換処理 ===\n");
			bw.write("\n");
			bw.write("; 分数変換 (1/2 → 2分の1)\n");
			bw.write("enable_transform_fraction = false\n");
			bw.write("\n");
			bw.write("; 日付変換 (2024/1/1 → 2024年1月1日)\n");
			bw.write("enable_transform_date = false\n");
			bw.write("\n");
			bw.write("; 日付フォーマット\n");
			bw.write("date_format = %Y年%m月%d日\n");
			bw.write("\n");
			bw.write("; 三点リーダー変換 (・・・ → ……)\n");
			bw.write("enable_convert_horizontal_ellipsis = false\n");
			bw.write("\n");
			bw.write("; 濁点フォント処理\n");
			bw.write("enable_dakuten_font = false\n");
			bw.write("\n");
			bw.write("; 長音記号の変換 (ーー → ――)\n");
			bw.write("enable_prolonged_sound_mark_to_dash = false\n");
			bw.write("\n");
			bw.write("; なろう独自タグの処理 ([newpage] → ［＃改ページ］)\n");
			bw.write("enable_narou_tag = true\n");
		}
	}

	/**
	 * 現在の設定をINI形式でファイルに保存する。
	 */
	public void save(File file) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
			bw.write("; AozoraEpub3-JDK21 narou.rb互換フォーマット設定\n");
			bw.write("; このファイルはGUI設定ダイアログから自動生成されます\n");
			bw.write("\n");
			writeEntry(bw, "author_comment_style", "\"" + authorCommentStyle + "\"", "前書き・後書きのスタイル");
			writeEntry(bw, "enable_yokogaki", enableYokogaki, "横書きモード");
			writeEntry(bw, "enable_display_end_of_book", enableDisplayEndOfBook, "本の終了マーカー表示");
			writeEntry(bw, "chapter_use_center_page", chapterUseCenterPage, "章中表紙のページ中央");
			writeEntry(bw, "chapter_use_hashira", chapterUseHashira, "章中表紙の柱表示");
			writeEntry(bw, "include_story", includeStory, "あらすじを表紙に含める");
			writeEntry(bw, "include_toc_url", includeTocUrl, "掲載URLを表紙に含める");
			writeEntry(bw, "show_post_date", showPostDate, "更新日時を各話に表示");
			bw.write("\n");
			writeEntry(bw, "enable_half_indent_bracket", enableHalfIndentBracket, "行頭かぎ括弧に二分アキ");
			writeEntry(bw, "enable_convert_num_to_kanji", enableConvertNumToKanji, "数字の漢数字化");
			writeEntry(bw, "enable_kanji_num_with_units", enableKanjiNumWithUnits, "漢数字の単位化");
			writeEntry(bw, "kanji_num_with_units_lower_digit_zero", kanjiNumWithUnitsLowerDigitZero, "単位化の下位桁ゼロ数");
			writeEntry(bw, "enable_convert_symbols_to_zenkaku", enableConvertSymbolsToZenkaku, "記号の全角化");
			writeEntry(bw, "enable_auto_join_in_brackets", enableAutoJoinInBrackets, "かぎ括弧内の自動連結");
			writeEntry(bw, "enable_auto_join_line", enableAutoJoinLine, "行末読点での自動連結");
			bw.write("\n");
			writeEntry(bw, "enable_author_comments", enableAuthorComments, "前書き・後書きの自動検出");
			writeEntry(bw, "enable_auto_indent", enableAutoIndent, "自動行頭字下げ");
			writeEntry(bw, "enable_enchant_midashi", enableEnchantMidashi, "改ページ直後の見出し化");
			writeEntry(bw, "enable_inspect_invalid_openclose_brackets", enableInspectInvalidOpenCloseBrackets, "かぎ括弧の開閉チェック");
			bw.write("\n");
			writeEntry(bw, "enable_transform_fraction", enableTransformFraction, "分数変換");
			writeEntry(bw, "enable_transform_date", enableTransformDate, "日付変換");
			writeEntry(bw, "date_format", dateFormat, "日付フォーマット");
			writeEntry(bw, "enable_convert_horizontal_ellipsis", enableConvertHorizontalEllipsis, "三点リーダー変換");
			writeEntry(bw, "enable_dakuten_font", enableDakutenFont, "濁点フォント処理");
			writeEntry(bw, "enable_prolonged_sound_mark_to_dash", enableProlongedSoundMarkToDash, "長音記号の変換");
			writeEntry(bw, "enable_narou_tag", enableNarouTag, "なろう独自タグの処理");
		}
	}

	private static void writeEntry(BufferedWriter bw, String key, boolean value, String comment) throws IOException {
		bw.write("; " + comment + "\n");
		bw.write(key + " = " + value + "\n");
	}

	private static void writeEntry(BufferedWriter bw, String key, int value, String comment) throws IOException {
		bw.write("; " + comment + "\n");
		bw.write(key + " = " + value + "\n");
	}

	private static void writeEntry(BufferedWriter bw, String key, String value, String comment) throws IOException {
		bw.write("; " + comment + "\n");
		bw.write(key + " = " + value + "\n");
	}
}
