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
		BookLedger saved = BookLedger.create("https://kakuyomu.jp/works/1?a=1&b=2", "[作者 名] 題：副題");
		saved.save(dir);
		BookLedger loaded = BookLedger.load(dir);
		assertNotNull(loaded);
		assertEquals(saved.sourceUrl, loaded.sourceUrl);
		assertEquals(saved.identifier, loaded.identifier);
		assertEquals("[作者 名] 題：副題", loaded.outputBaseName);
		// 一時ファイルを残さない
		String[] names = dir.list();
		assertEquals(1, names.length);
		assertEquals(BookLedger.FILE_NAME, names[0]);
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
		write(dir, "sourceUrl=https://kakuyomu.jp/works/1\noutputBaseName=../../evil\n");
		BookLedger loaded = BookLedger.load(dir);
		assertNotNull(loaded);
		assertFalse(loaded.outputBaseName, loaded.outputBaseName.contains("/"));
		assertFalse(loaded.outputBaseName, loaded.outputBaseName.contains("\\"));
	}

	@Test
	public void onlyHttpUrlsAreSources() {
		assertTrue(BookLedger.isHttpUrl("https://kakuyomu.jp/works/1"));
		assertTrue(BookLedger.isHttpUrl("http://127.0.0.1:8080/novel/"));
		assertFalse(BookLedger.isHttpUrl("file:///etc/passwd"));
		assertFalse(BookLedger.isHttpUrl("javascript:alert(1)"));
		assertFalse(BookLedger.isHttpUrl("not a url"));
		assertFalse(BookLedger.isHttpUrl(null));
	}

	private static void write(File dir, String text) throws Exception {
		Files.write(new File(dir, BookLedger.FILE_NAME).toPath(), text.getBytes(StandardCharsets.UTF_8));
	}
}
