package com.github.hmdev.web;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assume;
import org.junit.Test;

/**
 * パスなし URL（https://example.com）を渡しても例外にならないことのテスト（監査 #9）。
 *
 * 修正前は createWebAozoraConverter が
 * urlString.substring(0, urlString.indexOf('/', ...)) で
 * indexOf の -1 をそのまま substring に渡し、
 * StringIndexOutOfBoundsException になっていた。
 * 呼び出し側の catch でユーザーには
 * 「エラーが発生しました : begin 0, end -1, length 19」という
 * 意味不明なメッセージが出ていた。
 *
 * 末尾スラッシュ補正は convertToAozoraText の中にあり、
 * createWebAozoraConverter はそれより先に呼ばれるため、
 * この例外はネットワーク状態と無関係に発生する。
 */
public class WebAozoraConverterRootUrlTest {

	/** web/ 設定ディレクトリを解決する（作業ディレクトリ依存を避ける） */
	private static File webConfigPath() {
		Path root = Paths.get(".").toAbsolutePath().normalize();
		if (!Files.exists(root.resolve("web"))) {
			//Gradle のテスト JVM 以外から実行された場合のフォールバック
			Path here = root;
			while (here != null && !Files.exists(here.resolve("web"))) here = here.getParent();
			if (here != null) root = here;
		}
		return root.resolve("web").toFile();
	}

	/** 未定義サイトのパスなし URL は、例外ではなく null（サイト定義なし）が返る */
	@Test
	public void rootUrlOfUnknownSiteReturnsNullWithoutException() throws Exception {
		File webConfig = webConfigPath();
		Assume.assumeTrue("web/ が見つからない", webConfig.isDirectory());

		assertNull(WebAozoraConverter.createWebAozoraConverter("https://unknown-host.invalid", webConfig));
	}

	/** 定義済みサイトのパスなし URL は、例外にならず converter が得られる。
	 * 生成はローカルの extract.txt を読むだけでネットワークアクセスは発生しない */
	@Test
	public void rootUrlOfKnownSiteReturnsConverter() throws Exception {
		File webConfig = webConfigPath();
		Assume.assumeTrue("web/ncode.syosetu.com が見つからない",
			new File(webConfig, "ncode.syosetu.com").isDirectory());

		assertNotNull(WebAozoraConverter.createWebAozoraConverter("https://ncode.syosetu.com", webConfig));
	}

	/** パス付き URL は従来どおり動作し、パスなし URL と同じ fqdn に解決される（回帰防止）。
	 * converter は fqdn をキーにキャッシュされるため、同一インスタンスになることで
	 * パスなし URL の fqdn 切り出しが正しいことも同時に検証できる */
	@Test
	public void urlWithPathResolvesToSameConverterAsRootUrl() throws Exception {
		File webConfig = webConfigPath();
		Assume.assumeTrue("web/ncode.syosetu.com が見つからない",
			new File(webConfig, "ncode.syosetu.com").isDirectory());

		WebAozoraConverter withPath =
			WebAozoraConverter.createWebAozoraConverter("https://ncode.syosetu.com/n9623lp/", webConfig);
		WebAozoraConverter rootOnly =
			WebAozoraConverter.createWebAozoraConverter("https://ncode.syosetu.com", webConfig);

		assertNotNull(withPath);
		assertSame("パスなし URL の fqdn 切り出しがパス付きと一致しない", withPath, rootOnly);
	}
}
