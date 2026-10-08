package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 各話のページからの相対 src を解決する基準 URL（pageBaseUriOf）のテスト。
 *
 * 修正前は、末尾が / でない話の URL を chapterHref.indexOf('/', 7) で切っていた。
 * 7 文字目は "https://" の 2 本目のスラッシュなので、基準が "https:" だけになり、
 * FC2 小説の挿絵（src="nimg/..."）が "https:/nimg/..." に組み立てられて取れなかった。
 */
public class WebAozoraConverterPageBaseUriTest {

	@Test
	public void queryPageResolvesToItsDirectory() {
		// FC2 小説の各ページ。挿絵は src="nimg/338/004348/218411-00001.jpg"
		assertEquals("https://novel.fc2.com/",
			WebAozoraConverter.pageBaseUriOf("https://novel.fc2.com/novel.php?mode=rd&nid=218411&pg=1"));
	}

	@Test
	public void queryContainingSlashIsIgnored() {
		assertEquals("https://example.com/dir/",
			WebAozoraConverter.pageBaseUriOf("https://example.com/dir/page.php?next=/a/b#x/y"));
	}

	@Test
	public void pageWithTrailingSlashIsItself() {
		assertEquals("https://ncode.syosetu.com/n9623lp/1/",
			WebAozoraConverter.pageBaseUriOf("https://ncode.syosetu.com/n9623lp/1/"));
	}

	@Test
	public void fileLikePageResolvesToItsDirectory() {
		assertEquals("https://novel.syosetu.org/402358/",
			WebAozoraConverter.pageBaseUriOf("https://novel.syosetu.org/402358/1.html"));
		assertEquals("https://kakuyomu.jp/works/822139840468926025/episodes/",
			WebAozoraConverter.pageBaseUriOf("https://kakuyomu.jp/works/822139840468926025/episodes/822139840468926100"));
	}

	@Test
	public void hostOnlyResolvesToRoot() {
		assertEquals("https://example.com/", WebAozoraConverter.pageBaseUriOf("https://example.com"));
		assertEquals("https://example.com/", WebAozoraConverter.pageBaseUriOf("https://example.com?q=1"));
	}

	/** 解決した URL が、ブラウザと同じ java.net.URI の解決と一致する */
	@Test
	public void agreesWithUriResolve() throws Exception {
		String[] pages = {
			"https://novel.fc2.com/novel.php?mode=rd&nid=218411&pg=1",
			"https://ncode.syosetu.com/n9623lp/1/",
			"https://novel.syosetu.org/402358/1.html",
		};
		for (String page : pages) {
			String expected = new java.net.URI(page).resolve("nimg/a.jpg").toString();
			assertEquals(page, expected, WebAozoraConverter.pageBaseUriOf(page) + "nimg/a.jpg");
		}
	}
}
