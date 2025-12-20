package com.github.hmdev.io;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.info.BookInfo;

public class OutputNamerTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void autoFileNameUsesCreatorAndTitle() throws Exception {
		File src = temporaryFolder.newFile("sample.txt");
		File dst = temporaryFolder.newFolder("out");
		BookInfo bookInfo = new BookInfo(src);
		bookInfo.creator = "Author";
		bookInfo.title = "My Book";

		File result = OutputNamer.generateOutFile(src, dst, bookInfo, true, ".epub");

		File expected = new File(dst.getAbsolutePath() + "/[Author] My Book.epub");
		assertEquals(expected.getAbsolutePath(), result.getAbsolutePath());
	}

	@Test
	public void autoFileNameSanitizesInvalidCharacters() throws Exception {
		File src = temporaryFolder.newFile("sample.txt");
		File dst = temporaryFolder.newFolder("out2");
		BookInfo bookInfo = new BookInfo(src);
		bookInfo.creator = "Au:th?or";
		bookInfo.title = "Tit*le?";

		File result = OutputNamer.generateOutFile(src, dst, bookInfo, true, ".epub");

		File expected = new File(dst.getAbsolutePath() + "/[Author] Title.epub");
		assertEquals(expected.getAbsolutePath(), result.getAbsolutePath());
	}

	@Test
	public void nonAutoFileNameUsesSourceBaseNameAndCustomExt() throws Exception {
		File src = temporaryFolder.newFile("sample.txt");
		File dst = temporaryFolder.newFolder("out3");
		BookInfo bookInfo = new BookInfo(src);
		bookInfo.creator = "Author";
		bookInfo.title = "My Book";

		File result = OutputNamer.generateOutFile(src, dst, bookInfo, false, ".kepub.epub");

		File expected = new File(dst.getAbsolutePath() + "/sample.kepub.epub");
		assertEquals(expected.getAbsolutePath(), result.getAbsolutePath());
	}

	@Test
	public void emptyExtensionDefaultsToEpub() throws Exception {
		File src = temporaryFolder.newFile("sample.txt");
		File dst = temporaryFolder.newFolder("out4");
		BookInfo bookInfo = new BookInfo(src);
		bookInfo.title = "Title";

		File result = OutputNamer.generateOutFile(src, dst, bookInfo, true, "");

		File expected = new File(dst.getAbsolutePath() + "/Title.epub");
		assertEquals(expected.getAbsolutePath(), result.getAbsolutePath());
	}
}
