package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * CharUtils.escapeUrlToFile の Windows 予約デバイス名対策テスト（監査 #12）。
 *
 * Windows では予約デバイス名（CON / PRN / AUX / NUL / COM0-9 / LPT0-9）と完全一致する
 * パスセグメントが実ファイルではなくデバイスとして解決され得る。
 * 特に NUL は「書き込みは成功するのに Files.exists が false」となるため、
 * 章キャッシュが毎回無効と判定され再ダウンロードを繰り返し、最終的に章が欠落する。
 *
 * - 予約デバイス名と完全一致するセグメントの末尾に '_' が付くこと
 * - 完全一致しないもの（NUL.txt / con.example.com / COM10 等）は従来出力のままであること
 *   （キャッシュファイル名が変わると再ダウンロードが発生するため、無用な変更をしない）
 */
public class CharUtilsReservedDeviceNameTest {

	////////////////////////////////////////////////////////////////
	// 予約デバイス名セグメントの無害化

	/** NUL 単独セグメント（#12 の再現ケース） */
	@Test
	public void escapesNulSegment() {
		assertEquals("example.com/NUL_/1", CharUtils.escapeUrlToFile("example.com/NUL/1"));
	}

	/** 大文字小文字を区別しない */
	@Test
	public void escapesReservedNamesCaseInsensitively() {
		assertEquals("example.com/nul_", CharUtils.escapeUrlToFile("example.com/nul"));
		assertEquals("example.com/Con_", CharUtils.escapeUrlToFile("example.com/Con"));
		assertEquals("example.com/aUx_", CharUtils.escapeUrlToFile("example.com/aUx"));
	}

	/** CON / PRN / AUX / NUL */
	@Test
	public void escapesLegacyDeviceNames() {
		for (String name : new String[]{"CON", "PRN", "AUX", "NUL"}) {
			assertEquals("example.com/"+name+"_", CharUtils.escapeUrlToFile("example.com/"+name));
		}
	}

	/** COM0〜COM9 / LPT0〜LPT9 */
	@Test
	public void escapesNumberedDeviceNames() {
		for (int i=0; i<=9; i++) {
			assertEquals("example.com/COM"+i+"_", CharUtils.escapeUrlToFile("example.com/COM"+i));
			assertEquals("example.com/LPT"+i+"_", CharUtils.escapeUrlToFile("example.com/LPT"+i));
		}
	}

	/** 上付き数字を使う別名 COM¹ COM² COM³ / LPT¹ LPT² LPT³ */
	@Test
	public void escapesSuperscriptDeviceNames() {
		for (char c : "¹²³".toCharArray()) {
			assertEquals("example.com/COM"+c+"_", CharUtils.escapeUrlToFile("example.com/COM"+c));
			assertEquals("example.com/LPT"+c+"_", CharUtils.escapeUrlToFile("example.com/LPT"+c));
		}
	}

	/** 先頭（ホスト）セグメント・末尾スラッシュありでも正しく処理される */
	@Test
	public void escapesFirstSegmentAndKeepsTrailingSlash() {
		assertEquals("nul_/works/1", CharUtils.escapeUrlToFile("nul/works/1"));
		assertEquals("example.com/nul_/", CharUtils.escapeUrlToFile("example.com/nul/"));
	}

	/** 複数セグメントが該当する場合はすべて無害化される */
	@Test
	public void escapesMultipleSegments() {
		assertEquals("example.com/nul_/con_/x", CharUtils.escapeUrlToFile("example.com/nul/con/x"));
	}

	/** 「?」「&」→「/」置換で後から現れる予約名セグメントも無害化される */
	@Test
	public void escapesSegmentsCreatedByQueryReplacement() {
		assertEquals("example.com/page/nul_/x=1", CharUtils.escapeUrlToFile("example.com/page?nul&x=1"));
	}

	/**
	 * 末尾のドット・空白は Windows が無視するため、予約名 + 末尾ドットもデバイスに解決される。
	 * `NUL.` / `NUL...` は `NUL` と同じく write 成功・exists=false で実測確認済み。
	 */
	@Test
	public void escapesReservedNamesWithTrailingDotsAndSpaces() {
		assertEquals("example.com/NUL._", CharUtils.escapeUrlToFile("example.com/NUL."));
		assertEquals("example.com/NUL..._", CharUtils.escapeUrlToFile("example.com/NUL..."));
		assertEquals("example.com/con._", CharUtils.escapeUrlToFile("example.com/con."));
		assertEquals("example.com/COM1 _", CharUtils.escapeUrlToFile("example.com/COM1 "));
	}

	/** ドット・空白のみのセグメントは予約名ではない（「..」は既存処理で「__」になる） */
	@Test
	public void keepsDotOnlySegments() {
		assertEquals("example.com/./a", CharUtils.escapeUrlToFile("example.com/./a"));
		assertEquals("example.com/.../a", CharUtils.escapeUrlToFile("example.com/.../a"));
		assertEquals("example.com/__/a", CharUtils.escapeUrlToFile("example.com/../a"));
	}

	////////////////////////////////////////////////////////////////
	// 回帰テスト: 完全一致しないものは従来出力のまま

	/** 予約名 + 拡張子は Windows 11 では実ファイルとして作成できるため変更しない */
	@Test
	public void keepsReservedNameWithExtension() {
		assertEquals("example.com/NUL.txt", CharUtils.escapeUrlToFile("example.com/NUL.txt"));
		assertEquals("example.com/COM9.html", CharUtils.escapeUrlToFile("example.com/COM9.html"));
		// 末尾ドットを除いても "NUL.txt" のままで予約名ではない
		assertEquals("example.com/NUL.txt.", CharUtils.escapeUrlToFile("example.com/NUL.txt."));
	}

	/** 空文字列・連続スラッシュ・先頭スラッシュでも入力を壊さない */
	@Test
	public void keepsDegeneratePaths() {
		assertEquals("", CharUtils.escapeUrlToFile(""));
		assertEquals("/", CharUtils.escapeUrlToFile("/"));
		assertEquals("example.com//a", CharUtils.escapeUrlToFile("example.com//a"));
		assertEquals("/images/cover.jpg", CharUtils.escapeUrlToFile("/images/cover.jpg"));
		// 空セグメントの間に挟まれた予約名も正しく処理される
		assertEquals("example.com//nul_//a", CharUtils.escapeUrlToFile("example.com//nul//a"));
	}

	/** 予約名で始まるホスト名は変更しない（キャッシュツリーごと移動してしまうため） */
	@Test
	public void keepsHostStartingWithReservedName() {
		assertEquals("con.example.com/works/1", CharUtils.escapeUrlToFile("con.example.com/works/1"));
	}

	/** 予約名を部分文字列として含むだけのセグメントは変更しない */
	@Test
	public void keepsSegmentsMerelyContainingReservedNames() {
		assertEquals("example.com/nulla", CharUtils.escapeUrlToFile("example.com/nulla"));
		assertEquals("example.com/conf", CharUtils.escapeUrlToFile("example.com/conf"));
		assertEquals("example.com/COM10", CharUtils.escapeUrlToFile("example.com/COM10"));
		assertEquals("example.com/COM", CharUtils.escapeUrlToFile("example.com/COM"));
		assertEquals("example.com/xnul", CharUtils.escapeUrlToFile("example.com/xnul"));
	}

	/** 既存の「..」無害化と併用しても壊れない */
	@Test
	public void keepsParentSegmentNeutralization() {
		assertEquals("example.com/__/nul_", CharUtils.escapeUrlToFile("example.com/../nul"));
	}

	/** 実 URL 相当の回帰: 出力が 1 文字も変わらないこと */
	@Test
	public void keepsRealWorldPaths() {
		assertEquals("ncode.syosetu.com/n9623lp/12/",
			CharUtils.escapeUrlToFile("ncode.syosetu.com/n9623lp/12/"));
		assertEquals("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123",
			CharUtils.escapeUrlToFile("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123"));
		assertEquals("novel.syosetu.org/123456/45.html",
			CharUtils.escapeUrlToFile("novel.syosetu.org/123456/45.html"));
		assertEquals("novel18.syosetu.com/n1234ab/5/",
			CharUtils.escapeUrlToFile("novel18.syosetu.com/n1234ab/5/"));
		assertEquals("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip",
			CharUtils.escapeUrlToFile("www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip"));
		assertEquals("2.novelist.jp/novel/12345/",
			CharUtils.escapeUrlToFile("2.novelist.jp/novel/12345/"));
		assertEquals("www.akatsuki-novels.com/stories/view/123/novel_id~456",
			CharUtils.escapeUrlToFile("www.akatsuki-novels.com/stories/view/123/novel_id~456"));
	}

	////////////////////////////////////////////////////////////////
	// 実ファイルシステムでの検証

	/**
	 * 無害化後のパスが「書き込んだら exists が true になる」実ファイルであること。
	 * Windows では無害化前の "NUL" がこの assert に失敗する（#12 の症状そのもの）。
	 * 非 Windows では無害化前後どちらも成功するため、この検証は自明に通る。
	 */
	@Test
	public void escapedPathIsWritableRealFile() throws IOException {
		Path base = Files.createTempDirectory("aozora-reserved-name-test");
		try {
			for (String name : new String[]{"NUL", "NUL.", "NUL...", "CON", "COM1", "LPT1", "AUX", "PRN"}) {
				String escaped = CharUtils.escapeUrlToFile("example.com/"+name);
				Path file = base.resolve(escaped.replace('/', java.io.File.separatorChar));
				Files.createDirectories(file.getParent());
				Files.write(file, "cached".getBytes(StandardCharsets.UTF_8));
				assertTrue(name+" の無害化後パスが実ファイルとして存在しない: "+escaped, Files.exists(file));
				assertEquals("cached", Files.readString(file, StandardCharsets.UTF_8));
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
