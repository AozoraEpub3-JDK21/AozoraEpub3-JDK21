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
	public void writesWithAnOffsetUseOnlyThatRange() throws Exception
	{
		StringWriter sw = new StringWriter();
		try (LfWriter w = new LfWriter(sw)) {
			w.write("xa\r\nby", 1, 4);
			w.write("za\rbz".toCharArray(), 1, 3);
		}
		assertEquals("a\nba\nb", sw.toString());
	}

	@Test
	public void closingDoesNotCloseTheUnderlyingWriter() throws Exception
	{
		//閉じると以後の書き込みで例外を投げる Writer で確かめる（StringWriter.close() は何もしないので確かめにならない）
		java.io.CharArrayWriter buf = new java.io.CharArrayWriter();
		java.io.Writer closable = new java.io.FilterWriter(buf) {
			boolean closed = false;
			@Override public void write(int c) throws java.io.IOException { if (closed) throw new java.io.IOException("closed"); super.write(c); }
			@Override public void write(String s, int off, int len) throws java.io.IOException { if (closed) throw new java.io.IOException("closed"); super.write(s, off, len); }
			@Override public void close() { closed = true; }
		};
		new LfWriter(closable).close();
		closable.write("続き");
		assertEquals("続き", buf.toString());
	}

	@Test
	public void bytesAreNormalisedTheSameWay()
	{
		byte[] in = "<?xml?>\r\n<a>日本語\r</a>\n".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals("<?xml?>\n<a>日本語\n</a>\n".getBytes(StandardCharsets.UTF_8), Epub3Writer.toLf(in));
		//末尾が CRLF・CR でも LF 1 つにする
		assertArrayEquals("a\n".getBytes(StandardCharsets.UTF_8), Epub3Writer.toLf("a\r\n".getBytes(StandardCharsets.UTF_8)));
		assertArrayEquals("a\n".getBytes(StandardCharsets.UTF_8), Epub3Writer.toLf("a\r".getBytes(StandardCharsets.UTF_8)));
	}
}
