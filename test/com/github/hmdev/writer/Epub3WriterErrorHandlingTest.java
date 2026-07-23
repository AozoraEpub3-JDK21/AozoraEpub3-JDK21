package com.github.hmdev.writer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.info.SectionInfo;

/**
 * Epub3Writer.write() のエラー伝播・リソースクローズのテスト（監査 #2 / #3）。
 *
 * 修正前は write() が全例外を握り潰していたため、出力が途中失敗しても
 * 呼び出し側が「変換完了」と報告し、壊れた EPUB が成功として残っていた。
 * また入力ストリームは成功経路でしか閉じられていなかった。
 */
public class Epub3WriterErrorHandlingTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Path projectRoot;
	private String templatePath;
	private VelocityEngine velocityEngine;
	private Path txt;

	@Before
	public void setUp() throws Exception {
		projectRoot = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(projectRoot.resolve("template"))) {
			Path testClasses = Paths.get(
				Epub3WriterErrorHandlingTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			projectRoot = testClasses.getParent().getParent().getParent();
		}
		Path templateRoot = projectRoot.resolve("template");

		Properties vp = new Properties();
		vp.setProperty("resource.loaders", "file");
		vp.setProperty("resource.loader.file.class",
			"org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		vp.setProperty("resource.loader.file.path", templateRoot.toString());
		velocityEngine = new VelocityEngine(vp);

		templatePath = templateRoot.toString();
		if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) templatePath += "/";

		txt = tempFolder.newFile("sample.txt").toPath();
		Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));
	}

	/** 出力途中で例外が起きた場合、write() が例外を投げ、壊れた EPUB を残さない */
	@Test
	public void throwsAndDeletesPartialEpubOnFailure() throws Exception {
		FailingWriter writer = newWriter(new FailingWriter(templatePath, velocityEngine));
		File epub = new File(tempFolder.getRoot(), "fail.epub");
		try (BufferedReader src = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
			assertThrows(IOException.class, () -> write(writer, src, epub));
		}
		assertFalse("出力途中の EPUB が残っている", epub.exists());
	}

	/** 出力途中で例外が起きても入力ストリームは閉じられる（Windows のファイルロック対策） */
	@Test
	public void closesSrcOnFailure() throws Exception {
		FailingWriter writer = newWriter(new FailingWriter(templatePath, velocityEngine));
		File epub = new File(tempFolder.getRoot(), "fail2.epub");
		CloseTrackingReader src = new CloseTrackingReader(Files.newBufferedReader(txt, StandardCharsets.UTF_8));
		assertThrows(IOException.class, () -> write(writer, src, epub));
		assertTrue("入力ストリームが閉じられていない", src.closed);
	}

	/** キャンセル時は例外を投げずに戻るが、入力は閉じ、出力途中の EPUB は削除する */
	@Test
	public void closesSrcAndDeletesPartialEpubOnCancel() throws Exception {
		CancelingWriter writer = newWriter(new CancelingWriter(templatePath, velocityEngine));
		File epub = new File(tempFolder.getRoot(), "canceled.epub");
		CloseTrackingReader src = new CloseTrackingReader(Files.newBufferedReader(txt, StandardCharsets.UTF_8));
		write(writer, src, epub);
		assertTrue("入力ストリームが閉じられていない", src.closed);
		assertFalse("キャンセル時に出力途中の EPUB が残っている", epub.exists());
	}

	/** 正常時は EPUB が残り、入力も閉じられる（上記 3 件が誤検知でないことの確認） */
	@Test
	public void keepsEpubAndClosesSrcOnSuccess() throws Exception {
		Epub3Writer writer = newWriter(new Epub3Writer(templatePath, velocityEngine));
		File epub = new File(tempFolder.getRoot(), "ok.epub");
		CloseTrackingReader src = new CloseTrackingReader(Files.newBufferedReader(txt, StandardCharsets.UTF_8));
		write(writer, src, epub);
		assertTrue("正常時に EPUB が出力されていない", epub.exists());
		assertTrue("入力ストリームが閉じられていない", src.closed);
	}

	////////////////////////////////////////////////////////////////

	private void write(Epub3Writer writer, Reader src, File epub) throws Exception {
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString()+"/");
		BookInfo bookInfo = new BookInfo(txt.toFile());
		bookInfo.vertical = true;
		BufferedReader br = (src instanceof BufferedReader) ? (BufferedReader)src : new BufferedReader(src);
		writer.write(converter, br, txt.toFile(), "txt", epub, bookInfo, new ImageInfoReader(true, txt.toFile()));
	}

	/** EpubMimetypeTest と同じ最小構成 */
	private <T extends Epub3Writer> T newWriter(T writer) {
		writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600,
			SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, false, 0,
			1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
		writer.setTocParam(false, false);
		writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);
		return writer;
	}

	/** 本文出力の途中で失敗する Writer（ディスクフル・テンプレートエラー等の代役） */
	static class FailingWriter extends Epub3Writer {
		FailingWriter(String templatePath, VelocityEngine ve) { super(templatePath, ve); }
		@Override
		void writeSections(AozoraEpub3Converter converter, BufferedReader src, BufferedWriter bw,
				File srcFile, String srcExt, ZipArchiveOutputStream zos) throws Exception {
			throw new IOException("テスト用の出力失敗");
		}
	}

	/** 本文出力の途中でキャンセルされる Writer */
	static class CancelingWriter extends Epub3Writer {
		CancelingWriter(String templatePath, VelocityEngine ve) { super(templatePath, ve); }
		@Override
		void writeSections(AozoraEpub3Converter converter, BufferedReader src, BufferedWriter bw,
				File srcFile, String srcExt, ZipArchiveOutputStream zos) throws Exception {
			//write() 冒頭で canceled がリセットされるため、変換中に立てる必要がある
			cancel();
		}
	}

	/** close されたことを記録する Reader */
	static class CloseTrackingReader extends BufferedReader {
		boolean closed = false;
		CloseTrackingReader(Reader in) { super(in); }
		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
