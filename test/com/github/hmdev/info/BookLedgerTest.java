package com.github.hmdev.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 作品の台帳のテスト（internal #11）。identifier は掲載元の URL だけで決まり、題には左右されない。
 */
public class BookLedgerTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void theSameWorkGetsTheSameIdentifier() {
		String id = BookLedger.identifierFor("https://ncode.syosetu.com/n1234ab/");
		assertEquals(id, BookLedger.identifierFor("http://ncode.syosetu.com/n1234ab/"));
		assertEquals(id, BookLedger.identifierFor("https://NCODE.syosetu.com/n1234ab"));
		assertEquals(id, BookLedger.identifierFor("https://ncode.syosetu.com/n1234ab/#top"));
		assertEquals(id, BookLedger.identifierFor(" https://ncode.syosetu.com/n1234ab// "));
	}

	@Test
	public void differentWorksGetDifferentIdentifiers() {
		String id = BookLedger.identifierFor("https://ncode.syosetu.com/n1234ab/");
		assertNotEquals(id, BookLedger.identifierFor("https://ncode.syosetu.com/n1234ac/"));
		assertNotEquals(id, BookLedger.identifierFor("https://novel18.syosetu.com/n1234ab/"));
		// クエリで作品を分けるサイトがある
		assertNotEquals(BookLedger.identifierFor("https://example.com/novel?id=1"),
			BookLedger.identifierFor("https://example.com/novel?id=2"));
		// パスが無く、ホストの直後にクエリが来ても、クエリの大文字小文字は残す
		assertNotEquals(BookLedger.identifierFor("https://example.com?work=A"),
			BookLedger.identifierFor("https://example.com?work=a"));
		assertEquals(BookLedger.identifierFor("https://EXAMPLE.com?work=A"),
			BookLedger.identifierFor("https://example.com?work=A"));
	}

	@Test
	public void identifierIsAUuidThatDoesNotDependOnTheTitle() {
		BookLedger a = BookLedger.create("https://kakuyomu.jp/works/1", "[著者] 題");
		BookLedger b = BookLedger.create("https://kakuyomu.jp/works/1", "[著者] 【書籍化】題");
		assertEquals(a.identifier, b.identifier);
		assertTrue(BookLedger.isUuid(a.identifier));
	}

	@Test
	public void saveThenLoadKeepsEveryField() throws Exception {
		File dir = tempFolder.newFolder();
		BookLedger saved = BookLedger.create("https://kakuyomu.jp/works/1?a=1&b=2", "[作者 名] 題：副題")
			.withOutputBaseName("[作者!] 題 ");
		saved.save(dir);
		BookLedger loaded = BookLedger.load(dir);
		assertNotNull(loaded);
		assertEquals(saved.sourceUrl, loaded.sourceUrl);
		assertEquals(saved.identifier, loaded.identifier);
		assertEquals("[作者 名] 題：副題", loaded.textBaseName);
		assertEquals("[作者 名] 題：副題.txt", loaded.textFileName());
		// 今までの EPUB の名前は著者名の ! を残していた。前後の空白も記録したまま
		assertEquals("[作者!] 題 ", loaded.outputBaseName);
		// 一時ファイルを残さない
		String[] names = dir.list();
		assertEquals(1, names.length);
		assertEquals(BookLedger.FILE_NAME, names[0]);
	}

	@Test
	public void aLeadingSpaceSurvivesTheRoundTrip() throws Exception {
		File dir = tempFolder.newFolder();
		BookLedger.create("https://kakuyomu.jp/works/1", " 題").withOutputBaseName(" 題").save(dir);
		BookLedger loaded = BookLedger.load(dir);
		assertEquals(" 題", loaded.textBaseName);
		assertEquals(" 題", loaded.outputBaseName);
	}

	@Test
	public void withoutATextNameTheLedgerBelongsToConvertedTxt() {
		assertEquals("converted.txt", BookLedger.create("https://kakuyomu.jp/works/1", null).textFileName());
	}

	@Test
	public void noLedgerMeansNull() throws Exception {
		assertNull(BookLedger.load(tempFolder.newFolder()));
		assertNull(BookLedger.load(null));
	}

	@Test
	public void aLedgerWithoutTheUrlIsIgnored() throws Exception {
		File dir = tempFolder.newFolder();
		write(dir, "identifier=00000000-0000-0000-0000-000000000000\noutputBaseName=x\n");
		assertNull(BookLedger.load(dir));
	}

	@Test
	public void aBrokenIdentifierIsRecomputedFromTheUrl() throws Exception {
		File dir = tempFolder.newFolder();
		write(dir, "sourceUrl=https://kakuyomu.jp/works/1\nidentifier=not-a-uuid\n");
		BookLedger loaded = BookLedger.load(dir);
		assertNotNull(loaded);
		assertEquals(BookLedger.identifierFor("https://kakuyomu.jp/works/1"), loaded.identifier);
		assertNull(loaded.outputBaseName);
	}

	@Test
	public void anEditedOutputNameCannotLeaveTheFolder() throws Exception {
		File dir = tempFolder.newFolder();
		write(dir, "sourceUrl=https://kakuyomu.jp/works/1\noutputBaseName=../../evil\ntextBaseName=..\\\\..\\\\evil\n");
		BookLedger loaded = BookLedger.load(dir);
		assertNotNull(loaded);
		for (String name : new String[]{ loaded.outputBaseName, loaded.textBaseName }) {
			assertFalse(name, name.contains("/"));
			assertFalse(name, name.contains("\\"));
		}
	}

	@Test
	public void onlyHttpUrlsAreSources() {
		assertTrue(BookLedger.isHttpUrl("https://kakuyomu.jp/works/1"));
		assertTrue(BookLedger.isHttpUrl("http://127.0.0.1:8080/novel/"));
		assertTrue(BookLedger.isHttpUrl("HTTPS://example.com/novel"));
		// ブラウザが受け付ける | や空白の入った URL も書く（URI としては解析できない）
		assertTrue(BookLedger.isHttpUrl("https://example.com/novel?a=1|2 3"));
		assertFalse(BookLedger.isHttpUrl("file:///etc/passwd"));
		assertFalse(BookLedger.isHttpUrl("javascript:alert(1)"));
		assertFalse(BookLedger.isHttpUrl("not a url"));
		// XML に書けない制御文字（台帳の \\u0001 は Properties が文字に戻す）
		assertFalse(BookLedger.isHttpUrl("https://example.com/\u0001x"));
		assertFalse(BookLedger.isHttpUrl("https://example.com/\u007fx"));
		assertFalse(BookLedger.isHttpUrl(null));
	}

	private static void write(File dir, String text) throws Exception {
		Files.write(new File(dir, BookLedger.FILE_NAME).toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * identifier の値を固定する。変えると、すでにある本を本棚が Web の本と見分けられなくなる（internal #19）。
	 * 値は #111 のときに計算したもの（n9623lp は実物の EPUB の identifier と同じ）
	 */
	@Test
	public void identifierValuesArePinned() {
		assertEquals("e725d932-aad6-3d8f-872f-075b9a6e7ec5", BookLedger.identifierFor("https://ncode.syosetu.com/n9623lp/"));
		assertEquals(BookLedger.identifierFor("https://ncode.syosetu.com/n9623lp/"), BookLedger.identifierFor("http://NCODE.syosetu.com/n9623lp"));
	}
}
