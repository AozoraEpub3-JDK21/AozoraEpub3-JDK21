package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

/**
 * JIS 水準判定のテスト（docs/gaiji-fallback-plan.md 機能1）。
 *
 * 端末フォントに無い文字を注記表示にフォールバックする判断の土台になるため、
 * 水準の取り違えは本文表現の破壊に直結する。
 * 代表文字の固定検証に加え、chuki_utf.txt が自身の注記に持つ「第N水準」表記を
 * 正解データとした全件照合も行う。
 */
public class JisLevelUtilTest
{
	/** 報告事例 ※［＃「廴＋囘」、第4水準2-12-11］ */
	static final int U_2231E = 0x2231E;

	@Test
	public void 第1水準の文字はLEVEL_1_2()
	{
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.level('崎'));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.level('亜'));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.level('あ'));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.level('A'));
	}

	@Test
	public void 第2水準の文字はLEVEL_1_2()
	{
		//弌 は JIS X 0208 第2水準
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.level('弌'));
	}

	@Test
	public void 第3水準の文字はLEVEL_3()
	{
		Assume.assumeTrue("x-SJIS_0213 が無い環境ではスキップ", JisLevelUtil.isJisX0213Available());
		//俠 は JIS X 0213 面1 の追加分
		assertEquals(JisLevelUtil.LEVEL_3, JisLevelUtil.level('俠'));
		//㐂 (U+3402) は第3水準1-14-3
		assertEquals(JisLevelUtil.LEVEL_3, JisLevelUtil.level(0x3402));
	}

	@Test
	public void 第4水準の文字はLEVEL_4()
	{
		Assume.assumeTrue("x-SJIS_0213 が無い環境ではスキップ", JisLevelUtil.isJisX0213Available());
		//報告事例 𢌞 は第4水準2-12-11
		assertEquals(JisLevelUtil.LEVEL_4, JisLevelUtil.level(U_2231E));
	}

	@Test
	public void JIS規格外の文字はLEVEL_OUT()
	{
		//U+4E04 丄 は JIS X 0212 補助漢字にはあるが JIS X 0213 には無い
		assertEquals(JisLevelUtil.LEVEL_OUT, JisLevelUtil.level(0x4E04));
	}

	@Test
	public void 異体字セレクタは判定から除外される()
	{
		//侮 (第1・2水準) + IVS。セレクタを含めても基底字の水準になる
		String withIvs = new String(Character.toChars(0x4FAE)) + new String(Character.toChars(0xE0101));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.maxLevel(withIvs));
	}

	@Test
	public void maxLevelは最も上位の水準を返す()
	{
		Assume.assumeTrue("x-SJIS_0213 が無い環境ではスキップ", JisLevelUtil.isJisX0213Available());
		String mixed = "崎" + new String(Character.toChars(U_2231E)) + "り";
		assertEquals(JisLevelUtil.LEVEL_4, JisLevelUtil.maxLevel(mixed));
	}

	@Test
	public void 空文字列とnullはLEVEL_1_2()
	{
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.maxLevel(""));
		assertEquals(JisLevelUtil.LEVEL_1_2, JisLevelUtil.maxLevel(null));
	}

	@Test
	public void exceedsは閾値以上でtrue()
	{
		Assume.assumeTrue("x-SJIS_0213 が無い環境ではスキップ", JisLevelUtil.isJisX0213Available());
		String target = new String(Character.toChars(U_2231E));
		//第4水準なので「第4水準以上を抑止」「第3水準以上を抑止」では対象
		assertTrue(JisLevelUtil.exceeds(target, JisLevelUtil.LEVEL_4));
		assertTrue(JisLevelUtil.exceeds(target, JisLevelUtil.LEVEL_3));
		//「JIS規格外のみ抑止」では対象外
		assertFalse(JisLevelUtil.exceeds(target, JisLevelUtil.LEVEL_OUT));
		//第1水準はどの閾値でも対象外
		assertFalse(JisLevelUtil.exceeds("崎", JisLevelUtil.LEVEL_3));
	}

	/**
	 * chuki_utf.txt の注記に書かれた「第N水準」表記と判定結果を全件照合する。
	 *
	 * 不一致が出るのは以下の 2 パターンのみで、いずれも判定側の誤りではない
	 * (docs/gaiji-fallback-plan.md 発見5):
	 *   - 包摂適用で表が第1・2水準の字を保持しているもの (猪 祥 諸 都)
	 *   - 注記の説明文中に別の字の水準表記が入っているもの
	 * このため許容する不一致件数に上限を設けて検証する。
	 */
	@Test
	public void chuki_utfの水準表記と判定が一致する() throws IOException
	{
		Assume.assumeTrue("x-SJIS_0213 が無い環境ではスキップ", JisLevelUtil.isJisX0213Available());
		Path chukiUtf = Paths.get("chuki_utf.txt");
		Assume.assumeTrue("chuki_utf.txt が無い環境ではスキップ", Files.exists(chukiUtf));

		Pattern levelPattern = Pattern.compile("第([34])水準");
		int labelled = 0;
		int mismatch = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				Files.newInputStream(chukiUtf), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String utfChar = parseUtfChar(line);
				if (utfChar == null) continue;
				Matcher m = levelPattern.matcher(line);
				if (!m.find()) continue;
				labelled++;
				int expected = Integer.parseInt(m.group(1));
				if (JisLevelUtil.maxLevel(utfChar) != expected) mismatch++;
			}
		}

		assertTrue("水準表記のあるエントリが読めていない: " + labelled, labelled > 3000);
		//実測 3,698 件中 11 件。規則を壊す変更が入れば一気に増える
		assertTrue("水準表記との不一致が想定より多い: " + mismatch + " / " + labelled,
				mismatch <= 20);
	}

	/** chuki_utf.txt の 1 行から変換後の文字を取り出す
	 * <p>切り出しは AozoraGaijiConverter.loadChukiFile と同一にすること。
	 * ずれると本番の変換対象と違う文字列を検証してしまう。</p>
	 * @return 変換後の文字 対象行でなければ null */
	static String parseUtfChar(String line)
	{
		if (line.isEmpty() || line.charAt(0) == '#') return null;
		int charStart = line.indexOf('\t');
		if (charStart == -1) return null;
		charStart = line.indexOf('\t', charStart + 1);
		if (charStart == -1) return null;
		charStart++;
		int chukiStart = line.indexOf('\t', charStart);
		if (chukiStart == -1) return null;
		chukiStart++;
		if (!line.startsWith("※［＃", chukiStart)) return null;
		return line.substring(charStart, chukiStart - 1);
	}
}
