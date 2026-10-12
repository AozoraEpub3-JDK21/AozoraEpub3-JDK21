package com.github.hmdev.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * 変換器は変換ごとに新しく作り、読んだサイトの定義だけを使い回す（internal #22）。
 * 使い回すと、GUI の中止が本棚の更新に届き、前の変換の設定や守りが次の変換に残る
 */
public class WebAozoraConverterPerConversionTest {

	private static final File WEB = new File("web");
	private static final String URL = "https://ncode.syosetu.com/n0000aa/";

	/** 同じサイトでも呼ぶたびに別の変換器。定義（extract.txt・replace.txt）は同じものを使う */
	@Test
	public void eachCallGetsANewConverterSharingTheDefinition() throws Exception {
		WebAozoraConverter a = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		WebAozoraConverter b = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		assertNotNull(a);
		assertNotSame(a, b);
		assertNotNull(a.queryMap);
		assertSame(a.queryMap, b.queryMap);
		assertNotNull(a.replaceMap);
		assertSame(a.replaceMap, b.replaceMap);
		//設定は変換器ごと（共有すると、前の変換の設定が次に残る）
		assertNotSame(a.getFormatSettings(), b.getFormatSettings());
	}

	/** 一方の中止は、もう一方（本棚の更新など）に届かない */
	@Test
	public void cancellingOneDoesNotCancelAnother() throws Exception {
		WebAozoraConverter gui = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		WebAozoraConverter shelf = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		gui.canceled();
		assertTrue(gui.isCanceled());
		assertFalse(shelf.isCanceled());
	}

	/** 前の変換で立てた守りや設定は、次に作る変換器に残らない */
	@Test
	public void guardsAndSettingsDoNotCarryOver() throws Exception {
		WebAozoraConverter first = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		first.updateGuard = true;
		first.allowFewerEpisodes = true;
		first.skipImages = true;
		WebAozoraConverter next = WebAozoraConverter.createWebAozoraConverter(URL, WEB);
		assertFalse(next.updateGuard);
		assertFalse(next.allowFewerEpisodes);
		assertFalse(next.skipImages);
	}

	/** 定義の無いサイトは null（これまでどおり） */
	@Test
	public void unknownSiteIsNull() throws Exception {
		assertTrue(WebAozoraConverter.createWebAozoraConverter("https://unknown.example.invalid/x/", WEB) == null);
	}
}
