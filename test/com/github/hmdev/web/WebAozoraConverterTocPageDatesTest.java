package com.github.hmdev.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * 目次が複数ページ（101 話以上）のとき、各話の日付を全ページからつなげるテスト（joinTocPageLists）。
 * 修正前は 1 ページ目の分しか取らず、2 ページ目以降の話に日付が付かなかった（internal #14）。
 */
public class WebAozoraConverterTocPageDatesTest {

	private static List<String[]> pages(String[]... lists) {
		return new ArrayList<String[]>(Arrays.asList(lists));
	}

	@Test
	public void datesFromEveryPageAreJoinedInOrder() {
		String[] joined = WebAozoraConverter.joinTocPageLists(
			new String[] {"d1", "d2"}, 2,
			pages(new String[] {"d3", "d4"}, new String[] {"d5"}), Arrays.asList(2, 1));
		assertArrayEquals(new String[] {"d1", "d2", "d3", "d4", "d5"}, joined);
	}

	@Test
	public void aPageWithoutDatesKeepsTheLaterPagesInPlace() {
		// 2 ページ目の日付が取れなくても、3 ページ目の日付は 3 ページ目の話の位置に付く
		String[] joined = WebAozoraConverter.joinTocPageLists(
			new String[] {"d1"}, 1,
			pages(null, new String[] {"d4"}), Arrays.asList(2, 1));
		assertArrayEquals(new String[] {"d1", null, null, "d4"}, joined);
	}

	@Test
	public void noDatesAnywhereIsNull() {
		assertNull(WebAozoraConverter.joinTocPageLists(null, 3, pages((String[]) null), Arrays.asList(2)));
	}
}
