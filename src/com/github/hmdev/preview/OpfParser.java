package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 展開済み EPUB の {@code META-INF/container.xml} から OPF を辿り、
 * spine / manifest / メタデータを読み取る。
 *
 * <p>本プロジェクトには EPUB を書く側 ({@code Epub3Writer}) しか無く、
 * 読む側のコードはこのクラスが初めてとなる。</p>
 */
public class OpfParser
{
	private OpfParser() {}

	/** 展開済み EPUB のルートから OPF を読み取る */
	public static OpfPackage parse(Path epubRoot) throws IOException
	{
		String opfPath = findOpfPath(epubRoot);
		// container.xml の full-path は EPUB 内の任意の文字列。
		// ドライブ付きパス等でホスト上のファイルを読まされないよう封じ込める
		Path opfFile = PathUtils.resolveInside(epubRoot, opfPath);
		if (opfFile == null) {
			throw new IOException("container.xml の full-path が展開先の外を指しています: " + opfPath);
		}
		if (!Files.isRegularFile(opfFile)) {
			throw new IOException("OPF が見つかりません: " + opfPath);
		}
		return parse(XmlUtils.parse(opfFile), opfPath);
	}

	/**
	 * パース済みの OPF から内容を読み取る。
	 *
	 * <p>本棚 (LibraryScanner) は EPUB を展開せず ZIP エントリを直接読むため、
	 * ファイルではなく {@link Document} から入る経路が要る。
	 * 展開経路と同じ解釈になるよう、両者ともここに集約する。</p>
	 *
	 * @param doc OPF の DOM
	 * @param opfPath EPUB ルートから見た OPF のパス (href の解決基準になる)
	 */
	static OpfPackage parse(Document doc, String opfPath)
	{
		OpfPackage opf = new OpfPackage(opfPath);

		Element packageElement = XmlUtils.findFirst(doc, "package");
		if (packageElement != null) {
			opf.setVersion(XmlUtils.attr(packageElement, "version"));
		}

		readMetadata(doc, opf);
		readManifest(doc, opf);
		readSpine(doc, opf);
		return opf;
	}

	/** container.xml から rootfile の full-path を取得する */
	static String findOpfPath(Path epubRoot) throws IOException
	{
		Path container = epubRoot.resolve("META-INF").resolve("container.xml");
		if (!Files.isRegularFile(container)) {
			throw new IOException("META-INF/container.xml が見つかりません");
		}
		return findOpfPath(XmlUtils.parse(container));
	}

	/**
	 * パース済みの container.xml から rootfile の full-path を取得する。
	 * 展開先の外を指す full-path はここで弾く (ZIP 直読み経路も同じ検査を通す)。
	 */
	static String findOpfPath(Document doc) throws IOException
	{
		for (Element rootfile : XmlUtils.findAll(doc, "rootfile")) {
			String fullPath = XmlUtils.attr(rootfile, "full-path");
			if (fullPath.isEmpty()) continue;
			if (PathUtils.escapesRoot(fullPath)) {
				// "../" やドライブ修飾でホスト上のファイルを指そうとしている
				throw new IOException("container.xml の full-path が展開先の外を指しています: " + fullPath);
			}
			String normalized = PathUtils.normalizeRelative(fullPath);
			if (normalized == null) {
				throw new IOException("container.xml の full-path が不正です: " + fullPath);
			}
			return normalized;
		}
		throw new IOException("container.xml に rootfile がありません");
	}

	private static void readMetadata(Document doc, OpfPackage opf)
	{
		Element metadata = XmlUtils.findFirst(doc, "metadata");
		if (metadata == null) return;

		opf.setTitle(XmlUtils.text(XmlUtils.findFirst(metadata, "title")));
		opf.setCreator(XmlUtils.text(XmlUtils.findFirst(metadata, "creator")));
		opf.setPublisher(XmlUtils.text(XmlUtils.findFirst(metadata, "publisher")));
		opf.setLanguage(XmlUtils.text(XmlUtils.findFirst(metadata, "language")));
		opf.setIdentifier(XmlUtils.text(XmlUtils.findFirst(metadata, "identifier")));

		for (Element meta : XmlUtils.findAll(metadata, "meta")) {
			// EPUB3 形式: <meta property="...">value</meta>
			String property = XmlUtils.attr(meta, "property");
			// refines 付きは既存要素の補足情報なので、本体のメタデータとしては採らない
			boolean refines = !XmlUtils.attr(meta, "refines").isEmpty();
			if (!property.isEmpty() && !refines) {
				String value = XmlUtils.text(meta);
				if ("dcterms:modified".equals(property)) opf.setModified(value);
				else if ("rendition:layout".equals(property)) opf.setRenditionLayout(value);
			}
			// EPUB2 形式: <meta name="..." content="..."/>
			String name = XmlUtils.attr(meta, "name");
			if ("primary-writing-mode".equals(name)) {
				opf.setPrimaryWritingMode(XmlUtils.attr(meta, "content"));
			} else if ("cover".equals(name)) {
				// EPUB2 の表紙指定。値は manifest の id
				opf.setCoverIdref(XmlUtils.attr(meta, "content"));
			}
		}
	}

	private static void readManifest(Document doc, OpfPackage opf)
	{
		Element manifest = XmlUtils.findFirst(doc, "manifest");
		if (manifest == null) return;
		for (Element item : XmlUtils.findAll(manifest, "item")) {
			String id = XmlUtils.attr(item, "id");
			String href = XmlUtils.attr(item, "href");
			if (id.isEmpty() || href.isEmpty()) continue;
			// リモートリソースはプレビュー対象外
			if (href.contains("://")) continue;
			String path = opf.resolve(href);
			if (path == null) continue;
			opf.addManifestItem(new ManifestItem(
				id, href, path,
				XmlUtils.attr(item, "media-type"),
				XmlUtils.attr(item, "properties")));
		}
	}

	private static void readSpine(Document doc, OpfPackage opf)
	{
		Element spine = XmlUtils.findFirst(doc, "spine");
		if (spine == null) return;
		opf.setPageProgressionDirection(XmlUtils.attr(spine, "page-progression-direction"));
		opf.setNcxIdref(XmlUtils.attr(spine, "toc"));
		List<Element> itemrefs = XmlUtils.findAll(spine, "itemref");
		for (Element itemref : itemrefs) {
			String idref = XmlUtils.attr(itemref, "idref");
			if (idref.isEmpty()) continue;
			// linear="no" は本文の読み順に含まれない補助コンテンツ。章順がずれるため除外する
			if ("no".equalsIgnoreCase(XmlUtils.attr(itemref, "linear"))) continue;
			ManifestItem item = opf.getManifestItem(idref);
			if (item == null) continue;
			opf.addSpineItem(new SpineItem(idref, item.path(), item.mediaType()));
		}
	}
}
