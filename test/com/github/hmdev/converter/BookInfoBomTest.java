package com.github.hmdev.converter;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;

import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 表題・著者を取る事前走査 (getBookInfo) でも、先頭行の BOM を除く (本家 hmdev/AozoraEpub3 #5)。
 *
 * <p>本文の経路は以前から BOM を除いていたが、getBookInfo は除いていなかったので、
 * BOM 付きの UTF-8 ファイルでは表題の頭に U+FEFF が付き、dc:title と出力ファイル名に入っていた。</p>
 */
public class BookInfoBomTest
{
	BookInfo read(String text) throws Exception
	{
		Epub3Writer writer = new Epub3Writer("");
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, "");
		BufferedReader src = new BufferedReader(new StringReader(text));
		return converter.getBookInfo(new File("bom.txt"), src, null, BookInfo.TitleType.TITLE_AUTHOR, false);
	}

	@Test
	public void 先頭行のBOMは表題に入らない() throws Exception
	{
		BookInfo info = read("﻿タイトル\n著者\n\n本文\n");
		assertEquals("タイトル", info.title);
		assertEquals("著者", info.creator);
	}

	@Test
	public void BOMの無い入力は変わらない() throws Exception
	{
		BookInfo info = read("タイトル\n著者\n\n本文\n");
		assertEquals("タイトル", info.title);
		assertEquals("著者", info.creator);
	}
}
