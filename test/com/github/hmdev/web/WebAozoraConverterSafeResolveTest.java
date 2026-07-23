package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * WebAozoraConverter.safeResolve のパストラバーサル対策テスト。
 *
 * base ディレクトリ配下に解決されるパスは許可し、
 * 「..」等で base 外に解決されるパスは IOException になることを検証する。
 */
public class WebAozoraConverterSafeResolveTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Path base;

	@Before
	public void setUp() throws IOException {
		base = tempFolder.newFolder("cache").toPath();
	}

	/** 通常の相対パスは base 配下の File として解決される */
	@Test
	public void resolvesRelativePathUnderBase() throws IOException {
		File resolved = WebAozoraConverter.safeResolve(base, "ncode.syosetu.com/n9623lp/1/index.html");
		assertTrue(resolved.toPath().startsWith(base.toRealPath()));
		assertEquals("index.html", resolved.getName());
	}

	/** 存在するファイルは toRealPath 経由で解決される（2 段階パターンの existing 側） */
	@Test
	public void resolvesExistingFileUnderBase() throws IOException {
		Path existing = base.resolve("real.txt");
		Files.createFile(existing);
		File resolved = WebAozoraConverter.safeResolve(base, "real.txt");
		assertEquals(existing.toRealPath().toFile(), resolved);
	}

	/** base 直下に戻る「..」は許可される（base 外に出ないため） */
	@Test
	public void allowsDotDotStayingInsideBase() throws IOException {
		File resolved = WebAozoraConverter.safeResolve(base, "sub/../ok.txt");
		assertTrue(resolved.toPath().startsWith(base.toRealPath()));
		assertEquals("ok.txt", resolved.getName());
	}

	/** base の親に出る「..」は IOException */
	@Test
	public void rejectsParentEscape() {
		assertThrows(IOException.class,
			() -> WebAozoraConverter.safeResolve(base, "../evil.txt"));
	}

	/** 中間に「..」を挟んで base 外に出るパスも IOException */
	@Test
	public void rejectsNestedParentEscape() {
		assertThrows(IOException.class,
			() -> WebAozoraConverter.safeResolve(base, "sub/../../evil.txt"));
	}

	/** 深い traversal（実攻撃相当）も IOException */
	@Test
	public void rejectsDeepTraversal() {
		assertThrows(IOException.class,
			() -> WebAozoraConverter.safeResolve(base, "host.example.com/../../../../Users/x/evil.html"));
	}

	/** 絶対パスを渡された場合は base 外に解決されるため IOException */
	@Test
	public void rejectsAbsolutePath() throws IOException {
		Path outside = tempFolder.newFolder("outside").toPath();
		assertThrows(IOException.class,
			() -> WebAozoraConverter.safeResolve(base, outside.resolve("evil.txt").toString()));
	}

	/**
	 * base 自体が symlink / junction 配下にある場合でも誤検知しない（正常系の保護）。
	 *
	 * base と candidate を別基準で正規化すると startsWith が常に false になり、
	 * 全章・全画像が「安全でないパス」で無条件スキップされて
	 * 中身の無い EPUB が「成功」として出来上がる回帰を防ぐ。
	 */
	@Test
	public void allowsBaseReachedThroughSymlink() throws IOException {
		Path realBase = tempFolder.newFolder("realbase").toPath();
		Path linkBase = tempFolder.getRoot().toPath().resolve("linkbase");
		assumeSymlinkSupported(linkBase, realBase);

		File resolved = WebAozoraConverter.safeResolve(linkBase, "ncode.syosetu.com/n9623lp/1/index.html");
		assertTrue(resolved.toPath().startsWith(realBase.toRealPath()));
		assertEquals("index.html", resolved.getName());
	}

	/**
	 * base 配下の中間ディレクトリが symlink で base 外を指す場合は IOException。
	 *
	 * 葉が存在しなくても、実在する最も近い祖先を toRealPath で解決することで
	 * 「書き込み時に symlink を辿って base 外に出る」経路を塞ぐ。
	 */
	@Test
	public void rejectsSymlinkedAncestorEscapingBase() throws IOException {
		Path outside = tempFolder.newFolder("outside").toPath();
		Path link = base.resolve("host.example.com");
		assumeSymlinkSupported(link, outside);

		assertThrows(IOException.class,
			() -> WebAozoraConverter.safeResolve(base, "host.example.com/new.html"));
	}

	/** ディレクトリへのリンクを作成する。作れない環境ではテストをスキップする。
	 * Windows では開発者モード / 管理者権限が無いと symlink を作成できないため、
	 * 権限不要の directory junction (mklink /J) にフォールバックする。 */
	private static void assumeSymlinkSupported(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return;
		} catch (IOException | UnsupportedOperationException e) {
			if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
				org.junit.Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
				return;
			}
		}
		try {
			Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
				.redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			org.junit.Assume.assumeTrue("junction を作成できない環境のためスキップ",
				p.waitFor() == 0 && Files.exists(link));
		} catch (IOException | InterruptedException e) {
			org.junit.Assume.assumeNoException("junction を作成できない環境のためスキップ", e);
		}
	}
}
