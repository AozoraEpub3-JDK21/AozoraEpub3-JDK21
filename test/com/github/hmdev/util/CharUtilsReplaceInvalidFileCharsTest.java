package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * CharUtils.replaceInvalidFileChars のテスト（監査 #6）。
 *
 * 修正前は AozoraEpub3Applet 側に
 * replaceAll("\\?\\*\\&\\|\\<\\>\"\\\\", "_") と書かれており、
 * 文字クラス [ ] が無いためリテラル連続文字列 ?*&|<>"\ にしかマッチせず、
 * 個々の禁止文字が置換されずサニタイズが実質無効だった。
 */
public class CharUtilsReplaceInvalidFileCharsTest {

	/** 禁止文字はそれぞれ単独で '_' に置換される（修正前はどれも置換されなかった） */
	@Test
	public void replacesEachForbiddenCharIndividually() {
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a?b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a*b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a&b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a|b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a<b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a>b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a\"b"));
		assertEquals("a_b", CharUtils.replaceInvalidFileChars("a\\b"));
	}

	/** 連続した禁止文字は 1 文字ずつ置換される（修正前は全体で '_' 1 文字だった） */
	@Test
	public void replacesConsecutiveForbiddenCharsOneByOne() {
		assertEquals("________", CharUtils.replaceInvalidFileChars("?*&|<>\"\\"));
	}

	/** 実際の青空文庫 zip URL は変化しない */
	@Test
	public void keepsAozoraZipPath() {
		assertEquals("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip",
			CharUtils.replaceInvalidFileChars("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip"));
	}

	/** クエリ付き URL は Windows で作成できないファイル名になるため置換される */
	@Test
	public void replacesQueryInPath() {
		assertEquals("host/download_id=123_type=zip",
			CharUtils.replaceInvalidFileChars("host/download?id=123&type=zip"));
	}

	/** 日本語ファイル名・パス区切りは変化しない */
	@Test
	public void keepsJapaneseNameAndSlashes() {
		assertEquals("host/走れメロス.zip", CharUtils.replaceInvalidFileChars("host/走れメロス.zip"));
	}
}
