package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * WebAozoraConverter.resolveHtmlCacheFile のテスト（docs/code-audit-followups.md 項目 21）。
 *
 * カクヨムのように URL 末尾に「/」が無いサイトでは、一覧の保存先 {@code <workId>} と
 * 各話の保存先 {@code <workId>/<episodeId>} が同じ名前を取り合う。各話ディレクトリが先にできると
 * 一覧を同名ファイルとして書けず、例外は握られて古いキャッシュが使われ続けるため
 * 新着話を取り逃す。書き込み先を {@code <dir>/index.html} に寄せることで解消する。
 */
public class WebAozoraConverterHtmlCacheFileTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 同名ディレクトリが既にある場合は配下の index.html に寄せる（本題の再現ケース） */
	@Test
	public void redirectsToIndexHtmlWhenDirectoryExists() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		//各話キャッシュが先に作られた状態: works/822139840468926025/16817330653873249224
		Path episodeDir = cache.resolve("kakuyomu.jp/works/822139840468926025");
		Files.createDirectories(episodeDir);
		Files.write(episodeDir.resolve("16817330653873249224"), "episode".getBytes(StandardCharsets.UTF_8));

		File tocTarget = WebAozoraConverter.safeResolve(cache, "kakuyomu.jp/works/822139840468926025");
		File resolved = WebAozoraConverter.resolveHtmlCacheFile(tocTarget);

		assertEquals("index.html", resolved.getName());
		assertEquals(episodeDir.toRealPath().toFile(), resolved.getParentFile());
	}

	/** 寄せた先に実際に書き込めること（従来はここで AccessDeniedException になっていた） */
	@Test
	public void resolvedTargetIsWritable() throws IOException {
		Path cache = tempFolder.newFolder("cache").toPath();
		Path episodeDir = cache.resolve("kakuyomu.jp/works/822139840468926025");
		Files.createDirectories(episodeDir);

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
}
