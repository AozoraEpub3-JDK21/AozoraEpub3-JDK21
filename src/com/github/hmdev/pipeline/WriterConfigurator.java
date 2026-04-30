package com.github.hmdev.pipeline;

import java.util.Properties;

import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.writer.Epub3ImageWriter;
import com.github.hmdev.writer.Epub3Writer;

/**
 * Applies writer-related configuration sourced from properties to Epub3Writer and Epub3ImageWriter.
 */
public final class WriterConfigurator {
	private WriterConfigurator() {
	}

	public static void apply(Properties props, Epub3Writer epub3Writer, Epub3ImageWriter epub3ImageWriter) {
		// screen and image resize
		int dispW = 600;
		try { dispW = Integer.parseInt(props.getProperty("DispW")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int dispH = 800;
		try { dispH = Integer.parseInt(props.getProperty("DispH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int coverW = 600;
		try { coverW = Integer.parseInt(props.getProperty("CoverW")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int coverH = 800;
		try { coverH = Integer.parseInt(props.getProperty("CoverH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int resizeW = 0;
		if ("1".equals(props.getProperty("ResizeW"))) try { resizeW = Integer.parseInt(props.getProperty("ResizeNumW")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int resizeH = 0;
		if ("1".equals(props.getProperty("ResizeH"))) try { resizeH = Integer.parseInt(props.getProperty("ResizeNumH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int singlePageSizeW = 480;
		try { singlePageSizeW = Integer.parseInt(props.getProperty("SinglePageSizeW")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int singlePageSizeH = 640;
		try { singlePageSizeH = Integer.parseInt(props.getProperty("SinglePageSizeH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int singlePageWidth = 600;
		try { singlePageWidth = Integer.parseInt(props.getProperty("SinglePageWidth")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		float imageScale = 1;
		try { imageScale = Float.parseFloat(props.getProperty("ImageScale")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int imageFloatType = 0;
		try { imageFloatType = Integer.parseInt(props.getProperty("ImageFloatType")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int imageFloatW = 0;
		try { imageFloatW = Integer.parseInt(props.getProperty("ImageFloatW")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int imageFloatH = 0;
		try { imageFloatH = Integer.parseInt(props.getProperty("ImageFloatH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int imageSizeType = SectionInfo.IMAGE_SIZE_TYPE_HEIGHT;
		try { imageSizeType = Integer.parseInt(props.getProperty("ImageSizeType")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		boolean fitImage = "1".equals(props.getProperty("FitImage"));
		boolean svgImage = "1".equals(props.getProperty("SvgImage"));
		int rotateImage = 0;
		if ("1".equals(props.getProperty("RotateImage"))) rotateImage = 90; else if ("2".equals(props.getProperty("RotateImage"))) rotateImage = -90;
		float jpegQualty = 0.8f;
		try { jpegQualty = Integer.parseInt(props.getProperty("JpegQuality"))/100f; } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		float gamma = 1.0f;
		if ("1".equals(props.getProperty("Gamma"))) try { gamma = Float.parseFloat(props.getProperty("GammaValue")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int autoMarginLimitH = 0;
		int autoMarginLimitV = 0;
		int autoMarginWhiteLevel = 80;
		float autoMarginPadding = 0;
		int autoMarginNombre = 0;
		float nobreSize = 0.03f;
		if ("1".equals(props.getProperty("AutoMargin"))) {
			try { autoMarginLimitH = Integer.parseInt(props.getProperty("AutoMarginLimitH")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			try { autoMarginLimitV = Integer.parseInt(props.getProperty("AutoMarginLimitV")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			try { autoMarginWhiteLevel = Integer.parseInt(props.getProperty("AutoMarginWhiteLevel")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			try { autoMarginPadding = Float.parseFloat(props.getProperty("AutoMarginPadding")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			try { autoMarginNombre = Integer.parseInt(props.getProperty("AutoMarginNombre")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			try { autoMarginPadding = Float.parseFloat(props.getProperty("AutoMarginNombreSize")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		}

		epub3Writer.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth, imageSizeType, fitImage, svgImage, rotateImage,
				imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQualty, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, nobreSize);
		epub3ImageWriter.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth, imageSizeType, fitImage, svgImage, rotateImage,
				imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQualty, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, nobreSize);

		// toc nesting
		epub3Writer.setTocParam("1".equals(props.getProperty("NavNest")), "1".equals(props.getProperty("NcxNest")));

		// style settings
		String[] pageMargin = {};
		try { pageMargin = props.getProperty("PageMargin").split(","); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		if (pageMargin.length != 4) pageMargin = new String[]{"0", "0", "0", "0"};
		else {
			String pageMarginUnit = props.getProperty("PageMarginUnit");
			for (int i=0; i<4; i++) { pageMargin[i] += pageMarginUnit; }
		}
		String[] bodyMargin = {};
		try { bodyMargin = props.getProperty("BodyMargin").split(","); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		if (bodyMargin.length != 4) bodyMargin = new String[]{"0", "0", "0", "0"};
		else {
			String bodyMarginUnit = props.getProperty("BodyMarginUnit");
			for (int i=0; i<4; i++) { bodyMargin[i] += bodyMarginUnit; }
		}
		float lineHeight = 1.8f;
		try { lineHeight = Float.parseFloat(props.getProperty("LineHeight")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		int fontSize = 100;
		try { fontSize = Integer.parseInt(props.getProperty("FontSize")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
		boolean boldUseGothic = "1".equals(props.getProperty("BoldUseGothic"));
		boolean gothicUseBold = "1".equals(props.getProperty("gothicUseBold"));
		epub3Writer.setStyles(pageMargin, bodyMargin, lineHeight, fontSize, boldUseGothic, gothicUseBold);
	}
}
