package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** container.xml → OPF の解決と spine / manifest / メタデータの読み取り */
public class OpfParserTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path root;

	@Before
	public void setUp() throws IOException
	{
		this.root = EpubFixture.standard().explodeTo(temp.getRoot().toPath());
	}

	@Test
	public void findsOpfThroughBomPrefixedContainerXml() throws IOException
	{
		// 本プロジェクトの container.xml は BOM 付きで出力される
		assertEquals("OPS/package.opf", OpfParser.findOpfPath(this.root));
	}

	@Test
	public void rejectsContainerPointingOutsideTheExtractedRoot() throws IOException
	{
		// 細工した container.xml でホスト上の XML を読ませない
		String evil = EpubFixture.CONTAINER_XML
			.replace("OPS/package.opf", "C:/Windows/win.ini");
		Path root = EpubFixture.standard()
			.put("META-INF/container.xml", evil)
			.explodeTo(temp.getRoot().toPath());

		try {
			OpfParser.parse(root);
			org.junit.Assert.fail("展開先の外を指す full-path は拒否すること");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("展開先の外"));
		}
	}

	@Test
	public void rootRelativeHrefIsResolvedFromTheEpubRoot() throws IOException
	{
		// 先頭スラッシュは EPUB ルートからの参照。
		// OPF のディレクトリ (OPS/) を付けると OPS/xhtml/... になり、実体と一致しない
		String opf = EpubFixture.packageOpf().replace(
			"href=\"xhtml/text00002.xhtml\"", "href=\"/OPS/xhtml/text00002.xhtml\"");
		Path root = EpubFixture.standard()
			.put("OPS/package.opf", opf)
			.explodeTo(temp.getRoot().toPath());

		OpfPackage parsed = OpfParser.parse(root);
		assertEquals("OPS/xhtml/text00002.xhtml", parsed.getManifestItem("sectext00002").path());
	}

	@Test
	public void manifestHrefWithQueryResolvesToTheEntryName() throws IOException
	{
		// href のクエリをファイル名に混ぜると ZIP エントリと一致せず 404 になる
		String opf = EpubFixture.packageOpf().replace(
			"href=\"xhtml/text00001.xhtml\"", "href=\"xhtml/text00001.xhtml?mode=print\"");
		Path root = EpubFixture.standard()
			.put("OPS/package.opf", opf)
			.explodeTo(temp.getRoot().toPath());

		OpfPackage parsed = OpfParser.parse(root);
		assertEquals("OPS/xhtml/text00001.xhtml", parsed.getManifestItem("sectext00001").path());
	}

	@Test
	public void readsMetadata() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		assertEquals("テスト書籍", opf.getTitle());
		assertEquals("テスト著者", opf.getCreator());
		assertEquals("ja", opf.getLanguage());
		assertEquals("urn:uuid:test-0001", opf.getIdentifier());
		assertEquals("2026-08-08T00:00:00Z", opf.getModified());
		assertEquals("reflowable", opf.getRenditionLayout());
		assertEquals("horizontal-rl", opf.getPrimaryWritingMode());
		// EPUB 3.x では "3.0" が正しい値 (3.3 ではない)
		assertEquals("3.0", opf.getVersion());
		assertEquals("rtl", opf.getPageProgressionDirection());
	}

	@Test
	public void spineExcludesNonLinearItems() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		List<SpineItem> spine = opf.getSpine();
		// cover-page は linear="no" なので読み順から外れる
		assertEquals(3, spine.size());
		assertEquals("OPS/xhtml/nav.xhtml", spine.get(0).path());
		assertEquals("OPS/xhtml/text00001.xhtml", spine.get(1).path());
		assertEquals("OPS/xhtml/text00002.xhtml", spine.get(2).path());
		for (SpineItem item : spine) {
			assertFalse("linear=no の cover が混ざっている", item.path().endsWith("cover.xhtml"));
		}
	}

	@Test
	public void manifestHrefIsResolvedAgainstOpfDirectory() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		ManifestItem css = opf.getManifestItem("css-vertical");
		assertNotNull(css);
		// href は OPF からの相対。EPUB ルートからの相対に解決される
		assertEquals("css/vertical_text.css", css.href());
		assertEquals("OPS/css/vertical_text.css", css.path());
	}

	@Test
	public void percentEscapedHrefResolvesToTheZipEntryName() throws IOException
	{
		// OPF の href は URI なので、空白を含むファイル名はエスケープされている。
		// ZIP エントリ名は生の "cover art.jpg" なのでデコードしないと配信できない
		String opf = EpubFixture.packageOpf().replace(
			"<item id=\"css-vertical\" href=\"css/vertical_text.css\" media-type=\"text/css\"/>",
			"<item id=\"css-vertical\" href=\"css/vertical_text.css\" media-type=\"text/css\"/>\n"
			+ "    <item id=\"cover-img\" href=\"images/cover%20art.jpg\" media-type=\"image/jpeg\"/>");
		Path root = EpubFixture.standard()
			.put("OPS/package.opf", opf)
			.put("OPS/images/cover art.jpg", "jpeg")
			.explodeTo(temp.getRoot().toPath());

		OpfPackage parsed = OpfParser.parse(root);
		assertEquals("OPS/images/cover art.jpg", parsed.getManifestItem("cover-img").path());
	}

	@Test
	public void locatesNavAndNcx() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		assertEquals("OPS/xhtml/nav.xhtml", opf.getNavPath());
		assertEquals("OPS/toc.ncx", opf.getNcxPath());
	}

	@Test
	public void manifestPropertiesAreParsedAsTokens() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		assertTrue(opf.getManifestItem("nav").hasProperty("nav"));
		assertFalse(opf.getManifestItem("cover-page").hasProperty("nav"));
	}

	@Test
	public void spineIndexLookup() throws IOException
	{
		OpfPackage opf = OpfParser.parse(this.root);
		assertEquals(1, opf.indexOfSpine("OPS/xhtml/text00001.xhtml"));
		assertEquals(-1, opf.indexOfSpine("OPS/xhtml/cover.xhtml"));
	}
}
