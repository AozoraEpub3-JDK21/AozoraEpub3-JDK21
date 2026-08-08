package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** EPUB の展開と Zip Slip 対策 */
public class EpubExtractorTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void extractsAllEntries() throws IOException
	{
		Path epub = EpubFixture.standard().writeTo(temp.getRoot().toPath().resolve("book.epub"));
		Path target = temp.getRoot().toPath().resolve("out");

		EpubExtractor.extract(epub, target);

		assertTrue(Files.isRegularFile(target.resolve("META-INF/container.xml")));
		assertTrue(Files.isRegularFile(target.resolve("OPS/package.opf")));
		assertTrue(Files.isRegularFile(target.resolve("OPS/xhtml/text00001.xhtml")));
		assertTrue(Files.isRegularFile(target.resolve("OPS/gaiji/u3042-u3099.ttf")));
		assertEquals("application/epub+zip",
			Files.readString(target.resolve("mimetype"), StandardCharsets.UTF_8));
	}

	@Test
	public void rejectsZipSlipEntry() throws IOException
	{
		Path epub = temp.getRoot().toPath().resolve("evil.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("mimetype"));
			zip.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("../escaped.txt"));
			zip.write("pwned".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path target = temp.getRoot().toPath().resolve("out");

		try {
			EpubExtractor.extract(epub, target);
			fail("Zip Slip エントリを受け入れてはならない");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("不正なエントリ"));
		}
		assertFalse(Files.exists(temp.getRoot().toPath().resolve("escaped.txt")));
	}

	/** 上限は注入して検証する。実際に数百MBを書くとユニットテストが遅くなりCIを圧迫するため */
	private Path zipWith(String name, int entries, int bytesEach) throws IOException
	{
		Path epub = temp.getRoot().toPath().resolve(name);
		byte[] payload = new byte[bytesEach];
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			for (int i = 0; i < entries; i++) {
				zip.putNextEntry(new ZipEntry("e" + i + ".bin"));
				zip.write(payload);
				zip.closeEntry();
			}
		}
		return epub;
	}

	@Test
	public void rejectsTooManyEntries() throws IOException
	{
		Path epub = zipWith("many.epub", 5, 1);
		try {
			EpubExtractor.extract(epub, temp.getRoot().toPath().resolve("many-out"), 1024, 1024, 3);
			fail("エントリ数の上限を超えたら拒否すること");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("エントリ数"));
		}
	}

	@Test
	public void rejectsOversizedEntry() throws IOException
	{
		Path epub = zipWith("big.epub", 1, 200);
		try {
			EpubExtractor.extract(epub, temp.getRoot().toPath().resolve("big-out"), 100, 10_000, 100);
			fail("1 エントリの展開上限を超えたら拒否すること");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("展開サイズ"));
		}
	}

	@Test
	public void rejectsOversizedTotalAcrossEntries() throws IOException
	{
		// 1 エントリずつは上限内でも、合計で超えたら拒否すること (totalSoFar の計上検証)
		Path epub = zipWith("total.epub", 5, 100);
		try {
			EpubExtractor.extract(epub, temp.getRoot().toPath().resolve("total-out"), 1000, 250, 100);
			fail("合計の展開上限を超えたら拒否すること");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("展開サイズ"));
		}
	}

	@Test
	public void doesNotLeavePartialFileWhenLimitTrips() throws IOException
	{
		// 内部バッファ (8192B) より大きいペイロードにして、
		// 上限に達する前に実際の書き込みが発生する状況を作る。
		// 小さすぎると 0 バイトファイルの削除しか検証できない
		Path epub = zipWith("partial.epub", 1, 20_000);
		Path target = temp.getRoot().toPath().resolve("partial-out");
		try {
			EpubExtractor.extract(epub, target, 10_000, 1_000_000, 100);
			fail("上限超過で失敗すること");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("展開サイズ"));
			assertFalse("書きかけのファイルを残してはならない",
				Files.exists(target.resolve("e0.bin")));
		}
	}

	@Test
	public void harmlessNoOpEntriesAreSkippedNotRejected() throws IOException
	{
		// 他ツール製の EPUB は "./" のような実体の無いエントリを持つことがある。
		// これで EPUB 全体を拒否すると外部の本が開けなくなる
		Path epub = temp.getRoot().toPath().resolve("dotslash.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("./"));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("mimetype"));
			zip.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path target = temp.getRoot().toPath().resolve("dotslash-out");

		EpubExtractor.extract(epub, target);

		assertTrue(Files.isRegularFile(target.resolve("mimetype")));
	}

	@Test
	public void rejectsAbsolutePathEscape() throws IOException
	{
		Path epub = temp.getRoot().toPath().resolve("evil2.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			// 先頭スラッシュはルート相対として扱い、その先の ".." で検知させる
			zip.putNextEntry(new ZipEntry("/OPS/../../escaped2.txt"));
			zip.write("pwned".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		try {
			EpubExtractor.extract(epub, temp.getRoot().toPath().resolve("out2"));
			fail("ルート外へ抜けるエントリを受け入れてはならない");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("不正なエントリ"));
		}
	}

	@Test
	public void rejectsDriveQualifiedEntryOnEveryPlatform() throws IOException
	{
		// 以前は Windows の Path#resolve が絶対パス扱いすることに頼っていたため、
		// Linux / macOS ではルート配下に収まってしまい拒否できなかった
		Path epub = temp.getRoot().toPath().resolve("drive.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("C:/Windows/win.ini"));
			zip.write("pwned".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		try {
			EpubExtractor.extract(epub, temp.getRoot().toPath().resolve("drive-out"));
			fail("ドライブ修飾のエントリを受け入れてはならない");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("不正なエントリ"));
		}
	}

	@Test
	public void nestedColonEntryDoesNotRejectTheWholeEpub() throws IOException
	{
		// 先頭以外の ":" はルート外に出られない。OCF 上は不正な名前だが、
		// これで EPUB 全体を拒否すると Linux で読めていた本が開けなくなる。
		// Windows では当該エントリのみ読み飛ばされる (どちらでも本体は開ける)
		Path epub = temp.getRoot().toPath().resolve("colon.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("OPS/xhtml/a:chapter.xhtml"));
			zip.write("<html/>".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("mimetype"));
			zip.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path target = temp.getRoot().toPath().resolve("colon-out");

		EpubExtractor.extract(epub, target);

		assertTrue(Files.isRegularFile(target.resolve("mimetype")));
	}

	@Test
	public void entryUnderUncreatableDirectoryDoesNotFailTheWholeEpub() throws IOException
	{
		// Windows では NUL が予約デバイス名で、createDirectories が例外を投げずに何も作らないため
		// 続く書き込みが NoSuchFileException になり展開全体が失敗していた。
		// Linux ではただのディレクトリ名として展開される。どちらでも本体は開けること
		Path epub = temp.getRoot().toPath().resolve("device.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("OPS/NUL/x.jpg"));
			zip.write("img".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("mimetype"));
			zip.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path target = temp.getRoot().toPath().resolve("device-out");

		EpubExtractor.extract(epub, target);

		assertTrue(Files.isRegularFile(target.resolve("mimetype")));
	}

	@Test
	public void collidingEntriesKeepTheFirstInsteadOfOverwriting() throws IOException
	{
		// 大文字小文字を区別しない FS (Windows / 既定の APFS) では両者が同一ファイルになる。
		// 従来は後勝ちで無警告に上書きされ「章の中身が別章」になっていた。
		// 区別する FS (Linux) では両方が残るので、先頭の中身が保たれることを表明する
		Path epub = temp.getRoot().toPath().resolve("case.epub");
		try (OutputStream out = Files.newOutputStream(epub);
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("OPS/Text.xhtml"));
			zip.write("A".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("OPS/text.xhtml"));
			zip.write("B".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path target = temp.getRoot().toPath().resolve("case-out");

		EpubExtractor.extract(epub, target);

		assertEquals("A", Files.readString(target.resolve("OPS/Text.xhtml"), StandardCharsets.UTF_8));
	}
}
