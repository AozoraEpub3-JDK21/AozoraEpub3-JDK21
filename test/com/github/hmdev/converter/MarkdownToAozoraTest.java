package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** internal #72: Markdown を青空文庫形式に直す規則 */
public class MarkdownToAozoraTest {

	private static String conv(String md) {
		return MarkdownToAozora.convert(md, "既定の題");
	}

	@Test
	public void theFirstH1IsTheTitleAndFrontMatterGivesTheAuthor() {
		String out = conv("---\nauthor: 春雨\n---\n# 題名\n\n本文。\n");
		assertTrue(out, out.startsWith("題名\n春雨\n\n　本文。\n"));
	}

	@Test
	public void frontMatterTitleWinsAndTheH1BecomesAHeading() {
		String out = conv("---\ntitle: 表の題\n---\n# 章\n\n本文。\n");
		assertTrue(out, out.startsWith("表の題\n\n\n"));
		assertTrue(out, out.contains("［＃大見出し］章［＃大見出し終わり］"));
	}

	@Test
	public void withoutAnyTitleTheDefaultIsUsed() {
		String out = conv("本文だけ。\n");
		assertTrue(out, out.startsWith("既定の題\n\n\n　本文だけ。\n"));
	}

	@Test
	public void headingsMapToAozoraLevelsAndH2BreaksThePageAfterBody() {
		String out = conv("# 題\n\n## 最初\n\n本文。\n\n## 次\n\n### 中\n\n#### 小\n");
		//題名の直後の ## には改ページを入れない（空のページを作らない）
		assertTrue(out, out.contains("\n\n［＃大見出し］最初［＃大見出し終わり］\n"));
		assertTrue(out, out.contains("［＃改ページ］\n［＃大見出し］次［＃大見出し終わり］"));
		assertTrue(out, out.contains("［＃中見出し］中［＃中見出し終わり］"));
		assertTrue(out, out.contains("［＃小見出し］小［＃小見出し終わり］"));
	}

	@Test
	public void notationCharactersInTextAreNeutralised() {
		String out = conv("# 題\n\n漢字《かんじ》と｜縦棒と［＃注記］と a << b >> c と ＜＜全角＞＞。\n");
		assertFalse(out, out.contains("《"));
		assertFalse(out, out.contains("》"));
		assertFalse(out, out.contains("｜"));
		assertFalse(out, out.contains("［＃注記"));
		assertFalse(out, out.contains("<<"));
		assertFalse(out, out.contains(">>"));
		assertFalse(out, out.contains("＜＜"));
		assertFalse(out, out.contains("＞＞"));
	}

	@Test
	public void codeIsNeutralisedAndMarkdownInsideAFenceIsNotRead() {
		String out = conv("# 題\n\n```\n# 見出しではない\n**太字ではない** ［＃注記］\n```\n");
		assertFalse(out, out.contains("見出し］見出しではない"));
		assertFalse(out, out.contains("［＃太字］"));
		assertTrue(out, out.contains("# 見出しではない"));
		assertFalse(out, out.contains("［＃注記"));
	}

	@Test
	public void listsAndQuotesUseUnnestedIndentBlocks() {
		String out = conv("# 題\n\n- 一\n  - 二\n\n> 引用\n");
		assertTrue(out, out.contains("［＃ここから１字下げ、折り返して２字下げ］\n・一\n［＃ここで字下げ終わり］\n"));
		assertTrue(out, out.contains("［＃ここから２字下げ、折り返して３字下げ］\n・二\n［＃ここで字下げ終わり］\n"));
		assertTrue(out, out.contains("［＃ここから２字下げ］\n引用\n［＃ここで字下げ終わり］\n"));
		//開きと閉じが同じ数（入れ子にしない）
		assertEquals(out, count(out, "［＃ここから"), count(out, "［＃ここで字下げ終わり］"));
	}

	@Test
	public void tableRowsBecomeLinesAndLinksKeepTheUrl() {
		String out = conv("# 題\n\n| 名前 | 値 |\n|---|---|\n| 版 | 3.4 |\n\n[文](https://example.com) と <https://example.org>\n");
		assertTrue(out, out.contains("・名前：版／値：3.4\n"));
		assertTrue(out, out.contains("文（https://example.com）"));
		assertTrue(out, out.contains("https://example.org"));
		assertFalse(out, out.contains("https://example.org（"));
	}

	@Test
	public void boldAndSoftBreaks() {
		String out = conv("# 題\n\n日本語の\n行と **太字** と\nEnglish\nwords\n");
		assertTrue(out, out.contains("　日本語の行と ［＃太字］太字［＃太字終わり］ とEnglish words\n"));
	}

	/** codex P2（#215）: - が 50 個並ぶ行はコメントの区切りとして読まれ、後ろが消える */
	@Test
	public void aLongDashLineInCodeIsNotACommentDelimiter() {
		String dashes = "-".repeat(60);
		String out = conv("# 題\n\n```\n" + dashes + "\n```\n\n後ろ。\n");
		for (String line : out.split("\n")) assertFalse(out, line.startsWith("--------------------------------"));
		assertTrue(out, out.contains("後ろ。"));
	}

	/** codex P2（#215）: <img・<a は変換器が画像・リンクとして読む */
	@Test
	public void htmlTagsInCodeStayText() {
		String out = conv("# 題\n\n`<img src=\"x.png\">` と `<a href=\"u\">t</a>`\n");
		assertFalse(out, out.toLowerCase().contains("<img"));
		assertFalse(out, out.toLowerCase().contains("<a "));
		assertFalse(out, out.toLowerCase().contains("</a>"));
		assertTrue(out, out.contains("img src"));
	}

	/** codex P2（#215）: 太字の入った # の題名に注記の字を出さない */
	@Test
	public void aBoldTitleHasNoNotationInIt() {
		String out = conv("# **報告**と題\n\n本文。\n");
		assertTrue(out, out.startsWith("報告と題\n"));
	}

	/** codex P2（#215）: 節点の境目で ［＃ や << が組み上がらない */
	@Test
	public void notationDoesNotFormAcrossInlineNodes() {
		String out = conv("# 題\n\n［`＃改ページ`］ と a<`<b` と *<*img\n");
		assertFalse(out, out.contains("［＃改ページ］"));
		assertFalse(out, out.contains("<<"));
		assertFalse(out, out.contains("<img"));
	}

	/** codex P2（#215）: 太字の後の改行でも、英語の語は空白で区切る */
	@Test
	public void aSoftBreakAfterBoldKeepsTheSpace() {
		String out = conv("# 題\n\n**hello**\nworld and\n**bold** next\n");
		assertTrue(out, out.contains("hello［＃太字終わり］ world and ［＃太字］bold"));
	}

	/** codex P2（#215）: 〔半角〕は欧文アクセント、／＼ はくの字点として読まれる */
	@Test
	public void latinBracketsAndRepeatMarksStayText() {
		String out = conv("# 題\n\n〔e'tiquette〕 と `〔abc〕` と ／＼ と ／″＼\n");
		assertFalse(out, out.contains("〔e"));
		assertFalse(out, out.contains("〔a"));
		assertFalse(out, out.contains("／＼"));
		assertFalse(out, out.contains("／″"));
	}

	/** agy（#215）: 先頭の HTML コメントの後の # を題名にする */
	@Test
	public void aLeadingHtmlCommentDoesNotHideTheTitle() {
		String out = conv("<!-- generated -->\n# 題名\n\n本文。\n");
		assertTrue(out, out.startsWith("題名\n"));
	}

	/** agy（#215）: 題名が空にならない（1 行目が空だと本文が表題・著者に取られる） */
	@Test
	public void theTitleIsNeverEmpty() {
		assertFalse(MarkdownToAozora.convert("---\ntitle: \"\u0001\"\n---\n本文。\n", "").startsWith("\n"));
		assertTrue(MarkdownToAozora.convert("---\ntitle: \"\u0001\"\n---\n# 見出し\n\n本文。\n", "f").startsWith("見出し\n"));
		assertFalse(MarkdownToAozora.convert("", "").startsWith("\n"));
		assertFalse(MarkdownToAozora.convert(null, null).startsWith("\n"));
	}

	/** agy（#215）: 変換器が入口で消す内部の字で、逃がしの並びが元に戻らない */
	@Test
	public void internalCharactersAreRemovedBeforeEscaping() {
		String out = conv("# 題\n\n［\u0001＃改ページ］ と 〔\uFDD0abc〕\n");
		assertFalse(out, out.contains("\u0001"));
		assertFalse(out, out.contains("\uFDD0"));
		assertFalse(out, out.contains("［＃改ページ］"));
		assertFalse(out, out.contains("〔a"));
	}

	/** agy（#215）: ― や【 で始まる著者は変換器が捨てる */
	@Test
	public void anAuthorStartingWithADashIsKept() {
		String out = conv("---\nauthor: \"【編】春雨\"\n---\n# 題\n");
		String author = out.split("\n", -1)[1];
		assertFalse(out, author.startsWith("【"));
		assertTrue(out, author.contains("【編】春雨"));
	}

	/** agy（#215）: 見出しも値も空の升で ／ が重ならない */
	@Test
	public void emptyTableColumnsAreSkipped() {
		String out = conv("# 題\n\n| A | | C |\n|---|---|---|\n| 1 | | 3 |\n");
		assertTrue(out, out.contains("・A：1／C：3\n"));
	}

	private static int count(String s, String sub) {
		int n = 0;
		for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
		return n;
	}
}
