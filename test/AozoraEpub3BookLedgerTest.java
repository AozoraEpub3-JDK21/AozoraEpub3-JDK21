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

	private File txt(File dir, String text) throws Exception {
		File txt = new File(dir, "in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return txt;
	}

	@Test
	public void theLedgerDecidesIdentifierSourceAndFileName() throws Exception {
		File dir = tempFolder.newFolder();
		// 掲載先で題が変わった後の txt。台帳には最初の名前が残っている
		File txt = txt(dir, "【書籍化】題\n著者\n\n本文\n");
		BookLedger.create(URL, "[著者] 題").save(dir);

		String[] r = convert(txt);
		assertEquals("[著者] 題.epub", r[0]);
		String opf = r[1];
		assertTrue(opf, opf.contains("urn:uuid:" + BookLedger.identifierFor(URL)));
		assertTrue("& はエスケープして dc:source に書く: " + opf,
			opf.contains("<dc:source>https://example.com/novel?id=1&amp;p=2</dc:source>"));
		assertTrue("題は新しいもの: " + opf, opf.contains("<dc:title id=\"title\">【書籍化】題</dc:title>"));
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
			"sourceUrl=javascript:alert(1)\noutputBaseName=x\n".getBytes(StandardCharsets.UTF_8));

		String opf = convert(txt)[1];
		assertFalse(opf, opf.contains("dc:source"));
	}
}
