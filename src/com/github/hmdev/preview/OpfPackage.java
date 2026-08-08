package com.github.hmdev.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OPF (package document) から読み取った内容。
 *
 * <p>パスは全て「EPUB ルートからの相対パス」に正規化されており、
 * 区切りは常にスラッシュ。</p>
 */
public class OpfPackage
{
	/** OPF 自身のパス (例: OPS/package.opf) */
	private final String opfPath;
	/** OPF が置かれたディレクトリ (例: OPS。ルート直下なら空文字) */
	private final String opfDir;
	/** package/@version。EPUB 3.x は "3.0" が正しい値 */
	private String version = "";
	/** spine/@page-progression-direction */
	private String pageProgressionDirection = "";
	/** spine/@toc (toc.ncx を指す manifest id) */
	private String ncxIdref = "";
	/** EPUB2 の meta[name=cover]/@content (表紙画像を指す manifest id) */
	private String coverIdref = "";

	private String title;
	private String creator;
	private String publisher;
	private String language;
	private String identifier;
	private String modified;
	/** meta[property=rendition:layout] */
	private String renditionLayout;
	/** meta[name=primary-writing-mode] (Kindle 向けヒント) */
	private String primaryWritingMode;

	private final Map<String, ManifestItem> manifestById = new LinkedHashMap<>();
	private final List<SpineItem> spine = new ArrayList<>();

	OpfPackage(String opfPath)
	{
		this.opfPath = opfPath;
		int slash = opfPath.lastIndexOf('/');
		this.opfDir = (slash < 0) ? "" : opfPath.substring(0, slash);
	}

	/**
	 * OPF からの相対 href を EPUB ルートからの相対パスに解決する。
	 * href は URI なのでパーセントエスケープを解いてから ZIP エントリ名に合わせる。
	 */
	String resolve(String href)
	{
		if (href == null) return null;
		String cleaned = PathUtils.stripQueryAndFragment(href);
		if (cleaned.isEmpty()) return null;
		cleaned = PathUtils.decodeUri(cleaned);
		// 先頭スラッシュは EPUB ルートからの参照。OPF のディレクトリを付けてはいけない
		if (cleaned.startsWith("/")) return PathUtils.normalizeRelative(cleaned);
		String combined = this.opfDir.isEmpty() ? cleaned : this.opfDir + "/" + cleaned;
		return PathUtils.normalizeRelative(combined);
	}

	public String getOpfPath() { return this.opfPath; }
	public String getOpfDir() { return this.opfDir; }
	public String getVersion() { return this.version; }
	public String getPageProgressionDirection() { return this.pageProgressionDirection; }
	public String getTitle() { return this.title; }
	public String getCreator() { return this.creator; }
	public String getPublisher() { return this.publisher; }
	public String getLanguage() { return this.language; }
	public String getIdentifier() { return this.identifier; }
	public String getModified() { return this.modified; }
	public String getRenditionLayout() { return this.renditionLayout; }
	public String getPrimaryWritingMode() { return this.primaryWritingMode; }

	/** manifest 全項目 (OPF の記述順) */
	public List<ManifestItem> getManifest() { return new ArrayList<>(this.manifestById.values()); }

	/** id から manifest 項目を引く。無ければ null */
	public ManifestItem getManifestItem(String id) { return this.manifestById.get(id); }

	/** spine (linear="no" を除いた読み順) */
	public List<SpineItem> getSpine() { return this.spine; }

	/** spine 内でのインデックスを返す。含まれなければ -1 */
	public int indexOfSpine(String path)
	{
		for (int i = 0; i < this.spine.size(); i++) {
			if (this.spine.get(i).path().equals(path)) return i;
		}
		return -1;
	}

	/** properties="nav" の項目のパス。無ければ null */
	public String getNavPath()
	{
		for (ManifestItem item : this.manifestById.values()) {
			if (item.hasProperty("nav")) return item.path();
		}
		return null;
	}

	/** toc.ncx のパス。spine/@toc から引く。無ければ media-type で探す */
	public String getNcxPath()
	{
		if (!this.ncxIdref.isEmpty()) {
			ManifestItem item = this.manifestById.get(this.ncxIdref);
			if (item != null) return item.path();
		}
		for (ManifestItem item : this.manifestById.values()) {
			if ("application/x-dtbncx+xml".equals(item.mediaType())) return item.path();
		}
		return null;
	}

	/**
	 * 表紙画像の EPUB ルート相対パス。無ければ null。
	 *
	 * <p>EPUB3 の {@code properties="cover-image"} を優先し、無ければ
	 * EPUB2 の {@code <meta name="cover" content="id">} を見る。
	 * どちらも無い EPUB があるため、最後に「id か href に cover を含む画像」を
	 * 拾う推測を入れている (本棚のサムネイル用であり、外すことがあっても
	 * 表紙なしとして表示されるだけ)。</p>
	 */
	public String getCoverImagePath()
	{
		for (ManifestItem item : this.manifestById.values()) {
			if (item.hasProperty("cover-image")) return item.path();
		}
		if (!this.coverIdref.isEmpty()) {
			ManifestItem item = this.manifestById.get(this.coverIdref);
			// meta[name=cover] が XHTML の表紙ページを指している EPUB があるので、
			// 画像であることを確かめる
			if (item != null && isImage(item)) return item.path();
		}
		for (ManifestItem item : this.manifestById.values()) {
			if (!isImage(item)) continue;
			String id = item.id().toLowerCase(java.util.Locale.ROOT);
			String href = item.href().toLowerCase(java.util.Locale.ROOT);
			if (id.contains("cover") || href.contains("cover")) return item.path();
		}
		return null;
	}

	private static boolean isImage(ManifestItem item)
	{
		return item.mediaType() != null && item.mediaType().startsWith("image/");
	}

	void setVersion(String version) { this.version = (version == null) ? "" : version; }
	void setCoverIdref(String value) { this.coverIdref = (value == null) ? "" : value; }
	void setPageProgressionDirection(String value) { this.pageProgressionDirection = (value == null) ? "" : value; }
	void setNcxIdref(String value) { this.ncxIdref = (value == null) ? "" : value; }
	void setTitle(String value) { this.title = value; }
	void setCreator(String value) { this.creator = value; }
	void setPublisher(String value) { this.publisher = value; }
	void setLanguage(String value) { this.language = value; }
	void setIdentifier(String value) { this.identifier = value; }
	void setModified(String value) { this.modified = value; }
	void setRenditionLayout(String value) { this.renditionLayout = value; }
	void setPrimaryWritingMode(String value) { this.primaryWritingMode = value; }

	void addManifestItem(ManifestItem item) { this.manifestById.put(item.id(), item); }
	void addSpineItem(SpineItem item) { this.spine.add(item); }
}
