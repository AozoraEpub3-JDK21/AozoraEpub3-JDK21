package com.github.hmdev.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * GUI の Look and Feel（FlatLaf）とテーマ切り替えを一元管理するユーティリティ。
 *
 * <p>CLI（{@code AozoraEpub3}）からは呼ばれない。{@code AozoraEpub3Applet.main()} の
 * CLI 委譲分岐より後でのみ使用すること。</p>
 */
public final class UiThemeManager
{
	private static final Logger logger = LoggerFactory.getLogger(UiThemeManager.class);

	/** テーマ設定を保存する ini キー（全体設定 AozoraEpub3.ini） */
	public static final String INI_KEY = "UiTheme";

	/** OS ダークモード検出コマンドのタイムアウト（秒） */
	private static final long DETECT_TIMEOUT_SEC = 1;

	/** 出力読み取りスレッドの終了待ち上限（ミリ秒） */
	private static final long READER_JOIN_TIMEOUT_MS = 200;

	/** L&F からフォントサイズを取得できなかった場合の UI フォントサイズ（pt） */
	private static final int FALLBACK_FONT_SIZE = 12;

	/** テーマ種別 */
	public enum Mode
	{
		SYSTEM("system"),
		LIGHT("light"),
		DARK("dark");

		private final String iniValue;

		Mode(String iniValue) { this.iniValue = iniValue; }

		/** ini に保存する文字列表現 */
		public String iniValue() { return this.iniValue; }

		/** ini 文字列から Mode へ。未知・null は SYSTEM */
		public static Mode fromIni(String value)
		{
			if (value != null) {
				String v = value.trim().toLowerCase(java.util.Locale.ROOT);
				for (Mode m : values()) {
					if (m.iniValue.equals(v)) return m;
				}
			}
			return SYSTEM;
		}
	}

	private UiThemeManager() {}

	////////////////////////////////////////////////////////////////
	// OS ダークモード検出
	////////////////////////////////////////////////////////////////
	/**
	 * OS がダークモードかどうかを best-effort で判定する。
	 * 判定できない場合・例外・タイムアウトはすべて false（ライト扱い）。
	 */
	public static boolean isSystemDark()
	{
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		try {
			if (os.contains("windows")) {
				String out = runCommand("reg", "query",
					"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
					"/v", "AppsUseLightTheme");
				if (out == null) return false;
				//0x0 ならダーク (AppsUseLightTheme = 0)
				return out.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT).contains("0x0");
			} else if (os.contains("mac")) {
				String out = runCommand("defaults", "read", "-g", "AppleInterfaceStyle");
				return out != null && out.toLowerCase(java.util.Locale.ROOT).contains("dark");
			} else {
				String out = runCommand("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
				return out != null && out.toLowerCase(java.util.Locale.ROOT).contains("dark");
			}
		} catch (Throwable t) {
			logger.debug("OS ダークモード検出に失敗したためライト扱いにします", t);
			return false;
		}
	}

	/**
	 * 外部コマンドを実行して標準出力を返す。異常終了・タイムアウト時は null。
	 * GUI 起動をブロックしないよう {@link #DETECT_TIMEOUT_SEC} 秒で打ち切る。
	 *
	 * <p>出力の読み取りは必ず別スレッドで行うこと。呼び出しスレッドで EOF まで読むと、
	 * 何も出力せず終了もしないコマンドに当たったときに read() で無期限ブロックし、
	 * waitFor() のタイムアウトに到達できず GUI 起動が止まる。</p>
	 */
	private static String runCommand(String... command)
	{
		Process process = null;
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			process = pb.start();
			final Process target = process;
			final StringBuilder sb = new StringBuilder();
			Thread reader = new Thread(() -> {
				try (InputStream is = target.getInputStream()) {
					byte[] buf = new byte[1024];
					int len;
					while ((len = is.read(buf)) > 0) {
						synchronized (sb) {
							sb.append(new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8));
							if (sb.length() > 8192) break;
						}
					}
				} catch (Throwable ignore) { /* 意図的: 読み取り失敗時は出力なしとして扱う */ }
			}, "ui-theme-detect");
			reader.setDaemon(true);
			reader.start();

			if (!process.waitFor(DETECT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			//プロセス終了後は EOF まで即座に読めるが、念のため上限を設けて待つ
			reader.join(READER_JOIN_TIMEOUT_MS);
			if (process.exitValue() != 0) return null;
			synchronized (sb) { return sb.toString(); }
		} catch (InterruptedException e) {
			//割り込みステータスを復元してから既定 (ライト) 扱いにする
			Thread.currentThread().interrupt();
			return null;
		} catch (Throwable t) {
			return null;
		} finally {
			if (process != null && process.isAlive()) process.destroyForcibly();
		}
	}

	////////////////////////////////////////////////////////////////
	// フォント
	////////////////////////////////////////////////////////////////
	/**
	 * UI 個別コンポーネントで使う推奨日本語フォント名（OS 別候補から選択）。
	 * 英語 OS で日本語字形が中華フォントに化けるのを防ぐため、候補は OS ごとに固定。
	 * 候補が 1 つも見つからなければ論理フォント Dialog。
	 */
	public static String getPreferredJapaneseFontName()
	{
		try {
			String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
			String[] candidates;
			if (os.contains("windows")) {
				candidates = new String[]{"Yu Gothic UI", "Meiryo", "Yu Gothic", "MS UI Gothic", "MS Gothic"};
			} else if (os.contains("mac")) {
				candidates = new String[]{"Hiragino Sans", "Hiragino Kaku Gothic ProN", "Hiragino Kaku Gothic Pro"};
			} else {
				candidates = new String[]{"Noto Sans CJK JP", "Noto Sans JP", "IPAGothic", "VL Gothic", "TakaoGothic"};
			}
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			Set<String> available = new HashSet<>();
			for (String name : ge.getAvailableFontFamilyNames()) available.add(name);
			for (String c : candidates) if (available.contains(c)) return c;
		} catch (Throwable ignore) { /* 意図的: AWT 未初期化等で候補不可なら論理フォント Dialog で続行 */ }
		return Font.DIALOG;
	}

	////////////////////////////////////////////////////////////////
	// 適用
	////////////////////////////////////////////////////////////////
	/**
	 * 起動時の Look and Feel 設定。
	 * @param mode テーマ（null は SYSTEM 扱い）
	 * @param fontFamily UI 既定フォント名（null なら {@link #getPreferredJapaneseFontName()}）
	 */
	public static void setup(Mode mode, String fontFamily)
	{
		try {
			installLaf(mode, fontFamily);
		} catch (Throwable t) {
			logger.warn("Look and Feel の初期化に失敗しました。既定の L&F で続行します", t);
		}
	}

	/**
	 * 実行時のテーマ切り替え。EDT から呼ぶこと。
	 * {@link FlatLaf#updateUI()} が表示中の全ウィンドウのコンポーネントツリーを更新する。
	 * （切り替えアニメーション FlatAnimatedLafChange は flatlaf-extras 側のため未使用）
	 * @param mode テーマ（null は SYSTEM 扱い）
	 * @param fontFamily UI 既定フォント名（null なら {@link #getPreferredJapaneseFontName()}）
	 */
	public static void switchTo(Mode mode, String fontFamily)
	{
		try {
			installLaf(mode, fontFamily);
			FlatLaf.updateUI();
		} catch (Throwable t) {
			logger.warn("テーマ切り替えに失敗しました", t);
		}
	}

	/**
	 * UIManager から色を取得する。キーが無い L&F ではフォールバック色を返す。
	 */
	public static Color uiColor(String key, Color fallback)
	{
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	/**
	 * L&F を適用し、UI 既定フォントのファミリだけを日本語対応フォントへ差し替える。
	 *
	 * <p>フォントサイズは L&F が OS から取得した値（Windows のテキスト拡大設定など）を
	 * そのまま使う。{@code defaultFont} は L&F の初期化時に読まれるため、
	 * サイズを知るために一度 L&F を適用してから設定し、もう一度適用する。</p>
	 */
	private static void installLaf(Mode mode, String fontFamily) throws Exception
	{
		setLookAndFeel(mode);
		if (applyDefaultFont(fontFamily)) setLookAndFeel(mode);
	}

	/** defaultFont を UIManager に設定（FlatLaf のスケーリング機構に乗せる）。設定したら true */
	private static boolean applyDefaultFont(String fontFamily)
	{
		String family = (fontFamily == null || fontFamily.isEmpty()) ? getPreferredJapaneseFontName() : fontFamily;
		Font base = UIManager.getFont("defaultFont");
		if (base == null) base = UIManager.getFont("Label.font");
		int size = (base != null && base.getSize() > 0) ? base.getSize() : FALLBACK_FONT_SIZE;
		if (base != null && family.equals(base.getFamily()) && base.getSize() == size
			&& base.getStyle() == Font.PLAIN && base instanceof FontUIResource) {
			//既に同じ指定。再適用不要（L&F の二重インストールを避ける）
			return false;
		}
		UIManager.put("defaultFont", new FontUIResource(family, Font.PLAIN, size));
		return true;
	}

	/** モードを解決して FlatLightLaf / FlatDarkLaf を適用 */
	private static void setLookAndFeel(Mode mode) throws Exception
	{
		boolean dark = (mode == Mode.DARK) || (mode != Mode.LIGHT && isSystemDark());
		if (dark) UIManager.setLookAndFeel(new FlatDarkLaf());
		else UIManager.setLookAndFeel(new FlatLightLaf());
	}
}
