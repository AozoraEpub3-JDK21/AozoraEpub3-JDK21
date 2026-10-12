package com.github.hmdev.util;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

/**
 * 青空文庫の作品の URL を図書カードのテキストの zip に読み替える（internal の ADR 0001）。ネットワークに出ない。
 */
public class AozoraBunkoUrlTest
{
	private static final String CARD = "https://www.aozora.gr.jp/cards/000148/card789.html";

	private static Document card(String html, String baseUrl)
	{
		return Jsoup.parse(html, baseUrl);
	}

	private static Document fixture() throws Exception
	{
		return card(new String(Files.readAllBytes(Paths.get("test_data", "aozora", "card789.html")), StandardCharsets.UTF_8), CARD);
	}

	@Test
	public void workAndCardUrlsAreRecognized()
	{
		assertTrue(AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertTrue("http も", AozoraBunkoUrl.isWorkUrl("http://www.aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertTrue("www なしも", AozoraBunkoUrl.isWorkUrl("https://aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertTrue("図書カード", AozoraBunkoUrl.isWorkUrl(CARD));
		assertTrue("ページ内の位置つき", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp/cards/000148/files/789_14547.html#midashi10"));
		//ポートつき・大文字のホスト名も（Web 変換のサイトの決め方はポートを落とすので、外すと HTML の経路に落ちる）
		assertTrue("ポートつき", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp:443/cards/000148/files/789_14547.html"));
		assertTrue("大文字のホスト名", AozoraBunkoUrl.isWorkUrl("HTTPS://WWW.AOZORA.GR.JP/cards/000148/card789.html"));
		assertEquals(CARD, AozoraBunkoUrl.cardUrl("https://www.aozora.gr.jp:443/cards/000148/files/789_14547.html"));
	}

	@Test
	public void otherUrlsAreNotWorkUrls()
	{
		assertFalse("zip は今の zip の経路", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp/cards/000148/files/789_ruby_5639.zip"));
		assertFalse("作家の一覧", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp/index_pages/person148.html"));
		assertFalse("トップ", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp/"));
		assertFalse("別のサイト", AozoraBunkoUrl.isWorkUrl("https://example.com/cards/000148/files/789_14547.html"));
		assertFalse("ホスト名の後ろが違う", AozoraBunkoUrl.isWorkUrl("https://www.aozora.gr.jp.example.com/cards/000148/card789.html"));
		assertFalse("ホスト名の前が違う", AozoraBunkoUrl.isWorkUrl("https://xaozora.gr.jp/cards/000148/card789.html"));
		assertFalse("ホスト名の前が違う（www の代わり）", AozoraBunkoUrl.isWorkUrl("https://evil.aozora.gr.jp/cards/000148/card789.html"));
		assertFalse(AozoraBunkoUrl.isWorkUrl(null));
	}

	@Test
	public void aozoraArchivesAreReadAsShiftJis()
	{
		//作品の URL から読み替えた zip も、zip の URL を直に渡したときも
		assertTrue(AozoraBunkoUrl.isTextArchiveUrl("https://www.aozora.gr.jp/cards/000148/files/789_ruby_5639.zip"));
		assertTrue(AozoraBunkoUrl.isTextArchiveUrl("http://aozora.gr.jp/cards/000148/files/789_ruby_5639.zip"));
		assertFalse("別のサイトの zip は -enc・画面の文字コードのとおり", AozoraBunkoUrl.isTextArchiveUrl("https://example.com/cards/000148/files/789_ruby_5639.zip"));
		assertFalse(AozoraBunkoUrl.isTextArchiveUrl("https://www.aozora.gr.jp.example.com/a.zip"));
		assertFalse("作品の XHTML は書庫でない", AozoraBunkoUrl.isTextArchiveUrl("https://www.aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertFalse(AozoraBunkoUrl.isTextArchiveUrl(null));
	}

	@Test
	public void cardUrlIsBuiltFromTheWorkId()
	{
		assertEquals(CARD, AozoraBunkoUrl.cardUrl("https://www.aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertEquals("http で渡されても https で取る", CARD, AozoraBunkoUrl.cardUrl("http://aozora.gr.jp/cards/000148/files/789_14547.html"));
		assertEquals(CARD, AozoraBunkoUrl.cardUrl(CARD));
		assertEquals("789", AozoraBunkoUrl.workId(CARD));
		assertNull(AozoraBunkoUrl.cardUrl("https://www.aozora.gr.jp/"));
	}

	@Test
	public void zipOfThisWorkIsChosenFromTheCard() throws Exception
	{
		//関連の作品の zip（790_…）は選ばない。相対リンクは図書カードの URL で解決する
		assertEquals("https://www.aozora.gr.jp/cards/000148/files/789_ruby_5639.zip",
			AozoraBunkoUrl.zipUrlFromCard(fixture(), "789"));
	}

	@Test
	public void rubyZipIsPreferred()
	{
		Document d = card("<a href=\"./files/789_5640.zip\">a</a><a href=\"./files/789_ruby_5639.zip\">b</a>", CARD);
		assertEquals("https://www.aozora.gr.jp/cards/000148/files/789_ruby_5639.zip", AozoraBunkoUrl.zipUrlFromCard(d, "789"));
	}

	@Test
	public void plainZipIsUsedWhenThereIsNoRubyZip()
	{
		Document d = card("<a href=\"./files/789_14547.html\">x</a><a href=\"./files/789_5640.zip\">a</a>", CARD);
		assertEquals("https://www.aozora.gr.jp/cards/000148/files/789_5640.zip", AozoraBunkoUrl.zipUrlFromCard(d, "789"));
	}

	@Test
	public void noZipGivesNull()
	{
		//作品ID の頭が同じ別の作品（7890）も、後ろが同じ別の作品（1789）も選ばない
		Document d = card("<a href=\"./files/789_14547.html\">x</a><a href=\"./files/7890_ruby_1.zip\">y</a><a href=\"./files/1789_ruby_2.zip\">z</a>", CARD);
		assertNull(AozoraBunkoUrl.zipUrlFromCard(d, "789"));
	}
}
