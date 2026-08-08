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

	void setVersion(String version) { this.version = (version == null) ? "" : version; }
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
