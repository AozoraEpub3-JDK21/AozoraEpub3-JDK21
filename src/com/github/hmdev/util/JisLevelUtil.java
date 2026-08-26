package com.github.hmdev.util;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文字の JIS 水準を判別するクラス
 *
 * <p>端末フォントに無い文字を注記表示にフォールバックするかの判断に使う。
 * 判定は「注記由来か直接記述か」に依らずコードポイントのみで行うため、
 * ※［＃「廴＋囘」、第4水準2-12-11］ 経由でも ※［＃U+2231E］ 直接指定でも同じ結果になる。</p>
 *
 * <p>JDK 21 には EUC 系の JIS X 0213 コーデック(x-euc-jis-2004)が無く SS3 方式が使えないため、
 * x-SJIS_0213 でエンコードして Shift_JIS-2004 の面付け規則
 * (面1 = 先頭バイト 0x81-0x9F/0xE0-0xEF、面2 = 0xF0-0xFC) で面を判別する。
 * この規則は chuki_utf.txt 自身が持つ「第N水準」表記 3,698 件と照合して検証済み
 * (docs/gaiji-fallback-plan.md 発見5)。</p>
 */
public class JisLevelUtil
{
	/** JIS X 0208 の範囲 (第1水準・第2水準) */
	static public final int LEVEL_1_2 = 0;
	/** JIS X 0213 面1 の追加分 (第3水準) */
	static public final int LEVEL_3 = 3;
	/** JIS X 0213 面2 (第4水準) */
	static public final int LEVEL_4 = 4;
	/** JIS X 0213 でも表現できない文字 (Unicode 拡張漢字など) */
	static public final int LEVEL_OUT = 9;

	/** JIS X 0208 判定用 スレッド毎に持つ (CharsetEncoder はスレッドセーフでない) */
	static final ThreadLocal<CharsetEncoder> SJIS_ENCODER =
		ThreadLocal.withInitial(() -> Charset.forName("Shift_JIS").newEncoder());

	/** JIS X 0213 判定用 環境に存在しない場合は null */
	static final Charset SJIS_0213 = charsetOrNull("x-SJIS_0213");

	/** JIS X 0213 判定用エンコーダ SJIS_0213 が null の場合は使わない */
	static final ThreadLocal<CharsetEncoder> SJIS_0213_ENCODER =
		ThreadLocal.withInitial(() -> SJIS_0213 == null ? null : SJIS_0213.newEncoder());

	/** コードポイント毎の判定結果キャッシュ 外字は同じ文字が繰り返し現れる */
	static final Map<Integer, Integer> LEVEL_CACHE = new ConcurrentHashMap<Integer, Integer>();

	private JisLevelUtil() {}

	static Charset charsetOrNull(String name)
	{
		try {
			return Charset.forName(name);
		} catch (IllegalArgumentException e) {
			//UnsupportedCharsetException と IllegalCharsetNameException の両方を受ける
			/* 意図的: JIS X 0213 コーデックが無い環境では第3/第4水準を区別せず LEVEL_OUT に倒す */
			return null;
		}
	}

	/** x-SJIS_0213 が利用できるか
	 * <p>false の場合 {@link #level(int)} は第3・第4水準を返さず LEVEL_OUT に倒れる。</p> */
	static public boolean isJisX0213Available()
	{
		return SJIS_0213 != null;
	}

	/** コードポイントの JIS 水準を返す
	 * @param codePoint 判定するコードポイント
	 * @return LEVEL_1_2 / LEVEL_3 / LEVEL_4 / LEVEL_OUT のいずれか */
	static public int level(int codePoint)
	{
		Integer cached = LEVEL_CACHE.get(codePoint);
		if (cached != null) return cached.intValue();
		int level = computeLevel(codePoint);
		LEVEL_CACHE.put(codePoint, level);
		return level;
	}

	static int computeLevel(int codePoint)
	{
		if (!Character.isValidCodePoint(codePoint)) return LEVEL_OUT;
		String s = new String(Character.toChars(codePoint));

		if (SJIS_ENCODER.get().canEncode(s)) return LEVEL_1_2;

		CharsetEncoder encoder0213 = SJIS_0213_ENCODER.get();
		if (encoder0213 == null || !encoder0213.canEncode(s)) return LEVEL_OUT;

		byte[] bytes = s.getBytes(SJIS_0213);
		if (bytes.length == 0) return LEVEL_OUT;
		int lead = bytes[0] & 0xFF;
		//Shift_JIS-2004 の面2 (第4水準) は先頭バイトが 0xF0-0xFC
		return (lead >= 0xF0 && lead <= 0xFC) ? LEVEL_4 : LEVEL_3;
	}

	/** 文字列内で最も上位の (表現しにくい) 水準を返す
	 * <p>異体字セレクタは判定から除外する。セレクタ自体は JIS に無いため、
	 * 含めると基底字の水準が常に LEVEL_OUT に潰れてしまう。</p>
	 * @param str 判定する文字列
	 * @return 最も大きい水準値 判定対象が無ければ LEVEL_1_2 */
	static public int maxLevel(String str)
	{
		if (str == null || str.isEmpty()) return LEVEL_1_2;
		int max = LEVEL_1_2;
		int i = 0;
		while (i < str.length()) {
			int codePoint = str.codePointAt(i);
			i += Character.charCount(codePoint);
			if (isVariationSelector(codePoint)) continue;
			int level = level(codePoint);
			if (level > max) max = level;
		}
		return max;
	}

	/** 異体字セレクタ (VS1-16 と IVS) か */
	static public boolean isVariationSelector(int codePoint)
	{
		return (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
			|| (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
	}

	/** 指定水準以上なら端末で表示できない可能性が高いと判断する
	 * @param str 判定する文字列
	 * @param threshold 抑止を開始する水準 (LEVEL_3 / LEVEL_4 / LEVEL_OUT)
	 * @return threshold 以上の文字を含む場合 true */
	static public boolean exceeds(String str, int threshold)
	{
		return maxLevel(str) >= threshold;
	}
}
