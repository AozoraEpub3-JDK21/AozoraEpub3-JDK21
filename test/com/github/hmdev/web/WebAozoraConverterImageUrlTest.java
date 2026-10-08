package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

/**
 * 挿絵の src を、その画像のあるページの URL を基準に解決するテスト（imageUrl）。
 *
 * 修正前は、話の URL を chapterHref.indexOf('/', 7) で切って基準にしていた。
 * 7 文字目は "https://" の 2 本目のスラッシュなので、末尾が / でない話の URL では
 * 基準が "https:" だけになり、FC2 小説の挿絵（src="nimg/..."）が "https:/nimg/..." になって取れなかった。
 * また基準はダウンロードのループでしか設定されず、変換の時には最後の話の基準が全話に使われていた。
 * 今は各ページを Jsoup.parse に話の URL を渡して読み、img.absUrl("src") で解決する。
 */
public class WebAozoraConverterImageUrlTest {

	private static String resolve(String pageUrl, String src) {
		Document doc = Jsoup.parse("<html><body><img src=\"" + src + "\"></body></html>", pageUrl);
		Element img = doc.selectFirst("img");
		return WebAozoraConverter.imageUrl(img);
	}

	@Test
	public void relativeSrcOnQueryPage() {
		// FC2 小説の本文の挿絵
		assertEquals("https://novel.fc2.com/nimg/338/004348/218411-00001.jpg",
			resolve("https://novel.fc2.com/novel.php?mode=rd&nid=218411&pg=1", "nimg/338/004348/218411-00001.jpg"));
	}

	@Test
	public void relativeSrcResolvesAgainstItsOwnPage() {
		// 話ごとにディレクトリが違うサイトでは、同じ相対 src でも別の画像
		String ch1 = resolve("https://ncode.syosetu.com/n1234ab/1/", "img/01.jpg");
		String ch2 = resolve("https://ncode.syosetu.com/n1234ab/2/", "img/01.jpg");
		assertEquals("https://ncode.syosetu.com/n1234ab/1/img/01.jpg", ch1);
		assertEquals("https://ncode.syosetu.com/n1234ab/2/img/01.jpg", ch2);
		assertNotEquals(ch1, ch2);
	}

	@Test
	public void rootRelativeAndProtocolRelative() {
		assertEquals("https://novel.fc2.com/nimg/338/004348/218411-00000.jpg",
			resolve("https://novel.fc2.com/novel.php?mode=tc&nid=218411", "/nimg/338/004348/218411-00000.jpg"));
		assertEquals("https://media.example.com/a.jpg",
			resolve("https://novel.fc2.com/novel.php?mode=tc&nid=218411", "//media.example.com/a.jpg"));
	}

	@Test
	public void queryOnlyAndParentSegments() {
		assertEquals("https://host/dir/view.php?img=2", resolve("https://host/dir/view.php?id=1", "?img=2"));
		assertEquals("https://host/img/a.jpg", resolve("https://host/dir/page.html", "../img/a.jpg"));
		assertEquals("https://host/dir/a.jpg", resolve("https://host/dir/page.php?next=/x/y", "a.jpg"));
	}

	@Test
	public void fragmentIsDropped() {
		// #… はページの中の位置。画像の取得にもキャッシュの置き場所にも使わない
		assertEquals("https://host/dir/fig.jpg", resolve("https://host/dir/page.html", "fig.jpg#1"));
	}

	@Test
	public void absoluteSrcIsKept() {
		assertEquals("https://cdn.example.com/a.jpg", resolve("https://host/dir/", "https://cdn.example.com/a.jpg"));
	}

	@Test
	public void nonHttpOrUnresolvableIsNull() {
		assertNull(resolve("https://host/dir/", "data:image/png;base64,AAAA"));
		// 基準の URL が無い文書の相対 src は解決できない
		Document doc = Jsoup.parse("<img src=\"a.jpg\">");
		assertNull(WebAozoraConverter.imageUrl(doc.selectFirst("img")));
	}
}
