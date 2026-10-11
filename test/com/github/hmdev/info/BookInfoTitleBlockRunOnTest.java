package com.github.hmdev.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.File;

import org.junit.Test;

import com.github.hmdev.info.BookInfo.TitleType;

/**
 * 監査 35: 表題の後に空行が無く、本文の先頭が表題の塊に飲まれたときの印（titleBlockRunOnLine）。
 * 読み方（どの行を表題・著者にするか）は変えない。
 */
public class BookInfoTitleBlockRunOnTest
{
	/** 先頭行の配列は getBookInfo と同じ形: 10 行ぶん、空行と無い行は null */
	private static BookInfo read(TitleType type, boolean pubFirst, int start, String... lines)
	{
		String[] metaLines = new String[10];
		for (int i = 0; i < lines.length && i < metaLines.length; i++) metaLines[i] = lines[i].isEmpty() ? null : lines[i];
		BookInfo info = new BookInfo(new File("book.txt"));
		info.setMetaInfo(type, pubFirst, metaLines, start, -1);
		return info;
	}

	@Test
	public void bodyRunningOnAfterTheTitleBlockIsMarked()
	{
		//表題・著者＋本文 5 行。塊は 6 行まで取るので、7 行目（本文五）が続いている
		BookInfo info = read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五");
		assertEquals(5, info.titleEndLine);
		assertEquals(6, info.titleBlockRunOnLine);
		assertTrue(info.titleBlockRunOnDesc().startsWith("先頭の 6 行（1〜6 行目）"));
		assertTrue("直し方を添える", info.titleBlockRunOnDesc().endsWith(BookInfo.TITLE_BLOCK_ADVICE));
	}

	@Test
	public void lineNumbersFollowWhereTheTextStarts()
	{
		BookInfo info = read(TitleType.TITLE_AUTHOR, false, 3, "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五", "本文六");
		assertEquals(9, info.titleBlockRunOnLine);
		assertTrue(info.titleBlockRunOnDesc().startsWith("先頭の 6 行（4〜9 行目）"));
	}

	@Test
	public void authorFirstIsMarkedToo()
	{
		BookInfo info = read(TitleType.AUTHOR_TITLE, false, 0, "著者", "表題", "本文一", "本文二", "本文三", "本文四", "本文五");
		assertEquals(6, info.titleBlockRunOnLine);
	}

	@Test
	public void publisherLineCountsAsPartOfTheBlock()
	{
		//発行者が先頭: 発行者＋塊 6 行＝7 行が表題ページに回り、8 行目が続いている
		BookInfo info = read(TitleType.TITLE_AUTHOR, true, 0, "発行者", "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五");
		assertEquals(7, info.titleBlockRunOnLine);
		assertTrue(info.titleBlockRunOnDesc().startsWith("先頭の 7 行（1〜7 行目）"));
	}

	@Test
	public void blankLineAfterTheTitleBlockIsNotMarked()
	{
		assertEquals(-1, read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "", "本文一").titleBlockRunOnLine);
		assertEquals(-1, read(TitleType.TITLE_AUTHOR, false, 0, "表題", "副題", "著者", "", "本文一").titleBlockRunOnLine);
		assertEquals(-1, read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "訳者訳", "", "本文一").titleBlockRunOnLine);
	}

	@Test
	public void wholeShortTextTakenAsTheBlockIsLeftToTheEmptyBodyWarning()
	{
		//本文が 4 行以下だと全部塊に入り、続く行が無い。こちらは Epub3Writer の「本文がありませんでした」が知らせる
		assertEquals(-1, read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "本文一", "本文二").titleBlockRunOnLine);
	}

	@Test
	public void tenOrMoreLinesWithoutABlankAreNotReadAsATitle()
	{
		//10 行以上続くと表題を読まない（本文は消えない）ので印は付けない
		BookInfo info = read(TitleType.TITLE_AUTHOR, false, 2, "表題", "著者", "3", "4", "5", "6", "7", "8", "9", "10");
		assertEquals(-1, info.titleEndLine);
		assertEquals(-1, info.titleBlockRunOnLine);
	}

	@Test
	public void typesThatTakeAFixedNumberOfLinesAreNotMarked()
	{
		//表題のみ(1行)・表題+著者のみ(2行) は続く行を本文に残す
		assertEquals(-1, read(TitleType.TITLE_ONLY, false, 0, "表題", "本文一", "本文二").titleBlockRunOnLine);
		assertEquals(-1, read(TitleType.TITLE_AUTHOR_ONLY, false, 0, "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五").titleBlockRunOnLine);
	}

	@Test
	public void rereadingRecomputesTheMark()
	{
		//画面で表題の種類を変えて読み直す（JConfirmDialog → reloadMetadata）。警告は変換の直前に見るので、読み直しの結果が効く
		BookInfo info = read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五");
		assertEquals(6, info.titleBlockRunOnLine);
		info.reloadMetadata(TitleType.TITLE_AUTHOR_ONLY, false);
		assertEquals(-1, info.titleBlockRunOnLine);
		info.reloadMetadata(TitleType.TITLE_AUTHOR, false);
		assertEquals(6, info.titleBlockRunOnLine);
	}

	@Test
	public void rereadingAsNoTitleForgetsTheOldBlockEnd()
	{
		//「なし」に読み直したのに前の塊の終わりが残ると、変換がその行まで本文から落とす
		BookInfo info = read(TitleType.TITLE_AUTHOR, false, 0, "表題", "著者", "本文一", "本文二", "本文三", "本文四", "本文五");
		assertEquals(5, info.titleEndLine);
		info.reloadMetadata(TitleType.NONE, false);
		assertEquals(-1, info.titleEndLine);
		assertEquals(-1, info.titleBlockRunOnLine);
	}

	@Test
	public void onlyTitlePagesThatTakeTheBlockOutOfTheBody()
	{
		//「通常」の表題ページは塊を本文に残すので、何も消えていない（警告しない）
		BookInfo info = new BookInfo(new File("book.txt"));
		info.titlePageType = BookInfo.TITLE_NORMAL;
		assertFalse(info.titleBlockLeavesBody());
		info.titlePageType = BookInfo.TITLE_MIDDLE;
		assertTrue(info.titleBlockLeavesBody());
		info.titlePageType = BookInfo.TITLE_HORIZONTAL;
		assertTrue(info.titleBlockLeavesBody());
		info.titlePageType = BookInfo.TITLE_NONE;
		assertTrue(info.titleBlockLeavesBody());
	}
}
