package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 展開済み EPUB から階層目次を構築する。
 *
 * <p>解決順は <b>toc.ncx → nav.xhtml → spine</b>。
 * 本プロジェクトの {@code template/OPS/toc.ncx.vm} は navPoint を入れ子にできる一方、
 * {@code template/OPS/xhtml/xhtml_nav.vm} の toc は {@code <ol>} 直下に
 * {@code li.chapter} が並ぶフラット構造のため、階層情報は toc.ncx にしか無い。</p>
 */
public class TocParser
{
	private static final Logger logger = LoggerFactory.getLogger(TocParser.class);

	private TocParser() {}

	/**
	 * 目次を構築する。どの手段でも取得できなければ spine から生成する。
	 *
	 * @param epubRoot 展開済み EPUB のルート
	 * @param opf 解析済みの OPF
	 */
	public static List<TocEntry> parse(Path epubRoot, OpfPackage opf)
	{
		String ncxPath = opf.getNcxPath();
		if (ncxPath != null) {
			try {
				List<TocEntry> entries = parseNcx(epubRoot, opf, ncxPath);
				if (!entries.isEmpty()) return entries;
			} catch (IOException e) {
				logger.warn("toc.ncx の解析に失敗しました: {}", ncxPath, e);
			}
		}
		String navPath = opf.getNavPath();
		if (navPath != null) {
			try {
				List<TocEntry> entries = parseNav(epubRoot, opf, navPath);
				if (!entries.isEmpty()) return entries;
			} catch (IOException e) {
				logger.warn("nav.xhtml の解析に失敗しました: {}", navPath, e);
			}
		}
		return fromSpine(opf);
	}

	// ------------------------------------------------------------------
	// toc.ncx
	// ------------------------------------------------------------------

	static List<TocEntry> parseNcx(Path epubRoot, OpfPackage opf, String ncxPath) throws IOException
	{
		Path file = PathUtils.resolveInside(epubRoot, ncxPath);
		if (file == null || !Files.isRegularFile(file)) return List.of();
		org.w3c.dom.Document doc = XmlUtils.parse(file);
		org.w3c.dom.Element navMap = XmlUtils.findFirst(doc, "navMap");
		if (navMap == null) return List.of();
		return ncxChildren(navMap, opf, ncxPath);
	}

	/** navPoint は入れ子になりうるため、直下の子だけを辿って階層を保つ */
	private static List<TocEntry> ncxChildren(org.w3c.dom.Element parent, OpfPackage opf, String ncxPath)
	{
		List<TocEntry> entries = new ArrayList<>();
		for (org.w3c.dom.Element navPoint : XmlUtils.childrenByName(parent, "navPoint")) {
			org.w3c.dom.Element navLabel = null;
			for (org.w3c.dom.Element candidate : XmlUtils.childrenByName(navPoint, "navLabel")) {
				navLabel = candidate;
				break;
			}
			String label = (navLabel == null) ? null : XmlUtils.text(XmlUtils.findFirst(navLabel, "text"));

			String src = null;
			for (org.w3c.dom.Element content : XmlUtils.childrenByName(navPoint, "content")) {
				src = XmlUtils.attr(content, "src");
				break;
			}
			TocEntry entry = toEntry(label, src, ncxPath, opf, ncxChildren(navPoint, opf, ncxPath));
			if (entry != null) entries.add(entry);
		}
		return entries;
	}

	// ------------------------------------------------------------------
	// nav.xhtml
	// ------------------------------------------------------------------

	static List<TocEntry> parseNav(Path epubRoot, OpfPackage opf, String navPath) throws IOException
	{
		Path file = PathUtils.resolveInside(epubRoot, navPath);
		if (file == null || !Files.isRegularFile(file)) return List.of();
		// nav.xhtml は DOCTYPE を持つため DOM ではなく jsoup (既存依存) でパースする。
		// EPUB は UTF-16 も認められているため、文字コードを決め打ちせず
		// BOM / XML 宣言 / meta から jsoup に判定させる (charsetName に null を渡す)。
		// UTF-8 固定で読むと UTF-16 の EPUB で例外になり、目次が黙って spine 表示に劣化する
		Document doc;
		try (InputStream in = Files.newInputStream(file)) {
			doc = Jsoup.parse(in, null, "");
		}

		Element tocNav = null;
		for (Element nav : doc.select("nav")) {
			// epub:type="toc" が正だが、kindle モードでは属性が付かず id="toc" だけになる
			// (template/OPS/xhtml/xhtml_nav.vm 参照)。landmarks nav は拾ってはならない
			String epubType = nav.attr("epub:type");
			if ("toc".equals(epubType) || (epubType.isEmpty() && "toc".equals(nav.id()))) {
				tocNav = nav;
				break;
			}
		}
		if (tocNav == null) return List.of();

		Elements lists = tocNav.select("> ol, > ul");
		if (lists.isEmpty()) return List.of();
		return navChildren(lists.first(), navPath, opf);
	}

	private static List<TocEntry> navChildren(Element list, String navPath, OpfPackage opf)
	{
		List<TocEntry> entries = new ArrayList<>();
		for (Element li : list.children()) {
			if (!"li".equalsIgnoreCase(li.tagName())) continue;
			Elements anchors = li.select("> a");
			String label = anchors.isEmpty() ? li.ownText().trim() : anchors.first().text().trim();
			String href = anchors.isEmpty() ? null : anchors.first().attr("href");

			List<TocEntry> children = new ArrayList<>();
			for (Element nested : li.children()) {
				if ("ol".equalsIgnoreCase(nested.tagName()) || "ul".equalsIgnoreCase(nested.tagName())) {
					children.addAll(navChildren(nested, navPath, opf));
				}
			}
			TocEntry entry = toEntry(label, href, navPath, opf, children);
			if (entry != null) entries.add(entry);
		}
		return entries;
	}

	// ------------------------------------------------------------------
	// spine フォールバック
	// ------------------------------------------------------------------

	static List<TocEntry> fromSpine(OpfPackage opf)
	{
		List<TocEntry> entries = new ArrayList<>();
		List<SpineItem> spine = opf.getSpine();
		for (int i = 0; i < spine.size(); i++) {
			String path = spine.get(i).path();
			int slash = path.lastIndexOf('/');
			String label = (slash < 0) ? path : path.substring(slash + 1);
			entries.add(new TocEntry(label, path, null, i, List.of()));
		}
		return entries;
	}

	// ------------------------------------------------------------------

	/**
	 * ラベルと href から目次項目を作る。
	 * href は目次ファイル自身の位置を基準に解決し、フラグメントは別に保持する
	 * (本プロジェクトの目次は {@code xhtml/text00001.xhtml#chapter1} 形式)。
	 */
	private static TocEntry toEntry(String label, String href, String tocPath, OpfPackage opf, List<TocEntry> children)
	{
		String displayLabel = (label == null || label.isEmpty()) ? "(無題)" : label;
		if (href == null || href.isEmpty()) {
			// リンクを持たない見出しだけの項目。子があれば残す
			if (children.isEmpty()) return null;
			return new TocEntry(displayLabel, null, null, -1, children);
		}
		if (href.contains("://")) return null;
		String path = PathUtils.resolveAgainst(tocPath, href);
		if (path == null) return null;
		String fragment = PathUtils.fragmentOf(href);
		return new TocEntry(displayLabel, path, fragment, opf.indexOfSpine(path), children);
	}

	/** 目次を JSON 配列として出力する */
	static void toJson(StringBuilder buf, List<TocEntry> entries)
	{
		buf.append('[');
		boolean first = true;
		for (TocEntry entry : entries) {
			if (!first) buf.append(',');
			first = false;
			buf.append('{');
			Json.prop(buf, "label", entry.label());
			Json.prop(buf, "path", entry.path());
			Json.prop(buf, "fragment", entry.fragment());
			Json.prop(buf, "spineIndex", entry.spineIndex());
			Json.key(buf, "children");
			toJson(buf, entry.children());
			buf.append('}');
		}
		buf.append(']');
	}
}
