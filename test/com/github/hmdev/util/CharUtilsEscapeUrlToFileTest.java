package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * CharUtils.escapeUrlToFile のパストラバーサル対策テスト。
 *
 * - 「..」のみのパスセグメントが「__」に無害化されること（多層防御）
 * - 「..」セグメントを含まない従来入力の出力が完全に同一であること
 *   （キャッシュファイル名が変わると再ダウンロードが発生するため）
 */
public class CharUtilsEscapeUrlToFileTest {

	////////////////////////////////////////////////////////////////
	// 回帰テスト: 従来入力の出力が変わらないこと

	/** 小説家になろうの一覧 URL 相当 */
	@Test
	public void keepsNarouListPath() {
		assertEquals("ncode.syosetu.com/n9623lp/",
			CharUtils.escapeUrlToFile("ncode.syosetu.com/n9623lp/"));
	}

	/** 小説家になろうの各話 URL 相当 */
	@Test
	public void keepsNarouChapterPath() {
		assertEquals("ncode.syosetu.com/n9623lp/12/",
			CharUtils.escapeUrlToFile("ncode.syosetu.com/n9623lp/12/"));
	}

	/** カクヨムのエピソード URL 相当 */
	@Test
	public void keepsKakuyomuEpisodePath() {
		assertEquals("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123",
			CharUtils.escapeUrlToFile("kakuyomu.jp/works/822139840468926025/episodes/822139840468926123"));
	}

	/** クエリ文字列: ? と & は従来通り / に置換される */
	@Test
	public void convertsQuerySeparatorsToSlash() {
		assertEquals("example.com/page/id=2/mode=1",
			CharUtils.escapeUrlToFile("example.com/page?id=2&mode=1"));
	}

	/** ファイル名に使えない文字は従来通り _ に置換される */
	@Test
	public void convertsForbiddenCharsToUnderscore() {
		assertEquals("img.example.com/a_b_c_d_e_f_g_h.png",
			CharUtils.escapeUrlToFile("img.example.com/a:b*c|d<e>f\"g\\h.png"));
	}

	/** 「..」を含むが単独セグメントではないものは無害化対象外（従来出力を維持） */
	@Test
	public void keepsDotsInsideSegment() {
		assertEquals("example.com/a..b/c...d/file..png",
			CharUtils.escapeUrlToFile("example.com/a..b/c...d/file..png"));
	}

	/** 「.」単独セグメントは traversal にならないため従来出力を維持 */
	@Test
	public void keepsSingleDotSegment() {
		assertEquals("example.com/./a",
			CharUtils.escapeUrlToFile("example.com/./a"));
	}

	////////////////////////////////////////////////////////////////
	// パストラバーサル: 「..」セグメントが残らないこと

	/** 典型的な traversal 攻撃パス */
	@Test
	public void neutralizesParentSegments() {
		String result = CharUtils.escapeUrlToFile("example.com/../../etc/passwd");
		assertFalse("「..」セグメントが残ってはいけない: " + result, containsParentSegment(result));
		assertEquals("example.com/__/__/etc/passwd", result);
	}

	/** 先頭からの traversal */
	@Test
	public void neutralizesLeadingParentSegments() {
		String result = CharUtils.escapeUrlToFile("../../evil");
		assertFalse(containsParentSegment(result));
		assertEquals("__/__/evil", result);
	}

	/** 末尾の「..」 */
	@Test
	public void neutralizesTrailingParentSegment() {
		String result = CharUtils.escapeUrlToFile("a/..");
		assertFalse(containsParentSegment(result));
		assertEquals("a/__", result);
	}

	/** 「..」のみの入力 */
	@Test
	public void neutralizesBareParentSegment() {
		assertEquals("__", CharUtils.escapeUrlToFile(".."));
	}

	/** ? / & の / 置換で後から生成される「..」セグメントも無害化されること */
	@Test
	public void neutralizesParentSegmentsCreatedByQueryReplacement() {
		String result = CharUtils.escapeUrlToFile("a?..&b");
		assertFalse(containsParentSegment(result));
		assertEquals("a/__/b", result);

		result = CharUtils.escapeUrlToFile("..?..");
		assertFalse(containsParentSegment(result));
		assertEquals("__/__", result);
	}

	/** バックスラッシュ traversal は \ → _ 置換で無害化される（従来動作の確認） */
	@Test
	public void neutralizesBackslashTraversal() {
		String result = CharUtils.escapeUrlToFile("..\\..\\evil");
		assertFalse(containsParentSegment(result));
		assertEquals(".._.._evil", result);
	}

	/** 「..」がパスセグメントとして含まれるか判定 */
	private static boolean containsParentSegment(String path) {
		for (String segment : path.split("/", -1)) {
			if ("..".equals(segment)) return true;
		}
		return false;
	}
}
