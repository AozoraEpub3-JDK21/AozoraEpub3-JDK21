package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * CharUtils.escapeUrlToFile の InvalidPathException 対策テスト（監査 #13）。
 *
 * Windows では以下のパスセグメントが Path 解決の時点で InvalidPathException になる。
 * InvalidPathException は RuntimeException のため、対策前は呼び出し側の catch (IOException) を
 * すり抜けて変換全体が中断していた。
 *
 * - 制御文字 (0x00-0x1F、TAB を含む) を含むセグメント … 位置を問わず例外
 * - 末尾が半角スペースのセグメント … 例外
 *
 * 一方、先頭・中間の半角スペース / 末尾ドット / 全角スペースは例外にならないため対象外
 * （末尾ドットは Windows が黙って切り詰めるだけ。詳細は docs/code-audit-followups.md の「### 13.」）。
 */
public class CharUtilsInvalidPathCharsTest {

	private static final char CTRL_00 = 0x00;
	private static final char TAB = 0x09;
	private static final char CTRL_01 = 0x01;
	private static final char CTRL_1F = 0x1F;

	////////////////////////////////////////////////////////////////
	// 制御文字

	/** セグメント中間の TAB */
	@Test
	public void escapesTabInsideSegment() {
		assertEquals("example.com/fo_o/bar.html",
			CharUtils.escapeUrlToFile("example.com/fo" + TAB + "o/bar.html"));
	}

	/** セグメント末尾の TAB */
	@Test
	public void escapesTrailingTab() {
		assertEquals("example.com/foo_/bar.html",
			CharUtils.escapeUrlToFile("example.com/foo" + TAB + "/bar.html"));
	}

	/** 制御文字の範囲全体 (0x01-0x1F) */
	@Test
	public void escapesControlCharacters() {
		for (char c = 0x01; c <= 0x1F; c++) {
			assertEquals("制御文字 0x" + Integer.toHexString(c) + " が置換されていない",
				"example.com/a_b", CharUtils.escapeUrlToFile("example.com/a" + c + "b"));
		}
	}

	/** 制御文字の下端・上端（0x00 と 0x1F） */
	@Test
	public void escapesControlCharacterBoundaries() {
		assertEquals("a_b", CharUtils.escapeUrlToFile("a" + CTRL_00 + "b"));
		assertEquals("a_b", CharUtils.escapeUrlToFile("a" + CTRL_01 + "b"));
		assertEquals("a_b", CharUtils.escapeUrlToFile("a" + CTRL_1F + "b"));
	}

	/** 0x20 (半角スペース) は制御文字ではないので、中間にあれば置換されない（境界の確認） */
	@Test
	public void keepsSpaceAtControlCharBoundary() {
		assertEquals("a b", CharUtils.escapeUrlToFile("a b"));
	}

	////////////////////////////////////////////////////////////////
	// セグメント末尾の半角スペース

	/** 中間セグメントの末尾スペース */
	@Test
	public void escapesTrailingSpaceInMiddleSegment() {
		assertEquals("example.com/foo_/bar.html",
			CharUtils.escapeUrlToFile("example.com/foo /bar.html"));
	}

	/** 最終セグメントの末尾スペース */
	@Test
	public void escapesTrailingSpaceInLastSegment() {
		assertEquals("example.com/foo_", CharUtils.escapeUrlToFile("example.com/foo "));
	}

	/** 連続する末尾スペースはすべて置換される */
	@Test
	public void escapesMultipleTrailingSpaces() {
		assertEquals("example.com/foo___/bar", CharUtils.escapeUrlToFile("example.com/foo   /bar"));
	}

	/** 半角スペースのみのセグメント */
	@Test
	public void escapesSpaceOnlySegment() {
		assertEquals("example.com/__/bar", CharUtils.escapeUrlToFile("example.com/  /bar"));
	}

	/** 末尾スラッシュの直前も対象 */
	@Test
	public void escapesTrailingSpaceBeforeTrailingSlash() {
		assertEquals("example.com/foo_/", CharUtils.escapeUrlToFile("example.com/foo /"));
	}

	////////////////////////////////////////////////////////////////
	// 回帰: 例外にならないものは従来出力のまま

	/** 先頭・中間の半角スペースは Windows でも有効なので変更しない */
	@Test
	public void keepsLeadingAndInnerSpaces() {
		assertEquals("example.com/ foo/bar", CharUtils.escapeUrlToFile("example.com/ foo/bar"));
		assertEquals("example.com/fo o/bar", CharUtils.escapeUrlToFile("example.com/fo o/bar"));
		assertEquals("example.com/a b c/d", CharUtils.escapeUrlToFile("example.com/a b c/d"));
	}

	/** 末尾ドットは例外にならないため変更しない */
	@Test
	public void keepsTrailingDot() {
		assertEquals("example.com/foo./bar", CharUtils.escapeUrlToFile("example.com/foo./bar"));
	}

	/** 全角スペースは Windows でも有効なので変更しない */
	@Test
	public void keepsFullWidthSpace() {
		assertEquals("example.com/foo　/bar", CharUtils.escapeUrlToFile("example.com/foo　/bar"));
	}

	/** 実 URL 相当の入力は 1 文字も変わらない */
	@Test
	public void keepsRealWorldPaths() {
		assertEquals("ncode.syosetu.com/n9623lp/12/",
			CharUtils.escapeUrlToFile("ncode.syosetu.com/n9623lp/12/"));
		assertEquals("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123",
			CharUtils.escapeUrlToFile("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123"));
		assertEquals("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip",
			CharUtils.escapeUrlToFile("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip"));
	}

	/** 予約デバイス名の無害化（監査 #12）と併用しても壊れない */
	@Test
	public void keepsReservedDeviceNameHandling() {
		// "COM1 " は先に予約名として "COM1 _" になるため、末尾スペースではなくなる
		assertEquals("example.com/COM1 _", CharUtils.escapeUrlToFile("example.com/COM1 "));
		// 予約名でないものは末尾スペースが '_' になる
		assertEquals("example.com/COM99_", CharUtils.escapeUrlToFile("example.com/COM99 "));
		// 制御文字が先に '_' になるため、"NUL<TAB>" は予約名判定に掛からず "NUL_" になる
		assertEquals("example.com/NUL_", CharUtils.escapeUrlToFile("example.com/NUL" + TAB));
	}

	/** 「..」無害化（監査 #1）と併用しても壊れない */
	@Test
	public void keepsParentSegmentNeutralization() {
		// ".. " は「..」のみのセグメントではないため無害化対象外。末尾スペースだけが '_' になる
		assertEquals("example.com/.._/x", CharUtils.escapeUrlToFile("example.com/.. /x"));
		// 「..」セグメントは従来どおり「__」になる
		assertEquals("example.com/__/x", CharUtils.escapeUrlToFile("example.com/../x"));
		// 制御文字を含む「..」も無害化対象外（制御文字が先に '_' になる）
		assertEquals("example.com/.._/x", CharUtils.escapeUrlToFile("example.com/.." + TAB + "/x"));
	}

	////////////////////////////////////////////////////////////////
	// 実ファイルシステムでの検証

	/**
	 * 無害化後のパスが実際に解決・書き込みできること。
	 * Windows では無害化前の入力がここで InvalidPathException になる（#13 の症状そのもの）。
	 * 非 Windows では無害化前後どちらも成功するため、この検証は自明に通る。
	 */
	@Test
	public void escapedPathIsResolvableAndWritable() throws IOException {
		Path base = Files.createTempDirectory("aozora-invalid-path-test");
		try {
			String[] inputs = {
				"example.com/foo /bar.html",
				"example.com/foo   /bar.html",
				"example.com/fo" + TAB + "o/bar.html",
				"example.com/foo" + CTRL_01 + "/bar.html",
				"example.com/  /bar.html",
			};
			for (String input : inputs) {
				String escaped = CharUtils.escapeUrlToFile(input);
				Path file = base.resolve(escaped.replace('/', java.io.File.separatorChar));
				Files.createDirectories(file.getParent());
				Files.write(file, "cached".getBytes(StandardCharsets.UTF_8));
				assertTrue("無害化後パスが存在しない: " + escaped, Files.exists(file));
			}
		} finally {
			deleteRecursively(base);
		}
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			try (var children = Files.list(path)) {
				for (Path child : children.toList()) deleteRecursively(child);
			}
		}
		Files.deleteIfExists(path);
	}
}
