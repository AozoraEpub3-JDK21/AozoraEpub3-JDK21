package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 名前 1 つを 255 バイトに収める処理のテスト（internal #16）。
 */
public class PathUtilsFitFileNameTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static int bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}

	/** この場所のファイルシステムが 255 バイトを超える名前を受け付けるか（mac・Windows は受け付け、Linux の ext4 は受け付けない） */
	public static boolean acceptsLongNames(File dir) throws Exception {
		try {
			Files.delete(Files.createFile(dir.toPath().resolve("題".repeat(100) + ".txt")));
			return true;
		} catch (FileSystemException e) {
			return false;
		}
	}

	@Test
	public void aNameThatFitsIsLeftAlone() {
		assertEquals("[著者] 題", PathUtils.fitFileName("[著者] 題", ".epub"));
		// ちょうど 255 バイト（250 + ".epub"）。末尾の空白も、切っていなければ落とさない
		String exact = "a".repeat(249) + " ";
		assertEquals(exact, PathUtils.fitFileName(exact, ".epub"));
	}

	@Test
	public void aLongNameIsCutTo255BytesWithAMark() {
		String name = PathUtils.fitFileName("題".repeat(100), ".epub");
		assertTrue(bytes(name + ".epub") <= 255);
		// 印の 7 バイトを除いた 243 バイトに 3 バイトの字は 81 文字
		assertTrue(name, name.matches("題{81}~[0-9a-f]{6}"));
		assertEquals("同じ名前からは同じ名前", name, PathUtils.fitFileName("題".repeat(100), ".epub"));
	}

	@Test
	public void titlesThatDifferOnlyAtTheEndStayApart() {
		String upper = PathUtils.fitFileName("長い題".repeat(40) + "（上）", ".epub");
		String lower = PathUtils.fitFileName("長い題".repeat(40) + "（下）", ".epub");
		assertNotEquals(upper, lower);
	}

	@Test
	public void aSurrogatePairIsNotSplit() {
		String name = PathUtils.fitFileName("𠮷".repeat(100), ".txt");
		assertTrue(bytes(name + ".txt") <= 255);
		// 251 - 7 = 244 バイトに 4 バイトの字は 61 文字
		assertTrue(name, name.matches("(𠮷){61}~[0-9a-f]{6}"));
	}

	@Test
	public void aCutDoesNotLeaveATrailingSpaceOrDot() {
		// 切った位置の直前が空白・ドット
		String base = "題".repeat(80) + " ." + "題".repeat(20);
		String name = PathUtils.fitFileName(base, ".epub");
		assertTrue(name, name.matches("題{80}~[0-9a-f]{6}"));
	}

	@Test
	public void aShortNameIsNeverProbed() throws Exception {
		// 存在しない場所でも、短い名前は試さずにそのまま
		File missing = new File(tempFolder.getRoot(), "no/such/dir");
		assertEquals("[著者] 題", PathUtils.fitFileNameIn(missing, "[著者] 題", ".epub"));
		assertFalse(missing.exists());
	}

	@Test
	public void aLongNameIsCutOnlyWhereTheFileSystemRejectsIt() throws Exception {
		File dir = tempFolder.newFolder();
		String base = "題".repeat(100);
		String name = PathUtils.fitFileNameIn(dir, base, ".epub");
		if (acceptsLongNames(dir)) {
			assertEquals("受け付ける場所（mac・Windows）では今までの名前のまま", base, name);
		} else {
			assertTrue("受け付けない場所（Linux）では 255 バイト以内: " + name, bytes(name + ".epub") <= 255);
		}
		// 試したファイルを残さない
		assertEquals(0, dir.list().length);
	}

	@Test
	public void theProbeNameHasTheSameLengthInBytesAndCharacters() {
		// 先頭が ASCII・日本語・2 バイトの字・サロゲートペア・"." のそれぞれ（"." で始まる名前は PR の codex の指摘）
		for (String name : new String[]{ "[著者] 題.epub", "題名.txt", "é題.txt", "𠮷題.txt", ".題名.txt", "〇題.txt" }) {
			String probe = PathUtils.probeName(name);
			assertNotEquals(name, probe);
			assertEquals("バイト数: " + name, bytes(name), bytes(probe));
			assertEquals("文字数（Windows・mac の数え方）: " + name, name.length(), probe.length());
			assertEquals("先頭の 1 文字だけ違う: " + name, name.substring(name.offsetByCodePoints(0, 1)), probe.substring(probe.offsetByCodePoints(0, 1)));
		}
		assertTrue("先頭が ASCII なら隠しファイル", PathUtils.probeName("[著者] 題.epub").startsWith("."));
	}

	@Test
	public void aSmallerLimitCanBeApplied() {
		// eCryptfs は名前 1 つ 143 バイトまで
		String name = PathUtils.fitFileName("題".repeat(100), ".epub", 143);
		assertTrue(name, bytes(name + ".epub") <= 143);
		assertTrue(name, name.matches("題{43}~[0-9a-f]{6}"));
	}

	/** 書けない場所では、長さのせいと決めつけて名前を切らない（ゲート2の指摘。後の書き込みに理由を出させる） */
	@Test
	public void aFolderThatCannotBeWrittenDoesNotCutTheName() throws Exception {
		File dir = tempFolder.newFolder();
		Assume.assumeTrue("書けない場所を作れない環境（root など）のためスキップ", dir.setWritable(false, false) && !Files.isWritable(dir.toPath()));
		try {
			String base = "題".repeat(100);
			assertEquals(base, PathUtils.fitFileNameIn(dir, base, ".epub"));
		} finally {
			dir.setWritable(true, false);
		}
	}

	/** フォルダを含む名前は試さない（PR の codex の指摘。名前を指定する道で ../ を渡すと、出力先の外にファイルを作って消せた） */
	@Test
	public void aNameWithAFolderIsNeverProbed() throws Exception {
		File missing = new File(tempFolder.getRoot(), "work");
		for (String base : new String[]{ "../" + "題".repeat(100), "..\\" + "題".repeat(100) }) {
			assertEquals(base, PathUtils.fitFileNameIn(missing, base, ".txt"));
			assertFalse("試すとフォルダを作る", missing.exists());
		}
		// 拡張子の側の区切り文字も（CLI の -ext。PR の codex の指摘）
		assertEquals("題".repeat(100), PathUtils.fitFileNameIn(missing, "題".repeat(100), "/../x.epub"));
		assertFalse("試すとフォルダを作る", missing.exists());
	}
}
