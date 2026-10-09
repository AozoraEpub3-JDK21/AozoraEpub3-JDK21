package com.github.hmdev.pipeline;

import java.util.Properties;

import com.github.hmdev.config.SettingDefaults;
import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * GUI の変換と同じ値を、GUI が保存した ini（{@code AozoraEpub3Applet.setProperties} が書く形）から作り直す。
 *
 * <p>本棚の「続きを取る」を画面なしで動かすときに使う（internal #11 の H2）。GUI の {@code convertFiles} が画面の部品から
 * 書き出しと変換器に入れている値を、同じ順番・同じ解釈で入れる。CLI（{@link WriterConfigurator}・{@link ConversionSettings}）とは、
 * 次のところで読み方が違う（GUI に合わせている。internal #21）:</p>
 * <ul>
 * <li>{@code ImageScaleChecked} が OFF なら画像の倍率は 0（無効）</li>
 * <li>{@code ImageFloat} が ON のときだけ回り込みの種類は {@code ImageFloatType + 1}</li>
 * <li>{@code FontSize} は小数で読んで切り捨てる</li>
 * <li>{@code AutoMargin} が OFF なら余白の値は全部 0（白の閾値も 0）</li>
 * <li>変換器に {@code ChukiRuby}・{@code ImageFloatPage}・{@code ImageFloatBlock}・{@code ForceIndent} も入れる</li>
 * <li>改ページは {@code PageBreak} が ON のときだけ入れる。章のパターンは前後の空白を落とす</li>
 * </ul>
 *
 * <p>キーが無いときは GUI の部品の初期値（{@link SettingDefaults}）。数値が読めないときは GUI の既定値に倒す
 * （GUI は部品の入力で弾くので、そこで落ちることは無い）。</p>
 */
public final class GuiConversionSettings
{
	private GuiConversionSettings() {}

	private static boolean on(Properties p, String key)
	{
		return flag(p, key);
	}

	/**
	 * ini のチェックの値。GUI の ini は全部のキーを持つ。キーが無いとき（手書きの ini）は、既定値の表にあるキーは GUI と同じ既定値、
	 * 表に無いキーは OFF（GUI は部品の初期状態のまま。表に無いものは初期状態が OFF のものが多い）
	 */
	public static boolean flag(Properties p, String key)
	{
		try {
			return SettingDefaults.getBoolean(p, key);
		} catch (IllegalArgumentException e) {
			return "1".equals(p.getProperty(key));
		}
	}

	private static int intOf(Properties p, String key, int defaultValue)
	{
		try { return Integer.parseInt(p.getProperty(key).trim()); } catch (Exception e) { return defaultValue; }
	}

	private static float floatOf(Properties p, String key, float defaultValue)
	{
		try { return Float.parseFloat(p.getProperty(key).trim()); } catch (Exception e) { return defaultValue; }
	}

	/** 書き出し 2 つ（本文と画像だけの本）に、画像・目次・スタイルの設定を入れる（GUI の convertFiles と同じ） */
	public static void applyTo(Properties p, Epub3Writer writer, Epub3Writer imageWriter)
	{
		int resizeW = on(p, "ResizeW") ? intOf(p, "ResizeNumW", 0) : 0;
		int resizeH = on(p, "ResizeH") ? intOf(p, "ResizeNumH", 0) : 0;
		int dispW = intOf(p, "DispW", 600);
		int dispH = intOf(p, "DispH", 800);
		int coverW = intOf(p, "CoverW", 600);
		int coverH = intOf(p, "CoverH", 800);
		int singlePageSizeW = intOf(p, "SinglePageSizeW", SettingDefaults.getInt("SinglePageSizeW"));
		int singlePageSizeH = intOf(p, "SinglePageSizeH", SettingDefaults.getInt("SinglePageSizeH"));
		int singlePageWidth = intOf(p, "SinglePageWidth", 600);

		float imageScale = on(p, "ImageScaleChecked") ? floatOf(p, "ImageScale", 0) : 0;
		int imageFloatType = 0; //0=無効 1=上 2=下
		int imageFloatW = 0;
		int imageFloatH = 0;
		if (on(p, "ImageFloat")) {
			imageFloatType = intOf(p, "ImageFloatType", 0) + 1;
			imageFloatW = intOf(p, "ImageFloatW", 0);
			imageFloatH = intOf(p, "ImageFloatH", 0);
		}
		float jpegQuality = 0.8f;
		try { jpegQuality = Integer.parseInt(p.getProperty("JpegQuality").trim()) / 100f; } catch (Exception e) { /* 意図的: 既定値を維持 */ }
		float gamma = on(p, "Gamma") ? floatOf(p, "GammaValue", 1.0f) : 1.0f;
		int autoMarginLimitH = 0;
		int autoMarginLimitV = 0;
		int autoMarginWhiteLevel = 0;
		float autoMarginPadding = 0;
		int autoMarginNombre = 0;
		float autoMarginNombreSize = 0.03f;
		if (on(p, "AutoMargin")) {
			autoMarginLimitH = intOf(p, "AutoMarginLimitH", 0);
			autoMarginLimitV = intOf(p, "AutoMarginLimitV", 0);
			autoMarginWhiteLevel = intOf(p, "AutoMarginWhiteLevel", 0);
			autoMarginPadding = floatOf(p, "AutoMarginPadding", 0);
			autoMarginNombre = intOf(p, "AutoMarginNombre", 0);
			try { autoMarginNombreSize = Float.parseFloat(p.getProperty("AutoMarginNombreSize").trim()) * 0.01f; } catch (Exception e) { /* 意図的: 既定値を維持 */ }
		}
		int rotate = intOf(p, "RotateImage", 0);
		int rotateAngle = rotate == 1 ? 90 : (rotate == 2 ? -90 : 0);
		int imageSizeType = intOf(p, "ImageSizeType", SectionInfo.IMAGE_SIZE_TYPE_ASPECT) == SectionInfo.IMAGE_SIZE_TYPE_AUTO
			? SectionInfo.IMAGE_SIZE_TYPE_AUTO : SectionInfo.IMAGE_SIZE_TYPE_ASPECT;
		boolean fitImage = on(p, "FitImage");
		boolean svgImage = on(p, "SvgImage");

		writer.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth,
			imageSizeType, fitImage, svgImage, rotateAngle,
			imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQuality, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, autoMarginNombreSize);
		imageWriter.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth,
			imageSizeType, fitImage, svgImage, rotateAngle,
			imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQuality, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, autoMarginNombreSize);
		//目次階層化設定
		writer.setTocParam(on(p, "NavNest"), on(p, "NcxNest"));

		//スタイル設定。GUI は単位の 1 つ目（0）だけを em、それ以外を % にしている。値は部品の文字のまま（trim しない）
		writer.setStyles(margins(p, "PageMargin", "PageMarginUnit", "0.5"), margins(p, "BodyMargin", "BodyMarginUnit", "0"),
			floatOf(p, "LineHeight", 1.8f), (int)floatOf(p, "FontSize", 100),
			on(p, "BoldUseGothic"), on(p, "GothicUseBold"));
	}

	/**
	 * 余白の 4 つの値。GUI の読み方（loadProperties）と同じく、欄は初期値（ページ 0.5・本文 0）から始め、
	 * {@code split(",")} で取れた数だけ上書きする（Java の split は末尾の空を落とすので、",,," は 0 個＝初期値のまま）
	 */
	static String[] margins(Properties p, String key, String unitKey, String initial)
	{
		String unit = "1".equals(p.getProperty(unitKey)) ? "%" : "em";
		String[] margins = { initial, initial, initial, initial };
		String value = p.getProperty(key);
		if (value != null) {
			String[] values = value.split(",");
			for (int i = 0; i < Math.min(values.length, margins.length); i++) margins[i] = values[i];
		}
		for (int i = 0; i < margins.length; i++) margins[i] += unit;
		return margins;
	}

	/**
	 * 変換器に入れる（GUI の convertFiles と同じ順番）。
	 * @param forceComments GUI の Web 変換はコメントを出す・変換するに固定する（部品を一時的に書き換えている）
	 */
	public static void applyTo(Properties p, AozoraEpub3Converter converter, boolean forceComments)
	{
		converter.setNoIllust(on(p, "NoIllust"));
		converter.setWithMarkId(on(p, "MarkId"));
		converter.setAutoYoko(on(p, "AutoYoko"), on(p, "AutoYokoNum1"), on(p, "AutoYokoNum3"), on(p, "AutoYokoEQ1"));
		converter.setCharOutput(SettingDefaults.getInt(p, "DakutenType"), on(p, "IvsBMP"), on(p, "IvsSSP"));
		converter.setGaijiFallback(on(p, "GaijiFallback") ? SettingDefaults.getInt(p, "GaijiFallbackLevel") : 0,
			on(p, "GaijiFallbackCode"));
		converter.setSpaceHyphenation(SettingDefaults.getInt(p, "SpaceHyphenation"));
		//注記のルビ表示（GUI のラジオ 0/1/2。1 がルビ、2 が小書き）
		int chukiRuby = intOf(p, "ChukiRuby", 0);
		converter.setChukiRuby(chukiRuby == 1, chukiRuby == 2);
		converter.setCommentPrint(forceComments || on(p, "CommentPrint"), forceComments || on(p, "CommentConvert"));
		converter.setImageFloat(on(p, "ImageFloatPage"), on(p, "ImageFloatBlock"));
		converter.setRemoveEmptyLine(SettingDefaults.getInt(p, "RemoveEmptyLine"), SettingDefaults.getInt(p, "MaxEmptyLine"));
		converter.setForceIndent(on(p, "ForceIndent"));
		//強制改ページ。GUI は ON のときだけ入れる（OFF なら変換器の既定の 0 のまま）
		if (on(p, "PageBreak")) {
			int size = intOf(p, "PageBreakSize", 0) * 1024;
			int empty = 0;
			int emptySize = 0;
			int chapter = 0;
			int chapterSize = 0;
			if (on(p, "PageBreakEmpty")) {
				empty = intOf(p, "PageBreakEmptyLine", 0);
				emptySize = intOf(p, "PageBreakEmptySize", 0) * 1024;
			}
			if (on(p, "PageBreakChapter")) {
				chapter = 1;
				chapterSize = intOf(p, "PageBreakChapterSize", 0) * 1024;
			}
			converter.setForcePageBreak(size, empty, emptySize, chapter, chapterSize);
		}
		//目次設定
		String pattern = on(p, "ChapterPattern") ? p.getProperty("ChapterPatternText", "").trim() : "";
		converter.setChapterLevel(intOf(p, "MaxChapterNameLength", 64), on(p, "ChapterExclude"), on(p, "ChapterUseNextLine"), on(p, "ChapterSection"),
			on(p, "ChapterH"), on(p, "ChapterH1"), on(p, "ChapterH2"), on(p, "ChapterH3"), on(p, "SameLineChapter"),
			on(p, "ChapterName"),
			on(p, "ChapterNumOnly"), on(p, "ChapterNumTitle"), on(p, "ChapterNumParen"), on(p, "ChapterNumParenTitle"),
			pattern);
	}
}
