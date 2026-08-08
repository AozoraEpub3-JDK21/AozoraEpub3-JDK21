package com.github.hmdev.preview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * EPUB 内の XML (container.xml / OPF / toc.ncx) を読むためのユーティリティ。
 *
 * <p>JDK 内蔵の DOM パーサを XXE 対策を施した上で使用する。
 * 名前空間の付き方が EPUB 生成ツールにより異なるため、
 * 要素の取得は常に {@code getElementsByTagNameNS("*", ...)} 相当で行う。</p>
 */
final class XmlUtils
{
	/** メタデータ XML の読み込み上限。これを超える container.xml / OPF / ncx は不正とみなす */
	static final long MAX_METADATA_BYTES = 16L * 1024 * 1024;

	private XmlUtils() {}

	/**
	 * XXE を無効化した DocumentBuilder を生成する。
	 *
	 * @param allowDoctype DOCTYPE 宣言を許可するか。
	 *        EPUB2 の toc.ncx は {@code <!DOCTYPE ncx PUBLIC ...>} を持つことがあるため、
	 *        厳格パースに失敗した場合のみ true で再試行する。
	 *        true でも外部エンティティ・外部 DTD の読み込みは無効のままなので XXE は成立しない。
	 */
	private static DocumentBuilder newBuilder(boolean allowDoctype) throws ParserConfigurationException
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !allowDoctype);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		// 外部 DTD/エンティティは一切読みに行かない
		builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new ByteArrayInputStream(new byte[0])));
		return builder;
	}

	/**
	 * XML ファイルをパースする。
	 * BOM 付き UTF-8 を許容するため、先頭の BOM を除去してから渡す。
	 * (本プロジェクトが生成する META-INF/container.xml は BOM 付き)
	 */
	static Document parse(Path file) throws IOException
	{
		// container.xml / OPF / toc.ncx は本来せいぜい数百KB。
		// 高圧縮の悪意ある EPUB では 1 エントリ 512MB まで展開されうるので、
		// DOM を組み立てる前に読み込み量を抑える
		long size = Files.size(file);
		if (size > MAX_METADATA_BYTES) {
			throw new IOException("メタデータが大きすぎます (" + size + " bytes): " + file);
		}
		return parse(Files.readAllBytes(file), file.toString());
	}

	/**
	 * メモリ上の XML をパースする。
	 * 本棚 (LibraryScanner) は EPUB を展開せず ZIP エントリを直接読むため、
	 * ファイルではなくバイト列から入る経路が要る。
	 *
	 * @param bytes XML のバイト列
	 * @param description エラーメッセージに載せる出所 (ファイルパスや ZIP エントリ名)
	 */
	static Document parse(byte[] bytes, String description) throws IOException
	{
		if (bytes.length > MAX_METADATA_BYTES) {
			throw new IOException("メタデータが大きすぎます (" + bytes.length + " bytes): " + description);
		}
		// BOM 付き UTF-8 を許容する (本プロジェクトが生成する container.xml は BOM 付き)
		int offset = 0;
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
			offset = 3;
		}
		try {
			return parse(bytes, offset, false);
		} catch (ParserConfigurationException | SAXException e) {
			// EPUB2 の toc.ncx など DOCTYPE 付きのファイルを救済する
			try {
				return parse(bytes, offset, true);
			} catch (ParserConfigurationException | SAXException retry) {
				throw new IOException("XML の解析に失敗しました: " + description, retry);
			}
		}
	}

	private static Document parse(byte[] bytes, int offset, boolean allowDoctype)
		throws IOException, ParserConfigurationException, SAXException
	{
		try (InputStream is = new ByteArrayInputStream(bytes, offset, bytes.length - offset)) {
			return newBuilder(allowDoctype).parse(is);
		}
	}

	/** 名前空間を無視してローカル名で子孫要素を検索する */
	static List<Element> findAll(Node context, String localName)
	{
		List<Element> result = new ArrayList<>();
		NodeList nodes = (context instanceof Document doc)
			? doc.getElementsByTagNameNS("*", localName)
			: ((Element)context).getElementsByTagNameNS("*", localName);
		for (int i = 0; i < nodes.getLength(); i++) {
			result.add((Element)nodes.item(i));
		}
		return result;
	}

	/** 名前空間を無視してローカル名で最初の子孫要素を返す。無ければ null */
	static Element findFirst(Node context, String localName)
	{
		List<Element> all = findAll(context, localName);
		return all.isEmpty() ? null : all.get(0);
	}

	/** 直下の子要素のみをローカル名で検索する (navPoint の入れ子を辿るのに使う) */
	static List<Element> childrenByName(Element parent, String localName)
	{
		List<Element> result = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) continue;
			if (localName.equals(localName(node))) result.add((Element)node);
		}
		return result;
	}

	/** 名前空間接頭辞を除いたローカル名を返す */
	static String localName(Node node)
	{
		String name = node.getLocalName();
		if (name != null) return name;
		name = node.getNodeName();
		int colon = name.indexOf(':');
		return (colon >= 0) ? name.substring(colon + 1) : name;
	}

	/** 属性値を返す。未指定なら空文字 */
	static String attr(Element element, String name)
	{
		String value = element.getAttribute(name);
		return (value == null) ? "" : value;
	}

	/** 要素のテキスト内容を trim して返す。要素が null なら null */
	static String text(Element element)
	{
		if (element == null) return null;
		String content = element.getTextContent();
		return (content == null) ? null : content.trim();
	}
}
