import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.BookLedger;
import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * txt の隣に作品の台帳があると、EPUB の identifier・dc:source・自動のファイル名が台帳から決まることのテスト（internal #11）。
 * 台帳の無い txt（Web から取ったものでない本）は今までどおり題と作者から決まる。
 *
 * AozoraEpub3.run はテスト JVM ではテンプレートの場所を見つけられないので、
 * テンプレートの場所と専用の VelocityEngine を渡した Epub3Writer で AozoraEpub3.convertFile を呼ぶ（AozoraEpub3EmptyBodyTest と同じ）。
 */
public class AozoraEpub3BookLedgerTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String URL = "https://example.com/novel?id=1&p=2";

	/** 変換して OPF を返す。outFile は自動のファイル名で決める */
	private String[] convert(File txt) throws Exception {
		String templatePath = VelocityTestUtils.templateDir() + File.separator;
		Epub3Writer writer = new Epub3Writer(templatePath, VelocityTestUtils.engineForTemplateSubpath(""));
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt);
		BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, imageInfoReader, converter, "UTF-8",
			BookInfo.TitleType.TITLE_AUTHOR, false);
		assertNotNull(bookInfo);
		File out = tempFolder.newFolder();
		File epub = AozoraEpub3.getOutFile(txt, out, bookInfo, true, ".epub");
		assertTrue(AozoraEpub3.convertFile(txt, "txt", epub, converter, writer, "UTF-8", bookInfo, imageInfoReader, 0));
		try (ZipFile zf = new ZipFile(epub)) {
			String opf = new String(zf.getInputStream(zf.getEntry("OPS/package.opf")).readAllBytes(), StandardCharsets.UTF_8);
			return new String[]{ epub.getName(), opf };
		}
	}

	private File txt(File dir, String name, String text) throws Exception {
		File txt = new File(dir, name);
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	private File txt(File dir, String text) throws Exception {
		return txt(dir, "in.txt", text);
	}

	@Test
	public void theLedgerDecidesIdentifierSourceAndFileName() throws Exception {
		File dir = tempFolder.newFolder();
		// 掲載先で題が変わった後の txt。台帳には最初の EPUB の名前が残っている
		File txt = txt(dir, "【書籍化】題\n著者\n\n本文\n");
		BookLedger.create(URL, "in").withOutputBaseName("[著者] 題").save(dir);

		String[] r = convert(txt);
		assertEquals("[著者] 題.epub", r[0]);
		String opf = r[1];
		assertTrue(opf, opf.contains("urn:uuid:" + BookLedger.identifierFor(URL)));
		assertTrue("& はエスケープして dc:source に書く: " + opf,
			opf.contains("<dc:source>https://example.com/novel?id=1&amp;p=2</dc:source>"));
		assertTrue("題は新しいもの: " + opf, opf.contains("<dc:title id=\"title\">【書籍化】題</dc:title>"));
	}

	/**
	 * 台帳より前に変換した本は、最初の変換で今までと同じ名前になり、その名前を記録する（ゲート2の指摘）。
	 * Web の段の生の題・著者から名前を作ると、著者の ! やシリーズの行で名前が変わり、同じ本が 2 冊になっていた
	 */
	@Test
	public void theFirstConversionKeepsTheNameBooksHadBeforeTheLedger() throws Exception {
		File dir = tempFolder.newFolder();
		File txt = txt(dir, "題\n作者!\n\n本文\n");
		BookLedger.create(URL, "in").save(dir);

		String before = convertWithoutLedger("題\n作者!\n\n本文\n")[0];
		assertEquals("[作者!] 題.epub", before);
		assertEquals("台帳より前と同じ名前", before, convert(txt)[0]);
		assertEquals("最初の名前を記録する", "[作者!] 題", BookLedger.load(dir).outputBaseName);

		// 題が変わっても、記録した名前のまま
		txt(dir, "【書籍化】題\n作者!\n\n本文\n");
		assertEquals("[作者!] 題.epub", convert(txt)[0]);
	}

	@Test
	public void aStaleTxtBesideTheLedgerIsLeftAlone() throws Exception {
		File dir = tempFolder.newFolder();
		// 台帳が作る txt は「[著者] 新題.txt」。古い題の txt が残っている
		BookLedger.create(URL, "[著者] 新題").withOutputBaseName("[著者] 新題").save(dir);
		File stale = txt(dir, "[著者] 旧題.txt", "旧題\n著者\n\n本文\n");

		String[] r = convert(stale);
		assertEquals("[著者] 旧題.epub", r[0]);
		assertTrue(r[1], r[1].contains("urn:uuid:" + UUID.nameUUIDFromBytes("旧題-著者".getBytes())));
		assertFalse(r[1], r[1].contains("dc:source"));
	}

	private String[] convertWithoutLedger(String text) throws Exception {
		return convert(txt(tempFolder.newFolder(), text));
	}

	@Test
	public void withoutALedgerNothingChanges() throws Exception {
		File dir = tempFolder.newFolder();
		File txt = txt(dir, "題\n著者\n\n本文\n");

		String[] r = convert(txt);
		assertEquals("[著者] 題.epub", r[0]);
		String opf = r[1];
		assertTrue(opf, opf.contains("urn:uuid:" + UUID.nameUUIDFromBytes("題-著者".getBytes())));
		assertFalse(opf, opf.contains("dc:source"));
	}

	@Test
	public void aNonHttpSourceIsNotWritten() throws Exception {
		File dir = tempFolder.newFolder();
		File txt = txt(dir, "題\n著者\n\n本文\n");
		Files.write(new File(dir, BookLedger.FILE_NAME).toPath(),
			"sourceUrl=javascript:alert(1)\ntextBaseName=in\n".getBytes(StandardCharsets.UTF_8));

		String opf = convert(txt)[1];
		assertFalse(opf, opf.contains("dc:source"));
	}

	/**
	 * 長い題の作品の txt は、長い名前を作れない場所（Linux）では切った名前で作られる（internal #16）。
	 * 台帳はその txt にも当たる。当たらないと、Linux では長い題の作品の identifier と名前が固定されない
	 */
	@Test
	public void theLedgerFindsATxtWhoseLongNameWasCut() throws Exception {
		File dir = tempFolder.newFolder();
		String longName = "[著者] " + "長い題".repeat(40);
		String cutName = com.github.hmdev.util.PathUtils.fitFileName(longName, ".txt") + ".txt";
		File txt = txt(dir, cutName, "長い題\n著者\n\n本文\n");
		BookLedger.create(URL, longName).save(dir);

		String opf = convert(txt)[1];
		assertTrue(opf, opf.contains("urn:uuid:" + BookLedger.identifierFor(URL)));

		// 143 バイト（eCryptfs）で切った名前も
		File dir2 = tempFolder.newFolder();
		File txt2 = txt(dir2, com.github.hmdev.util.PathUtils.fitFileName(longName, ".txt", 143) + ".txt", "長い題\n著者\n\n本文\n");
		BookLedger.create(URL, longName).save(dir2);
		assertTrue(convert(txt2)[1].contains("urn:uuid:" + BookLedger.identifierFor(URL)));
	}

	/** 変換した本を本棚が「Web から取った本」と読める（書く側の identifier の形と、本棚の見分け方が合っている） */
	@Test
	public void theBookshelfRecognisesABookThisAppBuiltFromTheWeb() throws Exception
	{
		File dir = tempFolder.newFolder();
		File txt = txt(dir, "題\n著者\n\n本文\n");
		BookLedger.create(URL, "in").save(dir);
		String epubName = convert(txt)[0];

		java.util.List<com.github.hmdev.preview.LibraryEntry> entries = new java.util.ArrayList<>();
		for (File out : tempFolder.getRoot().listFiles(File::isDirectory)) {
			entries.addAll(com.github.hmdev.preview.LibraryScanner.scan(out.toPath(), 1, null));
		}
		com.github.hmdev.preview.LibraryEntry book = entries.stream()
			.filter(e -> e.file().getFileName().toString().equals(epubName)).findFirst().orElseThrow();
		assertEquals(URL, book.source());
	}
}
