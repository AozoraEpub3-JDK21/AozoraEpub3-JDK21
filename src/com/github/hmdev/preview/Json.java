package com.github.hmdev.preview;

/**
 * 依存を増やさないための最小限の JSON 生成ヘルパ。
 *
 * <p>プレビュー機能は「追加依存ゼロ」を設計方針としているため
 * (docs/epub-preview-plan.md 参照)、JSON ライブラリを導入せずに済ませる。
 * 出力専用であり、パースは行わない。</p>
 */
final class Json
{
	/** JavaScript では改行として扱われるため、JSON 文字列内でエスケープが必要な文字 */
	private static final char LINE_SEPARATOR = 0x2028;
	private static final char PARAGRAPH_SEPARATOR = 0x2029;

	private Json() {}

	/** 文字列を JSON の文字列リテラル (引用符込み) に変換する。null は "null" */
	static String str(String value)
	{
		if (value == null) return "null";
		StringBuilder buf = new StringBuilder(value.length() + 16);
		buf.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '"': buf.append("\\\""); break;
			case '\\': buf.append("\\\\"); break;
			case '\n': buf.append("\\n"); break;
			case '\r': buf.append("\\r"); break;
			case '\t': buf.append("\\t"); break;
			case '\b': buf.append("\\b"); break;
			case '\f': buf.append("\\f"); break;
			default:
				if (c < 0x20 || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
					buf.append(String.format("\\u%04x", (int)c));
				} else {
					buf.append(c);
				}
			}
		}
		buf.append('"');
		return buf.toString();
	}

	/** "key":value 形式で文字列プロパティを追加する。先頭以外はカンマを前置する */
	static void prop(StringBuilder buf, String key, String value)
	{
		appendComma(buf);
		buf.append(str(key)).append(':').append(str(value));
	}

	/** "key":value 形式で数値プロパティを追加する */
	static void prop(StringBuilder buf, String key, long value)
	{
		appendComma(buf);
		buf.append(str(key)).append(':').append(value);
	}

	/** "key":value 形式で真偽値プロパティを追加する */
	static void prop(StringBuilder buf, String key, boolean value)
	{
		appendComma(buf);
		buf.append(str(key)).append(':').append(value);
	}

	/** "key": を追加する (値は呼び出し側で追記する) */
	static void key(StringBuilder buf, String key)
	{
		appendComma(buf);
		buf.append(str(key)).append(':');
	}

	/** 直前が '{' '[' ':' ',' 以外ならカンマを追加する */
	private static void appendComma(StringBuilder buf)
	{
		if (buf.length() == 0) return;
		char last = buf.charAt(buf.length() - 1);
		if (last == '{' || last == '[' || last == ':' || last == ',') return;
		buf.append(',');
	}
}
