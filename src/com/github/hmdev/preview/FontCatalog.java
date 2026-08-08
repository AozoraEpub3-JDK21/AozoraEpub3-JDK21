package com.github.hmdev.preview;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * プレビューで選択できるフォントの一覧を作る。
 *
 * <p>本アプリが出力する EPUB の本文フォント指定は
 * {@code font-family: "@ＭＳ 明朝", "@MS Mincho", "ヒラギノ明朝 ProN W3", ...}
 * ({@code template/OPS/css/vertical_font.css}) であり、
 * 先頭の {@code @} は Windows の縦書き用フォントを指す旧来の EPUB リーダー向け記法。
 * <b>どのフォントが実際に使われるかは閲覧環境のインストール状況で変わり、
 * Kindle / Kobo / Apple Books の実機既定フォントとは一致しない。</b>
 * (Windows + Chrome では {@code @ＭＳ 明朝} が解決されるという実測がある。
 * docs/epub-preview-plan.md の F1 を参照)
 * したがってフォント選択はプレビューを実機の見た目へ近づけるための中核機能となる。</p>
 *
 * <p>フォントは同梱せず、OS にインストール済みのものだけを列挙する
 * (配布サイズを増やさないため)。</p>
 */
public class FontCatalog
{
	/**
	 * 推奨明朝体。実在するものだけを UI に出す。
	 *
	 * <p>名前は推測せず実環境の {@code getAvailableFontFamilyNames()} に合わせること。
	 * Windows 11 では {@code BIZ UDP明朝 Medium} のようにウェイトまで含む名前で返る一方、
	 * 当初 {@code BIZ UDP明朝} と書いていて 1 つもマッチしていなかった。</p>
	 */
	static final String[] RECOMMENDED_MINCHO = {
		"游明朝", "游明朝 Light", "游明朝 Demibold", "Yu Mincho", "游明朝体",
		"BIZ UDP明朝 Medium", "BIZ UD明朝 Medium", "BIZ UDPMincho", "BIZ UDMincho",
		"ヒラギノ明朝 ProN", "Hiragino Mincho ProN",
		"Noto Serif JP", "Noto Serif CJK JP",
		"源ノ明朝", "Source Han Serif JP", "Source Han Serif",
		"Zen Old Mincho", "しっぽり明朝", "Shippori Mincho",
		"IPAmj明朝", "IPAexMincho", "MS Mincho", "ＭＳ 明朝", "ＭＳ Ｐ明朝",
	};

	/** 推奨ゴシック体 */
	static final String[] RECOMMENDED_GOTHIC = {
		"游ゴシック", "Yu Gothic", "Yu Gothic UI",
		"BIZ UDPゴシック", "BIZ UDPGothic", "BIZ UDゴシック", "BIZ UDGothic",
		"ヒラギノ角ゴ ProN", "Hiragino Kaku Gothic ProN",
		"Noto Sans JP", "Noto Sans CJK JP",
		"源ノ角ゴシック", "Source Han Sans JP", "Source Han Sans",
		"メイリオ", "Meiryo", "MS Gothic", "ＭＳ ゴシック",
	};

	/**
	 * 教科書体など、あれば嬉しいもの。
	 * Windows 11 の実際のファミリ名は「デジタル」と「教科書体」の間にスペースが入り、
	 * ウェイトの {@code -R} は付かない。
	 */
	static final String[] RECOMMENDED_OTHER = {
		"UD デジタル 教科書体 NP", "UD デジタル 教科書体 N", "UD デジタル 教科書体 NK",
		"UD Digi Kyokasho NP-R", "UD Digi Kyokasho N-R", "UD Digi Kyokasho NK-R",
		"Klee One", "Kaisei Decol", "解星 デコール",
	};

	/**
	 * 本文フォントの既定値の優先順。
	 * 縦書きの日本語を読むのに素直な字形で、Windows 10 以降に標準搭載されている
	 * UD デジタル教科書体を先頭に置く。無い環境では明朝へ順に落ちる。
	 */
	static final String[] DEFAULT_BODY_PREFERENCE = {
		"UD デジタル 教科書体 NP", "UD デジタル 教科書体 N", "UD デジタル 教科書体 NK",
		"UD Digi Kyokasho NP-R", "UD Digi Kyokasho N-R", "UD Digi Kyokasho NK-R",
		"游明朝", "Yu Mincho", "游明朝体",
		"BIZ UDP明朝 Medium", "BIZ UD明朝 Medium",
		"ヒラギノ明朝 ProN", "Hiragino Mincho ProN",
		"Noto Serif JP", "Noto Serif CJK JP",
	};

	private final List<String> mincho;
	private final List<String> gothic;
	private final List<String> other;
	private final List<String> all;
	private final String defaultBody;

	FontCatalog(List<String> mincho, List<String> gothic, List<String> other, List<String> all,
		String defaultBody)
	{
		this.mincho = mincho;
		this.gothic = gothic;
		this.other = other;
		this.all = all;
		this.defaultBody = defaultBody;
	}

	/** 実行環境にインストールされているフォントから一覧を作る */
	public static FontCatalog detect()
	{
		List<String> installed;
		try {
			installed = Arrays.asList(
				GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getAvailableFontFamilyNames(Locale.JAPANESE));
		} catch (Throwable e) {
			// ヘッドレス環境やフォント設定不備でも、プレビュー自体は動かしたい
			installed = List.of();
		}
		return from(installed);
	}

	/** インストール済みファミリ名のリストから一覧を作る (テスト用に注入可能) */
	static FontCatalog from(List<String> installedFamilies)
	{
		Set<String> installed = new LinkedHashSet<>(installedFamilies);
		List<String> preferred = filterExisting(DEFAULT_BODY_PREFERENCE, installed);
		return new FontCatalog(
			filterExisting(RECOMMENDED_MINCHO, installed),
			filterExisting(RECOMMENDED_GOTHIC, installed),
			filterExisting(RECOMMENDED_OTHER, installed),
			new ArrayList<>(installed),
			preferred.isEmpty() ? null : preferred.get(0));
	}

	/** 推奨リストのうち実在するものだけを、推奨順を保って返す */
	private static List<String> filterExisting(String[] candidates, Set<String> installed)
	{
		List<String> result = new ArrayList<>();
		for (String candidate : candidates) {
			if (installed.contains(candidate) && !result.contains(candidate)) result.add(candidate);
		}
		return result;
	}

	public List<String> getMincho() { return this.mincho; }
	public List<String> getGothic() { return this.gothic; }
	public List<String> getOther() { return this.other; }
	public List<String> getAll() { return this.all; }

	/**
	 * 本文フォントの既定値。無ければ null (EPUB の指定のまま表示する)。
	 * {@link #DEFAULT_BODY_PREFERENCE} の順で実在する先頭を返す。
	 */
	public String getDefaultBody() { return this.defaultBody; }

	/** 既定として選ばせたい明朝体。無ければ null (ブラウザ既定にフォールバック) */
	public String getDefaultMincho() { return this.mincho.isEmpty() ? null : this.mincho.get(0); }

	/** 既定として選ばせたいゴシック体。無ければ null */
	public String getDefaultGothic() { return this.gothic.isEmpty() ? null : this.gothic.get(0); }

	/** JSON として出力する */
	void toJson(StringBuilder buf)
	{
		buf.append('{');
		Json.key(buf, "mincho"); array(buf, this.mincho);
		Json.key(buf, "gothic"); array(buf, this.gothic);
		Json.key(buf, "other"); array(buf, this.other);
		Json.key(buf, "all"); array(buf, this.all);
		Json.prop(buf, "defaultBody", getDefaultBody());
		Json.prop(buf, "defaultMincho", getDefaultMincho());
		Json.prop(buf, "defaultGothic", getDefaultGothic());
		buf.append('}');
	}

	private static void array(StringBuilder buf, List<String> values)
	{
		buf.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) buf.append(',');
			buf.append(Json.str(values.get(i)));
		}
		buf.append(']');
	}
}
