import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JTextArea;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.util.VelocityTestUtils;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 表題・著者の後に空行が無く、本文の先頭の行が表題ページに回って本文から落ちるときだけ警告し、直し方を添える（監査項目 35）。
 * 変換の中身は変えない（AozoraEpub3EmptyBodyTest と同じく convertFile を直接呼ぶ）
 */
public class AozoraEpub3TitleBlockWarningTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private static final String RUN_ON = "表題・著者の後に空行がありません";

	/** text を表題ページの種類 titlePage で変換し、ログを返す */
	private String convertLog(String text, int titlePage) throws Exception {
		File dir = tempFolder.newFolder();
		File txt = new File(dir, "in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File epub = new File(dir, "in.epub");
		String templatePath = VelocityTestUtils.templateDir() + File.separator;
		Epub3Writer writer = new Epub3Writer(templatePath, VelocityTestUtils.engineForTemplateSubpath(""));
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, VelocityTestUtils.templateDir().getParent() + File.separator);
		JTextArea log = new JTextArea();
		LogAppender.setTextArea(log);
		try {
			ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt);
			BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, imageInfoReader, converter, "UTF-8",
				BookInfo.TitleType.TITLE_AUTHOR, false);
			assertNotNull(bookInfo);
			bookInfo.vertical = true;
			bookInfo.titlePageType = titlePage;
			assertTrue(AozoraEpub3.convertFile(txt, "txt", epub, converter, writer, "UTF-8", bookInfo, imageInfoReader, 0));
		} finally {
			LogAppender.setTextArea(null);
		}
		return log.getText();
	}

	/** 本文が表題ページに回る（表題ページを別に作る種類）: 警告し、空行と -t 4 の直し方を添える */
	@Test
	public void warnsWhenBodyLinesGoToTheTitlePage() throws Exception {
		String log = convertLog("表題\n著者\n本文1\n本文2\n本文3\n本文4\n本文5\n本文6\n", BookInfo.TITLE_MIDDLE);
		assertTrue(log, log.contains(RUN_ON));
		assertTrue("直し方が無い:\n" + log, log.contains("空行を 1 行入れてください") && log.contains("-t 4"));
		assertTrue("1 回だけ:\n" + log, log.indexOf(RUN_ON) == log.lastIndexOf(RUN_ON));
	}

	/** 表題を本文に出す種類（通常）では本文から何も落ちないので、警告しない */
	@Test
	public void noWarningWhenTheTitleStaysInTheBody() throws Exception {
		String log = convertLog("表題\n著者\n本文1\n本文2\n本文3\n本文4\n本文5\n本文6\n", BookInfo.TITLE_NORMAL);
		assertFalse(log, log.contains(RUN_ON));
	}

	/** 本文が全部飲まれたときの「本文がありませんでした」にも、同じ直し方を添える */
	@Test
	public void theEmptyBodyWarningCarriesTheAdvice() throws Exception {
		String log = convertLog("表題\n著者\n本文1\n本文2\n", BookInfo.TITLE_MIDDLE);
		assertTrue(log, log.contains("本文がありませんでした"));
		assertTrue("直し方が無い:\n" + log, log.contains("空行を 1 行入れてください"));
	}

	/** 対照: 表題・著者の後に空行があれば警告しない */
	@Test
	public void noWarningWithABlankLine() throws Exception {
		String log = convertLog("表題\n著者\n\n本文1\n本文2\n本文3\n本文4\n本文5\n本文6\n", BookInfo.TITLE_MIDDLE);
		assertFalse(log, log.contains(RUN_ON));
		assertFalse(log, log.contains("本文がありませんでした"));
	}
}
