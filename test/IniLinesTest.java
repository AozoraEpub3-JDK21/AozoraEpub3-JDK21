import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

/** ini を、変わったキーの行だけ書き換える（internal #30） */
public class IniLinesTest {

	@org.junit.Rule
	public org.junit.rules.TemporaryFolder temp = new org.junit.rules.TemporaryFolder();

	private static Properties load(byte[] bytes) throws Exception {
		Properties p = new Properties();
		p.load(new ByteArrayInputStream(bytes));
		return p;
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/** 書いた結果を読むと、書きたかった設定と同じになる */
	private static byte[] updateAndCheck(byte[] original, Properties after) throws Exception {
		byte[] out = IniLines.update(original, load(original), after);
		assertEquals(after, load(out));
		return out;
	}

	/** 配布の ini: コメントもキーの順も残り、変わるのは足した行だけ */
	@Test
	public void theShippedIniKeepsItsCommentsAndOrder() throws Exception {
		byte[] shipped = Files.readAllBytes(Path.of("AozoraEpub3.ini"));
		Properties after = load(shipped);
		after.setProperty("WebShelfDir", "C:\\Users\\利用者\\本棚\\Web");
		after.setProperty("PreviewLibraryDir.1", "/Users/me/本棚");
		byte[] out = updateAndCheck(shipped, after);
		String text = new String(out, StandardCharsets.ISO_8859_1);
		String head = new String(shipped, StandardCharsets.ISO_8859_1);
		assertTrue("元の中身はそのまま先頭に残る", text.startsWith(head.endsWith("\n") ? head : head + "\n"));
		List<String> added = List.of(text.substring(head.length()).strip().split("\r?\n"));
		assertEquals(List.of("PreviewLibraryDir.1=/Users/me/\\u672C\\u68DA", "WebShelfDir=C\\:\\\\Users\\\\\\u5229\\u7528\\u8005\\\\\\u672C\\u68DA\\\\Web"), added);
		assertTrue("UTF-8 のコメントは 1 バイトも変わらない", new String(out, StandardCharsets.UTF_8).contains("配布時の既定設定"));
	}

	/** 値の変わったキーはその行で書き換える。前後の行・コメントは触らない */
	@Test
	public void aChangedKeyIsRewrittenInPlace() throws Exception {
		byte[] original = bytes("# 説明\nA=1\n# B の説明\nB=old\nC=3\n");
		Properties after = load(original);
		after.setProperty("B", "new");
		assertEquals("# 説明\nA=1\n# B の説明\nB=new\nC=3\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** 無くなったキーは、続きの行ごと消す。CRLF は CRLF のまま */
	@Test
	public void aRemovedKeyLosesItsWholeLogicalLine() throws Exception {
		byte[] original = bytes("A=1\r\nPreviewLibraryDir.2=/a\\\r\n  /b\r\nB=2\r\n");
		Properties after = load(original);
		assertEquals("/a/b", after.getProperty("PreviewLibraryDir.2"));
		after.remove("PreviewLibraryDir.2");
		after.setProperty("C", "3");
		assertEquals("A=1\r\nB=2\r\n\r\nC=3\r\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** 同じキーが 2 行あれば 1 行目を書き換えて 2 行目を消す（load は後の行を使うので、残すと戻る） */
	@Test
	public void aDuplicateKeyEndsUpOnce() throws Exception {
		byte[] original = bytes("K=1\nX=0\nK=2\n");
		Properties after = load(original);
		after.setProperty("K", "3");
		assertEquals("K=3\nX=0\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** 最後の行に改行が無くても、足す行は次の行になる */
	@Test
	public void appendingAfterALastLineWithoutANewline() throws Exception {
		byte[] original = bytes("A=1");
		Properties after = load(original);
		after.setProperty("B", "2");
		assertEquals("A=1\n\nB=2\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** 最後の行が \ で終わって続きを待っていても、足す行は前の値に入らない（PR の手元の codex） */
	@Test
	public void appendingAfterAPendingContinuation() throws Exception {
		for (String original : new String[]{ "Other=value\\", "Other=value\\\n", "Other=value\\\r\n" }) {
			Properties after = load(bytes(original));
			assertEquals(original, "value", after.getProperty("Other"));
			after.setProperty("PreviewLibraryDir.1", "/books");
			byte[] out = updateAndCheck(bytes(original), after);
			assertTrue("元の行は残る: " + original, new String(out, StandardCharsets.UTF_8).startsWith(original));
		}
	}

	/** 改行が CR だけのファイルには CR で足す */
	@Test
	public void aCrOnlyFileGetsCrLines() throws Exception {
		byte[] original = bytes("A=1\rB=2\r");
		Properties after = load(original);
		after.setProperty("C", "3");
		assertEquals("A=1\rB=2\r\rC=3\r", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** 番号付きのキーは数の順に足す（.2 が .10 より前） */
	@Test
	public void numberedKeysAreAppendedInNumberOrder() throws Exception {
		byte[] original = bytes("A=1\n");
		Properties after = load(original);
		for (int i = 1; i <= 11; i++) after.setProperty("PreviewLibraryDir." + i, "/s" + i);
		String out = new String(updateAndCheck(original, after), StandardCharsets.UTF_8);
		assertTrue(out, out.indexOf("PreviewLibraryDir.2=") < out.indexOf("PreviewLibraryDir.10="));
		assertTrue(out, out.indexOf("PreviewLibraryDir.9=") < out.indexOf("PreviewLibraryDir.10="));
	}

	/** Properties が飛ばさない空白（\u000B など）で始まる行も、Properties と同じくキーとして扱う（PR のゲート2） */
	@Test
	public void onlyThePropertiesSpacesStartAComment() throws Exception {
		byte[] original = bytes("\u000B#K=v\nA=1\n\u001F\n");
		Properties before = load(original);
		assertTrue(before.containsKey("\u000B#K"));
		Properties after = load(original);
		after.remove("\u000B#K");
		after.remove("\u001F");
		assertEquals("A=1\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** GUI の保存: 配布の ini に全部のキーを書いても、コメントは残り、読み直すと同じ設定になる */
	@Test
	public void theGuiSaveKeepsTheShippedComments() throws Exception {
		Path ini = temp.newFolder().toPath().resolve("AozoraEpub3.ini");
		byte[] shipped = Files.readAllBytes(Path.of("AozoraEpub3.ini"));
		Files.write(ini, shipped);
		Properties gui = load(shipped);
		//GUI は自分の全部の設定を書く（配布の ini に無いキーも）
		gui.setProperty("BodyMargin", "0");
		gui.setProperty("LastDir", "C:\\本");
		gui.setProperty("Vertical", "");
		IniLines.writeInPlace(ini, gui, "AozoraEpub3 Parameters");
		byte[] out = Files.readAllBytes(ini);
		assertEquals(gui, load(out));
		assertTrue("コメントは残る", new String(out, StandardCharsets.UTF_8).contains("配布時の既定設定"));
		//キーの順も残る: 元の行は、書き換えた行（Vertical）のほかは同じ順に並ぶ
		List<String> was = new java.util.ArrayList<>(List.of(new String(shipped, StandardCharsets.ISO_8859_1).split("\r?\n")));
		was.removeIf(l -> l.startsWith("Vertical="));
		List<String> now = new java.util.ArrayList<>(List.of(new String(out, StandardCharsets.ISO_8859_1).split("\r?\n")));
		now.retainAll(was);
		assertEquals(was, now.subList(0, Math.min(was.size(), now.size())));
	}

	/** 書けるが読めない ini は、前からの GUI と同じく store で全部を書く（PR の手元の codex） */
	@Test
	public void aWriteOnlyIniIsWrittenWhole() throws Exception {
		Path ini = temp.newFolder().toPath().resolve("AozoraEpub3.ini");
		Files.write(ini, bytes("# 説明\nA=1\n"));
		try {
			Files.setPosixFilePermissions(ini, java.nio.file.attribute.PosixFilePermissions.fromString("-w-------"));
		} catch (UnsupportedOperationException e) {
			org.junit.Assume.assumeNoException("POSIX の権限が無い環境", e);
		}
		org.junit.Assume.assumeFalse("読めてしまう（root など）", Files.isReadable(ini));
		Properties p = new Properties();
		p.setProperty("A", "2");
		IniLines.writeInPlace(ini, p, "AozoraEpub3 Parameters");
		Files.setPosixFilePermissions(ini, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
		assertEquals(p, load(Files.readAllBytes(ini)));
	}

	/** 変わらなければファイルに触らない（GUI は窓が手前から外れるたびに保存する） */
	@Test
	public void theGuiSaveDoesNotTouchAnUnchangedFile() throws Exception {
		Path ini = temp.newFolder().toPath().resolve("AozoraEpub3.ini");
		Files.write(ini, bytes("# 説明\nA=1\n"));
		java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(ini, old);
		IniLines.writeInPlace(ini, load(Files.readAllBytes(ini)), "AozoraEpub3 Parameters");
		assertEquals(old, Files.getLastModifiedTime(ini));
	}

	/** 読めない ini や、まだ無い ini は、前からの GUI と同じく store で全部を書く */
	@Test
	public void anUnreadableOrMissingIniIsWrittenWhole() throws Exception {
		Path dir = temp.newFolder().toPath();
		Properties p = new Properties();
		p.setProperty("A", "1");
		Path broken = dir.resolve("broken.ini");
		Files.write(broken, "Bad=\\uZZZZ\n# 説明\n".getBytes(StandardCharsets.ISO_8859_1));
		IniLines.writeInPlace(broken, p, "AozoraEpub3 Parameters");
		assertEquals(p, load(Files.readAllBytes(broken)));
		assertTrue(new String(Files.readAllBytes(broken), StandardCharsets.ISO_8859_1).startsWith("#AozoraEpub3 Parameters"));
		Path missing = dir.resolve("missing.ini");
		IniLines.writeInPlace(missing, p, "AozoraEpub3 Parameters");
		assertEquals(p, load(Files.readAllBytes(missing)));
	}

	/** その場で書く: ini がリンクなら、リンクのまま先を書き換える（PR #125 の codex と同じ） */
	@Test
	public void theGuiSaveWritesThroughALink() throws Exception {
		Path dir = temp.newFolder().toPath();
		Path real = dir.resolve("real.ini");
		Files.write(real, bytes("# 説明\nA=1\n"));
		Path link = dir.resolve("AozoraEpub3.ini");
		try {
			Files.createSymbolicLink(link, real);
		} catch (UnsupportedOperationException | java.io.IOException e) {
			org.junit.Assume.assumeNoException("シンボリックリンクを作れない環境", e);
		}
		Properties p = load(Files.readAllBytes(real));
		p.setProperty("A", "2");
		IniLines.writeInPlace(link, p, "AozoraEpub3 Parameters");
		assertTrue(Files.isSymbolicLink(link));
		assertEquals("# 説明\nA=2\n", new String(Files.readAllBytes(real), StandardCharsets.UTF_8));
	}

	/** 比べ方は推移的: 番号付きと番号なしが混ざっても、同じキーを 2 行にしない（PR のゲート2） */
	@Test
	public void keyOrderIsTransitive() throws Exception {
		List<String> keys = List.of("a.9", "a.10", "a.1x", "a.", "a", "b.2", "a.01");
		for (String x : keys) for (String y : keys) for (String z : keys) {
			if (IniLines.compareKeys(x, y) < 0 && IniLines.compareKeys(y, z) < 0) {
				assertTrue(x + " < " + y + " < " + z, IniLines.compareKeys(x, z) < 0);
			}
		}
		byte[] original = bytes("a.1x=0\n");
		Properties after = load(original);
		after.setProperty("a.1x", "1");
		after.setProperty("a.9", "9");
		after.setProperty("a.10", "10");
		String out = new String(updateAndCheck(original, after), StandardCharsets.UTF_8);
		assertEquals(out, out.indexOf("a.1x="), out.lastIndexOf("a.1x="));
	}

	/** 変わらなければ 1 バイトも変えない（区切りが : や空白でも、キーにエスケープがあっても） */
	@Test
	public void nothingChangedMeansTheSameBytes() throws Exception {
		byte[] original = bytes("! 感嘆符のコメント\n  A : 1\nB 2\nkey\\ with\\ space=v\n#\\\nC=3\n");
		Properties after = load(original);
		assertEquals("key with space", after.stringPropertyNames().stream().filter(k -> k.startsWith("key")).findFirst().orElse(""));
		assertEquals(new String(original, StandardCharsets.UTF_8), new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}

	/** コメントの行末の \ は次の行を飲まない（コメントは続かない） */
	@Test
	public void aCommentEndingInABackslashDoesNotSwallowTheNextLine() throws Exception {
		byte[] original = bytes("#C:\\dir\\\nA=1\n");
		Properties after = load(original);
		after.setProperty("A", "2");
		assertEquals("#C:\\dir\\\nA=2\n", new String(updateAndCheck(original, after), StandardCharsets.UTF_8));
	}
}
