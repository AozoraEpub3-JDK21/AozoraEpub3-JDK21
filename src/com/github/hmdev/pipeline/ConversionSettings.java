package com.github.hmdev.pipeline;

import java.util.Properties;

import com.github.hmdev.config.SettingDefaults;
import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.info.BookInfo;

/**
 * ini（GUI が保存するのと同じ形）から読んだ、変換器と本ごとの設定。
 *
 * <p>CLI の {@code AozoraEpub3.run} が ini から読んでいた分をここにまとめた（internal #11 の H1）。
 * 本棚の「続きを取る」を画面なしで動かすときにも同じものを使い、GUI・CLI・本棚で読み方をそろえる。
 * キーが無いときは GUI と同じ既定値を使う（{@link SettingDefaults}。docs/code-audit-followups.md 項目 22 / 24）。</p>
 *
 * <p>CLI がわざと固定の値にしているもの（表題の種類・縦書き・出力のファイル名など）は、ここでは読まない。
 * 呼び出し側が引数で決める。</p>
 */
public final class ConversionSettings
{
	//本ごと（BookInfo に入れる）
	public final boolean coverPage;
	public final int titlePage;
	public final boolean tocPage;
	public final boolean tocVertical;
	public final boolean insertTitleToc;
	public final boolean coverPageToc;

	//変換器
	final boolean noIllust;
	final boolean withMarkId;
	final boolean autoYoko;
	final boolean autoYokoNum1;
	final boolean autoYokoNum3;
	final boolean autoYokoEQ1;
	final int dakutenType;
	final boolean printIvsBMP;
	final boolean printIvsSSP;
	final int gaijiFallbackLevel;
	final boolean gaijiFallbackCode;
	final int spaceHyphenation;
	final boolean commentPrint;
	final boolean commentConvert;
	final int removeEmptyLine;
	final int maxEmptyLine;
	final int forcePageBreakSize;
	final int forcePageBreakEmpty;
	final int forcePageBreakEmptySize;
	final int forcePageBreakChapter;
	final int forcePageBreakChapterSize;
	final int maxChapterNameLength;
	final boolean chapterExclude;
	final boolean chapterUseNextLine;
	final boolean chapterSection;
	final boolean chapterH;
	final boolean chapterH1;
	final boolean chapterH2;
	final boolean chapterH3;
	final boolean sameLineChapter;
	final boolean chapterName;
	final boolean chapterNumOnly;
	final boolean chapterNumTitle;
	final boolean chapterNumParen;
	final boolean chapterNumParenTitle;
	final String chapterPattern;

	private ConversionSettings(Properties props)
	{
		this.coverPage = SettingDefaults.getBoolean(props, "CoverPage");
		this.titlePage = SettingDefaults.getBoolean(props, "TitlePageWrite")
			? SettingDefaults.getInt(props, "TitlePage") : BookInfo.TITLE_NONE;
		this.withMarkId = SettingDefaults.getBoolean(props, "MarkId");
		this.commentPrint = SettingDefaults.getBoolean(props, "CommentPrint");
		this.commentConvert = SettingDefaults.getBoolean(props, "CommentConvert");
		this.autoYoko = SettingDefaults.getBoolean(props, "AutoYoko");
		this.autoYokoNum1 = SettingDefaults.getBoolean(props, "AutoYokoNum1");
		this.autoYokoNum3 = SettingDefaults.getBoolean(props, "AutoYokoNum3");
		this.autoYokoEQ1 = SettingDefaults.getBoolean(props, "AutoYokoEQ1");
		this.spaceHyphenation = SettingDefaults.getInt(props, "SpaceHyphenation");
		this.tocPage = SettingDefaults.getBoolean(props, "TocPage");
		this.tocVertical = SettingDefaults.getBoolean(props, "TocVertical");
		this.coverPageToc = SettingDefaults.getBoolean(props, "CoverPageToc");
		this.removeEmptyLine = SettingDefaults.getInt(props, "RemoveEmptyLine");
		this.maxEmptyLine = SettingDefaults.getInt(props, "MaxEmptyLine");

		//自動改ページ
		int pageBreakSize = 0;
		int pageBreakEmpty = 0;
		int pageBreakEmptySize = 0;
		int pageBreakChapter = 0;
		int pageBreakChapterSize = 0;
		if (SettingDefaults.getBoolean(props, "PageBreak")) {
			pageBreakSize = SettingDefaults.getInt(props, "PageBreakSize") * 1024;
			if (SettingDefaults.getBoolean(props, "PageBreakEmpty")) {
				pageBreakEmpty = SettingDefaults.getInt(props, "PageBreakEmptyLine");
				pageBreakEmptySize = SettingDefaults.getInt(props, "PageBreakEmptySize") * 1024;
			}
			if (SettingDefaults.getBoolean(props, "PageBreakChapter")) {
				pageBreakChapter = 1;
				pageBreakChapterSize = SettingDefaults.getInt(props, "PageBreakChapterSize") * 1024;
			}
		}
		this.forcePageBreakSize = pageBreakSize;
		this.forcePageBreakEmpty = pageBreakEmpty;
		this.forcePageBreakEmptySize = pageBreakEmptySize;
		this.forcePageBreakChapter = pageBreakChapter;
		this.forcePageBreakChapterSize = pageBreakChapterSize;

		//GUI が書くのは "MaxChapterNameLength"。旧名 "ChapterNameLength" は SettingDefaults の表には載せず
		//（GUI は書かない）、手書きの ini 向けの互換読み出しとしてここだけで扱う
		int maxLength = SettingDefaults.getInt(props, "MaxChapterNameLength");
		if (!props.containsKey("MaxChapterNameLength")) {
			try { maxLength = Integer.parseInt(props.getProperty("ChapterNameLength")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		}
		this.maxChapterNameLength = maxLength;
		this.insertTitleToc = SettingDefaults.getBoolean(props, "TitleToc");
		this.chapterExclude = SettingDefaults.getBoolean(props, "ChapterExclude");
		this.chapterUseNextLine = SettingDefaults.getBoolean(props, "ChapterUseNextLine");
		this.chapterSection = SettingDefaults.getBoolean(props, "ChapterSection");
		this.chapterH = SettingDefaults.getBoolean(props, "ChapterH");
		this.chapterH1 = SettingDefaults.getBoolean(props, "ChapterH1");
		this.chapterH2 = SettingDefaults.getBoolean(props, "ChapterH2");
		this.chapterH3 = SettingDefaults.getBoolean(props, "ChapterH3");
		this.sameLineChapter = SettingDefaults.getBoolean(props, "SameLineChapter");
		this.chapterName = SettingDefaults.getBoolean(props, "ChapterName");
		this.chapterNumOnly = SettingDefaults.getBoolean(props, "ChapterNumOnly");
		this.chapterNumTitle = SettingDefaults.getBoolean(props, "ChapterNumTitle");
		this.chapterNumParen = SettingDefaults.getBoolean(props, "ChapterNumParen");
		//GUI は "ChapterNumParenTitle" で書く
		this.chapterNumParenTitle = SettingDefaults.getBoolean(props, "ChapterNumParenTitle");
		//ChapterPattern=1 だけを書いた手書き ini では ChapterPatternText が無い。null を渡すと「パターンが不正」の警告が出る (項目 23)
		String pattern = "";
		if (SettingDefaults.getBoolean(props, "ChapterPattern")) {
			String patternText = props.getProperty("ChapterPatternText");
			if (patternText != null) pattern = patternText;
		}
		this.chapterPattern = pattern;

		this.noIllust = "1".equals(props.getProperty("NoIllust"));
		this.dakutenType = SettingDefaults.getInt(props, "DakutenType");
		this.printIvsBMP = "1".equals(props.getProperty("IvsBMP"));
		this.printIvsSSP = "1".equals(props.getProperty("IvsSSP"));
		//外字の注記表示フォールバック (docs/gaiji-fallback-plan.md 機能1)
		this.gaijiFallbackLevel = SettingDefaults.getBoolean(props, "GaijiFallback")
			? SettingDefaults.getInt(props, "GaijiFallbackLevel") : 0;
		this.gaijiFallbackCode = SettingDefaults.getBoolean(props, "GaijiFallbackCode");
	}

	/** ini の値から作る。キーが無いものは GUI と同じ既定値 */
	public static ConversionSettings fromProps(Properties props)
	{
		return new ConversionSettings(props);
	}

	/** 変換器に入れる。順番は CLI が入れていた順のまま */
	public void applyTo(AozoraEpub3Converter converter)
	{
		//挿絵なし
		converter.setNoIllust(this.noIllust);
		//栞用span出力
		converter.setWithMarkId(this.withMarkId);
		//変換オプション設定
		converter.setAutoYoko(this.autoYoko, this.autoYokoNum1, this.autoYokoNum3, this.autoYokoEQ1);
		//文字出力設定
		converter.setCharOutput(this.dakutenType, this.printIvsBMP, this.printIvsSSP);
		//外字の注記表示フォールバック
		converter.setGaijiFallback(this.gaijiFallbackLevel, this.gaijiFallbackCode);
		//全角スペースの禁則
		converter.setSpaceHyphenation(this.spaceHyphenation);
		//コメント
		converter.setCommentPrint(this.commentPrint, this.commentConvert);
		converter.setRemoveEmptyLine(this.removeEmptyLine, this.maxEmptyLine);
		//強制改ページ
		converter.setForcePageBreak(this.forcePageBreakSize, this.forcePageBreakEmpty, this.forcePageBreakEmptySize,
			this.forcePageBreakChapter, this.forcePageBreakChapterSize);
		//目次設定
		converter.setChapterLevel(this.maxChapterNameLength, this.chapterExclude, this.chapterUseNextLine, this.chapterSection,
			this.chapterH, this.chapterH1, this.chapterH2, this.chapterH3, this.sameLineChapter,
			this.chapterName,
			this.chapterNumOnly, this.chapterNumTitle, this.chapterNumParen, this.chapterNumParenTitle,
			this.chapterPattern);
	}
}
