package com.github.hmdev.converter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Test;

import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.CharUtils;
import com.github.hmdev.writer.Epub3Writer;

public class Probe32bTest
{
	@Test
	public void probe() throws Exception
	{
		try (PrintWriter pw = new PrintWriter(new FileWriter(System.getProperty("probe.out")))) {
			String[] cases = {
				"破損※※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお、漢字《かんじ》の後",
				"破損※※［＃始め二重山括弧］あいう※［＃終わり二重山括弧］えお｜漢字",
				"破損※※［＃始め二重山括弧］あいう［＃縦中横］12［＃縦中横終わり］えお",
				"裸の米印※ルビ漢字《かんじ》の後",
				"裸の米印2つ※※漢字《かんじ》の後",
				"米印の後の山括弧※<<あいう>>えお",
				"米印※《かんじ》と書いた行",
				"注記の無い行※です",
			};
			for (String src : cases) {
				AozoraEpub3Converter c = new AozoraEpub3Converter(new Epub3Writer(""), "");
				c.bookInfo = new BookInfo(null);
				c.vertical = true;
				StringWriter sw = new StringWriter();
				BufferedWriter bw = new BufferedWriter(sw);
				c.convertTextLineToEpub3(bw, src, 0, false, false);
				bw.close();
				pw.println("SRC  " + src);
				pw.println("LINE " + sw.toString().trim());
				pw.println("NORUBY " + CharUtils.removeRuby(c.convertGaijiChuki(src, true, false)));
				pw.println();
			}
		}
	}
}
