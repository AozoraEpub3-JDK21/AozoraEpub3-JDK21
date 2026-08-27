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

	//------------------------------------------------------------------
	// 仮名＋濁点/半濁点（同梱の gaiji/dakuten/*.ttf で表示できる）
	//------------------------------------------------------------------

	/** 半濁点付き平仮名か chuki_utf.txt:7341 → か(U+304B) + 結合半濁点(U+309A) */
	static final String CHUKI_HANDAKUTEN_KA = "※［＃半濁点付き平仮名か、1-4-87］";
	/** 濁点付き二の字点 chuki_utf.txt:7323 → 〻(U+303B) + 結合濁点(U+3099) */
	static final String CHUKI_DAKUTEN_NONOJITEN = "※［＃濁点付き二の字点］";
	/** 井に濁点 chuki_utf.txt:7328 → 井(U+4E95) + 結合濁点(U+3099) 仮名でない唯一の濁点注記 */
	static final String CHUKI_DAKUTEN_I = "※［＃「井に濁点」］";
	/** 結合濁点 U+3099 */
	static final String COMBINING_DAKUTEN = new String(Character.toChars(0x3099));
	/** 結合半濁点 U+309A */
	static final String COMBINING_HANDAKUTEN = new String(Character.toChars(0x309A));

	/** 縦書き + 濁点フォント利用 (narou.rb の実測設定) にする */
	void setDakutenFontMode()
	{
		converter.vertical = true;
		converter.setCharOutput(2, false, false);
	}

	/** 縦書き + 濁点を CSS で重ねる (既定) にする */
	void setDakutenSpanMode()
	{
		converter.vertical = true;
		converter.setCharOutput(1, false, false);
	}

	/**
	 * 基底字が第1・2水準の仮名＋濁点/半濁点は抑止しない。
	 *
	 * 結合半濁点 U+309A は JIS に無いので単体の水準は LEVEL_OUT になるが、
	 * 単体では字にならず必ず基底字に付くので水準判定から除外する。
	 * 出力は が・ぱ のような合成済み文字か、基底字 か(第1・2水準) + 濁点 のどちらかになり、
	 * dakutenType のどの設定でも端末フォント頼みにならない。
	 */
	@Test
	public void 濁点付き仮名は抑止しない()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		String converted = converter.convertGaijiChuki(CHUKI_HANDAKUTEN_KA, true, false);
		assertEquals("か"+COMBINING_HANDAKUTEN, converted);
		assertEquals(0, converter.gaijiFallbackCount);
	}

	/**
	 * 仮名でない濁点注記も抑止しない。
	 *
	 * ※［＃「井に濁点」］ は 井(U+4E95・第1・2水準) + 結合濁点 の 2 文字で、
	 * 仮名でないので濁点の対としては扱われないが、結合濁点を水準判定から外せば
	 * 井 の水準だけで判断されるので抑止対象にならない。
	 * 出力時に濁点は U+309B(第1・2水準) に正規化されるので表示できる。
	 */
	@Test
	public void 仮名以外の濁点注記も抑止しない()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		assertEquals("井"+COMBINING_DAKUTEN, converter.convertGaijiChuki(CHUKI_DAKUTEN_I, true, false));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	/**
	 * 基底字自体が第3水準の濁点付き文字は、出力の仕方で扱いが変わる。
	 *
	 * 〻(U+303B) は第3水準なので、第3水準以上を抑止する設定では基底字がそのまま出る形は使えない。
	 * dakutenType=2 (narou.rb の実測設定) なら同梱の gaiji/dakuten/u303b-u3099.ttf で出せるので抑止しない。
	 */
	@Test
	public void 濁点付き二の字点は濁点フォント利用なら抑止しない()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenFontMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String converted = converter.convertGaijiChuki(CHUKI_DAKUTEN_NONOJITEN, true, false);
		assertEquals("〻"+COMBINING_DAKUTEN, converted);
		assertEquals(0, converter.gaijiFallbackCount);
	}

	/**
	 * 同じ 〻＋濁点でも、濁点フォントを使わない設定では基底字が端末フォント頼みになるので抑止する。
	 *
	 * 修正前はここで基底字だけが 〓 になり、濁点が孤立して 〓゛ になっていた。
	 */
	@Test
	public void 濁点付き二の字点は濁点フォントを使わなければ抑止する()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String converted = converter.convertGaijiChuki(CHUKI_DAKUTEN_NONOJITEN, true, false);
		assertEquals("〓［＃行右小書き］（濁点付き二の字点）［＃行右小書き終わり］", converted);
		assertEquals(1, converter.gaijiFallbackCount);
	}

	@Test
	public void 第4水準以上の抑止では濁点付き二の字点は対象外()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_4, false);
		//〻 は第3水準なので第4水準以上の抑止では対象にならない
		assertEquals("〻"+COMBINING_DAKUTEN, converter.convertGaijiChuki(CHUKI_DAKUTEN_NONOJITEN, true, false));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	@Test
	public void 濁点判定は仮名と濁点の2文字だけを対象にする()
	{
		//結合文字と正規化後の両方を受ける
		assertTrue(converter.isDakutenKana("か"+COMBINING_HANDAKUTEN));
		assertTrue(converter.isDakutenKana("か"+new String(Character.toChars(0x309C))));
		assertTrue(converter.isDakutenKana("カ"+COMBINING_DAKUTEN));
		assertTrue(converter.isDakutenKana("〻"+COMBINING_DAKUTEN));
		//仮名以外・濁点以外・長さ違いは対象外
		assertFalse(converter.isDakutenKana("漢"+COMBINING_DAKUTEN));
		assertFalse(converter.isDakutenKana("かき"));
		assertFalse(converter.isDakutenKana("か"));
		assertFalse(converter.isDakutenKana("かき"+COMBINING_DAKUTEN));
		assertFalse(converter.isDakutenKana(null));
	}

	/** 合成済みの文字になる組み合わせは、合成後の文字の水準で判断する */
	@Test
	public void 合成できる濁点は合成後の水準で判断する()
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		//か + 濁点 → が (第1・2水準)
		assertEquals('が', AozoraEpub3Converter.composedDakuten('か', '゛'));
		//ワ + 濁点 → ヷ (第3水準)
		assertEquals('ヷ', AozoraEpub3Converter.composedDakuten('ワ', '゛'));
		assertEquals(JisLevelUtil.LEVEL_3, JisLevelUtil.level('ヷ'));
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		assertFalse("が は第1・2水準なので抑止しない", converter.isDakutenPairFallback('か', '゛'));
		assertTrue("ヷ は第3水準なので抑止する", converter.isDakutenPairFallback('ワ', '゛'));
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

	//------------------------------------------------------------------
	// 仮名＋濁点/半濁点を行として変換したとき（濁点が孤立しないこと）
	//------------------------------------------------------------------

	/** 濁点 U+309B convertEscapedText が結合濁点を正規化した後の形 */
	static final String DAKUTEN = "゛";

	/**
	 * 第3水準の基底字＋濁点を抑止するとき、濁点だけが取り残されてはいけない。
	 *
	 * 修正前は基底字 〻 が先に 〓 になり、後続の ゛(第1・2水準) がそのまま出て 〓゛ になっていた。
	 */
	@Test
	public void 濁点付き二の字点を抑止しても濁点が孤立しない() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("〻"+DAKUTEN+"の字");
		assertFalse("濁点が孤立している: "+out, out.contains("〓"+DAKUTEN));
		assertFalse("濁点だけ残ってはいけない: "+out, out.contains(DAKUTEN));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		assertTrue("後続の文字は残る: "+out, out.contains("の字"));
		assertEquals(1, converter.gaijiFallbackCount);
	}

	@Test
	public void 濁点フォント利用なら第3水準の基底字でも抑止しない() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenFontMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("〻"+DAKUTEN+"の字");
		assertFalse("〓 になってはいけない: "+out, out.contains("〓"));
		assertTrue("濁点フォントの glyph タグが出る: "+out, out.contains("u303b-u3099"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	@Test
	public void 第1_2水準の基底字の濁点は抑止しない() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		//か + 半濁点 は合成できないので基底字と濁点がそのまま出る どちらも第1・2水準
		String out = convertLine("か"+new String(Character.toChars(0x309C))+"の字");
		assertFalse("〓 になってはいけない: "+out, out.contains("〓"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	@Test
	public void 同じ長さのルビでも濁点が孤立しない() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenSpanMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		//本文とルビが同じ長さの場合は1文字ずつルビを振る経路に入り、縦中横変換を通らない
		String out = convertLine("〻"+DAKUTEN+"《ああ》");
		assertFalse("濁点が孤立している: "+out, out.contains("〓"+DAKUTEN));
		assertFalse("濁点だけ残ってはいけない: "+out, out.contains(DAKUTEN));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
		//本文 2 文字を 〓 1 文字にしたので、本文の無い rt を残してはいけない
		assertEquals("ルビは本文と同じ数だけ出す: "+out, 1, out.split("<rt>", -1).length-1);
		//まとめた分のルビも落とさない
		assertTrue("読みが欠けている: "+out, out.contains("<rt>ああ</rt>"));
	}

	/**
	 * 同じ長さのルビ経路には合成も濁点フォントも無いので、基底字だけで判断する。
	 *
	 * この経路は printGlyphFontTag を通らないため、dakutenType=2 でも 〻 は生のまま出てしまう。
	 * convertTcyText 向けの判定（フォントがあるから免除）をそのまま使うと免除しすぎになる。
	 */
	@Test
	public void 同じ長さのルビでは濁点フォント利用でも基底字で判断する() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenFontMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("〻"+DAKUTEN+"《ああ》");
		assertFalse("この経路では glyph タグが出ないので 〻 を残してはいけない: "+out, out.contains("〻"));
		assertTrue("〓 に置き換わる: "+out, out.contains("〓"));
	}

	/**
	 * 逆に、合成前の基底字が第1・2水準なら同じ長さのルビ経路では抑止しない。
	 *
	 * ワ＋濁点は convertTcyText なら ヷ(第3水準) に合成されるので抑止対象だが、
	 * この経路では ワ と ゛ がそのまま並ぶだけでどちらも第1・2水準なので表示できる。
	 */
	@Test
	public void 同じ長さのルビでは合成後の水準で抑止しない() throws IOException
	{
		Assume.assumeTrue(JisLevelUtil.isJisX0213Available());
		setDakutenFontMode();
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		String out = convertLine("ワ"+DAKUTEN+"《ああ》");
		assertFalse("〓 になってはいけない: "+out, out.contains("〓"));
		assertTrue("基底字は残る: "+out, out.contains("ワ"));
		assertEquals(0, converter.gaijiFallbackCount);
	}

	/** 結合濁点は単体では字にならないので、それ自体を理由に 〓 にしてはいけない */
	@Test
	public void 結合濁点単体は水準判定から除外する()
	{
		converter.setGaijiFallback(JisLevelUtil.LEVEL_3, false);
		assertFalse(converter.isRawCharFallback(0x3099));
		assertFalse(converter.isRawCharFallback(0x309A));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.maxLevel("井"+COMBINING_DAKUTEN));
	}
}
