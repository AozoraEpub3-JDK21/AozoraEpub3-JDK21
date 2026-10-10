import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hmdev.config.SettingDefaults;
import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.pipeline.GuiConversionSettings;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.web.NarouFormatSettings;
import com.github.hmdev.web.WebAozoraConverter;
import com.github.hmdev.writer.Epub3ImageWriter;
import com.github.hmdev.writer.Epub3Writer;

/**
 * Web の作品を、画面を使わずに GUI と同じ設定で EPUB にする（本棚の「続きを取る」。internal #11 の H2）。
 *
 * <p>GUI の Web 変換（{@code AozoraEpub3Applet.convertWeb} → {@code convertFiles} → {@code convertFile}）が画面の部品から読む値を、
 * GUI が保存した ini（{@code setProperties} の形）から作り直す。GUI が Web 変換のときだけ固定する値（入力は UTF-8・表題の種類は 0・
 * ファイル名を表題に使わない・コメントを出して変換する・表紙は「入力と同じ名前の画像」）も同じにする。</p>
 *
 * <p>画面に依るもの（変換前確認・表紙の履歴・出力先の選択・kindlegen）は扱わない。kindle（.mobi）の出力は断る。</p>
 */
public class HeadlessWebConversion
{
	static final Logger logger = LoggerFactory.getLogger(HeadlessWebConversion.class);

	/** GUI が「入力と同じ名前の画像」・「表紙なし」を ini に書く値（AozoraEpub3Applet.COVER_SAME_FILE / COVER_NONE） */
	static final String COVER_SAME_FILE = "#samefile";
	static final String COVER_NONE = "#none";

	/**
	 * 上書きの前の本を残す名前（作品のフォルダの中）。本ごとに分ける: 同じ作品の本が本棚に 2 冊あると、
	 * 片方の更新がもう片方の 1 つ前の版を消していた（PR #120 の win2 の確認）
	 */
	static String previousEpubName(File workDir, File book)
	{
		//どの本の版かが名前で分かるよう、本の名前を添える。同じ名前の本が別のフォルダにあることもあるので、本の印の頭 8 桁も付ける
		//（PR のゲート2）。長い題は、その場所で作れる長さに切る
		String name = book.getName();
		int dot = name.toLowerCase(java.util.Locale.ROOT).endsWith(".kepub.epub") ? name.length() - ".kepub.epub".length() : name.lastIndexOf('.');
		String base = "previous " + com.github.hmdev.info.BookLedger.bookKey(book).substring(0, 8) + " " + (dot > 0 ? name.substring(0, dot) : name);
		return com.github.hmdev.util.PathUtils.fitFileNameIn(workDir, base, ".epub") + ".epub";
	}

	/** 守りで止めた理由: 掲載元で作品が見つからない（404・410） */
	public static final String STOP_GONE = "gone";
	/** 守りで止めた理由: 目次の話数が前より減った */
	public static final String STOP_SHRUNK = "shrunk";

	/** 変換の結果。stop は本棚の更新の守りで止めた理由（STOP_*）。止めていなければ null */
	public record Result(boolean ok, boolean noUpdate, File epub, String message, String stop)
	{
		public Result(boolean ok, boolean noUpdate, File epub, String message)
		{
			this(ok, noUpdate, epub, message, null);
		}
	}

	private final Properties props;
	/** template/・web/・setting_narourb.ini・replace_narourb.txt・chuki_*.txt のあるフォルダ（末尾に区切りを付けた文字列。GUI は ""＝カレント） */
	private final String basePath;
	private final File cachePath;
	private final Epub3Writer writer;
	/** 画像だけの本の書き出し。画面なしの変換では設定を入れるだけ（GUI と同じく両方に入れる） */
	private final Epub3Writer imageWriter;

	public HeadlessWebConversion(Properties props, String basePath, File cachePath)
	{
		this(props, basePath, cachePath, new Epub3Writer(basePath + "template/"), new Epub3ImageWriter(basePath + "template/"));
	}

	/** 書き出しを渡す形（試験で専用の VelocityEngine を使うため） */
	HeadlessWebConversion(Properties props, String basePath, File cachePath, Epub3Writer writer, Epub3Writer imageWriter)
	{
		this.props = props;
		this.basePath = basePath;
		this.cachePath = cachePath;
		this.writer = writer;
		this.imageWriter = imageWriter;
	}

	/**
	 * URL の作品を取り、dstPath に EPUB を書く。
	 * @param expectedOutFile 書くはずの EPUB（本棚の本）。決まった出力がこれと違うときは書かずに失敗を返す。null なら確かめない
	 * @param overwrite 出力がもうあるとき上書きするか（ini の OverWrite より優先）
	 */
	public Result convert(String url, File dstPath, File expectedOutFile, boolean overwrite)
	{
		return convert(url, dstPath, expectedOutFile, overwrite, false, false);
	}

	/**
	 * 本棚の更新の守りを付けて変換する（internal #11）。updateGuard なら、目次が取れないときと、話数が台帳より減ったときは書かずに止める
	 * @param allowFewerEpisodes 話数が減っていても続ける（利用者が選んだとき）
	 */
	public Result convert(String url, File dstPath, File expectedOutFile, boolean overwrite, boolean updateGuard, boolean allowFewerEpisodes)
	{
		//kindle は取りに行く前に断る（取ってから断ると、次の「更新分のみ」で更新なしに見える。#116 のゲート2）
		String outExt = outExt();
		if (outExt.startsWith(".mobi")) return new Result(false, false, null, "kindle（" + outExt + "）の出力は本棚からは作れません");
		try {
			//変換器は FQDN ごとに使い回され、txt もキャッシュに書き直されるので、GUI の Web 変換とは 1 つずつ（WebAozoraConverter.WEB_LOCK）
			File srcFile;
			synchronized (WebAozoraConverter.WEB_LOCK) {
				WebAozoraConverter web = WebAozoraConverter.createWebAozoraConverter(url, new File(this.basePath + "web"));
				if (web == null) return new Result(false, false, null, "このサイトには対応していません: " + url);
				//WebAozoraConverter は FQDN ごとに使い回されるので、毎回すべて入れ直す（GUI と同じ）
				web.updateGuard = updateGuard;
				web.allowFewerEpisodes = allowFewerEpisodes;
				web.guardBook = updateGuard ? expectedOutFile : null;
				try {
					web.setUseApi(GuiConversionSettings.flag(this.props, "UseNarouApi"));
					web.setApiFallbackEnabled(GuiConversionSettings.flag(this.props, "ApiFallback"));
					//narou.rb 互換の整形。読めなくても GUI と同じく、知らせて既定のまま続ける（#116 のゲート2）
					try {
						File settingFile = new File(this.basePath + "setting_narourb.ini");
						NarouFormatSettings.generateDefaultIfMissing(settingFile);
						web.loadFormatSettings(settingFile);
						web.getFormatSettings().loadReplacePatterns(new File(this.basePath + "replace_narourb.txt"));
						String[] styles = { "css", "simple", "plain" };
						int style = intOf("AuthorCommentStyle", 0);
						if (style >= 0 && style < styles.length) web.getFormatSettings().setAuthorCommentStyle(styles[style]);
					} catch (Exception e) {
						logger.warn("フォーマット設定を読み込めませんでした", e);
						LogAppender.println("フォーマット設定読み込みエラー: " + e.getMessage());
					}
					web.skipImages = GuiConversionSettings.flag(this.props, "WebSkipImages");

					int interval = 500;
					try { interval = (int)(Float.parseFloat(GuiConversionSettings.text(this.props, "WebInterval").trim()) * 1000); } catch (Exception e) { /* 意図的: GUI と同じく読めなければ 500 */ }
					//本棚の更新は、いつも作品の全部で本を作る。「最新 N 話」「追加更新分のみ」は GUI が 1 回だけ出すファイルのための設定で、
					//本棚の本に当てると、全話の本が一部の話だけの本で上書きされる（PR の手元の codex）
					int beforeChapter = !updateGuard && GuiConversionSettings.flag(this.props, "WebBeforeChapter") ? intOf("WebBeforeChapterCount", 0) : 0;
					float modifiedExpire = 0;
					try { modifiedExpire = Float.parseFloat(GuiConversionSettings.text(this.props, "WebModifiedExpire").trim()); } catch (Exception e) { /* 意図的: GUI と同じく読めなければ 0 */ }
					boolean convertUpdated = GuiConversionSettings.flag(this.props, "WebConvertUpdated");
					boolean modifiedOnly = !updateGuard && GuiConversionSettings.flag(this.props, "WebModifiedOnly");

					srcFile = web.convertToAozoraText(url, this.cachePath, interval, modifiedExpire,
						convertUpdated, modifiedOnly, modifiedOnly && GuiConversionSettings.flag(this.props, "WebModifiedTail"), beforeChapter);
					if (srcFile == null) {
						//本棚のカードの 2 行に収まる長さにする。どれも本は書き換えていない
						if (updateGuard) {
							int status = web.listFailure;
							if ((status == 404 || status == 410) && !web.listFailureOnLaterPage) {
								return new Result(false, false, null, "掲載元に作品がありません (HTTP " + status + ")", STOP_GONE);
							}
							if (status != 0) {
								return new Result(false, false, null, "目次を取れませんでした" + (status > 0 ? " (HTTP " + status + ")" : ""));
							}
							if (web.shrunkFrom >= 0) {
								return new Result(false, false, null, "話数が減ったので止めました (" + web.shrunkFrom + " → " + web.shrunkTo + " 話)", STOP_SHRUNK);
							}
						}
						if ((convertUpdated || modifiedOnly) && !web.isUpdated()) return new Result(false, true, null, "更新はありません");
						return new Result(false, false, null, "取得できませんでした: " + url);
					}
					//EPUB を作り終わるまで鍵を持つ。手放すと、同じ作品を GUI が変換したときに、読んでいる途中の txt
					//（キャッシュ）が書き直される（PR #118 の codex）
					//EPUB を作れなかったら、台帳の話数を前に戻す（本は前のままなので、次の更新が前の話数と比べるように。PR の手元の codex）
					boolean written = false;
					try {
						Result r = convertText(srcFile, dstPath, expectedOutFile, overwrite);
						written = r.ok();
						return r;
					} finally {
						if (!written && web.episodesRecorded) restoreEpisodes(srcFile, web.ledgerBeforeEpisodes);
					}
				} finally {
					//変換器は GUI と使い回すので、守りは必ず倒す
					web.updateGuard = false;
					web.allowFewerEpisodes = false;
					web.guardBook = null;
				}
			}
		} catch (Exception e) {
			logger.error("画面なしの変換に失敗: {}", url, e);
			return new Result(false, false, null, "変換できませんでした: " + e.getMessage());
		}
	}

	/** 台帳の話数（作品の話数と本ごとの話数）を before のものに戻す */
	static void restoreEpisodes(File srcFile, com.github.hmdev.info.BookLedger before)
	{
		if (before == null) return;
		File workDir = srcFile.getAbsoluteFile().getParentFile();
		com.github.hmdev.info.BookLedger ledger = com.github.hmdev.info.BookLedger.load(workDir);
		if (ledger == null) return;
		try {
			ledger.withEpisodeCountsOf(before).save(workDir);
		} catch (IOException e) {
			logger.warn("台帳の話数を戻せませんでした: {}", workDir, e);
		}
	}

	/** Web 変換でできた txt を EPUB にする（GUI の convertFile の、Web 変換のときの値で） */
	Result convertText(File srcFile, File dstPath, File expectedOutFile, boolean overwrite) throws Exception
	{
		String outExt = outExt();
		if (outExt.startsWith(".mobi")) return new Result(false, false, null, "kindle（" + outExt + "）の出力は本棚からは作れません");

		GuiConversionSettings.applyTo(this.props, this.writer, this.imageWriter);
		this.writer.setIsKindle(false);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(this.writer, this.basePath);
		//GUI の Web 変換はコメントを出して変換する（部品を一時的に書き換えている）
		GuiConversionSettings.applyTo(this.props, converter, true);

		ImageInfoReader imageInfoReader = new ImageInfoReader(true, srcFile);
		//GUI の Web 変換は入力を UTF-8・表題の種類を 0 にする。PubFirst は ini の値が効く
		BookInfo bookInfo = AozoraEpub3.getBookInfo(srcFile, "txt", 0, imageInfoReader, converter, "UTF-8",
			BookInfo.TitleType.indexOf(0), GuiConversionSettings.flag(this.props, "PubFirst"));
		if (bookInfo == null) return new Result(false, false, null, "書籍の情報が取得できませんでした");

		bookInfo.insertCoverPage = GuiConversionSettings.flag(this.props, "CoverPage");
		bookInfo.insertTocPage = GuiConversionSettings.flag(this.props, "TocPage");
		bookInfo.insertCoverPageToc = GuiConversionSettings.flag(this.props, "CoverPageToc");
		bookInfo.insertTitleToc = GuiConversionSettings.flag(this.props, "TitleToc");
		if (!bookInfo.insertTitleToc && bookInfo.titleLine >= 0) bookInfo.removeChapterLineInfo(bookInfo.titleLine);
		bookInfo.setTocVertical(GuiConversionSettings.flag(this.props, "TocVertical"));
		bookInfo.vertical = GuiConversionSettings.flag(this.props, "Vertical");
		converter.vertical = bookInfo.vertical;
		bookInfo.titlePageType = GuiConversionSettings.flag(this.props, "TitlePageWrite")
			? SettingDefaults.getInt(this.props, "TitlePage") : BookInfo.TITLE_NONE;

		//表紙: GUI の Web 変換は「先頭の挿絵」「入力と同じ名前」を「入力と同じ名前」にそろえる。「表紙なし」と直接の指定はそのまま
		String cover = normalizeCover(this.props.getProperty("Cover", ""));
		String coverFileName;
		if (COVER_NONE.equals(cover)) coverFileName = null;
		else if (cover.isEmpty() || COVER_SAME_FILE.equals(cover)) coverFileName = AozoraEpub3.getSameCoverFileName(srcFile);
		else coverFileName = cover;
		bookInfo.coverFileName = coverFileName;
		bookInfo.coverImageIndex = -1;

		//表題・著者が取れなければファイル名から（GUI の Web 変換はファイル名を表題に使わない）
		String[] titleCreator = BookInfo.getFileTitleCreator(srcFile.getName());
		if (bookInfo.title == null || bookInfo.title.length() == 0) {
			bookInfo.title = titleCreator[0] == null ? "" : titleCreator[0];
			if (bookInfo.creator == null || bookInfo.creator.length() == 0) bookInfo.creator = titleCreator[1] == null ? "" : titleCreator[1];
		}

		boolean autoFileName = GuiConversionSettings.flag(this.props, "AutoFileName");
		//本棚の本と名前を照らすときは、照らす前に台帳へ名前を記録しない（違ったときに、違う名前が台帳に残る。#116 のゲート2）
		File ledgerDir = bookInfo.ledgerDir;
		if (expectedOutFile != null) bookInfo.ledgerDir = null;
		File outFile = AozoraEpub3.getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);
		if (expectedOutFile != null) {
			if (!outFile.getCanonicalFile().equals(expectedOutFile.getCanonicalFile())) {
				return new Result(false, false, outFile, "本棚の本と違う名前で出力されるので、書きませんでした: " + outFile.getName());
			}
			//名前が合っていれば、記録する（台帳にまだ名前が無いときだけ記録される）
			bookInfo.ledgerDir = ledgerDir;
			outFile = AozoraEpub3.getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);
		}
		if (outFile.exists() && !overwrite) return new Result(false, false, outFile, "ファイルが存在します: " + outFile.getName());

		LogAppender.println("画面なしで変換します : " + srcFile.getPath());
		//同じフォルダの一時ファイルに書いてから置き換える。書き出しは失敗すると出力を消すので、直接書くと
		//本棚の本が消える。途中で止まっても（本棚を閉じてプロセスが終わるなど）本棚の本は元のまま（PR #118 のゲート2）。
		//一時ファイルの名前は .epub で終わらせない（本棚に並ばないように）
		File tmp = File.createTempFile("." + outFile.getName() + ".", ".tmp", outFile.getAbsoluteFile().getParentFile());
		File workDir = srcFile.getAbsoluteFile().getParentFile();
		File backup = null;
		try {
			boolean ok = AozoraEpub3.convertFile(srcFile, "txt", tmp, converter, this.writer, "UTF-8", bookInfo, imageInfoReader, 0);
			if (!ok) return new Result(false, false, outFile, "変換に失敗しました（本棚の本はそのまま）");
			//今の本を作品のフォルダに 1 つ前の版として残す（internal #11。続きを取って何かが消えても戻せるように）。
			//いったん控えに写し、本を置き換えられてから 1 つ前の版の名前にする（先に上書きすると、変換や置き換えに
			//失敗したとき＝Windows で本が開かれていたときなど、本は元のままなのに、もっと前の版を失う。PR のゲート2・#121 の codex）
			if (outFile.exists()) {
				backup = File.createTempFile(".previous.", ".tmp", workDir);
				java.nio.file.Files.copy(outFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			//前の本の権限を引き継ぐ（一時ファイルの既定の権限にしない。PR #118 の codex）
			if (outFile.exists()) {
				try {
					java.nio.file.Files.setPosixFilePermissions(tmp.toPath(), java.nio.file.Files.getPosixFilePermissions(outFile.toPath()));
				} catch (UnsupportedOperationException | IOException e) {
					/* 意図的: POSIX でない（Windows）なら何もしない */
				}
			}
			try {
				java.nio.file.Files.move(tmp.toPath(), outFile.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				java.nio.file.Files.move(tmp.toPath(), outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			//本はもう置き換えたので、ここから先の失敗で更新を失敗にしない（台帳の話数が戻って、本と食い違う。#121 の codex）
			String note = "";
			if (backup != null) {
				File previous = new File(workDir, previousEpubName(workDir, outFile));
				try {
					java.nio.file.Files.move(backup.toPath(), previous.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					//前の版が開かれている（Windows）など。控えは消さずに残す（前の本はそこから戻せる）
					logger.warn("1 つ前の版を残せませんでした: {}", previous, e);
					LogAppender.println("1 つ前の版を " + previous.getName() + " に残せませんでした。前の本は " + backup.getName() + " にあります");
					note = "（1 つ前の版は残せませんでした）";
					backup = null;
				}
			}
			//置き換えた本の話数を記録する（書き出しは一時ファイルなので convertFile は記録しない）
			com.github.hmdev.info.BookLedger.recordBookEpisodes(bookInfo, outFile);
			return new Result(true, false, outFile, "変換しました" + note);
		} finally {
			java.nio.file.Files.deleteIfExists(tmp.toPath());
			if (backup != null) java.nio.file.Files.deleteIfExists(backup.toPath());
		}
	}

	/**
	 * 旧形式の ini は、表紙の選択肢の表示名（「[先頭の挿絵]」など）をそのまま書いていた。GUI はそれを選択肢として読むので、
	 * 同じく「先頭の挿絵」「入力と同じ名前」「表紙なし」に読み替える（PR #116 の codex）。日本語・英語のどちらの表示名でも
	 */
	static String normalizeCover(String cover)
	{
		if (cover == null) return "";
		for (java.util.Locale locale : new java.util.Locale[]{ java.util.Locale.JAPANESE, java.util.Locale.ENGLISH }) {
			try {
				java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages", locale);
				if (cover.equals(bundle.getString("ui.combo.cover.first"))) return "";
				if (cover.equals(bundle.getString("ui.combo.cover.sameFile"))) return COVER_SAME_FILE;
				if (cover.equals(bundle.getString("ui.combo.cover.none"))) return COVER_NONE;
			} catch (java.util.MissingResourceException e) {
				/* 意図的: その言語の資源が無ければ次へ */
			}
		}
		return cover;
	}

	private int intOf(String key, int defaultValue)
	{
		try { return Integer.parseInt(GuiConversionSettings.text(this.props, key).trim()); } catch (Exception e) { return defaultValue; }
	}

	private String outExt()
	{
		String ext = this.props.getProperty("Ext", ".epub").trim();
		return ext.isEmpty() ? ".epub" : ext;
	}
}
