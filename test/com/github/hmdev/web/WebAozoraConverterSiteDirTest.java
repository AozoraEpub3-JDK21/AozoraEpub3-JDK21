package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.web.ExtractInfo.ExtractId;

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
		//数字でないものはポートではない（ユーザー情報 user:pw@host を含む URL もそのまま）
		assertEquals("host:abc", WebAozoraConverter.siteDirName("host:abc", web));
		assertEquals("user:pw@host", WebAozoraConverter.siteDirName("user:pw@host", web));
	}

	@Test
	public void extractAndReplaceAreReadFromTheHostFolder() throws Exception
	{
		//ポート付きの URL でも、web/<ホスト名>/ の extract.txt と replace.txt の両方を読む
		File web = temp.newFolder("web");
		File site = new File(web, "127.0.0.1");
		site.mkdirs();
		java.nio.file.Files.write(new File(site, "extract.txt").toPath(), "TITLE\th1:0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		java.nio.file.Files.write(new File(site, "replace.txt").toPath(), "TITLE\t旧\t新\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		WebAozoraConverter converter = new WebAozoraConverter("127.0.0.1:8080", web);
		org.junit.Assert.assertNotNull("extract.txt を読んでいない", converter.queryMap);
		org.junit.Assert.assertNotNull("extract.txt の TITLE が無い", converter.queryMap.get(ExtractId.TITLE));
		org.junit.Assert.assertNotNull("replace.txt を読んでいない", converter.replaceMap);
		java.util.ArrayList<String[]> titleReplaces = converter.replaceMap.get(ExtractId.TITLE);
		org.junit.Assert.assertNotNull("replace.txt の TITLE が無い", titleReplaces);
		assertEquals("旧", titleReplaces.get(0)[0]);
		assertEquals("新", titleReplaces.get(0)[1]);
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
