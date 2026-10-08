package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 仮名＋濁点/半濁点の合成 (縦書き)。
 *
 * <p>本家 hmdev/AozoraEpub3 #17: 基底字がもう濁音・半濁音のときや促音のときに、
 * 文字の番号へ +1/+2 して別の字にしていた (が゛→き・ぱ゜→び・っ゛→つ)。
 * 直したあとは、合成できない対として DakutenType ごとの出し方 (並べる・重ねる・濁点フォント) に回る。</p>
 *
 * <p>本家 #18 (縦中横タグの直前で濁点の重ねが効かない) は 6f1245a で直っているので、
 * その形もここで固定する。</p>
 */
public class DakutenComposeTest
{
	static final String COMBINING_DAKUTEN = "゙";

	/** 縦書き・DakutenType を指定して本文 1 行を変換する */
	String convert(int dakutenType, String line) throws IOException
	{
		Epub3Writer writer = new Epub3Writer("");
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, "");
		converter.writer = writer;
		converter.bookInfo = new BookInfo(null);
		converter.vertical = true;
		converter.setCharOutput(dakutenType, false, false);
		StringWriter sw = new StringWriter();
		BufferedWriter out = new BufferedWriter(sw);
		converter.convertTextLineToEpub3(out, line, 1, true, true);
		out.flush();
		return sw.toString();
	}

	/** {入力, DakutenType=0, 1, 2 の期待} */
	static final String[][] NOT_COMPOSED = {
		//本家 #17 の報告の 4 例
		{"が゛", "が゛", "<span class=\"dakuten\">が<span>゛</span></span>", "が゛"},
		{"ば゛", "ば゛", "<span class=\"dakuten\">ば<span>゛</span></span>", "ば゛"},
		{"ギ゛", "ギ゛", "<span class=\"dakuten\">ギ<span>゛</span></span>", "ギ゛"},
		{"ぶ゜", "ぶ゜", "<span class=\"dakuten\">ぶ<span>゜</span></span>", "ぶ゜"},
		//報告に無いが同じ原因のもの
		{"っ゛", "っ゛", "<span class=\"dakuten\">っ<span>゛</span></span>", "<span class=\"glyph u3063-u3099\">っ</span>"},
		{"で゛", "で゛", "<span class=\"dakuten\">で<span>゛</span></span>", "で゛"},
		{"ぱ゜", "ぱ゜", "<span class=\"dakuten\">ぱ<span>゜</span></span>", "ぱ゜"},
		//結合文字は前処理で ゛ に揃う
		{"が"+COMBINING_DAKUTEN, "が゛", "<span class=\"dakuten\">が<span>゛</span></span>", "が゛"},
		//カタカナの促音は修正前から合成しない
		{"ッ゛", "ッ゛", "<span class=\"dakuten\">ッ<span>゛</span></span>", "<span class=\"glyph u30c3-u3099\">ッ</span>"},
	};

	@Test
	public void 濁音半濁音と促音には合成しない() throws IOException
	{
		for (String[] row : NOT_COMPOSED) {
			for (int type = 0; type <= 2; type++) {
				assertEquals(row[0]+" DakutenType="+type, row[type+1], convert(type, row[0]));
			}
		}
	}

	/** {入力, 合成後} どの DakutenType でも合成済みの 1 文字で出る */
	static final String[][] COMPOSED = {
		{"か゛", "が"},
		{"は゜", "ぱ"},
		{"ウ゛", "ヴ"},
		{"つ゛", "づ"},
		{"ツ゛", "ヅ"},
	};

	@Test
	public void 清音への合成は変わらない() throws IOException
	{
		for (String[] row : COMPOSED) {
			for (int type = 0; type <= 2; type++) {
				assertEquals(row[0]+" DakutenType="+type, row[1], convert(type, row[0]));
			}
		}
	}

	/** 本家 #18: 縦中横タグの直前でも直後でも、DakutenType どおりに出る */
	@Test
	public void 縦中横の前後でも濁点の出し方は変わらない() throws IOException
	{
		String line = "あ゛あ゛［＃縦中横］AB［＃縦中横終わり］あ゛";
		String tcy = "<span class=\"tcy\"><span>AB</span></span>";
		String span = "<span class=\"dakuten\">あ<span>゛</span></span>";
		String glyph = "<span class=\"glyph u3042-u3099\">あ</span>";
		assertEquals("あ゛あ゛"+tcy+"あ゛", convert(0, line));
		assertEquals(span+span+tcy+span, convert(1, line));
		assertEquals(glyph+glyph+tcy+glyph, convert(2, line));
	}

	@Test
	public void 合成の可否を対で判断する()
	{
		//合成しない: 基底字がもう濁音・半濁音、または促音
		String[] none = {"が゛", "ば゛", "ギ゛", "ぶ゜", "っ゛", "ッ゛", "で゛", "ぱ゜", "ぱ゛", "ば゜"};
		for (String pair : none) {
			assertEquals(pair, 0, AozoraEpub3Converter.composedDakuten(pair.charAt(0), pair.charAt(1)));
		}
		//合成する: 清音 (結合文字の濁点も受ける)
		assertEquals('が', AozoraEpub3Converter.composedDakuten('か', '゛'));
		assertEquals('が', AozoraEpub3Converter.composedDakuten('か', '゙'));
		assertEquals('ぱ', AozoraEpub3Converter.composedDakuten('は', '゜'));
		assertEquals('ば', AozoraEpub3Converter.composedDakuten('は', '゛'));
		assertEquals('ヅ', AozoraEpub3Converter.composedDakuten('ツ', '゛'));
		assertEquals('ゞ', AozoraEpub3Converter.composedDakuten('ゝ', '゛'));
	}
}
