package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** EPUB 内パスの正規化。パストラバーサル検知の土台になる */
public class PathUtilsTest
{
	@Test
	public void normalizeRemovesRedundantSegments()
	{
		assertEquals("OPS/xhtml/text00001.xhtml", PathUtils.normalizeRelative("OPS/./xhtml/text00001.xhtml"));
		assertEquals("OPS/text.xhtml", PathUtils.normalizeRelative("OPS/xhtml/../text.xhtml"));
		assertEquals("OPS/package.opf", PathUtils.normalizeRelative("/OPS/package.opf"));
		assertEquals("OPS/package.opf", PathUtils.normalizeRelative("OPS\\package.opf"));
	}

	@Test
	public void normalizeRejectsEscapingRoot()
	{
		assertNull(PathUtils.normalizeRelative("../etc/passwd"));
		assertNull(PathUtils.normalizeRelative("OPS/../../secret.txt"));
		assertNull(PathUtils.normalizeRelative(".."));
		assertNull(PathUtils.normalizeRelative(""));
	}

	@Test
	public void escapesRootDistinguishesDangerousFromHarmless()
	{
		// 危険なパスと「実体が無いだけのパス」を区別する。
		// 後者で EPUB 全体を拒否すると外部ツール製の本が開けなくなる
		assertTrue(PathUtils.escapesRoot("../etc/passwd"));
		assertTrue(PathUtils.escapesRoot("OPS/../../secret.txt"));
		assertTrue(PathUtils.escapesRoot(".."));

		assertFalse(PathUtils.escapesRoot("./"));
		assertFalse(PathUtils.escapesRoot("/"));
		assertFalse(PathUtils.escapesRoot(""));
		assertFalse(PathUtils.escapesRoot("OPS/xhtml/../text.xhtml"));
	}

	@Test
	public void resolveInsideRejectsDriveQualifiedPaths()
	{
		// 細工した container.xml の full-path でホスト上のファイルを読ませない。
		// Windows では "C:/..." が絶対パスとして解決され、resolve がルート外を指す
		java.nio.file.Path root = java.nio.file.Paths.get("D:", "tmp", "epub-root");

		assertNull(PathUtils.resolveInside(root, "C:/Windows/win.ini"));
		assertNull(PathUtils.resolveInside(root, "C:\\Windows\\win.ini"));
		assertNull(PathUtils.resolveInside(root, "../outside.xml"));
		assertNull(PathUtils.resolveInside(root, "OPS/../../outside.xml"));

		assertEquals(root.resolve("OPS").resolve("package.opf"),
			PathUtils.resolveInside(root, "OPS/package.opf"));
	}

	@Test
	public void queryStringIsStrippedFromHref()
	{
		// manifest の href にクエリが付いていても ZIP エントリ名に混ぜない
		assertEquals("chapter.xhtml", PathUtils.stripQueryAndFragment("chapter.xhtml?mode=print"));
		assertEquals("chapter.xhtml", PathUtils.stripQueryAndFragment("chapter.xhtml?a=1#frag"));
		assertEquals("chapter.xhtml", PathUtils.stripQueryAndFragment("chapter.xhtml#frag"));
		assertEquals("chapter.xhtml", PathUtils.stripQueryAndFragment("chapter.xhtml"));

		assertEquals("OPS/xhtml/chapter.xhtml",
			PathUtils.resolveAgainst("OPS/xhtml/nav.xhtml", "chapter.xhtml?mode=print"));
	}

	@Test
	public void resolveAgainstUsesDocumentDirectory()
	{
		// nav.xhtml から見た同階層のリンク
		assertEquals("OPS/xhtml/text00001.xhtml",
			PathUtils.resolveAgainst("OPS/xhtml/nav.xhtml", "text00001.xhtml#chapter1"));
		// toc.ncx から見た xhtml/ 配下へのリンク
		assertEquals("OPS/xhtml/text00002.xhtml",
			PathUtils.resolveAgainst("OPS/toc.ncx", "xhtml/text00002.xhtml#chapter2"));
		// 親へ戻るリンク
		assertEquals("OPS/css/vertical_text.css",
			PathUtils.resolveAgainst("OPS/xhtml/text00001.xhtml", "../css/vertical_text.css"));
	}

	@Test
	public void resolveAgainstRejectsEscapingRoot()
	{
		assertNull(PathUtils.resolveAgainst("OPS/toc.ncx", "../../etc/passwd"));
	}

	@Test
	public void fragmentIsSeparatedFromPath()
	{
		assertEquals("chapter1", PathUtils.fragmentOf("text00001.xhtml#chapter1"));
		assertNull(PathUtils.fragmentOf("text00001.xhtml"));
		assertNull(PathUtils.fragmentOf("text00001.xhtml#"));
	}

	@Test
	public void fragmentIsDecodedLikeThePath()
	{
		// HTML の id は生の文字列で照合されるので、パス部と同じくデコードする
		assertEquals("章1", PathUtils.fragmentOf("text.xhtml#%E7%AB%A01"));
		// '#' がエスケープされている場合はパスの一部。フラグメント区切りにしない
		assertNull(PathUtils.fragmentOf("images/100%23.png"));
		assertEquals("OPS/images/100#.png",
			PathUtils.resolveAgainst("OPS/package.opf", "images/100%23.png"));
	}

	@Test
	public void hrefPercentEscapesAreDecoded()
	{
		// OPF / nav / ncx の href は URI。ZIP エントリ名は生の文字列なのでデコードが要る
		assertEquals("OPS/images/cover art.jpg",
			PathUtils.resolveAgainst("OPS/package.opf", "images/cover%20art.jpg"));
		assertEquals("OPS/xhtml/第一章.xhtml",
			PathUtils.resolveAgainst("OPS/xhtml/nav.xhtml", "%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml"));
	}

	@Test
	public void brokenEscapeInHrefIsKeptLiteral()
	{
		// 壊れたエスケープはリテラルの '%' を含むファイル名とみなす (href は寛容に扱う)
		assertEquals("OPS/images/100%.png",
			PathUtils.resolveAgainst("OPS/package.opf", "images/100%.png"));
	}

	@Test
	public void plusIsNeverTreatedAsSpace()
	{
		// URLDecoder(form 用) を使うと "a+b" が "a b" になる
		assertEquals("a+b.png", PathUtils.decodeUriStrict("a+b.png"));
		assertEquals("OPS/images/a+b.png",
			PathUtils.resolveAgainst("OPS/package.opf", "images/a+b.png"));
	}

	@Test
	public void supplementaryCharactersSurviveDecoding()
	{
		// リテラルの絵文字とパーセントエスケープが同居する場合、
		// 1 文字ずつ UTF-8 化するとサロゲートペアが片割れずつ処理されて '?' に化ける
		assertEquals("images/😀 cover.jpg",
			PathUtils.decodeUriStrict("images/😀%20cover.jpg"));
		assertEquals("OPS/images/😀 cover.jpg",
			PathUtils.resolveAgainst("OPS/package.opf", "images/😀%20cover.jpg"));
	}

	@Test
	public void strictDecodeRejectsBrokenEscapes()
	{
		assertNull(PathUtils.decodeUriStrict("%zz"));
		assertNull(PathUtils.decodeUriStrict("abc%4"));
		assertEquals("あ.png", PathUtils.decodeUriStrict("%E3%81%82.png"));
	}

	@Test
	public void extensionIsLowerCased()
	{
		assertEquals("xhtml", PathUtils.extensionOf("OPS/xhtml/Text.XHTML"));
		assertEquals("ttf", PathUtils.extensionOf("OPS/gaiji/u3042-u3099.ttf"));
		assertEquals("", PathUtils.extensionOf("mimetype"));
	}
}
