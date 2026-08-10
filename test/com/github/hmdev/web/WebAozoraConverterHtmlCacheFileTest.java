package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * WebAozoraConverter.resolveHtmlCacheFile のテスト（docs/code-audit-followups.md 項目 21）。
 *
 * カクヨムのように URL 末尾に「/」が無いサイトでは、一覧の保存先 {@code <workId>} と
 * 各話の保存先 {@code <workId>/episodes/<episodeId>} が同じ名前を取り合う。作品ディレクトリが
 * 先にできると一覧を同名ファイルとして書けず、例外は握られて古いキャッシュが使われ続けるため
 * 新着話を取り逃す。書き込み先を {@code <dir>/index.html} に寄せることで解消する。
 *
 * <p>本テストはヘルパー単体の契約を固定する（一覧取得〜キャッシュ書き込みの実経路は
 * ネットワーク依存のため対象外）。
 */
public class WebAozoraConverterHtmlCacheFileTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 同名ディレクトリが既にある場合は配下の index.html に寄せる（本題の再現ケース） */
	@Test
	public void redirectsToIndexHtmlWhenDirectoryExists() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		//各話キャッシュが先に作られ、作品ディレクトリ works/<workId> がディレクトリ化済みの状態
		Path workDir = cache.resolve("kakuyomu.jp/works/822139840468926025");
		Files.createDirectories(workDir);
		Files.write(workDir.resolve("16817330653873249224"), "episode".getBytes(StandardCharsets.UTF_8));

		File tocTarget = WebAozoraConverter.safeResolve(cache, "kakuyomu.jp/works/822139840468926025");
		File resolved = WebAozoraConverter.resolveHtmlCacheFile(tocTarget);

		assertEquals("index.html", resolved.getName());
		assertEquals(workDir.toRealPath().toFile(), resolved.getParentFile());
	}

	/** 寄せた先に実際に書き込めること（従来はここで AccessDeniedException になっていた） */
	@Test
	public void resolvedTargetIsWritable() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		Path workDir = cache.resolve("kakuyomu.jp/works/822139840468926025");
		Files.createDirectories(workDir);

		File resolved = WebAozoraConverter.resolveHtmlCacheFile(
			WebAozoraConverter.safeResolve(cache, "kakuyomu.jp/works/822139840468926025"));
		Files.write(resolved.toPath(), "<html>toc</html>".getBytes(StandardCharsets.UTF_8));

		assertTrue(resolved.isFile());
		assertEquals("<html>toc</html>",
			new String(Files.readAllBytes(resolved.toPath()), StandardCharsets.UTF_8));
	}

	/** 既存ファイル（ディレクトリ化していない通常のキャッシュ）はそのまま */
	@Test
	public void keepsPathWhenExistingFile() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		Path tocFile = cache.resolve("kakuyomu.jp/works/822139840468926025");
		Files.createDirectories(tocFile.getParent());
		Files.write(tocFile, "<html>toc</html>".getBytes(StandardCharsets.UTF_8));

		File resolved = WebAozoraConverter.resolveHtmlCacheFile(
			WebAozoraConverter.safeResolve(cache, "kakuyomu.jp/works/822139840468926025"));

		assertEquals(tocFile.toRealPath().toFile(), resolved);
	}

	/** 何も存在しないパスもそのまま（初回ダウンロード時） */
	@Test
	public void keepsPathWhenNothingExists() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();

		File target = WebAozoraConverter.safeResolve(cache, "ncode.syosetu.com/n9623lp/index.html");
		File resolved = WebAozoraConverter.resolveHtmlCacheFile(target);

		assertEquals(target, resolved);
	}

	/** ディレクトリ配下の index.html がキャッシュ外を指す symlink なら拒否する（書き込みの脱出防止） */
	@Test
	public void rejectsIndexHtmlSymlinkEscapingCacheRoot() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		Path outside = tempFolder.newFolder("outside").toPath();
		Path victim = outside.resolve("victim.html");
		Files.write(victim, "outside".getBytes(StandardCharsets.UTF_8));
		Path workDir = cache.resolve("kakuyomu.jp/works/1");
		Files.createDirectories(workDir);
		try {
			Files.createSymbolicLink(workDir.resolve("index.html"), victim);
		} catch (IOException | UnsupportedOperationException e) {
			//Windows は既定で symlink 作成に権限が要る。作れない環境ではスキップ
			Assume.assumeNoException("symlink を作成できない環境のためスキップ", e);
		}

		File tocTarget = WebAozoraConverter.safeResolve(cache, "kakuyomu.jp/works/1");
		try {
			WebAozoraConverter.resolveHtmlCacheFile(tocTarget);
			fail("キャッシュ外への symlink を許してはいけない");
		} catch (IOException expected) {
			/* 期待どおり: 安全でないパスとして拒否される */
		}
	}
}
