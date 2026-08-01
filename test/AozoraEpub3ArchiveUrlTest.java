import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CLI の -url にアーカイブ（zip / txtz / rar）URL を渡した場合のテスト（監査 #16）。
 *
 * 修正前は拡張子を見ずに常に WebAozoraConverter（HTML スクレイピング）へ渡していたため、
 * 青空文庫のテキスト zip URL を指定すると「タイトルがありません」で exit 1 になっていた。
 *
 * ネットワーク不要のテストは file: スキームの URL を使う（NetUtils は file: でも動作する）。
 * 実ネットワークを使う E2E は opt-in（-DarchiveUrlE2E=true）。
 */
public class AozoraEpub3ArchiveUrlTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** zip URL がスクレイピングではなくダウンロード経路に振り分けられ、
	 * ダウンロードしたアーカイブが変換対象（ローカルファイル変換経路）に積まれること。
	 *
	 * EPUB 生成そのものは Gradle のテスト JVM では template/ が解決できず失敗し得るため
	 * （AozoraEpub3ExitCodeTest 参照）、ここではダウンロードまでを検証する。
	 * 分岐が無いと WebAozoraConverter に渡って zip は一切ダウンロードされないので、
	 * この検証で分岐の有無を判定できる。 */
	@Test
	public void zipUrlIsDownloadedInsteadOfScraped() throws Exception {
		Path zip = createAozoraZip(tempFolder.newFile("1567_ruby_4948.zip").toPath());
		File outDir = tempFolder.newFolder("out");
		File cacheDir = tempFolder.newFolder("cache");

		AozoraEpub3.run(new String[]{
			"-url", zip.toUri().toString(),
			"-cache", cacheDir.getPath(),
			"-of", "-d", outDir.getPath()});

		assertTrue("zip がダウンロードされていない（アーカイブ分岐が働いていない）",
			Files.exists(outDir.toPath().resolve("1567_ruby_4948.zip")));
	}

	/** アーカイブでない URL は従来どおり Web 変換経路に渡り、
	 * 対応サイト定義が無ければ変換失敗として exit 1 になること（分岐の巻き添えがないこと） */
	@Test
	public void nonArchiveUrlStillGoesToWebConverter() throws Exception {
		File outDir = tempFolder.newFolder("out");
		File cacheDir = tempFolder.newFolder("cache");

		int exitCode = AozoraEpub3.run(new String[]{
			"-url", "http://127.0.0.1:1/not_supported_site/",
			"-cache", cacheDir.getPath(),
			"-of", "-d", outDir.getPath()});

		assertEquals(1, exitCode);
		assertEquals("Web 変換経路の URL でファイルがダウンロードされている",
			0, outDir.list().length);
	}

	/** ダウンロードに失敗した URL は errorCount に数えられ exit 1 になること */
	@Test
	public void failedArchiveDownloadReturnsNonZero() throws Exception {
		File outDir = tempFolder.newFolder("out");
		File cacheDir = tempFolder.newFolder("cache");
		String missingUrl = tempFolder.getRoot().toPath().resolve("no_such.zip").toUri().toString();

		int exitCode = AozoraEpub3.run(new String[]{
			"-url", missingUrl,
			"-cache", cacheDir.getPath(),
			"-of", "-d", outDir.getPath()});

		assertEquals(1, exitCode);
	}

	/** 実ネットワークでの E2E（opt-in）。
	 * 実行方法: gradlew jar test --tests AozoraEpub3ArchiveUrlTest -DarchiveUrlE2E=true */
	@Test
	public void aozoraZipUrlEndToEnd() throws Exception {
		Assume.assumeTrue("E2E テストをスキップ (有効化するには -DarchiveUrlE2E=true を指定)",
			"true".equalsIgnoreCase(System.getProperty("archiveUrlE2E")));
		File jarFile = new File("build/libs/AozoraEpub3.jar");
		Assume.assumeTrue("fat JAR が見つかりません。先に gradlew jar を実行してください。",
			jarFile.exists());
		String url = "https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip";
		Assume.assumeTrue("ネットワーク到達不可 (青空文庫への接続が必要)", isUrlAvailable(url));

		File outDir = tempFolder.newFolder("out");
		List<String> cmd = new ArrayList<>(List.of(
			System.getProperty("java.home") + "/bin/java",
			"-Dfile.encoding=UTF-8",
			"-jar", jarFile.getPath().replace('\\', '/'),
			"-url", url,
			"-of",
			"-d", outDir.getAbsolutePath()));
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(new File(".").getAbsoluteFile());
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		String output;
		try (InputStream is = proc.getInputStream()) {
			output = new String(is.readAllBytes(), Charset.defaultCharset());
		}
		int exitCode = proc.waitFor();
		System.out.println(output);

		assertEquals("zip URL の変換が失敗した", 0, exitCode);
		assertTrue("変換完了が出力されていない", output.contains("変換完了"));
		File[] epubs = outDir.listFiles((d, name) -> name.endsWith(".epub"));
		assertTrue("EPUB が生成されていない", epubs != null && epubs.length > 0);
	}

	// ================================================================
	// ユーティリティ
	// ================================================================

	/** 青空文庫テキスト zip 相当（Shift_JIS の txt を 1 件含む zip）を生成 */
	private Path createAozoraZip(Path zipPath) throws Exception {
		String text = "テスト表題\nテスト著者\n\n"
			+ "　これはアーカイブ URL 変換のテスト用本文です。\n";
		try (OutputStream os = Files.newOutputStream(zipPath);
			ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
			zos.putNextEntry(new ZipEntry("1567_ruby_4948.txt"));
			zos.write(text.getBytes(Charset.forName("MS932")));
			zos.closeEntry();
		}
		return zipPath;
	}

	/** URL に到達でき 2xx/3xx が返ること */
	private boolean isUrlAvailable(String urlStr) {
		try {
			URL url = new URI(urlStr).toURL();
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			conn.setRequestMethod("HEAD");
			return conn.getResponseCode() < 400;
		} catch (Exception e) {
			return false;
		}
	}
}
