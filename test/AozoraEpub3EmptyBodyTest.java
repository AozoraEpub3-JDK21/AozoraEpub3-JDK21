import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 本文の行が 1 つも残らない入力でも、変換全体が落ちずに EPUB ができるテスト（監査 35・internal #15）。
 *
 * 表題の後に空行が無いと、先頭の数行が表題の塊として読まれる。短い本文が全部そこに飲み込まれると、
 * 本文の節が 1 度も開かれず、最後に節を閉じるところで「No current entry」になって EPUB が残らなかった
 * （空行なしで本文 0〜4 行、空のファイル。5 行以上なら通っていた）。
 *
 * AozoraEpub3.run はテスト JVM ではテンプレートの場所（jarPath）を見つけられないので、
 * テンプレートの場所と専用の VelocityEngine（JVM で共有の静的な Velocity は先に走った試験の設定が残る）を
 * 渡した Epub3Writer で AozoraEpub3.convertFile を呼ぶ。
 */
public class AozoraEpub3EmptyBodyTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** 表題ページの種類ごと（CLI の既定は AozoraEpub3.ini の TitlePage=2 ＝横書き） */
	private static final int[] TITLE_PAGES = {
		BookInfo.TITLE_NONE, BookInfo.TITLE_NORMAL, BookInfo.TITLE_MIDDLE, BookInfo.TITLE_HORIZONTAL,
	};

	private void convertsToEpub(String text) throws Exception {
		for (int titlePage : TITLE_PAGES) {
			File dir = tempFolder.newFolder();
			File txt = new File(dir, "in.txt");
			Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
			File epub = new File(dir, "in.epub");
			String where = "TitlePage=" + titlePage + " : " + text;

			String templatePath = VelocityTestUtils.templateDir() + File.separator;
			Epub3Writer writer = new Epub3Writer(templatePath, VelocityTestUtils.engineForTemplateSubpath(""));
			AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
			ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt);
			BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, imageInfoReader, converter, "UTF-8",
				BookInfo.TitleType.TITLE_AUTHOR, false);
			assertNotNull("表題・著者を読めない: " + where, bookInfo);
			bookInfo.vertical = true;
			bookInfo.titlePageType = titlePage;

			boolean ok = AozoraEpub3.convertFile(txt, "txt", epub, converter, writer, "UTF-8", bookInfo, imageInfoReader, 0);
			assertTrue("変換が成功する: " + where, ok);
			assertTrue("EPUB ができる: " + where, epub.isFile());
			try (ZipFile zf = new ZipFile(epub)) {
				assertNotNull("本文の節がある: " + where, zf.getEntry("OPS/xhtml/0001.xhtml"));
			}
		}
	}

	@Test
	public void noBlankLineAndNoBody() throws Exception {
		convertsToEpub("テスト\n著者\n");
	}

	@Test
	public void noBlankLineAndShortBody() throws Exception {
		convertsToEpub("テスト\n著者\n本文1\n本文2\n本文3\n本文4\n");
	}

	@Test
	public void titleBlankAndOneLine() throws Exception {
		// internal #15 の入力（表題・空行・1 行）
		convertsToEpub("テスト\n\nあいう\n");
	}

	@Test
	public void emptyFile() throws Exception {
		convertsToEpub("");
	}

	/** 対照: 表題の後に空行がある普通の入力 */
	@Test
	public void titleAuthorBlankAndBody() throws Exception {
		convertsToEpub("テスト\n著者\n\n本文1\n");
	}
}
