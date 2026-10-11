package com.github.hmdev.writer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** EPUB に書くテキストの改行を LF にそろえる（テンプレートの改行が置き場や git の設定で変わっても、出力は同じにする） */
public class LfWriterTest
{
	private static String viaWriter(String... chunks) throws Exception
	{
		StringWriter sw = new StringWriter();
		try (LfWriter w = new LfWriter(sw)) {
			for (String c : chunks) w.write(c);
		}
		return sw.toString();
	}

	@Test
	public void crlfAndLoneCrBecomeLf() throws Exception
	{
		assertEquals("a\nb\nc\nd", viaWriter("a\r\nb\rc\nd"));
	}

	@Test
	public void aCrlfSplitAcrossWritesIsOneLf() throws Exception
	{
		// Velocity は細切れに書くので、CR と LF が別の write に分かれても LF 1 つにする
		assertEquals("a\nb", viaWriter("a\r", "\nb"));
		assertEquals("a\n\nb", viaWriter("a\r", "\r", "\nb"));
	}

	@Test
	public void otherCharactersPassThrough() throws Exception
	{
		// 改行以外（制御文字も）は触らない。変換の内部の印が漏れたときに見えなくしないため
		String s = "縦書き\u0001﷐\t<p>本文</p>";
		assertEquals(s, viaWriter(s));
	}

	@Test
	public void closingDoesNotCloseTheUnderlyingWriter() throws Exception
	{
		StringWriter sw = new StringWriter();
		new LfWriter(sw).close();
		sw.write("続き");
		assertEquals("続き", sw.toString());
	}

	@Test
	public void bytesAreNormalisedTheSameWay()
	{
		byte[] in = "<?xml?>\r\n<a>日本語\r</a>\n".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals("<?xml?>\n<a>日本語\n</a>\n".getBytes(StandardCharsets.UTF_8), Epub3Writer.toLf(in));
	}
}
