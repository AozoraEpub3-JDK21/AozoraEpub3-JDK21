package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** サイト定義はホスト名で探し、ポート番号は見ない（Windows はフォルダ名に ':' を使えないため。internal #29） */
public class WebAozoraConverterSiteDirTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void thePortIsIgnored() throws Exception
	{
		File web = temp.newFolder("web");
		assertEquals("127.0.0.1", WebAozoraConverter.siteDirName("127.0.0.1:8080", web));
		assertEquals("example.com", WebAozoraConverter.siteDirName("example.com:443", web));
	}

	@Test
	public void aNameWithoutAPortIsUnchanged() throws Exception
	{
		File web = temp.newFolder("web");
		assertEquals("kakuyomu.jp", WebAozoraConverter.siteDirName("kakuyomu.jp", web));
		//ポートでない ':' は外さない
		assertEquals("[::1]", WebAozoraConverter.siteDirName("[::1]", web));
		assertEquals("[::1]", WebAozoraConverter.siteDirName("[::1]:8080", web));
		assertEquals("host:", WebAozoraConverter.siteDirName("host:", web));
	}

	@Test
	public void anOldHostAndPortFolderIsStillUsed() throws Exception
	{
		File web = temp.newFolder("web");
		//「ホスト名:ポート」のフォルダは Windows では作れないので、作れる OS でだけ確かめる
		org.junit.Assume.assumeTrue("この OS は ':' を含むフォルダを作れない", new File(web, "127.0.0.1:8080").mkdirs());
		assertEquals("127.0.0.1:8080", WebAozoraConverter.siteDirName("127.0.0.1:8080", web));
	}
}
