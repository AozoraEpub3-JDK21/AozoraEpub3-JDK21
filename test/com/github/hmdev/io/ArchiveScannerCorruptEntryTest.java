package com.github.hmdev.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 書庫の中のテキストが 1 つ壊れていても、その 1 つだけを飛ばして残りを読む（internal #36） */
public class ArchiveScannerCorruptEntryTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	/** 1 つ目のテキストの圧縮データを壊した zip（目次はそのまま） */
	private File zipWithCorruptFirstText() throws Exception
	{
		File zip = temp.newFile("two.zip");
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 2000; i++) big.append("壊れる本の").append(i).append("行目です。\n");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
			zos.putNextEntry(new ZipEntry("1_bad.txt"));
			zos.write(big.toString().getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("2_good.txt"));
			zos.write("題名\n著者\n\n本文です。\n".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("fig.png"));
			zos.write(new byte[]{1, 2, 3});
			zos.closeEntry();
		}
		byte[] b = Files.readAllBytes(zip.toPath());
		//1 つ目の項目のローカルヘッダ（30 バイト＋名前）の後ろが圧縮データ。その真ん中を壊す
		int nameLen = (b[26] & 0xff) | ((b[27] & 0xff) << 8);
		int extraLen = (b[28] & 0xff) | ((b[29] & 0xff) << 8);
		int data = 30 + nameLen + extraLen;
		for (int i = data + 40; i < data + 80; i++) b[i] = (byte)0xff;
		Files.write(zip.toPath(), b);
		return zip;
	}

	@Test
	public void aCorruptTextIsSkippedAndTheRestIsRead() throws Exception
	{
		List<ArchiveCache.TextEntry> texts = new ArrayList<>();
		List<String> images = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		ArchiveScanner.scanZip(zipWithCorruptFirstText().toPath(), texts, images, skipped);
		assertEquals(1, skipped.size());
		assertTrue(skipped.get(0), skipped.get(0).startsWith("1_bad.txt"));
		assertEquals(1, texts.size());
		assertEquals("2_good.txt", texts.get(0).name);
		assertArrayEquals("題名\n著者\n\n本文です。\n".getBytes(StandardCharsets.UTF_8), texts.get(0).content);
		assertEquals(List.of("fig.png"), images);
	}

	/** 壊れていない zip は前と同じ並びで読む（ファイルの中の順） */
	@Test
	public void aSoundZipKeepsItsEntryOrder() throws Exception
	{
		File zip = temp.newFile("order.zip");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
			for (String n : new String[]{ "b.txt", "a.txt", "c.png", "d.txt" }) {
				zos.putNextEntry(new ZipEntry(n));
				zos.write(n.getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		List<ArchiveCache.TextEntry> texts = new ArrayList<>();
		List<String> images = new ArrayList<>();
		ArchiveScanner.scanZip(zip.toPath(), texts, images, new ArrayList<>());
		assertEquals(List.of("b.txt", "a.txt", "d.txt"), texts.stream().map(t -> t.name).toList());
		assertEquals(List.of("c.png"), images);
	}
}
