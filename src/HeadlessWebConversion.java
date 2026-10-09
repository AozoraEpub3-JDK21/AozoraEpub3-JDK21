import java.io.File;
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

	/** 変換の結果 */
	public record Result(boolean ok, boolean noUpdate, File epub, String message) {}

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
		//kindle は取りに行く前に断る（取ってから断ると、次の「更新分のみ」で更新なしに見える。#116 のゲート2）
		String outExt = outExt();
		if (outExt.startsWith(".mobi")) return new Result(false, false, null, "kindle（" + outExt + "）の出力は本棚からは作れません");
		try {
			WebAozoraConverter web = WebAozoraConverter.createWebAozoraConverter(url, new File(this.basePath + "web"));
			if (web == null) return new Result(false, false, null, "このサイトには対応していません: " + url);
			//WebAozoraConverter は FQDN ごとに使い回されるので、毎回すべて入れ直す（GUI と同じ）
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
			int beforeChapter = GuiConversionSettings.flag(this.props, "WebBeforeChapter") ? intOf("WebBeforeChapterCount", 0) : 0;
			float modifiedExpire = 0;
			try { modifiedExpire = Float.parseFloat(GuiConversionSettings.text(this.props, "WebModifiedExpire").trim()); } catch (Exception e) { /* 意図的: GUI と同じく読めなければ 0 */ }
			boolean convertUpdated = GuiConversionSettings.flag(this.props, "WebConvertUpdated");
			boolean modifiedOnly = GuiConversionSettings.flag(this.props, "WebModifiedOnly");

			File srcFile = web.convertToAozoraText(url, this.cachePath, interval, modifiedExpire,
				convertUpdated, modifiedOnly, GuiConversionSettings.flag(this.props, "WebModifiedTail"), beforeChapter);
			if (srcFile == null) {
				if ((convertUpdated || modifiedOnly) && !web.isUpdated()) return new Result(false, true, null, "更新はありません");
				return new Result(false, false, null, "取得できませんでした: " + url);
			}
			return convertText(srcFile, dstPath, expectedOutFile, overwrite);
		} catch (Exception e) {
			logger.error("画面なしの変換に失敗: {}", url, e);
			return new Result(false, false, null, "変換できませんでした: " + e.getMessage());
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
		boolean ok = AozoraEpub3.convertFile(srcFile, "txt", outFile, converter, this.writer, "UTF-8", bookInfo, imageInfoReader, 0);
		return new Result(ok, false, outFile, ok ? "変換しました" : "変換に失敗しました");
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
