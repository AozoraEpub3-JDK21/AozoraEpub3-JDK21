import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.info.BookInfo;

/**
 * 深いフォルダに出すとき、パス全体の長さ（MAX_PATH 259 文字・題から作る名前は 250 文字）で名前を切っても、
 * 末尾だけ違う題が同じ名前にならないことのテスト（internal #16 の残件）。
 *
 * 修正前は名前の後ろを印なしで切っていたので、「…上」「…下」が同じ名前になり、あとの本が前の本を上書きしていた。
 * 名前 1 つが 255 バイトを超えるときの切り詰め（PR #112）と同じ印（"~" と 16 進 6 桁）を付ける。
 */
public class AozoraEpub3DeepFolderNameTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 出力先のパスがだいたい depth 文字になるフォルダ（win2 の実測は 187 文字） */
	private File deepFolder(int depth) throws Exception {
		File dir = tempFolder.getRoot();
		while (dir.getAbsolutePath().length() < depth - 11) {
			dir = new File(dir, "deepfolder");
		}
		dir.mkdirs();
		return dir;
	}

	private File outFile(File dst, String title) throws Exception {
		BookInfo bookInfo = new BookInfo(new File(dst, "in.txt"));
		bookInfo.creator = "著者";
		bookInfo.title = title;
		return AozoraEpub3.getOutFile(new File(dst, "in.txt"), dst, bookInfo, true, ".epub");
	}

	/** 末尾だけ違う長い題 */
	private static final String BODY = "とても長い題の作品".repeat(10);

	@Test
	public void titlesThatDifferOnlyAtTheEndDoNotShareANameInADeepFolder() throws Exception {
		File dst = deepFolder(187);
		File upper = outFile(dst, BODY + "（上）");
		File lower = outFile(dst, BODY + "（下）");
		assertNotEquals("上と下が同じ名前になって上書きしない", upper.getName(), lower.getName());
		for (File f : new File[] { upper, lower }) {
			assertTrue("フルパスは 259 文字以内: " + f.getAbsolutePath().length(), f.getAbsolutePath().length() <= 259);
			assertTrue("題から作る名前は拡張子を除いて 250 文字以内",
				f.getAbsolutePath().length() - ".epub".length() <= 250);
			assertTrue(f.getName(), f.getName().matches("\\[著者\\] とても長い題の作品.*~[0-9a-f]{6}\\.epub"));
		}
		//同じ題からは毎回同じ名前（変換し直すたびに別の本が増えない）
		assertEquals(upper.getName(), outFile(dst, BODY + "（上）").getName());
	}

	/** 出力先のパスがちょうど length 文字のフォルダ */
	private File folderOfLength(int length) throws Exception {
		File dir = tempFolder.getRoot();
		while (dir.getAbsolutePath().length() < length - 20) {
			dir = new File(dir, "deepfolder");
		}
		int rest = length - dir.getAbsolutePath().length() - 1;
		dir = new File(dir, "d".repeat(rest));
		dir.mkdirs();
		assertEquals(length, dir.getAbsolutePath().length());
		return dir;
	}

	/**
	 * 名前に使える文字が印の長さ（7 文字）ちょうどのときも、印を付ける（PR #114 の codex の指摘）。
	 * 題から作る名前の上限は、拡張子を除いたフルパスで 250 文字なので、出力先が 242 文字だと名前に残るのは 7 文字。
	 * 修正前は印を付けない切り方に落ち、上・下が同じ名前になって上書きしていた。
	 */
	@Test
	public void aNameWithRoomOnlyForTheMarkIsTheMarkAlone() throws Exception {
		File dst = folderOfLength(242);
		File upper = outFile(dst, BODY + "（上）");
		File lower = outFile(dst, BODY + "（下）");
		assertNotEquals("上と下が同じ名前になって上書きしない", upper.getName(), lower.getName());
		assertTrue(upper.getName(), upper.getName().matches("~[0-9a-f]{6}\\.epub"));
		assertTrue(lower.getName(), lower.getName().matches("~[0-9a-f]{6}\\.epub"));
	}

	@Test
	public void aNameThatFitsIsNotChanged() throws Exception {
		File dst = deepFolder(187);
		assertEquals("[著者] 短い題.epub", outFile(dst, "短い題").getName());
	}

	@Test
	public void theCutDoesNotSplitASurrogatePairOrLeaveATrailingSpace() throws Exception {
		File dst = deepFolder(187);
		//𠮷 (U+20BB7) はサロゲートペア。空白も混ぜて、どこで切れても壊れないことを見る
		String title = "𠮷 ".repeat(60);
		String name = outFile(dst, title).getName();
		String base = name.substring(0, name.length() - ".epub".length() - 7);
		assertTrue("末尾に空白を残さない: [" + base + "]", !base.endsWith(" "));
		char last = base.charAt(base.length() - 1);
		assertTrue("サロゲートペアを割らない", !Character.isHighSurrogate(last));
	}
}
