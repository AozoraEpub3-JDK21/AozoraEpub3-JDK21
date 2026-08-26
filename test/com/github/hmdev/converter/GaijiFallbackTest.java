package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.JisLevelUtil;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 外字の注記表示フォールバックのテスト（docs/gaiji-fallback-plan.md 機能1）。
 *
 * 端末フォントに無い文字が ? や豆腐になるより、何の字か分かる注記を残したいという要望への対応。
 * 既定では従来どおり変換するので、既定のまま出力が変わらないことも併せて検証する。
 */
public class GaijiFallbackTest
{
	/** 報告事例の文字 𢌞 (第4水準2-12-11) */
	static final int U_2231E = 0x2231E;
	/** 報告事例 ※［＃「廴＋囘」、第4水準2-12-11］ → 𢌞 (U+2231E) */
	static final String CHUKI_2231E = "※［＃「廴＋囘」、第4水準2-12-11］り";
	/** 面区点コードを持たず第1・2水準の 二 (U+4E8C) に変換される注記 */
	static final String CHUKI_NI = "※［＃「三／二」］";
	/** 面区点コードを持つ注記 chuki_utf.txt:3277 のフィールド2は U+732A だが
	 *  コード指定 第3水準1-87-79 の方が優先されるため U+FA16 になる */
	static final String CHUKI_INOSHISHI = "※［＃「けものへん＋睹のつくり」、第3水準1-87-79］";
	/** JIS X 0213 面1 87区79点 互換漢字の 猪 */
	static final String CHAR_FA16 = new String(Character.toChars(0xFA16));
	/** JIS X 0208 の 猪 */
	static final String CHAR_732A = new String(Character.toChars(0x732A));

	AozoraEpub3Converter converter;

	@Before
	public void setUp() throws IOException
	{
		Epub3Writer writer = new Epub3Writer("");
		this.converter = new AozoraEpub3Converter(writer, "");
		this.converter.writer = writer;
		this.converter.bookInfo = new BookInfo(null);
	}

	@Test
	public void 既定では従来どおり変換する()
	{
		String converted = converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertEquals("𢌞り", converted);
	}

	@Test
	public void 第4水準以上を抑止すると注記表示になる()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		String converted = converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertEquals("〓［＃行右小書き］（「廴＋囘」）［＃行右小書き終わり］り", converted);
	}

	@Test
	public void 水準コードを含める設定では注記がそのまま残る()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, true);
		String converted = converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertEquals("〓［＃行右小書き］（「廴＋囘」、第4水準2-12-11）［＃行右小書き終わり］り", converted);
	}

	@Test
	public void JIS規格外のみ抑止では第4水準は変換されたまま()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_OUT, false);
		String converted = converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertEquals("𢌞り", converted);
	}

	@Test
	public void 第3水準以上を抑止すると第4水準も対象になる()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String converted = converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertTrue(converted.startsWith("〓［＃行右小書き］"));
	}

	@Test
	public void 変換結果が第1_2水準なら抑止しない()
	{
		//※［＃「三／二」］ は面区点コードを持たないので注記名称から 二 (第1水準) に変換される
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String converted = converter.convertGaijiChuki(CHUKI_NI, true, false);
		assertEquals("二", converted);
		assertEquals(0, converter.gaijiFallbackCount);
	}

	/**
	 * 面区点コード付きの注記では、コード側の変換が注記名称より優先される。
	 *
	 * chuki_utf.txt:3277 は「※［＃「けものへん＋睹のつくり」、第3水準1-87-79］」に対して
	 * フィールド2 に U+732A(JIS X 0208 の 猪) を持つが、
	 * AozoraEpub3Converter は codeToCharString(chukiValues[1]) を toUtf(chukiValues[0]) より先に試すため、
	 * 実際の変換結果は面区点 1-87-79 が指す U+FA16(互換漢字の 猪) になる。
	 * 水準判定はこの実際の変換結果に対して効く必要がある。
	 */
	@Test
	public void 面区点コードが注記名称より優先される()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		//既定では変換されたまま
		String converted = converter.convertGaijiChuki(CHUKI_INOSHISHI, true, false);
		assertEquals(CHAR_FA16, converted);
		assertNotEquals("フィールド2 の U+732A ではなくコード側の U+FA16 になる", CHAR_732A, converted);
		//U+FA16 は第3水準なので、第3水準以上の抑止では注記表示になる
		assertEquals(JisLevelUtil.LEVEL_3, JisLevelUtil.maxLevel(CHAR_FA16));
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		assertEquals("〓［＃行右小書き］（「けものへん＋睹のつくり」）［＃行右小書き終わり］",
				converter.convertGaijiChuki(CHUKI_INOSHISHI, true, false));
	}

	@Test
	public void Uプラス直接指定でも抑止される()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		String converted = converter.convertGaijiChuki("※［＃U+2231E］り", true, false);
		assertEquals("〓［＃行右小書き］（U+2231E）［＃行右小書き終わり］り", converted);
	}

	@Test
	public void フォールバック件数を数える()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		converter.gaijiFallbackCount = 0;
		converter.convertGaijiChuki(CHUKI_2231E, true, false);
		converter.convertGaijiChuki(CHUKI_2231E, true, false);
		assertEquals(2, converter.gaijiFallbackCount);
	}

	/**
	 * 1文字フォントが登録されている文字は、注記経由でも抑止されない。
	 *
	 * convertGaijiChuki は convertChar のフォント探索より手前で動くため、
	 * フォントの有無を見ずに置換すると gaiji/u2231e.ttf を置いても 〓 になってしまう。
	 */
	@Test
	public void 一文字フォントがある文字は抑止しない()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		HashMap<Integer, String> saved = AozoraEpub3Converter.utf32FontMap;
		try {
			HashMap<Integer, String> fontMap = new HashMap<Integer, String>();
			fontMap.put(U_2231E, "u2231e.ttf");
			AozoraEpub3Converter.utf32FontMap = fontMap;
			converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
			assertTrue("1文字フォントがあれば抑止対象にならない",
					converter.hasGaijiFont(new String(Character.toChars(U_2231E))));
			assertFalse(converter.isGaijiFallback(new String(Character.toChars(U_2231E))));
		} finally {
			AozoraEpub3Converter.utf32FontMap = saved;
		}
	}

	@Test
	public void 一文字フォントが無ければ抑止する()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		assertFalse(converter.hasGaijiFont(new String(Character.toChars(U_2231E))));
		assertTrue(converter.isGaijiFallback(new String(Character.toChars(U_2231E))));
	}

	@Test
	public void 外字を含まない行は素通しする()
	{
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		assertEquals("ただの本文です", converter.convertGaijiChuki("ただの本文です", true, false));
	}

	//------------------------------------------------------------------
	// 注記を経由しない生の文字（本文に直接書かれた第3・第4水準）
	//------------------------------------------------------------------

	/** 本文を XHTML に変換して中身を取り出す */
	String convertLine(String line) throws IOException
	{
		StringWriter sw = new StringWriter();
		BufferedWriter bw = new BufferedWriter(sw);
		converter.convertTextLineToEpub3(bw, line, 0, false, true);
		bw.close();
		return sw.toString();
	}

	@Test
	public void 生の第4水準文字は既定では出力される() throws IOException
	{
		String out = convertLine("𢌞り");
		assertTrue("本文に 𢌞 が残る: "+out, out.contains("𢌞"));
	}

	@Test
	public void 生の第4水準文字は抑止すると下駄記号になる() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		String out = convertLine("𢌞り");
		assertFalse("𢌞 が残っている: "+out, out.contains("𢌞"));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("後続の文字は残る: "+out, out.contains("り"));
		assertEquals(1, converter.gaijiFallbackCount);
	}

	@Test
	public void 生のBMP第3水準文字も抑止できる() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		//俠 は BMP 内の第3水準。4バイト文字とは別の経路を通る
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("俠客");
		assertFalse("俠 が残っている: "+out, out.contains("俠"));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("第1・2水準の 客 は残る: "+out, out.contains("客"));
	}

	@Test
	public void 生の第1_2水準文字は抑止されない() throws IOException
	{
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("崎の字は残る");
		assertTrue(out.contains("崎"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	@Test
	public void JIS規格外のみ抑止では第3水準の生文字は残る() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_OUT, false);
		String out = convertLine("俠客");
		assertTrue("俠 は第3水準なので残る: "+out, out.contains("俠"));
	}

	//------------------------------------------------------------------
	// 異体字セレクタ付き（IVS/VS の分岐は水準判定より手前で出力していた）
	//------------------------------------------------------------------

	/** IVS U+E0100 見た目に出ない文字なのでコードポイントから作る */
	static final String IVS_E0100 = new String(Character.toChars(0xE0100));
	/** VS1 U+FE00 見た目に出ない文字なのでコードポイントから作る */
	static final String VS_FE00 = new String(Character.toChars(0xFE00));

	@Test
	public void IVS付きの生の4バイト文字も抑止される() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		String out = convertLine("𢌞"+IVS_E0100+"り");
		assertFalse("𢌞 が残っている: "+out, out.contains("𢌞"));
		assertFalse("異体字セレクタだけ残ってはいけない: "+out, out.contains(IVS_E0100));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("後続の文字は残る: "+out, out.contains("り"));
		assertEquals(1, converter.gaijiFallbackCount);
	}

	@Test
	public void IVS付きの生のBMP文字も抑止される() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("俠"+IVS_E0100+"客");
		assertFalse("俠 が残っている: "+out, out.contains("俠"));
		assertFalse("異体字セレクタだけ残ってはいけない: "+out, out.contains(IVS_E0100));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("第1・2水準の 客 は残る: "+out, out.contains("客"));
	}

	@Test
	public void VS付きの生のBMP文字も抑止される() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("俠"+VS_FE00+"客");
		assertFalse("俠 が残っている: "+out, out.contains("俠"));
		assertFalse("異体字セレクタだけ残ってはいけない: "+out, out.contains(VS_FE00));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("第1・2水準の 客 は残る: "+out, out.contains("客"));
	}

	@Test
	public void IVS付きでも既定なら変換されたまま() throws IOException
	{
		String out = convertLine("𢌞"+IVS_E0100+"り");
		assertTrue("本文に 𢌞 が残る: "+out, out.contains("𢌞"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	//------------------------------------------------------------------
	// 同じ長さのルビ（convertTcyText を通らず convertReplacedChar に直接出力する経路）
	//------------------------------------------------------------------

	@Test
	public void 一文字ルビの本文も抑止される() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		//本文とルビが同じ長さの場合は1文字ずつルビを振る経路に入り、縦中横変換を通らない
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("俠《き》");
		assertFalse("俠 が残っている: "+out, out.contains("俠"));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("ルビは残る: "+out, out.contains("き"));
	}

	@Test
	public void 一文字ルビでも既定なら変換されたまま() throws IOException
	{
		String out = convertLine("俠《き》");
		assertTrue("本文に 俠 が残る: "+out, out.contains("俠"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	@Test
	public void 同じ長さのルビでも第1_2水準は抑止しない() throws IOException
	{
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("崎《さ》");
		assertTrue("本文に 崎 が残る: "+out, out.contains("崎"));
		assertEquals(0, converter.gaijiFallbackCount);
	}
}
