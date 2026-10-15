package com.github.hmdev.preview;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hmdev.update.UpdateChecker;

/**
 * 本棚の上に「新しい版があります」を出すかを決める（internal #48）。
 *
 * <p>利用者の決定（2026-10-15）: 本棚を開いたときに確かめる。前の確認から 24 時間たっていなければ外に通信せず、
 * 覚えている最新の版で決める。閉じたら、その版ではもう出さない（もっと新しい版が出たらまた出す）。
 * 確かめに行くのは切れる（既定はオン）。</p>
 *
 * <p>状態はホームの {@code .aozoraepub3/update-check.properties} に置く。ini に置かないのは、GUI が終わるときに
 * 自分の持つ値で ini を書き戻し、サーバの書いた値を消すため（WebShelf.java の注）。表示設定の
 * preview-settings.json はページが丸ごと書くので、そこにも混ぜない。</p>
 */
public class UpdateNotice
{
	private static final Logger logger = LoggerFactory.getLogger(UpdateNotice.class);

	/** 確かめる間隔。これより前に確かめていれば外に通信しない */
	static final long INTERVAL_MS = 24L * 60 * 60 * 1000;

	/** 版として受け取る形（閉じた版を書くとき、ファイルに変な値を残さない） */
	private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z._-]{1,40}");

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_CHECKED = "checkedAt";
	private static final String KEY_LATEST = "latest";
	private static final String KEY_DISMISSED = "dismissed";

	private final Path file;
	private final String currentVersion;
	private final Supplier<UpdateChecker.Result> checker;
	private final LongSupplier clock;

	/** GUI・CLI から。今の版で GitHub の最新のリリースを確かめる */
	public UpdateNotice(String currentVersion)
	{
		this(Path.of(System.getProperty("user.home", "."), ".aozoraepub3", "update-check.properties"),
			currentVersion, () -> UpdateChecker.check(currentVersion), System::currentTimeMillis);
	}

	UpdateNotice(Path file, String currentVersion, Supplier<UpdateChecker.Result> checker, LongSupplier clock)
	{
		this.file = file;
		this.currentVersion = currentVersion;
		this.checker = checker;
		this.clock = clock;
	}

	/** 本棚に出す知らせ（JSON）。確かめる間隔がたっていれば GitHub に問い合わせる */
	public String json()
	{
		return json(true);
	}

	/**
	 * 本棚に出す知らせ（JSON）。mayCheck で、確かめる間隔がたっていれば GitHub に問い合わせる（最大 10 秒、失敗しても
	 * 止めない）。mayCheck でなければ覚えている版だけで答える（表示設定の欄を開いたとき。本棚を開くまでは通信しない）。
	 * {@code {"available":true,"enabled":bool,"show":bool,"current":"…","latest":"…"|null,"url":"…"}}
	 */
	public synchronized String json(boolean mayCheck)
	{
		Properties p = load();
		boolean enabled = !"false".equals(p.getProperty(KEY_ENABLED));
		String latest = p.getProperty(KEY_LATEST);
		if (enabled && mayCheck) {
			long now = this.clock.getAsLong();
			long checkedAt = parseLong(p.getProperty(KEY_CHECKED));
			//時計が戻ったとき（前の確認が未来）も確かめ直す
			if (checkedAt <= 0 || now - checkedAt >= INTERVAL_MS || now < checkedAt) {
				UpdateChecker.Result r = this.checker.get();
				if (r.isSuccess() && r.latestVersion() != null && VERSION.matcher(r.latestVersion()).matches()) {
					latest = r.latestVersion();
					p.setProperty(KEY_LATEST, latest);
				} else {
					logger.debug("新しい版を確かめられませんでした: {}", r.error());
				}
				//失敗しても時刻は進める（つながらない台で、本棚を開くたびに 10 秒待たせない）
				p.setProperty(KEY_CHECKED, Long.toString(now));
				save(p);
			}
		}
		String dismissed = p.getProperty(KEY_DISMISSED);
		boolean show = enabled && latest != null
			&& UpdateChecker.compareVersions(latest, this.currentVersion) > 0
			&& (dismissed == null || UpdateChecker.compareVersions(latest, dismissed) > 0);
		return "{\"available\":true,\"enabled\":" + enabled + ",\"show\":" + show
			+ ",\"current\":" + quote(this.currentVersion)
			+ ",\"latest\":" + (latest == null ? "null" : quote(latest))
			+ ",\"url\":" + quote(UpdateChecker.DOWNLOAD_PAGE_URL) + "}";
	}

	/** その版の知らせを閉じる。その版より新しい版が出るまで出さない。版として読めなければ false */
	public synchronized boolean dismiss(String version)
	{
		if (version == null || !VERSION.matcher(version).matches()) return false;
		Properties p = load();
		p.setProperty(KEY_DISMISSED, version);
		return save(p);
	}

	/** 確かめに行くかを切り替える。オンに戻したときは、次に本棚を開いたときに確かめ直す */
	public synchronized boolean setEnabled(boolean enabled)
	{
		Properties p = load();
		p.setProperty(KEY_ENABLED, Boolean.toString(enabled));
		if (enabled) p.remove(KEY_CHECKED);
		return save(p);
	}

	private Properties load()
	{
		Properties p = new Properties();
		if (!Files.isRegularFile(this.file)) return p;
		try (Reader r = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (IOException | IllegalArgumentException e) {
			/* 意図的: 読めなければ何も覚えていないとして動かす */
			logger.debug("更新の確認の記録を読めませんでした: {}", this.file, e);
			return new Properties();
		}
		return p;
	}

	/** 一時ファイルに書いて置き換える（途中で止まっても壊れたファイルを残さない） */
	private boolean save(Properties p)
	{
		try {
			Path parent = this.file.toAbsolutePath().getParent();
			Files.createDirectories(parent);
			Path tmp = Files.createTempFile(parent, ".update-check.", ".tmp");
			try {
				try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
					p.store(w, "AozoraEpub3: the shelf's new-version notice (internal #48)");
				}
				try {
					Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (java.nio.file.AtomicMoveNotSupportedException e) {
					Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(tmp);
			}
			return true;
		} catch (IOException e) {
			/* 意図的: 書けなくても本棚は使える（次も確かめに行くだけ） */
			logger.debug("更新の確認の記録を書けませんでした: {}", this.file, e);
			return false;
		}
	}

	private static long parseLong(String s)
	{
		try { return s == null ? 0 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
	}

	private static String quote(String s)
	{
		StringBuilder b = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			if (c == '"' || c == '\\') b.append('\\').append(c);
			else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
			else b.append(c);
		}
		return b.append('"').toString();
	}
}
