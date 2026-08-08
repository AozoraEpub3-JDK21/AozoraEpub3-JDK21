package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 目次の解決。
 * 本プロジェクトでは階層情報が toc.ncx にしか無く、nav.xhtml はフラットなので、
 * toc.ncx → nav.xhtml → spine の順で解決する必要がある。
 */
public class TocParserTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void ncxKeepsHierarchyAndFragments() throws IOException
	{
		Path root = EpubFixture.standard().explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		assertEquals(1, toc.size());
		TocEntry first = toc.get(0);
		assertEquals("第一章", first.label());
		assertEquals("OPS/xhtml/text00001.xhtml", first.path());
		assertEquals("chapter1", first.fragment());
		assertEquals(1, first.spineIndex());

		// navPoint の入れ子が階層として保たれている
		assertEquals(1, first.children().size());
		TocEntry child = first.children().get(0);
		assertEquals("第二章", child.label());
		assertEquals("OPS/xhtml/text00002.xhtml", child.path());
		assertEquals("chapter2", child.fragment());
		assertEquals(2, child.spineIndex());
	}

	@Test
	public void fallsBackToNavWhenNcxMissing() throws IOException
	{
		Path root = EpubFixture.standard().remove("OPS/toc.ncx").explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		assertEquals(2, toc.size());
		assertEquals("第一章", toc.get(0).label());
		assertEquals("OPS/xhtml/text00001.xhtml", toc.get(0).path());
		assertEquals("chapter1", toc.get(0).fragment());
		assertEquals("第二章", toc.get(1).label());
	}

	@Test
	public void navParsingIgnoresLandmarksNav() throws IOException
	{
		Path root = EpubFixture.standard().remove("OPS/toc.ncx").explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		// landmarks nav の項目 (「ランドマーク」) を拾ってはならない
		List<String> labels = new ArrayList<>();
		collectLabels(toc, labels);
		for (String label : labels) {
			assertNotEquals("landmarks nav を目次として拾っている", "ランドマーク", label);
		}
	}

	@Test
	public void navParsingAcceptsKindleStyleNavWithoutEpubType() throws IOException
	{
		// kindle モードでは toc nav に epub:type が付かず id="toc" だけになる
		Path root = EpubFixture.standard()
			.remove("OPS/toc.ncx")
			.put("OPS/xhtml/nav.xhtml", EpubFixture.navXhtml(false))
			.explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		assertEquals(2, toc.size());
		assertEquals("第一章", toc.get(0).label());
	}

	@Test
	public void navWithUtf16BomIsParsedInsteadOfSilentlyDegrading() throws IOException
	{
		// EPUB は UTF-16 も認められている (XML 仕様上 BOM 必須)。
		// UTF-8 固定で読むと例外になり、目次が取れないまま黙って spine 表示に劣化する。
		// ここで効いているのは jsoup の BOM 検出
		Path root = EpubFixture.standard().remove("OPS/toc.ncx").explodeTo(temp.getRoot().toPath());
		byte[] utf16 = EpubFixture.navXhtml(true)
			.replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"")
			.getBytes(java.nio.charset.StandardCharsets.UTF_16);
		java.nio.file.Files.write(root.resolve("OPS/xhtml/nav.xhtml"), utf16);

		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		assertEquals(2, toc.size());
		assertEquals("第一章", toc.get(0).label());
		assertEquals("chapter1", toc.get(0).fragment());
	}

	@Test
	public void fallsBackToSpineWhenNoTocAvailable() throws IOException
	{
		Path root = EpubFixture.standard()
			.remove("OPS/toc.ncx")
			.put("OPS/xhtml/nav.xhtml", "<html><body><p>目次なし</p></body></html>")
			.explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		List<TocEntry> toc = TocParser.parse(root, opf);

		assertEquals(opf.getSpine().size(), toc.size());
		assertTrue(toc.get(0).label().endsWith(".xhtml"));
		assertEquals(0, toc.get(0).spineIndex());
	}

	@Test
	public void tocJsonIncludesFragmentAndChildren() throws IOException
	{
		Path root = EpubFixture.standard().explodeTo(temp.getRoot().toPath());
		OpfPackage opf = OpfParser.parse(root);
		StringBuilder buf = new StringBuilder();
		TocParser.toJson(buf, TocParser.parse(root, opf));
		String json = buf.toString();

		assertTrue(json.contains("\"fragment\":\"chapter1\""));
		assertTrue(json.contains("\"fragment\":\"chapter2\""));
		assertTrue(json.contains("\"children\":["));
	}

	private static void collectLabels(List<TocEntry> entries, List<String> out)
	{
		for (TocEntry entry : entries) {
			out.add(entry.label());
			collectLabels(entry.children(), out);
		}
	}
}
