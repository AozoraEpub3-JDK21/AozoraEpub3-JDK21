package com.github.hmdev.info;

import com.github.hmdev.util.CharUtils;

/** 目次用の章の情報を格納（仮） */
public class ChapterInfo
{
	/** xhtmlファイルのセクション毎の連番ID */
	String sectionId;
	/** 章ID 見出し行につけたspanのID */
	String chapterId;
	/** 章名称 */
	String chapterName;
	
	/** 目次階層レベル */
	public int chapterLevel;
	
	/** navPointを閉じる回数 */
	public int navClose = 1;
	/** NAV の目次で、項目の a の後ろに続けて出す文字列（階層化しないときは li を閉じるだけ） */
	String navAfter = "</li>";
	/** 見出しではなく、改ページの後の本文の行から作った項目なら true（目次の親にしない） */
	public boolean pageBreakLine = false;
	/** 注記ではなく、章の名前や数字の形から自動で拾った項目なら true */
	public boolean autoLine = false;
	
	public ChapterInfo(String sectionId, String chapterId, String chapterName, int chapterLevel)
	{
		this.sectionId = sectionId;
		this.chapterId = chapterId;
		this.chapterName = chapterName;
		this.chapterLevel = chapterLevel;
	}

	public String getSectionId()
	{
		return sectionId;
	}
	public void setSectionId(String sectionId)
	{
		this.sectionId = sectionId;
	}

	public String getChapterId()
	{
		return chapterId;
	}
	public void setChapterId(String chapterId)
	{
		this.chapterId = chapterId;
	}

	public String getChapterName()
	{
		return chapterName;
	}
	public void setChapterName(String chapterName)
	{
		this.chapterName = chapterName;
	}
	public String getNoTagChapterName()
	{
		return CharUtils.removeTag(chapterName);
	}
	
	public int getChapterLevel()
	{
		return chapterLevel;
	}
	
	public String getNavAfter()
	{
		return navAfter;
	}
	public void setNavAfter(String navAfter)
	{
		this.navAfter = navAfter;
	}
	
	/** Velocityでループするために配列を返す */
	public int[] getNavClose()
	{
		if (this.navClose <= 0) return null;
		return new int[this.navClose];
	}
}
