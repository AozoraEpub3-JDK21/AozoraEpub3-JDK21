package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 目次・表題の名前（getChapterName）は、柱の区間を中身ごと外す（監査項目 20） */
public class CharUtilsHashiraTest
{
	@Test
	public void theHashiraSpanIsDroppedFromTheName()
	{
		assertEquals("", CharUtils.getChapterName("［＃ここから柱］作品タイトル［＃ここで柱終わり］", 100, false));
		assertEquals("前後", CharUtils.getChapterName("前［＃ここから柱］作品［＃ここで柱終わり］後", 100, false));
		assertEquals("AB", CharUtils.getChapterName("A［＃ここから柱］x［＃ここで柱終わり］B［＃ここから柱］y［＃ここで柱終わり］", 100, false));
	}

	/** 閉じが無ければ行の終わりまで。開いていない閉じは注記として外れるだけ（前の文字は残る） */
	@Test
	public void unclosedAndStrayMarkersOnlyAffectTheName()
	{
		assertEquals("前", CharUtils.getChapterName("前［＃ここから柱］作品", 100, false));
		assertEquals("本文続き", CharUtils.getChapterName("本文［＃ここで柱終わり］続き", 100, false));
	}
}
