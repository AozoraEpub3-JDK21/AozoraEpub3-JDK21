package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * フォント一覧。実行環境に依存しないよう、インストール済みファミリを注入して検証する。
 */
public class FontCatalogTest
{
	@Test
	public void keepsOnlyInstalledRecommendations()
	{
		FontCatalog catalog = FontCatalog.from(List.of("游明朝", "游ゴシック", "Arial", "Comic Sans MS"));

		assertEquals(List.of("游明朝"), catalog.getMincho());
		assertEquals(List.of("游ゴシック"), catalog.getGothic());
		assertTrue(catalog.getOther().isEmpty());
		// インストール済み一覧には推奨外のフォントもそのまま残る
		assertTrue(catalog.getAll().contains("Comic Sans MS"));
	}

	@Test
	public void recommendationOrderIsPreserved()
	{
		// 入力の並びではなく推奨リストの並びで返る
		// (フォント名は Windows 11 実機の getAvailableFontFamilyNames() に合わせる)
		FontCatalog catalog = FontCatalog.from(List.of("Noto Serif JP", "BIZ UDP明朝 Medium", "游明朝"));
		assertEquals(List.of("游明朝", "BIZ UDP明朝 Medium", "Noto Serif JP"), catalog.getMincho());
		assertEquals("游明朝", catalog.getDefaultMincho());
	}

	@Test
	public void noRecommendedFontFallsBackToNull()
	{
		FontCatalog catalog = FontCatalog.from(List.of("Arial"));
		assertNull("推奨フォントが無ければブラウザ既定にフォールバックする", catalog.getDefaultMincho());
		assertNull(catalog.getDefaultGothic());
	}

	@Test
	public void emptyEnvironmentIsHandled()
	{
		FontCatalog catalog = FontCatalog.from(List.of());
		assertTrue(catalog.getMincho().isEmpty());
		assertTrue(catalog.getAll().isEmpty());
		assertNull(catalog.getDefaultMincho());
	}

	@Test
	public void udKyokashoIsPreferredAsTheBodyDefault()
	{
		// 縦書きの日本語を読むのに素直な字形なので、あれば既定にする。
		// 名前は Windows 11 実機の getAvailableFontFamilyNames() に合わせている
		// (「デジタル」と「教科書体」の間にスペース、-R は付かない)
		FontCatalog withUd = FontCatalog.from(
			List.of("游明朝", "UD デジタル 教科書体 N", "UD デジタル 教科書体 NP", "Meiryo"));
		assertEquals("UD デジタル 教科書体 NP", withUd.getDefaultBody());
		assertTrue("推奨リストにも出ること", withUd.getOther().contains("UD デジタル 教科書体 N"));

		// 無い環境では明朝へ落ちる
		FontCatalog withoutUd = FontCatalog.from(List.of("游明朝", "Meiryo"));
		assertEquals("游明朝", withoutUd.getDefaultBody());

		// 推奨がひとつも無ければ EPUB の指定のまま
		assertNull(FontCatalog.from(List.of("Arial")).getDefaultBody());
	}

	@Test
	public void jsonMatchesExactly()
	{
		// 「{ で始まり } で終わる」程度の検査では区切りの誤りを見逃すため、全体を突き合わせる
		StringBuilder buf = new StringBuilder();
		FontCatalog.from(List.of("游明朝", "Meiryo")).toJson(buf);

		assertEquals("{\"mincho\":[\"游明朝\"],"
			+ "\"gothic\":[\"Meiryo\"],"
			+ "\"other\":[],"
			+ "\"all\":[\"游明朝\",\"Meiryo\"],"
			+ "\"defaultBody\":\"游明朝\","
			+ "\"defaultMincho\":\"游明朝\","
			+ "\"defaultGothic\":\"Meiryo\"}", buf.toString());
	}

	@Test
	public void jsonUsesNullWhenNoRecommendationExists()
	{
		StringBuilder buf = new StringBuilder();
		FontCatalog.from(List.of("Arial")).toJson(buf);

		assertEquals("{\"mincho\":[],\"gothic\":[],\"other\":[],\"all\":[\"Arial\"],"
			+ "\"defaultBody\":null,\"defaultMincho\":null,\"defaultGothic\":null}", buf.toString());
		assertFalse("空配列の直後の区切りが壊れてはならない", buf.toString().contains("[],,"));
	}
}
