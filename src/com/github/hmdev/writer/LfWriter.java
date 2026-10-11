package com.github.hmdev.writer;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * 改行を LF にそろえて書く Writer（CRLF と単独の CR を LF にする）。
 * テンプレートの改行は置き場や git の設定（Windows の autocrlf）で CRLF にも LF にもなり、
 * そのまま EPUB に出ていた。本文は変換処理が LF で書くので、テンプレートもこれを通して LF にそろえる。
 * EPUB と XML は改行を決めておらず、XML を読む側は CRLF を LF に読み替える（XML 1.0 §2.11）ので、表示は変わらない。
 * close() しても下の Writer は閉じない（同じ Writer に続けて書くため）。
 */
class LfWriter extends FilterWriter
{
	/** 直前に書いた文字が CR だった（次が LF ならその LF は捨てる） */
	private boolean afterCr = false;

	LfWriter(Writer out)
	{
		super(out);
	}

	@Override
	public void write(int c) throws IOException
	{
		if (c == '\r') {
			this.out.write('\n');
			this.afterCr = true;
			return;
		}
		if (c == '\n' && this.afterCr) {
			this.afterCr = false;
			return;
		}
		this.afterCr = false;
		this.out.write(c);
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException
	{
		for (int i = off; i < off + len; i++) write(cbuf[i]);
	}

	@Override
	public void write(String str, int off, int len) throws IOException
	{
		for (int i = off; i < off + len; i++) write(str.charAt(i));
	}

	/** 下の Writer は閉じずに流すだけ */
	@Override
	public void close() throws IOException
	{
		flush();
	}
}
