import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Supplier;

import com.github.hmdev.preview.BookUpdater;

/**
 * 本棚の「続きを取る」（internal #11）。本の掲載元を取り直し、GUI と同じ設定で本棚の本を上書きする。
 *
 * <p>設定は、更新のたびに取り直す（GUI から開いたときは画面の今の値、CLI から開いたときは ini）。
 * 手元・内部の宛先（127.0.0.1・[::1]・プライベートの範囲・localhost）は取りに行かない（internal #19）。
 * そもそもサイト定義の無いホストには行かないが、細工した EPUB は掲載元を自由に書けるので、二重に止める。</p>
 */
public class HeadlessBookUpdater implements BookUpdater
{
	/** 試験で手元のサーバを使うときだけ立てる（本番では立てない） */
	static final String ALLOW_LOCAL_PROPERTY = "aozora.allowLocalSource";

	private final Supplier<Properties> settings;
	private final String basePath;
	/** Web 本棚の、記録とキャッシュを置くフォルダの名前（internal #11 の案 A） */
	static final String SHELF_CACHE = ".aozora";

	/**
	 * 設定とキャッシュの場所から画面なしの変換を作る。試験では、専用の VelocityEngine を渡した書き出しで作る（静的な Velocity を使わない）
	 */
	java.util.function.BiFunction<Properties, File, HeadlessWebConversion> conversions;

	/**
	 * @param settings 更新のたびに呼ぶ。GUI の ini と同じ形の設定
	 * @param basePath template/・web/・setting_narourb.ini などのあるフォルダ（末尾に区切り。GUI は ""）
	 */
	public HeadlessBookUpdater(Supplier<Properties> settings, String basePath)
	{
		this.settings = settings;
		this.basePath = basePath;
		this.conversions = (props, cache) -> new HeadlessWebConversion(props, basePath, cache);
	}

	@Override
	public Result update(String sourceUrl, Path epubFile) throws Exception
	{
		return update(sourceUrl, epubFile, false);
	}

	/** 本棚の更新は、目次が取れないとき・話数が減ったときは書かずに止める（internal #11 の守り） */
	@Override
	public Result update(String sourceUrl, Path epubFile, boolean allowFewerEpisodes) throws Exception
	{
		if (isLocalOrPrivate(sourceUrl) && !Boolean.getBoolean(ALLOW_LOCAL_PROPERTY)) {
			return new Result(false, false, "手元・内部の宛先は取りに行きません: " + sourceUrl);
		}
		//設定は Web 変換の鍵を取ってから写す。GUI は Web 変換の間、画面の部品を一時的に書き換えている（表紙・文字コード・
		//コメント）ので、その間に写すと一時的な値（別の作品の表紙など）が入る（PR #118 のゲート2）
		synchronized (com.github.hmdev.web.WebAozoraConverter.WEB_LOCK) {
			Properties props = this.settings.get();
			HeadlessWebConversion.Result r = this.conversions.apply(props, cacheFor(props, epubFile))
				.convert(sourceUrl, epubFile.toAbsolutePath().getParent().toFile(), epubFile.toFile(), true, true, allowFewerEpisodes);
			return new Result(r.ok(), r.noUpdate(), r.message(), r.stop());
		}
	}

	/**
	 * Web 本棚に新しく落とす。キャッシュは Web 本棚の .aozora（本と記録を一緒に写せる。設定のキャッシュの場所に依らない）
	 */
	@Override
	public Result download(String url, Path shelfDir) throws Exception
	{
		if (isLocalOrPrivate(url) && !Boolean.getBoolean(ALLOW_LOCAL_PROPERTY)) {
			return new Result(false, false, "手元・内部の宛先は取りに行きません: " + url);
		}
		synchronized (com.github.hmdev.web.WebAozoraConverter.WEB_LOCK) {
			Properties props = this.settings.get();
			File shelf = shelfDir.toFile();
			HeadlessWebConversion.Result r = this.conversions.apply(props, new File(shelf, SHELF_CACHE)).convertNewBook(url, shelf);
			return new Result(r.ok(), r.noUpdate(), r.message(), r.stop());
		}
	}

	/** 本のキャッシュの場所。本の隣に .aozora があれば（Web 本棚の本）そこ、無ければ設定のキャッシュ */
	File cacheFor(Properties props, Path epubFile)
	{
		File shelfCache = new File(epubFile.toAbsolutePath().getParent().toFile(), SHELF_CACHE);
		return shelfCache.isDirectory() ? shelfCache : cachePathOf(props, this.basePath);
	}

	/**
	 * キャッシュの場所。ini の CachePath（GUI は空なら ".cache" を書く）、空なら ".cache"。
	 * 相対なら基のフォルダから（GUI の基は "" ＝カレント。CLI は jar の隣。別のフォルダから CLI を起こしても GUI と同じキャッシュになるように。
	 * PR #118 のゲート2）
	 */
	static File cachePathOf(Properties props, String basePath)
	{
		String value = props.getProperty("CachePath", "").trim();
		if (value.isEmpty()) value = ".cache";
		File file = new File(value);
		return file.isAbsolute() || basePath.isEmpty() ? file : new File(basePath + value);
	}

	/**
	 * URL のホストが手元・内部の宛先か。名前の引き直し（DNS）はしない: ホストが IP の書き方か localhost のときだけ見る
	 * （名前で手元を指すものは、サイト定義の無いホストには行かない制限で止まる）
	 */
	static boolean isLocalOrPrivate(String url)
	{
		String rest = url.substring(url.indexOf("://") + 3);
		int end = rest.length();
		for (char c : new char[]{ '/', '?', '#' }) {
			int i = rest.indexOf(c);
			if (i >= 0 && i < end) end = i;
		}
		String host = rest.substring(0, end);
		//利用者情報（user@）を除く（PR #118 のゲート2。本棚の確かめで弾いているが、ここだけでも止まるように）
		int at = host.lastIndexOf('@');
		if (at >= 0) host = host.substring(at + 1);
		if (host.startsWith("[")) {
			host = host.substring(1, Math.max(1, host.indexOf(']')));
		} else {
			int colon = host.lastIndexOf(':');
			if (colon >= 0) host = host.substring(0, colon);
		}
		host = host.toLowerCase(Locale.ROOT);
		if (host.equals("localhost") || host.endsWith(".localhost")) return true;
		boolean ipLiteral = host.indexOf(':') >= 0 || host.matches("[0-9.]+");
		if (!ipLiteral) return false;
		try {
			InetAddress address = InetAddress.getByName(host);
			if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
				|| address.isAnyLocalAddress()) return true;
			//IPv6 の ULA（fc00::/7）は Java では site-local と見なされない
			byte[] bytes = address.getAddress();
			if (bytes.length == 16) return (bytes[0] & 0xfe) == 0xfc;
			//CGNAT（100.64.0.0/10）と 0.0.0.0/8 も
			int b0 = bytes[0] & 0xff;
			int b1 = bytes[1] & 0xff;
			return (b0 == 100 && b1 >= 64 && b1 <= 127) || b0 == 0;
		} catch (Exception e) {
			//IP の書き方なのに読めないものは取りに行かない
			return true;
		}
	}
}
