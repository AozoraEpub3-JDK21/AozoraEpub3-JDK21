package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * インスペクタ (R6) の Java 側。書誌・構成・manifest 内訳・埋め込みフォントを集計する。
 *
 * <p>ここで扱うのは <b>宣言値</b> のみ。
 * 「ブラウザが実際にどう解釈したか」(実効値) はビューアー側で
 * {@code getComputedStyle} から取得し、別欄に表示する。
 * 両者は食い違いうるため (vertical_font.css の {@code @ＭＳ 明朝} 参照)、
 * 混ぜて表示してはならない。</p>
 */
public class EpubInspection
{
	/** manifest の分類 */
	enum Category
	{
		XHTML("xhtml"), CSS("css"), IMAGE("image"), FONT("font"), NCX("ncx"), OTHER("other");

		final String key;
		Category(String key) { this.key = key; }
	}

	private final Path epubRoot;
	private final OpfPackage opf;
	private final Path epubFile;

	public EpubInspection(Path epubRoot, OpfPackage opf, Path epubFile)
	{
		this.epubRoot = epubRoot;
		this.opf = opf;
		this.epubFile = epubFile;
	}

	/** media-type から分類を決める */
	static Category categorize(String mediaType, String path)
	{
		String type = (mediaType == null) ? "" : mediaType.toLowerCase(java.util.Locale.ROOT);
		if (type.equals("application/xhtml+xml") || type.equals("text/html")) return Category.XHTML;
		if (type.equals("text/css")) return Category.CSS;
		if (type.equals("application/x-dtbncx+xml")) return Category.NCX;
		if (type.startsWith("image/")) return Category.IMAGE;
		if (type.startsWith("font/")
			|| type.startsWith("application/font")
			|| type.equals("application/vnd.ms-opentype")
			|| type.equals("application/x-font-ttf")) return Category.FONT;
		// media-type が欠けている EPUB 向けに拡張子でも判定する
		String ext = PathUtils.extensionOf(path);
		return switch (ext) {
			case "xhtml", "html", "htm" -> Category.XHTML;
			case "css" -> Category.CSS;
			case "ncx" -> Category.NCX;
			case "png", "jpg", "jpeg", "gif", "svg", "webp" -> Category.IMAGE;
			case "ttf", "otf", "woff", "woff2" -> Category.FONT;
			default -> Category.OTHER;
		};
	}

	/** 集計結果を JSON で返す */
	public String toJson()
	{
		Map<Category, int[]> counts = new LinkedHashMap<>();
		Map<Category, long[]> sizes = new LinkedHashMap<>();
		for (Category category : Category.values()) {
			counts.put(category, new int[1]);
			sizes.put(category, new long[1]);
		}
		List<String> cssPaths = new ArrayList<>();
		List<ManifestItem> fonts = new ArrayList<>();

		for (ManifestItem item : this.opf.getManifest()) {
			Category category = categorize(item.mediaType(), item.path());
			long size = fileSize(item.path());
			counts.get(category)[0]++;
			sizes.get(category)[0] += size;
			if (category == Category.CSS) cssPaths.add(item.path());
			if (category == Category.FONT) fonts.add(item);
		}
		// manifest に載らないファイル (mimetype / META-INF/container.xml / OPF 自身) も
		// 展開されているため、内訳の合計ではなく実際のディレクトリから数える
		long totalSize = extractedSize();

		StringBuilder buf = new StringBuilder(2048);
		buf.append('{');

		Json.key(buf, "bibliography");
		buf.append('{');
		Json.prop(buf, "title", this.opf.getTitle());
		Json.prop(buf, "creator", this.opf.getCreator());
		Json.prop(buf, "publisher", this.opf.getPublisher());
		Json.prop(buf, "language", this.opf.getLanguage());
		Json.prop(buf, "identifier", this.opf.getIdentifier());
		Json.prop(buf, "modified", this.opf.getModified());
		buf.append('}');

		Json.key(buf, "structure");
		buf.append('{');
		Json.prop(buf, "packageVersion", this.opf.getVersion());
		Json.prop(buf, "pageProgressionDirection", this.opf.getPageProgressionDirection());
		Json.prop(buf, "renditionLayout", this.opf.getRenditionLayout());
		Json.prop(buf, "primaryWritingMode", this.opf.getPrimaryWritingMode());
		Json.prop(buf, "opfPath", this.opf.getOpfPath());
		Json.prop(buf, "spineCount", this.opf.getSpine().size());
		Json.prop(buf, "manifestCount", this.opf.getManifest().size());
		Json.prop(buf, "fileSize", (this.epubFile == null) ? 0 : fileSizeOf(this.epubFile));
		Json.prop(buf, "extractedSize", totalSize);
		buf.append('}');

		Json.key(buf, "breakdown");
		buf.append('[');
		boolean first = true;
		for (Category category : Category.values()) {
			int count = counts.get(category)[0];
			if (count == 0) continue;
			if (!first) buf.append(',');
			first = false;
			buf.append('{');
			Json.prop(buf, "category", category.key);
			Json.prop(buf, "count", count);
			Json.prop(buf, "size", sizes.get(category)[0]);
			buf.append('}');
		}
		buf.append(']');

		Json.key(buf, "cssFiles");
		buf.append('[');
		for (int i = 0; i < cssPaths.size(); i++) {
			if (i > 0) buf.append(',');
			buf.append(Json.str(cssPaths.get(i)));
		}
		buf.append(']');

		Json.key(buf, "fonts");
		buf.append('[');
		for (int i = 0; i < fonts.size(); i++) {
			if (i > 0) buf.append(',');
			ManifestItem font = fonts.get(i);
			buf.append('{');
			Json.prop(buf, "path", font.path());
			Json.prop(buf, "mediaType", font.mediaType());
			Json.prop(buf, "size", fileSize(font.path()));
			buf.append('}');
		}
		buf.append(']');

		buf.append('}');
		return buf.toString();
	}

	/** 展開先ディレクトリ全体のサイズ */
	private long extractedSize()
	{
		try (java.util.stream.Stream<Path> files = Files.walk(this.epubRoot)) {
			return files.filter(Files::isRegularFile).mapToLong(EpubInspection::fileSizeOf).sum();
		} catch (IOException e) {
			/* 意図的: 走査できない場合は 0 として表示を続ける */
			return 0;
		}
	}

	private long fileSize(String relativePath)
	{
		Path file = PathUtils.resolveInside(this.epubRoot, relativePath);
		return (file == null) ? 0 : fileSizeOf(file);
	}

	private static long fileSizeOf(Path file)
	{
		try {
			return Files.isRegularFile(file) ? Files.size(file) : 0;
		} catch (IOException e) {
			/* 意図的: サイズが取れないファイルは 0 として集計を続ける */
			return 0;
		}
	}
}
