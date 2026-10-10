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
			HeadlessWebConversion.Result r = this.conversions.apply(props, cacheFor(props, epubFile, sourceUrl))
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
		//もう落としてある作品なら、取りに行かずに知らせる（Web 本棚の下のフォルダに整理してあっても。PR の codex）
		Path existing = existingBook(shelfDir, url);
		if (existing != null) {
			return new Result(false, false, "もう Web 本棚にある本です: " + shelfDir.relativize(existing) + "（続きは本棚の ⟳ で取れます）");
		}
		synchronized (com.github.hmdev.web.WebAozoraConverter.WEB_LOCK) {
			Properties props = this.settings.get();
			File shelf = shelfDir.toFile();
			HeadlessWebConversion.Result r = this.conversions.apply(props, new File(shelf, SHELF_CACHE)).convertNewBook(url, shelf);
			return new Result(r.ok(), r.noUpdate(), r.message(), r.stop());
		}
	}

	/**
	 * 本のキャッシュの場所。本のフォルダか、その上のフォルダの .aozora に、この作品の台帳があれば（Web 本棚に落とした本。
	 * 下のフォルダに整理したときも）そこ、無ければ設定のキャッシュ。台帳を確かめるのは、前から本のあるフォルダを Web 本棚にしたとき、
	 * 前からの本まで .aozora に切り替わって、話数の記録（守り）と名前を失わないように（PR のゲート2・手元の codex）
	 */
	File cacheFor(Properties props, Path epubFile, String sourceUrl)
	{
		for (Path dir = epubFile.toAbsolutePath().getParent(); dir != null; dir = dir.getParent()) {
			File shelfCache = new File(dir.toFile(), SHELF_CACHE);
			if (shelfCache.isDirectory() && holdsWork(shelfCache.toPath(), sourceUrl)) return shelfCache;
		}
		return cachePathOf(props, this.basePath);
	}

	/**
	 * Web 本棚にもう落としてある、この作品の本（.aozora に台帳があり、その名前の本が Web 本棚のどこかにある）。無ければ null。
	 * . で始まるフォルダ（.aozora の 1 つ前の版など）は見ない
	 */
	static Path existingBook(Path shelfDir, String sourceUrl)
	{
		Path shelfCache = shelfDir.resolve(SHELF_CACHE);
		if (!java.nio.file.Files.isDirectory(shelfCache)) return null;
		String identifier = com.github.hmdev.info.BookLedger.identifierFor(sourceUrl);
		String name = null;
		try (java.util.stream.Stream<Path> files = java.nio.file.Files.walk(shelfCache, 8)) {
			name = files.filter(p -> p.getFileName().toString().equals(com.github.hmdev.info.BookLedger.FILE_NAME))
				.map(p -> com.github.hmdev.info.BookLedger.load(p.getParent().toFile()))
				.filter(l -> l != null && identifier.equals(l.identifier) && l.outputBaseName != null)
				.map(l -> l.outputBaseName).findFirst().orElse(null);
		} catch (java.io.IOException | java.io.UncheckedIOException e) {
			return null;
		}
		if (name == null) return null;
		String bookName = name;
		String prefix = name + ".";
		try (java.util.stream.Stream<Path> files = java.nio.file.Files.walk(shelfDir, com.github.hmdev.preview.LibraryScanner.DEFAULT_MAX_DEPTH)) {
			return files.filter(p -> {
				String file = p.getFileName().toString();
				if (!file.toLowerCase(java.util.Locale.ROOT).endsWith(".epub") || hiddenUnder(shelfDir, p)) return false;
				if (file.startsWith(prefix)) return true;
				//深いフォルダでは、名前がさらに縮められている（getOutFile と同じ決まり。PR の codex）
				try {
					for (String ext : new String[]{ ".epub", ".kepub.epub" }) {
						if (file.equals(AozoraEpub3.fittedTitleName(p.getParent().toFile(), bookName, ext) + ext)) return true;
					}
				} catch (java.io.IOException e) {
					/* 意図的: 長さを数えられなければ、縮めない名前だけで見る */
				}
				return false;
			}).findFirst().orElse(null);
		} catch (java.io.IOException | java.io.UncheckedIOException e) {
			return null;
		}
	}

	/** shelfDir から p までに . で始まるフォルダがあるか */
	private static boolean hiddenUnder(Path shelfDir, Path p)
	{
		for (Path part : shelfDir.relativize(p)) {
			if (part.toString().startsWith(".")) return true;
		}
		return false;
	}

	/** .aozora の中に、この作品の台帳があるか（サイトと作品のフォルダの下。深さは URL の作りによる） */
	static boolean holdsWork(Path shelfCache, String sourceUrl)
	{
		return ledgerDirOf(shelfCache, sourceUrl) != null;
	}

	/** キャッシュの中の、この作品の台帳のフォルダ。無ければ null */
	static Path ledgerDirOf(Path cache, String sourceUrl)
	{
		if (!java.nio.file.Files.isDirectory(cache)) return null;
		String identifier = com.github.hmdev.info.BookLedger.identifierFor(sourceUrl);
		try (java.util.stream.Stream<Path> files = java.nio.file.Files.walk(cache, 8)) {
			return files.filter(p -> p.getFileName().toString().equals(com.github.hmdev.info.BookLedger.FILE_NAME))
				.filter(p -> {
					com.github.hmdev.info.BookLedger l = com.github.hmdev.info.BookLedger.load(p.getParent().toFile());
					return l != null && identifier.equals(l.identifier);
				})
				.map(Path::getParent).findFirst().orElse(null);
		} catch (java.io.IOException | java.io.UncheckedIOException e) {
			return null;
		}
	}

	/**
	 * 本棚の本の名前を変える。本の隣に同じ名前があれば断る。台帳があれば、本ごとの話数と 1 つ前の版を新しい名前に動かし、
	 * 作品の名前がこの本の名前だったら（同じ作品の本が 1 冊のとき）作品の名前も変える（もう一度落とすときに見つけられるように）
	 */
	@Override
	public Result rename(String sourceUrl, Path epubFile, String newBaseName) throws Exception
	{
		String reason = com.github.hmdev.info.ShelfNames.invalidReason(newBaseName);
		if (reason != null) return new Result(false, false, reason);
		String fileName = epubFile.getFileName().toString();
		String lower = fileName.toLowerCase(Locale.ROOT);
		String ext = lower.endsWith(".kepub.epub") ? fileName.substring(fileName.length() - ".kepub.epub".length())
			: fileName.lastIndexOf('.') > 0 ? fileName.substring(fileName.lastIndexOf('.')) : "";
		String oldBase = fileName.substring(0, fileName.length() - ext.length());
		Path target = epubFile.resolveSibling(newBaseName + ext);
		if (target.equals(epubFile)) return new Result(true, false, "名前は同じです");
		//変換が本を書き換えている間に動かさない
		synchronized (com.github.hmdev.web.WebAozoraConverter.WEB_LOCK) {
			if (java.nio.file.Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
				&& !java.nio.file.Files.isSameFile(target, epubFile)) {
				return new Result(false, false, "同じ名前の本がもうあります: " + target.getFileName());
			}
			Properties props = this.settings.get();
			Path ledgerDir = ledgerDirOf(cacheFor(props, epubFile, sourceUrl).toPath(), sourceUrl);
			java.nio.file.Files.move(epubFile, target);
			if (ledgerDir != null) moveRecords(ledgerDir, epubFile, target, oldBase, newBaseName);
			return new Result(true, false, "名前を変えました: " + target.getFileName());
		}
	}

	/** 台帳の本ごとの記録を、新しい名前の本に動かす（失敗しても名前は変わっている。記録が古い名前のまま残るだけ） */
	private static void moveRecords(Path ledgerDir, Path from, Path to, String oldBase, String newBase)
	{
		File dir = ledgerDir.toFile();
		try {
			File previous = new File(dir, HeadlessWebConversion.previousEpubName(dir, from.toFile()));
			if (previous.isFile()) {
				java.nio.file.Files.move(previous.toPath(), new File(dir, HeadlessWebConversion.previousEpubName(dir, to.toFile())).toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			com.github.hmdev.info.BookLedger ledger = com.github.hmdev.info.BookLedger.load(dir);
			if (ledger == null) return;
			com.github.hmdev.info.BookLedger next = ledger;
			int own = ledger.ownEpisodesFor(from.toFile());
			if (own >= 0) next = next.withBookEpisodes(from.toFile(), -1).withBookEpisodes(to.toFile(), own);
			if (oldBase.equals(ledger.outputBaseName)) next = next.withOutputBaseName(newBase);
			if (next != ledger) next.save(dir);
		} catch (java.io.IOException e) {
			org.slf4j.LoggerFactory.getLogger(HeadlessBookUpdater.class).warn("台帳の記録を新しい名前に動かせませんでした: {}", ledgerDir, e);
		}
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
