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

	/**
	 * @param settings 更新のたびに呼ぶ。GUI の ini と同じ形の設定
	 * @param basePath template/・web/・setting_narourb.ini などのあるフォルダ（末尾に区切り。GUI は ""）
	 */
	public HeadlessBookUpdater(Supplier<Properties> settings, String basePath)
	{
		this.settings = settings;
		this.basePath = basePath;
	}

	@Override
	public Result update(String sourceUrl, Path epubFile) throws Exception
	{
		if (isLocalOrPrivate(sourceUrl) && !Boolean.getBoolean(ALLOW_LOCAL_PROPERTY)) {
			return new Result(false, false, "手元・内部の宛先は取りに行きません: " + sourceUrl);
		}
		Properties props = this.settings.get();
		HeadlessWebConversion.Result r = new HeadlessWebConversion(props, this.basePath, cachePathOf(props, this.basePath))
			.convert(sourceUrl, epubFile.toAbsolutePath().getParent().toFile(), epubFile.toFile(), true);
		return new Result(r.ok(), r.noUpdate(), r.message());
	}

	/** キャッシュの場所。GUI と同じく ini の CachePath（相対ならカレントから）、空なら基のフォルダの .cache */
	static File cachePathOf(Properties props, String basePath)
	{
		String value = props.getProperty("CachePath", "").trim();
		return value.isEmpty() ? new File(basePath + ".cache") : new File(value);
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
			return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
		} catch (Exception e) {
			//IP の書き方なのに読めないものは取りに行かない
			return true;
		}
	}
}
