import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 本文の行が 1 つも残らない入力でも、変換全体が落ちずに EPUB ができるテスト（監査 35・internal #15）。
 *
 * 表題の後に空行が無いと、先頭の数行が表題の塊として読まれる。短い本文が全部そこに飲み込まれると、
 * 本文の節が 1 度も開かれず、最後に節を閉じるところで「No current entry」になって EPUB が残らなかった
 * （空行なしで本文 0〜4 行。5 行以上なら通っていた）。
 *
 * AozoraEpub3.run はテスト JVM ではテンプレートの場所（jarPath）を見つけられないので、
 * テンプレートの場所と専用の VelocityEngine を渡した Epub3Writer で AozoraEpub3.convertFile を呼ぶ。
 */
public class AozoraEpub3EmptyBodyTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	/** プロジェクトの root（template/ がある所）を探す。見つからなければスキップ */
	private static Path projectRoot() {
		Path here = Paths.get(".").toAbsolutePath().normalize();
		while (here != null && !Files.isRegularFile(here.resolve("template/mimetype"))) here = here.getParent();
		Assume.assumeTrue("template/ が見つからない", here != null);
		return here;
	}

	private void convertsToEpub(String text) throws Exception {
		Path root = projectRoot();
		File txt = tempFolder.newFile("in.txt");
		Files.write(txt.toPath(), text.getBytes(StandardCharsets.UTF_8));
		File epub = new File(tempFolder.newFolder("out"), "in.epub");

		//テンプレートの仕組み（Velocity）は JVM で共有の静的な初期化が先に走った試験の設定のまま残るので、
		//この升専用のエンジンをテンプレートのフォルダに向けて渡す（試験の順番に左右されない）
		String templatePath = root.resolve("template") + File.separator;
		java.util.Properties p = new java.util.Properties();
		p.setProperty("resource.loaders", "file");
		p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		p.setProperty("resource.loader.file.path", templatePath);
		p.setProperty("resource.loader.file.cache", "false");
		org.apache.velocity.app.VelocityEngine engine = new org.apache.velocity.app.VelocityEngine(p);
		engine.init();
		Epub3Writer writer = new Epub3Writer(templatePath, engine);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, root + File.separator);
		ImageInfoReader imageInfoReader = new ImageInfoReader(true, txt);
		BookInfo bookInfo = AozoraEpub3.getBookInfo(txt, "txt", 0, imageInfoReader, converter, "UTF-8",
			BookInfo.TitleType.TITLE_AUTHOR, false);
		assertNotNull("表題・著者を読めない: " + text, bookInfo);
		//CLI の既定（AozoraEpub3.ini の TitlePage=2・縦書き）と同じく、表題の塊を表題ページに回して本文から抜く
		bookInfo.vertical = true;
		converter.vertical = true;
		bookInfo.titlePageType = BookInfo.TITLE_HORIZONTAL;

		boolean ok = AozoraEpub3.convertFile(txt, "txt", epub, converter, writer, "UTF-8", bookInfo, imageInfoReader, 0);
		assertTrue("変換が成功する: " + text, ok);
		assertTrue("EPUB ができる: " + text, epub.isFile());
		try (ZipFile zf = new ZipFile(epub)) {
			assertNotNull("本文の節がある: " + text, zf.getEntry("OPS/xhtml/0001.xhtml"));
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

	/** 対照: 表題の後に空行がある普通の入力 */
	@Test
	public void titleAuthorBlankAndBody() throws Exception {
		convertsToEpub("テスト\n著者\n\n本文1\n");
	}
}
