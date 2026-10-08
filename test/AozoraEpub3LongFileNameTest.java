import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 題の長い本の EPUB の名前が、名前 1 つ 255 バイトの上限（Linux の ext4 など）に収まることのテスト（internal #16）。
 *
 * 日本語は UTF-8 で 1 文字 3 バイトなので、85 文字前後で上限を超える。
 * mac・Windows は文字数で数えるので、超えても作れてしまう。
 *
 * AozoraEpub3.run はテスト JVM ではテンプレートの場所を見つけられないので、
 * テンプレートの場所と専用の VelocityEngine を渡した Epub3Writer で AozoraEpub3.convertFile を呼ぶ（AozoraEpub3EmptyBodyTest と同じ）。
 */
public class AozoraEpub3LongFileNameTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 111 文字・315 バイトの名前になった実物（なろう n9623lp）と同じくらいの長さ */
	private static final String LONG_TITLE = "【書籍化】" + "長い題".repeat(35);

	private File outFile(String text) throws Exception {
		File dir = tempFolder.newFolder();
		File txt = new File(dir, "in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		Epub3Writer writer = writer();
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, new ImageInfoReader(true, txt), converter, "UTF-8",
			BookInfo.TitleType.TITLE_AUTHOR, false);
		assertNotNull(bookInfo);
		return AozoraEpub3.getOutFile(txt, tempFolder.newFolder(), bookInfo, true, ".epub");
	}

	private static Epub3Writer writer() throws Exception {
		return new Epub3Writer(VelocityTestUtils.templateDir() + File.separator, VelocityTestUtils.engineForTemplateSubpath(""));
	}

	@Test
	public void theNameOfALongTitleFitsIn255Bytes() throws Exception {
		File epub = outFile(LONG_TITLE + "\n著者\n\n本文\n");
		int bytes = epub.getName().getBytes(StandardCharsets.UTF_8).length;
		assertTrue("名前は 255 バイト以内: " + bytes + " バイト " + epub.getName(), bytes <= 255);
		assertTrue(epub.getName(), epub.getName().startsWith("[著者] 【書籍化】長い題"));
		assertTrue(epub.getName(), epub.getName().endsWith(".epub"));
	}

	@Test
	public void aLongTitleConverts() throws Exception {
		File dir = tempFolder.newFolder();
		File txt = new File(dir, "in.txt");
		Files.write(txt.toPath(), (LONG_TITLE + "\n著者\n\n本文\n").getBytes(StandardCharsets.UTF_8));
		Epub3Writer writer = writer();
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt);
		BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, imageInfoReader, converter, "UTF-8",
			BookInfo.TitleType.TITLE_AUTHOR, false);
		File epub = AozoraEpub3.getOutFile(txt, tempFolder.newFolder(), bookInfo, true, ".epub");
		assertTrue("変換が成功する: " + epub.getName(),
			AozoraEpub3.convertFile(txt, "txt", epub, converter, writer, "UTF-8", bookInfo, imageInfoReader, 0));
		assertTrue(epub.isFile());
	}
}
