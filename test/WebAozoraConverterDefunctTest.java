import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

import com.github.hmdev.web.WebAozoraConverter;

/**
 * WebAozoraConverter - サービス終了サイト (DEFUNCT マーカー) のテスト
 *
 * extract.txt に DEFUNCT が定義されたサイトは、ネットワークアクセスせずに
 * 変換を中断してメッセージを表示する (docs/web-site-support-status.md 残件 2)。
 * DEFUNCT チェックは convertToAozoraText の先頭 (URL 補正の HTTP アクセスより前)
 * にあるため、このテストは消滅済みドメインを指定してもネットワークに出ない。
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterDefunctTest
 */
public class WebAozoraConverterDefunctTest {

	private static final File WEB_CONFIG = new File("web");
	private static final File CACHE = new File("build/defunct_test_cache");

	private File convert(String url) throws Exception {
		WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(url, WEB_CONFIG);
		assertNotNull("extract.txt があるサイトなので converter は生成されること", converter);
		return converter.convertToAozoraText(url, CACHE, 1000, 0, false, false, false, 0);
	}

	@Test
	public void testDnovelsIsDefunct() throws Exception {
		assertNull("閉鎖済みサイトは変換せず null を返すこと",
			convert("http://www.dnovels.net/novel/12345/"));
	}

	@Test
	public void testMaiNetIsDefunct() throws Exception {
		assertNull("閉鎖済みサイトは変換せず null を返すこと",
			convert("http://www.mai-net.net/bbs/sst/sst.php?act=dump&cate=all&all=4600"));
	}

	@Test
	public void testNewvelIsDefunct() throws Exception {
		assertNull("閉鎖済みサイトは変換せず null を返すこと",
			convert("http://www.newvel.jp/library/12345/"));
	}

	/**
	 * web/ 全サイトを列挙し、DEFUNCT が定義されているのは既知の消滅 3 サイトだけであること。
	 * サイトを追加したときも自動でこのチェックの対象になる
	 */
	@Test
	public void testOnlyKnownDefunctSitesHaveMarker() throws Exception {
		java.util.Set<String> knownDefunct = java.util.Set.of(
			"www.dnovels.net", "www.mai-net.net", "www.newvel.jp");
		File[] siteDirs = WEB_CONFIG.listFiles(File::isDirectory);
		assertNotNull("web/ 配下にサイトディレクトリがあること", siteDirs);
		assertTrue("サイト数が想定以上あること", siteDirs.length >= 12);
		for (File dir : siteDirs) {
			java.nio.file.Path extract = dir.toPath().resolve("extract.txt");
			if (!java.nio.file.Files.exists(extract)) continue;
			boolean hasDefunct = java.nio.file.Files.readAllLines(extract).stream()
				.anyMatch(line -> line.startsWith("DEFUNCT\t"));
			assertEquals(dir.getName() + " の DEFUNCT 定義有無が既知の消滅サイト一覧と一致すること",
				knownDefunct.contains(dir.getName()), hasDefunct);
		}
	}
}
