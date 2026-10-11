package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 柱の印を 1 行の中で対にする・柱を中身ごと外す（監査項目 20） */
public class CharUtilsHashiraTest
{
	/** 前走査が見る行: 閉じた柱は中身ごと外す */
	@Test
	public void removeHashiraDropsClosedSpans()
	{
		assertEquals("", CharUtils.removeHashira("［＃ここから柱］作品タイトル［＃ここで柱終わり］"));
		assertEquals("前後", CharUtils.removeHashira("前［＃ここから柱］作品［＃ここで柱終わり］後"));
		assertEquals("AB", CharUtils.removeHashira("A［＃ここから柱］x［＃ここで柱終わり］B［＃ここから柱］y［＃ここで柱終わり］"));
		//行の区切りに見える文字（スクレイピングした題名に入りうる）があっても区間として外す
		assertEquals("", CharUtils.removeHashira("［＃ここから柱］作\u2028品［＃ここで柱終わり］"));
		assertEquals("柱のない行", CharUtils.removeHashira("柱のない行"));
	}

	/** 対にならない印は印だけ外し、中の文字は残す（出力と同じ見方） */
	@Test
	public void unmatchedMarkersAreDroppedButTheirTextStays()
	{
		assertEquals("前作品", CharUtils.removeHashira("前［＃ここから柱］作品"));
		assertEquals("本文続き", CharUtils.removeHashira("本文［＃ここで柱終わり］続き"));
		assertEquals("前作品", CharUtils.balanceHashira("前［＃ここから柱］作品"));
		assertEquals("本文続き", CharUtils.balanceHashira("本文［＃ここで柱終わり］続き"));
		//開始が 2 つ続くと、後ろの開始と閉じが対になる
		assertEquals("a［＃ここから柱］b［＃ここで柱終わり］c", CharUtils.balanceHashira("［＃ここから柱］a［＃ここから柱］b［＃ここで柱終わり］c"));
		assertEquals("ac", CharUtils.removeHashira("［＃ここから柱］a［＃ここから柱］b［＃ここで柱終わり］c"));
		//閉じた柱は出力にそのまま残す
		assertEquals("［＃ここから柱］作品［＃ここで柱終わり］", CharUtils.balanceHashira("［＃ここから柱］作品［＃ここで柱終わり］"));
	}
}
