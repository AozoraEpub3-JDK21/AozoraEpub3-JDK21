package com.github.hmdev.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * internal #33: 画像の名前がフォルダを指すとき（名前が空の <img src=""> は入力のフォルダそのものを指す）、
 * フォルダを画像として開かない。以前は開こうとして Windows で AccessDeniedException になり、それを標準エラーにそのまま出していた。
 */
public class ImageInfoReaderFolderTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private ImageInfoReader readerFor(File dir) throws Exception
	{
		File txt = new File(dir, "book.txt");
		Files.write(txt.toPath(), "表題\n".getBytes(StandardCharsets.UTF_8));
		return new ImageInfoReader(true, txt);
	}

	@Test
	public void anEmptyNameIsNotTheInputFolder() throws Exception
	{
		ImageInfoReader reader = readerFor(temp.getRoot());
		PrintStream err = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
		try {
			assertNull(reader.getImageInfo(""));
		} finally {
			System.setErr(err);
		}
		assertEquals("標準エラーに何も出さない", "", captured.toString(StandardCharsets.UTF_8));
	}

	@Test
	public void aFolderNamedLikeAnImageIsNotAnImage() throws Exception
	{
		File dir = temp.getRoot();
		new File(dir, "pic.png").mkdirs();
		ImageInfoReader reader = readerFor(dir);
		assertNull(reader.getImageInfo("pic.png"));
		assertNull("フォルダを読もうとしない", reader.getImage("pic.png"));
		//拡張子を直して探すときも、フォルダは見つかったことにしない
		assertNull(reader.correctExt("pic.png"));
	}

	@Test
	public void anImageNotFoundAfterFixingTheExtensionIsNotLookedUpAsNull() throws Exception
	{
		//correctExt は見つからなければ null を返す。それを名前に足して「null」というフォルダ（やファイル）を探さない
		File dir = temp.getRoot();
		new File(dir, "pic.png").mkdirs();
		new File(dir, "null").mkdirs();
		ImageInfoReader reader = readerFor(dir);
		assertNull(reader.getImage("pic.png"));
	}
}
