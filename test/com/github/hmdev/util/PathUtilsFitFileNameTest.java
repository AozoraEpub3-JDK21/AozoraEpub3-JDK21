package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * 名前 1 つを 255 バイトに収める処理のテスト（internal #16）。
 */
public class PathUtilsFitFileNameTest {

	private static int bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}

	@Test
	public void aNameThatFitsIsLeftAlone() {
		assertEquals("[著者] 題", PathUtils.fitFileName("[著者] 題", ".epub"));
		// ちょうど 255 バイト（250 + ".epub"）。末尾の空白も切っていなければ落とさない
		String exact = "a".repeat(249) + " ";
		assertEquals(exact, PathUtils.fitFileName(exact, ".epub"));
	}

	@Test
	public void aLongJapaneseNameIsCutTo255BytesWithTheExtension() {
		String name = PathUtils.fitFileName("題".repeat(100), ".epub");
		assertTrue(bytes(name + ".epub") <= 255);
		// 250 バイトに 3 バイトの字は 83 文字まで入る
		assertEquals("題".repeat(83), name);
	}

	@Test
	public void aSurrogatePairIsNotSplit() {
		String name = PathUtils.fitFileName("𠮷".repeat(100), ".txt");
		assertTrue(bytes(name + ".txt") <= 255);
		assertEquals("𠮷".repeat(62), name);
		assertFalse(name.endsWith("\uD842"));
	}

	@Test
	public void aCutDoesNotLeaveATrailingSpaceOrDot() {
		// 切った位置の直前が空白・ドット・全角の空白
		String base = "題".repeat(82) + " .　" + "題".repeat(10);
		String name = PathUtils.fitFileName(base, ".epub");
		assertEquals("題".repeat(82), name);
	}
}
