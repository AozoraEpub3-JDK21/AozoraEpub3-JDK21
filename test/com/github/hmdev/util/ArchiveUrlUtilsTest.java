package com.github.hmdev.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * アーカイブ URL の判定・ダウンロードのテスト（監査 #16）。
 *
 * 修正前は CLI の -url が拡張子を見ずに常に WebAozoraConverter（HTML スクレイピング）へ
 * 渡していたため、青空文庫のテキスト zip URL を指定すると「タイトルがありません」で失敗していた。
 */
public class ArchiveUrlUtilsTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	// ================================================================
	// 拡張子判定
	// ================================================================

	/** 青空文庫のテキスト zip（_ruby_ を含むパターンも含む） */
	@Test
	public void aozoraZipUrlIsArchive() {
		assertTrue(ArchiveUrlUtils.isArchiveUrl(
			"https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip"));
		assertTrue(ArchiveUrlUtils.isArchiveUrl(
			"https://www.aozora.gr.jp/cards/000035/files/1567_14913.zip"));
	}

	/** txtz / rar もアーカイブ扱い（GUI と同じ判定基準） */
	@Test
	public void txtzAndRarUrlsAreArchive() {
		assertTrue(ArchiveUrlUtils.isArchiveUrl("http://example.com/book.txtz"));
		assertTrue(ArchiveUrlUtils.isArchiveUrl("http://example.com/book.rar"));
	}

	/** 拡張子の大文字小文字は区別しない */
	@Test
	public void extensionIsCaseInsensitive() {
		assertTrue(ArchiveUrlUtils.isArchiveUrl("http://example.com/BOOK.ZIP"));
		assertTrue(ArchiveUrlUtils.isArchiveUrl("http://example.com/Book.TxtZ"));
	}

	/** 小説ページ等の HTML URL はアーカイブではない（従来どおり WebAozoraConverter へ） */
	@Test
	public void webNovelUrlsAreNotArchive() {
		assertFalse(ArchiveUrlUtils.isArchiveUrl("https://ncode.syosetu.com/n9623lp/"));
		assertFalse(ArchiveUrlUtils.isArchiveUrl("https://www.aozora.gr.jp/cards/000035/card1567.html"));
		assertFalse(ArchiveUrlUtils.isArchiveUrl("https://kakuyomu.jp/works/1177354054882154257"));
		assertFalse(ArchiveUrlUtils.isArchiveUrl("http://example.com/book.txt"));
	}

	/** 拡張子が無い URL / null でも例外にならない */
	@Test
	public void missingExtensionIsNotArchive() {
		assertFalse(ArchiveUrlUtils.isArchiveUrl("https://example.com/novel"));
		assertFalse(ArchiveUrlUtils.isArchiveUrl(""));
		assertFalse(ArchiveUrlUtils.isArchiveUrl(null));
	}

	/** 拡張子取得は小文字化されること */
	@Test
	public void urlExtensionIsLowerCased() {
		assertEquals("zip", ArchiveUrlUtils.urlExtension("http://example.com/a.ZIP"));
		assertEquals("", ArchiveUrlUtils.urlExtension(null));
	}

	// ================================================================
	// 保存先ファイル名の導出
	// ================================================================

	/** URL の末尾要素が出力先直下のファイル名になる */
	@Test
	public void dstFileNameComesFromUrlLastSegment() throws Exception {
		File dstPath = tempFolder.newFolder("out");
		File dstFile = ArchiveUrlUtils.getArchiveDstFile(
			"https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip", dstPath);
		assertEquals("1567_ruby_4948.zip", dstFile.getName());
		assertEquals(dstPath.getCanonicalPath(), dstFile.getCanonicalFile().getParent());
	}

	/** ファイル名に使えない文字は '_' に置換される（監査 #14 の replaceInvalidFileChars） */
	@Test
	public void invalidFileCharsAreReplaced() throws Exception {
		File dstPath = tempFolder.newFolder("out");
		File dstFile = ArchiveUrlUtils.getArchiveDstFile(
			"http://example.com/dl/a\"b<c>d.zip", dstPath);
		assertEquals("a_b_c_d.zip", dstFile.getName());
	}

	// ================================================================
	// ダウンロード
	// ================================================================

	/** file: スキームでダウンロードできること（NetUtils 経由なのでタイムアウトが効く） */
	@Test
	public void downloadsArchiveToDstPath() throws Exception {
		byte[] content = "青空文庫テキストのダミー".getBytes(StandardCharsets.UTF_8);
		Path srcZip = tempFolder.newFile("1567_ruby_4948.zip").toPath();
		Files.write(srcZip, content);
		File dstPath = tempFolder.newFolder("out");

		File downloaded = ArchiveUrlUtils.downloadArchive(srcZip.toUri().toString(), dstPath);

		assertEquals("1567_ruby_4948.zip", downloaded.getName());
		assertTrue(downloaded.exists());
		assertArrayEquals(content, Files.readAllBytes(downloaded.toPath()));
	}

	/** 既にダウンロード済みのファイルがある場合は上書きする（GUI と同じ挙動） */
	@Test
	public void existingFileIsOverwritten() throws Exception {
		byte[] content = "新しい内容".getBytes(StandardCharsets.UTF_8);
		Path srcZip = tempFolder.newFile("book.zip").toPath();
		Files.write(srcZip, content);
		File dstPath = tempFolder.newFolder("out");
		Path stale = dstPath.toPath().resolve("book.zip");
		Files.write(stale, "古い内容が残っていた古い内容が残っていた".getBytes(StandardCharsets.UTF_8));

		ArchiveUrlUtils.downloadArchive(srcZip.toUri().toString(), dstPath);

		assertArrayEquals(content, Files.readAllBytes(stale));
	}

	/** 転送が途中で切れた場合は例外を投げ、途中まで書かれたファイルを削除すること。
	 * 壊れた zip が残ると次回以降「読み込めません」になるため（監査 #5 の対応） */
	@Test
	public void interruptedDownloadDeletesPartialFile() throws Exception {
		File dstPath = tempFolder.newFolder("out");
		try (ServerSocket server = new ServerSocket(0, 1)) {
			Thread responder = new Thread(() -> {
				try (Socket socket = server.accept()) {
					//リクエストヘッダを読み捨てる
					InputStream is = socket.getInputStream();
					int prev = -1, c;
					while ((c = is.read()) != -1) {
						if (prev == '\n' && c == '\n') break;
						if (c != '\r') prev = c;
					}
					OutputStream os = socket.getOutputStream();
					os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\n"
						+ "Content-Length: 100000\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
					os.write(new byte[1000]);
					os.flush();
					//RST で切断して転送を中断させる
					socket.setSoLinger(true, 0);
				} catch (Exception e) { /* テスト用スタブなので握り潰す */ }
			});
			responder.setDaemon(true);
			responder.start();

			String url = "http://127.0.0.1:" + server.getLocalPort() + "/book.zip";
			assertThrows(IOException.class, () -> ArchiveUrlUtils.downloadArchive(url, dstPath));

			assertFalse("転送が中断したのに途中のファイルが残っている",
				Files.exists(dstPath.toPath().resolve("book.zip")));
		}
	}

	/** 取得に失敗した場合は例外を投げ、中途半端なファイルを残さない */
	@Test
	public void failedDownloadLeavesNoFile() throws Exception {
		File dstPath = tempFolder.newFolder("out");
		String missingUrl = tempFolder.getRoot().toPath().resolve("no_such.zip").toUri().toString();

		assertThrows(IOException.class, () -> ArchiveUrlUtils.downloadArchive(missingUrl, dstPath));

		assertFalse("失敗したのにファイルが残っている",
			Files.exists(dstPath.toPath().resolve("no_such.zip")));
	}
}
