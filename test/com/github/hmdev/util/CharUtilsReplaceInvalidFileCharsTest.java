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

	////////////////////////////////////////////////////////////////
	// 制御文字（監査 #14）

	/**
	 * 制御文字 (0x00-0x1F、TAB = 0x09 を含む) は '_' に置換される。
	 *
	 * Windows のファイル API は制御文字を含む名前を受け付けない。この関数の戻り値は
	 * AozoraEpub3Applet の青空 zip 直接ダウンロード経路でファイル名になるため、
	 * 置換しないと AozoraEpub3Applet.java:4194 の getCanonicalPath() が IOException で失敗し、
	 * サニタイズできたはずの名前でダウンロードが落ちる。
	 */
	@Test
	public void replacesControlCharacters() {
		for (char c = 0x00; c <= 0x1F; c++) {
			assertEquals("制御文字 0x" + Integer.toHexString(c) + " が置換されていない",
				"host/a_b.zip", CharUtils.replaceInvalidFileChars("host/a" + c + "b.zip"));
		}
	}

	/**
	 * ':' も '_' に置換される。
	 *
	 * ':' は RFC 3986 上パスセグメント内で percent-encoding 不要の合法文字なので、
	 * http://host/a:b.zip のような URL が正規のリンクとして到達し得る。
	 * Windows では ':' を含む名前はファイル API が受け付けず（NTFS の代替データストリーム記法）、
	 * 制御文字と同じく getCanonicalPath() が IOException になることを実測で確認した。
	 * 制御文字は URL 中では通常 %01 形式で到達し本経路はデコードしないため、
	 * 実際の遭遇確率は ':' の方が高い。
	 */
	@Test
	public void replacesColon() {
		assertEquals("host/a_b.zip", CharUtils.replaceInvalidFileChars("host/a:b.zip"));
		// ポート番号付き URL でも最終セグメントは変わらない（getName() で切り出されるため）
		assertEquals("host_8080/foo.zip", CharUtils.replaceInvalidFileChars("host:8080/foo.zip"));
	}

	/** 0x20 (半角スペース) は制御文字ではないので置換しない（境界の確認） */
	@Test
	public void keepsSpace() {
		assertEquals("host/a b.zip", CharUtils.replaceInvalidFileChars("host/a b.zip"));
	}

	/** 0x7F (DEL) は Windows のパスとして有効なので置換しない（境界の確認） */
	@Test
	public void keepsDelCharacter() {
		assertEquals("host/a" + (char) 0x7F + "b.zip",
			CharUtils.replaceInvalidFileChars("host/a" + (char) 0x7F + "b.zip"));
	}
}
