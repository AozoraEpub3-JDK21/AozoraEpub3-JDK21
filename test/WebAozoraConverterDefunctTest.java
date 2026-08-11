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

	/** 稼働中サイトの extract.txt に DEFUNCT が誤って定義されていないこと */
	@Test
	public void testActiveSitesAreNotDefunct() throws Exception {
		String[] activeSites = {
			"ncode.syosetu.com", "novel18.syosetu.com", "kakuyomu.jp",
			"novel.syosetu.org", "www.aozora.gr.jp", "novel.fc2.com",
			"novelist.jp", "2.novelist.jp", "www.akatsuki-novels.com",
		};
		for (String fqdn : activeSites) {
			java.nio.file.Path extract = java.nio.file.Paths.get("web", fqdn, "extract.txt");
			assertTrue(fqdn + " の extract.txt が存在すること", java.nio.file.Files.exists(extract));
			for (String line : java.nio.file.Files.readAllLines(extract)) {
				assertFalse(fqdn + " に DEFUNCT が定義されていないこと", line.startsWith("DEFUNCT\t"));
			}
		}
	}
}
