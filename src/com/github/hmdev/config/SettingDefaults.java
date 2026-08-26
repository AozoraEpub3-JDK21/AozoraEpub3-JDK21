package com.github.hmdev.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * GUI と CLI が共有する ini 設定の既定値テーブル。
 *
 * <p>ini にキーが無いときの値は「GUI のチェックボックスの初期状態」が正であり、
 * CLI もこの表を通して同じ値を得る。CLI が {@code "1".equals(props.getProperty(key))} で
 * 直接読むと、キーを持たない ini(同梱の AozoraEpub3.ini など)を使ったときに GUI と挙動がずれる
 * (docs/code-audit-followups.md 項目 22 — 章見出しが CLI でのみ目次に入らなかった)。</p>
 *
 * <p>キーを追加するときは、GUI のウィジェット生成と CLI の読み出しの両方をこの表に向けること。
 * 表に無いキーを渡すと {@link IllegalArgumentException} になるので、キー名のタイポは
 * (かつての {@code hapterNumParenTitle} のように黙って false になるのではなく)その場で失敗する。</p>
 */
public final class SettingDefaults
{
	private SettingDefaults() {}

	private static final Map<String, Boolean> BOOLEANS = createBooleanDefaults();
	private static final Map<String, Integer> INTEGERS = createIntegerDefaults();

	private static Map<String, Boolean> createBooleanDefaults()
	{
		Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
		//目次に入れるページ
		map.put("CoverPageToc", false);
		map.put("TitleToc", true);
		//目次階層化
		map.put("NavNest", false);
		map.put("NcxNest", false);
		//見出しの拾い方
		map.put("ChapterUseNextLine", false);
		map.put("ChapterExclude", true);
		map.put("ChapterSection", true);
		//見出し注記
		map.put("ChapterH", true);
		map.put("ChapterH1", true);
		map.put("ChapterH2", true);
		map.put("ChapterH3", true);
		map.put("SameLineChapter", false);
		//章名・章番号・パターン
		map.put("ChapterName", true);
		map.put("ChapterNumOnly", false);
		map.put("ChapterNumTitle", false);
		map.put("ChapterNumParen", false);
		map.put("ChapterNumParenTitle", false);
		map.put("ChapterPattern", false);
		//ページ出力 (docs/code-audit-followups.md 項目 24)
		map.put("CoverPage", true);
		map.put("TitlePageWrite", true);
		map.put("TocPage", false);
		map.put("TocVertical", true);
		//本文変換
		map.put("AutoYoko", true);
		map.put("AutoYokoNum1", false);
		map.put("AutoYokoNum3", false);
		map.put("AutoYokoEQ1", false);
		map.put("MarkId", false);
		map.put("CommentPrint", false);
		map.put("CommentConvert", false);
		//自動改ページ。PageBreak が OFF だと下の 2 つと Size 系はすべて読まれない
		map.put("PageBreak", true);
		map.put("PageBreakEmpty", false);
		map.put("PageBreakChapter", false);
		//画像
		map.put("FitImage", true);
		//プレビュー。CLI も ini から読む (AutoPreview の CLI 連動、docs/epub-preview-plan.md)
		map.put("AutoPreview", false);
		//外字の注記表示フォールバック (docs/gaiji-fallback-plan.md 機能1)
		//既定 false = 従来どおり変換する。互換性重視で、設定しない限り出力は変わらない
		map.put("GaijiFallback", false);
		map.put("GaijiFallbackCode", false);
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, Integer> createIntegerDefaults()
	{
		Map<String, Integer> map = new LinkedHashMap<String, Integer>();
		map.put("MaxChapterNameLength", 64);
		//ページ出力 (docs/code-audit-followups.md 項目 24)
		map.put("TitlePage", 1);
		map.put("SpaceHyphenation", 0);
		map.put("RemoveEmptyLine", 0);
		map.put("MaxEmptyLine", 0);
		map.put("DakutenType", 1);
		map.put("MaxCoverLine", 10);
		//自動改ページ (KB / 行数)
		map.put("PageBreakSize", 400);
		map.put("PageBreakEmptyLine", 2);
		map.put("PageBreakEmptySize", 300);
		map.put("PageBreakChapterSize", 200);
		//画像
		map.put("ImageSizeType", 3);
		map.put("SinglePageSizeW", 400);
		map.put("SinglePageSizeH", 600);
		map.put("JpegQuality", 85);
		//外字を注記表示にする水準 3=第3水準以上 4=第4水準以上 9=JIS規格外のみ
		//GaijiFallback が false ならこの値は読まれない
		map.put("GaijiFallbackLevel", 4);
		return Collections.unmodifiableMap(map);
	}

	/** ini を読み込む前のウィジェット初期状態。GUI のチェックボックス生成用 */
	public static boolean isSelected(String key)
	{
		Boolean value = BOOLEANS.get(key);
		if (value == null) throw new IllegalArgumentException("boolean の既定値が未定義: "+key);
		return value.booleanValue();
	}

	/** ini の値。キーが無ければ GUI と同じ既定値を返す */
	public static boolean getBoolean(Properties props, String key)
	{
		boolean defaultValue = isSelected(key);
		if (props == null || !props.containsKey(key)) return defaultValue;
		return "1".equals(props.getProperty(key));
	}

	/** ini を読み込む前のウィジェット初期状態。GUI のテキストフィールド生成用 */
	public static int getInt(String key)
	{
		Integer value = INTEGERS.get(key);
		if (value == null) throw new IllegalArgumentException("int の既定値が未定義: "+key);
		return value.intValue();
	}

	/** ini の値。キーが無い・数値でない場合は GUI と同じ既定値を返す */
	public static int getInt(Properties props, String key)
	{
		int defaultValue = getInt(key);
		if (props == null) return defaultValue;
		try { return Integer.parseInt(props.getProperty(key)); }
		catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		return defaultValue;
	}

	/** 表に載っている boolean キー(テストで GUI/CLI の網羅を確認するため) */
	public static Set<String> booleanKeys()
	{
		return BOOLEANS.keySet();
	}

	/** 表に載っている int キー(テストで GUI/CLI の網羅を確認するため) */
	public static Set<String> intKeys()
	{
		return INTEGERS.keySet();
	}
}
