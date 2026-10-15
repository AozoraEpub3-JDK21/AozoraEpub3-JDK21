package com.github.hmdev.preview;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
	 * 本棚に出す知らせ（JSON）。mayCheck で、確かめる間隔がたっていれば GitHub に問い合わせる（最大 15 秒、失敗しても
	 * 止めない）。mayCheck でなければ覚えている版だけで答える（表示設定の欄を開いたとき・閉じる・切り替えの応答・HEAD）。
	 * {@code {"available":true,"enabled":bool,"show":bool,"current":"…","latest":"…"|null,"url":"…"}}
	 *
	 * <p>問い合わせは鍵の外でする。鍵を持ったまま待つと、通信しない覗き・閉じる・切り替えまで最大 15 秒止まる
	 * （PR のゲート2）。同時に 2 つ目が来たら、問い合わせ中なので覚えている版で答える。結果を書くときはファイルを
	 * 読み直し、確かめた時刻と最新の版だけを書く（待っている間にほかの起動が書いた「閉じた版」などを消さない）。</p>
	 */
	public String json(boolean mayCheck)
	{
		long now = this.clock.getAsLong();
		boolean due;
		synchronized (this) {
			//読めない記録（壊れた・フォルダになった）なら確かめに行かない。上書きもしない（止めた設定を消さない。PR の検証）
			Properties p = read();
			due = mayCheck && p != null && !this.checking && isEnabled(p) && isDue(p, now);
			if (due) this.checking = true;
		}
		if (due) {
			UpdateChecker.Result r = null;
			try {
				r = this.checker.get();
			} catch (RuntimeException e) {
				logger.debug("新しい版を確かめられませんでした", e);
			}
			UpdateChecker.Result result = r;
			try {
				update(false, p -> {
					if (result != null && result.isSuccess() && result.latestVersion() != null && VERSION.matcher(result.latestVersion()).matches()) {
						p.setProperty(KEY_LATEST, result.latestVersion());
					} else if (result != null) {
						logger.debug("新しい版を確かめられませんでした: {}", result.error());
					}
					//失敗しても時刻は進める（つながらない台で、本棚を開くたびに待たせない）
					p.setProperty(KEY_CHECKED, Long.toString(now));
					return true;
				});
			} finally {
				synchronized (this) {
					this.checking = false;
				}
			}
		}
		synchronized (FILE_LOCK) {
			Properties p = read();
			return answer(p == null ? new Properties() : p);
		}
	}

	/** 問い合わせ中か（2 つ目の問い合わせを出さない） */
	private boolean checking;

	private static boolean isEnabled(Properties p)
	{
		return !"false".equals(p.getProperty(KEY_ENABLED));
	}

	/** 確かめる間隔がたったか。時計が戻ったとき（前の確認が未来）も確かめ直す */
	private static boolean isDue(Properties p, long now)
	{
		long checkedAt = parseLong(p.getProperty(KEY_CHECKED));
		return checkedAt <= 0 || now - checkedAt >= INTERVAL_MS || now < checkedAt;
	}

	private String answer(Properties p)
	{
		boolean enabled = isEnabled(p);
		String latest = p.getProperty(KEY_LATEST);
		String dismissed = p.getProperty(KEY_DISMISSED);
		boolean show = enabled && latest != null
			&& UpdateChecker.compareVersions(latest, this.currentVersion) > 0
			&& (dismissed == null || UpdateChecker.compareVersions(latest, dismissed) > 0);
		return "{\"available\":true,\"enabled\":" + enabled + ",\"show\":" + show
			+ ",\"current\":" + Json.str(this.currentVersion)
			+ ",\"latest\":" + Json.str(latest)
			+ ",\"url\":" + Json.str(UpdateChecker.DOWNLOAD_PAGE_URL) + "}";
	}

	/**
	 * その版の知らせを閉じる。その版より新しい版が出るまで出さない。版として読めなければ false。
	 * 閉じた版より古い版を閉じても、閉じた版は戻さない（古い画面から届いても、新しい版の帯を出し直さない。PR の検証）
	 */
	public boolean dismiss(String version)
	{
		if (version == null || !VERSION.matcher(version).matches()) return false;
		return update(true, p -> {
			String dismissed = p.getProperty(KEY_DISMISSED);
			if (dismissed == null || UpdateChecker.compareVersions(version, dismissed) > 0) p.setProperty(KEY_DISMISSED, version);
			return true;
		});
	}

	/** 確かめに行くかを切り替える。オンに戻したときは、次に本棚を開いたときに（24 時間たっていなくても）確かめ直す */
	public boolean setEnabled(boolean enabled)
	{
		return update(true, p -> {
			p.setProperty(KEY_ENABLED, Boolean.toString(enabled));
			if (enabled) p.remove(KEY_CHECKED);
			return true;
		});
	}

	/**
	 * 同じ JVM の中の読み書きをそろえる鍵。ファイルの鍵（{@link FileChannel#lock}）は同じ JVM の中では重ねて取れない
	 * （OverlappingFileLockException）ので、インスタンスではなくクラスで持つ
	 */
	private static final Object FILE_LOCK = new Object();

	/**
	 * 記録を読み直して変え、書く。ほかのプロセス（GUI と CLI の本棚）とは、隣の update-check.lock の鍵でそろえる
	 * （読んでから書くまでに、ほかの起動の書いた値を消さない。PR の codex）。
	 * 読めない記録は、explicit（利用者が切り替えた・閉じた）のときだけ新しく書き直す。自動の確認では触らない
	 */
	private boolean update(boolean explicit, java.util.function.Predicate<Properties> change)
	{
		synchronized (FILE_LOCK) {
			Path parent = this.file.toAbsolutePath().getParent();
			try {
				Files.createDirectories(parent);
				try (FileChannel channel = FileChannel.open(parent.resolve("update-check.lock"),
						StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					FileLock lock = channel.lock()) {
					Properties p = read();
					if (p == null) {
						if (!explicit) return false;
						p = new Properties();
					}
					return change.test(p) && save(p);
				}
			} catch (IOException | RuntimeException e) {
				/* 意図的: 鍵を取れなくても本棚は使える（次も確かめに行くだけ） */
				logger.debug("更新の確認の記録を書けませんでした: {}", this.file, e);
				return false;
			}
		}
	}

	/** 記録。無ければ空。あるのに読めない（壊れた・フォルダ・読む権限が無い）なら null */
	private Properties read()
	{
		Properties p = new Properties();
		if (!Files.exists(this.file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return p;
		if (!Files.isRegularFile(this.file)) return null;
		try (Reader r = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (IOException | IllegalArgumentException e) {
			logger.debug("更新の確認の記録を読めませんでした: {}", this.file, e);
			return null;
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
}
