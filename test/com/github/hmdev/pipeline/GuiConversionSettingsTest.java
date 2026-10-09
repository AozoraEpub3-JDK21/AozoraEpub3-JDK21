package com.github.hmdev.pipeline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * GUI の変換と同じ値を ini から作り直すテスト（internal #11 の H2）。
 *
 * GUI の Web 変換と画面なしの変換が同じ EPUB を作ることは、本物の GUI と比べて確かめた（PR に記録。ini 2 つで全項目が一致）。
 * ここでは GUI と CLI で読み方が違うところと、書き出し・変換器に入れる値の位置を固定する。
 */
public class GuiConversionSettingsTest {

	private static Properties props(String... keyValues) {
		Properties p = new Properties();
		for (int i = 0; i < keyValues.length; i += 2) p.setProperty(keyValues[i], keyValues[i + 1]);
		return p;
	}

	/** GUI の部品の読み方（loadProperties）: 欄は初期値から始め、split(",") で取れた数だけ上書き。単位は "1" だけが % */
	@Test
	public void marginsFollowTheGuiFields() {
		assertArrayEquals(new String[]{ "0.5em", "0.5em", "0.5em", "0.5em" },
			GuiConversionSettings.margins(props("PageMargin", ",,,"), "PageMargin", "PageMarginUnit", "0.5"));
		assertArrayEquals(new String[]{ "1%", "2%", "0.5%", "0.5%" },
			GuiConversionSettings.margins(props("PageMargin", "1,2", "PageMarginUnit", "1"), "PageMargin", "PageMarginUnit", "0.5"));
		assertArrayEquals(new String[]{ "em", "3em", "0em", "0em" },
			GuiConversionSettings.margins(props("BodyMargin", ",3", "BodyMarginUnit", "2"), "BodyMargin", "BodyMarginUnit", "0"));
		assertArrayEquals(new String[]{ "0em", "0em", "0em", "0em" },
			GuiConversionSettings.margins(new Properties(), "BodyMargin", "BodyMarginUnit", "0"));
	}

	/** 書き出しが受け取る値を記録する */
	static final class RecordingWriter extends Epub3Writer {
		final List<String> calls = new ArrayList<>();
		RecordingWriter() throws Exception {
			super(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
		}
		@Override public void setImageParam(int dispW, int dispH, int coverW, int coverH, int resizeW, int resizeH,
				int singlePageSizeW, int singlePageSizeH, int singlePageWidth, int imageSizeType, boolean fitImage, boolean isSvgImage, int rotateAngle,
				float imageScale, int imageFloatType, int imageFloatW, int imageFloatH, float jpegQuality, float gamma,
				int autoMarginLimitH, int autoMarginLimitV, int autoMarginWhiteLevel, float autoMarginPadding, int autoMarginNombre, float nombreSize) {
			calls.add("image " + dispW + " " + dispH + " " + coverW + " " + coverH + " " + resizeW + " " + resizeH + " "
				+ singlePageSizeW + " " + singlePageSizeH + " " + singlePageWidth + " " + imageSizeType + " " + fitImage + " " + isSvgImage + " " + rotateAngle + " "
				+ imageScale + " " + imageFloatType + " " + imageFloatW + " " + imageFloatH + " " + jpegQuality + " " + gamma + " "
				+ autoMarginLimitH + " " + autoMarginLimitV + " " + autoMarginWhiteLevel + " " + autoMarginPadding + " " + autoMarginNombre + " " + nombreSize);
		}
		@Override public void setTocParam(boolean navNest, boolean ncxNest) { calls.add("toc " + navNest + " " + ncxNest); }
		@Override public void setStyles(String[] pageMargin, String[] bodyMargin, float lineHeight, int fontSize, boolean boldUseGothic, boolean gothicUseBold) {
			calls.add("styles " + String.join(",", pageMargin) + " " + String.join(",", bodyMargin) + " " + lineHeight + " " + fontSize + " " + boldUseGothic + " " + gothicUseBold);
		}
	}

	/**
	 * CLI（WriterConfigurator）と違う、GUI の読み方を固定する: 倍率はチェックが OFF なら 0、回り込みは ON のときだけ種類 +1、
	 * 文字の大きさは小数で読んで切り捨て、余白の自動調整が OFF なら値は全部 0
	 */
	@Test
	public void theWriterGetsTheGuiValues() throws Exception {
		RecordingWriter off = new RecordingWriter();
		RecordingWriter offImage = new RecordingWriter();
		GuiConversionSettings.applyTo(props(
			"ImageScaleChecked", "", "ImageScale", "2.0",
			"ImageFloat", "", "ImageFloatType", "1", "ImageFloatW", "300", "ImageFloatH", "200",
			"AutoMargin", "", "AutoMarginWhiteLevel", "80",
			"FontSize", "110.5"), off, offImage);
		assertEquals(off.calls.get(0), offImage.calls.get(0));
		assertEquals("image 600 800 600 800 0 0 400 600 600 3 true false 0 0.0 0 0 0 0.8 1.0 0 0 0 0.0 0 0.03",
			off.calls.get(0));
		assertEquals("styles 0.5em,0.5em,0.5em,0.5em 0em,0em,0em,0em 1.8 110 false false", off.calls.get(2));

		RecordingWriter on = new RecordingWriter();
		GuiConversionSettings.applyTo(props(
			"DispW", "700", "DispH", "900", "CoverW", "610", "CoverH", "810",
			"ResizeW", "1", "ResizeNumW", "2000", "ResizeH", "1", "ResizeNumH", "3000",
			"SinglePageSizeW", "401", "SinglePageSizeH", "601", "SinglePageWidth", "602",
			"ImageSizeType", "1", "FitImage", "", "SvgImage", "1", "RotateImage", "2",
			"ImageScaleChecked", "1", "ImageScale", "2.0",
			"ImageFloat", "1", "ImageFloatType", "1", "ImageFloatW", "300", "ImageFloatH", "200",
			"JpegQuality", "90", "Gamma", "1", "GammaValue", "1.5",
			"AutoMargin", "1", "AutoMarginLimitH", "15", "AutoMarginLimitV", "16", "AutoMarginWhiteLevel", "80",
			"AutoMarginPadding", "1.5", "AutoMarginNombre", "1", "AutoMarginNombreSize", "3.0",
			"NavNest", "1", "NcxNest", "",
			"PageMargin", "1,2,3,4", "PageMarginUnit", "1", "BodyMargin", "5,6,7,8", "BodyMarginUnit", "0",
			"LineHeight", "2.0", "FontSize", "110.5", "BoldUseGothic", "1", "GothicUseBold", ""), on, new RecordingWriter());
		assertEquals(List.of(
			"image 700 900 610 810 2000 3000 401 601 602 1 false true -90 2.0 2 300 200 0.9 1.5 15 16 80 1.5 1 0.03",
			"toc true false",
			"styles 1%,2%,3%,4% 5em,6em,7em,8em 2.0 110 true false"), on.calls);
	}

	/** GUI だけが入れる設定も記録する */
	static final class GuiRecordingConverter extends ConversionSettingsTest.RecordingConverter {
		GuiRecordingConverter() throws Exception { super(); }
		@Override public void setChukiRuby(boolean ruby, boolean kogaki) { calls.add("chukiRuby " + ruby + " " + kogaki); }
		@Override public void setImageFloat(boolean page, boolean block) { calls.add("imageFloat " + page + " " + block); }
		@Override public void setForceIndent(boolean v) { calls.add("forceIndent " + v); }
	}

	/**
	 * GUI の convertFiles と同じ順番・位置で変換器に入れる。コメントは Web 変換では出して変換する（ini の値によらない）。
	 * 改ページは ON のときだけ入れる。章のパターンは前後の空白を落とす
	 */
	@Test
	public void theConverterGetsTheGuiValuesInTheGuiOrder() throws Exception {
		GuiRecordingConverter c = new GuiRecordingConverter();
		GuiConversionSettings.applyTo(props(
			"NoIllust", "1", "MarkId", "", "AutoYoko", "1", "AutoYokoNum1", "", "AutoYokoNum3", "1", "AutoYokoEQ1", "",
			"DakutenType", "2", "IvsBMP", "1", "IvsSSP", "", "GaijiFallback", "1", "GaijiFallbackLevel", "3", "GaijiFallbackCode", "",
			"SpaceHyphenation", "1", "ChukiRuby", "2", "CommentPrint", "", "CommentConvert", "",
			"ImageFloatPage", "1", "ImageFloatBlock", "", "RemoveEmptyLine", "5", "MaxEmptyLine", "6", "ForceIndent", "1",
			"PageBreak", "1", "PageBreakSize", "7", "PageBreakEmpty", "1", "PageBreakEmptyLine", "8", "PageBreakEmptySize", "9",
			"PageBreakChapter", "1", "PageBreakChapterSize", "11",
			"MaxChapterNameLength", "12", "ChapterExclude", "1", "ChapterUseNextLine", "", "ChapterSection", "1", "ChapterH", "",
			"ChapterH1", "1", "ChapterH2", "", "ChapterH3", "1", "SameLineChapter", "", "ChapterName", "1", "ChapterNumOnly", "",
			"ChapterNumTitle", "1", "ChapterNumParen", "", "ChapterNumParenTitle", "1",
			"ChapterPattern", "1", "ChapterPatternText", "  ^X  "), c, true);
		assertEquals(List.of(
			"noIllust true",
			"markId false",
			"autoYoko true false true false",
			"charOutput 2 true false",
			"gaiji 3 false",
			"spaceHyp 1",
			"chukiRuby false true",
			"comment true true",
			"imageFloat true false",
			"emptyLine 5 6",
			"forceIndent true",
			"pageBreak " + (7 * 1024) + " 8 " + (9 * 1024) + " 1 " + (11 * 1024),
			"chapter 12 truefalsetruefalsetruefalsetruefalsetruefalsetruefalsetrue ^X"
		), c.calls);

		//コメントを固定しないとき（Web 以外）は ini の値。改ページが OFF なら入れない
		GuiRecordingConverter d = new GuiRecordingConverter();
		GuiConversionSettings.applyTo(props("CommentPrint", "1", "CommentConvert", "", "PageBreak", "", "ChukiRuby", "1"), d, false);
		assertEquals("comment true false", d.calls.get(7));
		assertEquals("chukiRuby true false", d.calls.get(6));
		assertEquals(0, d.calls.stream().filter(x -> x.startsWith("pageBreak")).count());
	}
}
