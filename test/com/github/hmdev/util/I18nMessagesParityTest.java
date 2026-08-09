package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * ja / en の文言ファイルが同じキーを持っているかを検証する。
 *
 * <p>UI 文言を片方の言語にだけ足す事故が繰り返し起きているため、
 * 「気をつける」ではなくテストで塞ぐ。キーが欠けた側では
 * {@link I18n#t(String, Object...)} がキー文字列をそのまま画面に出す。</p>
 */
public class I18nMessagesParityTest
{
	/** MessageFormat の引数 ({0} など)。エスケープ ('{0}') は文言側で使っていない */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[^}]*\\}");

	private static Properties load(String lang) throws IOException
	{
		Path file = Paths.get("src", "i18n", "messages_"+lang+".properties");
		assertTrue(file+" が見つかりません", Files.isRegularFile(file));
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		return props;
	}

	/** 文言に含まれる引数番号 */
	private static Set<String> placeholders(String value)
	{
		Set<String> found = new TreeSet<String>();
		Matcher matcher = PLACEHOLDER.matcher(value);
		while (matcher.find()) found.add(matcher.group(1));
		return found;
	}

	@Test
	public void jaAndEnHaveTheSameKeys() throws IOException
	{
		Set<String> ja = new TreeSet<String>(load("ja").stringPropertyNames());
		Set<String> en = new TreeSet<String>(load("en").stringPropertyNames());

		Set<String> missingInEn = new LinkedHashSet<String>(ja);
		missingInEn.removeAll(en);
		Set<String> missingInJa = new LinkedHashSet<String>(en);
		missingInJa.removeAll(ja);

		assertEquals("messages_en.properties に無いキー", Set.of(), missingInEn);
		assertEquals("messages_ja.properties に無いキー", Set.of(), missingInJa);
	}

	@Test
	public void jaAndEnUseTheSameMessageFormatArguments() throws IOException
	{
		Properties ja = load("ja");
		Properties en = load("en");
		List<String> mismatched = new ArrayList<String>();
		for (String key : new TreeSet<String>(ja.stringPropertyNames())) {
			String enValue = en.getProperty(key);
			//キーの過不足は別のテストが見る
			if (enValue == null) continue;
			if (!placeholders(ja.getProperty(key)).equals(placeholders(enValue))) mismatched.add(key);
		}
		assertEquals("ja と en で {0} などの引数が食い違うキー", List.of(), mismatched);
	}

	@Test
	public void previewTabKeysExistInBothLanguages() throws IOException
	{
		Properties ja = load("ja");
		Properties en = load("en");
		for (String key : List.of("ui.tab.preview", "ui.chk.autoPreview", "ui.tooltip.autoPreview")) {
			assertTrue(key+" が ja にありません", ja.containsKey(key));
			assertTrue(key+" が en にありません", en.containsKey(key));
		}
	}
}
