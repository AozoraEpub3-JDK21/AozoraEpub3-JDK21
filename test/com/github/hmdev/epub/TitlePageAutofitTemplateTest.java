package com.github.hmdev.epub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.hmdev.util.VelocityTestUtils;

/**
 * タイトルページ(title_horizontal.vm / title_middle.vm)の
 * 長タイトル自動調整(TITLE_LENGTH による font-size ラダー)のテスト。
 * docs/title-page-autofit-plan.md 参照。
 */
public class TitlePageAutofitTemplateTest
{
	static VelocityEngine engine;

	@BeforeClass
	public static void setUpClass() throws Exception
	{
		engine = VelocityTestUtils.engineForTemplateSubpath("OPS/xhtml");
	}

	private String renderHorizontal(String title, Integer titleLength, String series)
	{
		VelocityContext ctx = new VelocityContext();
		ctx.put("title", title);
		ctx.put("TITLE", title);
		if (titleLength != null) ctx.put("TITLE_LENGTH", titleLength);
		if (series != null) ctx.put("SERIES", series);
		ctx.put("CREATOR", "テスト著者");
		StringWriter sw = new StringWriter();
		engine.getTemplate("title_horizontal.vm", "UTF-8").merge(ctx, sw);
		return sw.toString();
	}

	private String renderMiddle(String title, Integer titleLength)
	{
		VelocityContext ctx = new VelocityContext();
		ctx.put("title", title);
		ctx.put("TITLE", title);
		if (titleLength != null) ctx.put("TITLE_LENGTH", titleLength);
		ctx.put("CREATOR", "テスト著者");
		StringWriter sw = new StringWriter();
		engine.getTemplate("title_middle.vm", "UTF-8").merge(ctx, sw);
		return sw.toString();
	}

	private static int countOf(String text, String sub)
	{
		int count = 0;
		for (int i = text.indexOf(sub); i >= 0; i = text.indexOf(sub, i + sub.length())) count++;
		return count;
	}

	/** .title { } ブロック内の font-size を返す
	 * (.subtitle 等の固定ルールにも font-size:1.6em 等が含まれるため、containsではトートロジーになる) */
	private static String titleBlockFontSize(String out)
	{
		int start = out.indexOf(".title {");
		assertTrue(".title block not found", start >= 0);
		int end = out.indexOf("}", start);
		String block = out.substring(start + ".title {".length(), end);
		return block.trim();
	}

	//---------- title_horizontal.vm: font-size ラダー ----------

	@Test
	public void horizontalFontSizeLadder()
	{
		assertEquals("font-size:2em;", titleBlockFontSize(renderHorizontal("短いタイトル", 20, null)));
		assertEquals("font-size:2em;", titleBlockFontSize(renderHorizontal("タイトル", 30, null)));
		assertEquals("font-size:1.75em;", titleBlockFontSize(renderHorizontal("タイトル", 31, null)));
		assertEquals("font-size:1.75em;", titleBlockFontSize(renderHorizontal("タイトル", 45, null)));
		assertEquals("font-size:1.6em;", titleBlockFontSize(renderHorizontal("タイトル", 46, null)));
		assertEquals("font-size:1.6em;", titleBlockFontSize(renderHorizontal("タイトル", 60, null)));
		assertEquals("font-size:1.4em;", titleBlockFontSize(renderHorizontal("タイトル", 61, null)));
		assertEquals("font-size:1.4em;", titleBlockFontSize(renderHorizontal("タイトル", 80, null)));
		assertEquals("font-size:1.25em;", titleBlockFontSize(renderHorizontal("タイトル", 81, null)));
		assertEquals("font-size:1.25em;", titleBlockFontSize(renderHorizontal("タイトル", 120, null)));
		assertEquals("font-size:1.1em;", titleBlockFontSize(renderHorizontal("タイトル", 121, null)));
	}

	//---------- title_horizontal.vm: 45文字以下は余白の段を残す ----------

	@Test
	public void shortTitleKeepsSpacers()
	{
		String out = renderHorizontal("短いタイトル", 20, null);
		assertEquals("padding:10% 5% 0 5%; min-height:50%; text-align:center;", rule(out, ".upper"));
		//SERIES/ORGTITLE/SUBTITLE/SUBORGTITLE なし → space 4個
		assertEquals(4, countOf(out, "<div class=\"space\"></div>"));
	}

	/** 題名と著者の段は高さを固定しない（外側の枠は固定のまま。題名の段の min-height:50% の基準になる）。固定すると、スマホで文字を大きくしたときに題名が段からあふれて、
	 * 著者と重なる（Apple Books）か、著者だけ次のページへ送られる（Kindle）。著者の段は場所も取らない。internal #67 */
	@Test
	public void blocksGrowInsteadOfOverflowing()
	{
		for (int length : new int[]{10, 28, 30, 31, 45, 46, 90}) {
			for (String series : new String[]{null, "シリーズ名"}) {
				String out = renderHorizontal("タイトル", length, series);
				String label = length + "/" + series + ": " + out;
				assertEquals(label, "padding:" + (length > 45 ? "5%" : "10%") + " 5% 0 5%; min-height:50%; text-align:center;", rule(out, ".upper"));
				assertEquals(label, "padding:0 5%; text-align:center;", rule(out, ".lower"));
				//外側の枠は高さを固定する（題名の段の min-height:50% の基準になる）
				assertTrue(label, rule(out, ".outer").contains(" height:98.5%;"));
				//固定の高さは html と body（ADE のための 98%）と外側の枠だけ
				assertEquals(label, 3, out.split("(?<![\\w-])height\\s*:", -1).length - 1);
			}
		}
	}

	/** 副題や原題があるときも同じ形（題名の段がいちばん伸びる場合） */
	@Test
	public void subtitlesUseTheSameGrowingBlocks()
	{
		VelocityContext ctx = new VelocityContext();
		ctx.put("title", "t");
		ctx.put("TITLE", "タイトル");
		ctx.put("TITLE_LENGTH", 28);
		ctx.put("SUBTITLE", "副題");
		ctx.put("ORGTITLE", "原題");
		ctx.put("CREATOR", "テスト著者");
		StringWriter sw = new StringWriter();
		engine.getTemplate("title_horizontal.vm", "UTF-8").merge(ctx, sw);
		String out = sw.toString();
		assertEquals("padding:10% 5% 0 5%; min-height:50%; text-align:center;", rule(out, ".upper"));
		assertEquals("padding:0 5%; text-align:center;", rule(out, ".lower"));
	}

	/** CSS の規則の中身（空白をそろえる） */
	private static String rule(String out, String selector)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(out);
		assertTrue(selector + " が無い: " + out, m.find());
		return m.group(1).replaceAll("\\s+", " ").trim();
	}

	//---------- title_horizontal.vm: 46文字以上で構造調整 ----------

	@Test
	public void longTitleDropsSpacers()
	{
		String out = renderHorizontal("長いタイトル", 90, null);
		//min-height でタイトルが50%を超えた時だけ伸びる(著者名と重ならず、通常は下段位置を維持)
		assertEquals("padding:5% 5% 0 5%; min-height:50%; text-align:center;", rule(out, ".upper"));
		assertEquals(0, countOf(out, "<div class=\"space\"></div>"));
	}

	/** 長タイトルでも SERIES がある場合は series div が出力される */
	@Test
	public void longTitleKeepsSeries()
	{
		String out = renderHorizontal("長いタイトル", 90, "シリーズ名");
		assertTrue(out.contains("<div class=\"series\">シリーズ名</div>"));
	}

	//---------- フォールバック: TITLE_LENGTH 未設定(旧jar + 新テンプレート) ----------

	@Test
	public void fallbackUsesTitleStringLength()
	{
		String fiftyChars = "あ".repeat(50);
		assertEquals("font-size:1.6em;", titleBlockFontSize(renderHorizontal(fiftyChars, null, null)));

		String twentyChars = "あ".repeat(20);
		assertEquals("font-size:2em;", titleBlockFontSize(renderHorizontal(twentyChars, null, null)));
	}

	/** TITLEもTITLE_LENGTHも無い場合(タイトル無し変換)は短い題名と同じ形になる
	 * (フォールバックの #set が no-op になり null 比較で space が消えるバグの回帰テスト) */
	@Test
	public void noTitleUsesTheShortTitleLayout()
	{
		VelocityContext ctx = new VelocityContext();
		ctx.put("title", "t");
		ctx.put("CREATOR", "テスト著者");
		StringWriter sw = new StringWriter();
		engine.getTemplate("title_horizontal.vm", "UTF-8").merge(ctx, sw);
		String out = sw.toString();
		assertEquals(4, countOf(out, "<div class=\"space\"></div>"));
		assertEquals("padding:10% 5% 0 5%; min-height:50%; text-align:center;", rule(out, ".upper"));
		assertEquals("font-size:2em;", titleBlockFontSize(out));
	}

	//---------- title_middle.vm: 簡易ラダー ----------

	@Test
	public void middleFontSizeLadder()
	{
		assertTrue(renderMiddle("タイトル", 45).contains(".title { font-size:1.75em; }"));
		assertTrue(renderMiddle("タイトル", 46).contains(".title { font-size:1.4em; }"));
		assertTrue(renderMiddle("タイトル", 80).contains(".title { font-size:1.4em; }"));
		assertTrue(renderMiddle("タイトル", 81).contains(".title { font-size:1.2em; }"));
	}

	/** title_middle.vm も horizontal と同じフォールバックを持つため対でテストする */
	@Test
	public void middleFallbackAndNoTitle()
	{
		//TITLE_LENGTH 未設定 → TITLE.length() フォールバック
		String fiftyChars = "あ".repeat(50);
		assertTrue(renderMiddle(fiftyChars, null).contains(".title { font-size:1.4em; }"));

		//TITLE も無し → 0 扱いで既定サイズ
		VelocityContext ctx = new VelocityContext();
		ctx.put("title", "t");
		StringWriter sw = new StringWriter();
		engine.getTemplate("title_middle.vm", "UTF-8").merge(ctx, sw);
		assertTrue(sw.toString().contains(".title { font-size:1.75em; }"));
	}
}
