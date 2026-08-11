import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hmdev.config.SettingDefaults;
import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.util.ArchiveUrlUtils;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.io.ArchiveTextExtractor;
import com.github.hmdev.pipeline.WriterConfigurator;
import com.github.hmdev.web.NarouFormatSettings;
import com.github.hmdev.web.WebAozoraConverter;
import com.github.hmdev.writer.Epub3ImageWriter;
import com.github.hmdev.writer.Epub3Writer;

/** コマンドライン実行用mainとePub3変換関数 */
public class AozoraEpub3
{
	private static final Logger logger = LoggerFactory.getLogger(AozoraEpub3.class);

	public static final String VERSION = "1.5.1-jdk21";

	/** 最後に出力に成功した EPUB。CLI の --preview が変換後に開く対象。
	 * GUI は変換経路が異なるため AozoraEpub3Applet.previewTargetFile を使う */
	private static volatile File lastOutputFile;

	/** コマンドライン実行用。
	 * 失敗時のみ非 0 で終了する（成功時に System.exit を呼ばないのは、
	 * main() を in-process で直接呼ぶテストを終了させないため）。 */
	public static void main(String args[])
	{
		int exitCode = run(args);
		if (exitCode != 0) System.exit(exitCode);
	}

	/** コマンドライン処理本体
	 * @return 終了コード。全件成功なら 0、1 件でも失敗があれば 1 */
	static int run(String args[])
	{
		//変換に失敗した件数。1 件でもあれば非 0 で終了する
		int errorCount = 0;
		//テストは main() を in-process で複数回呼ぶため、前回実行の出力を引き継がないよう毎回クリアする
		//（引き継ぐと、変換に失敗した --preview が前回の EPUB を開いて待機してしまう）
		lastOutputFile = null;
		String jarPath = System.getProperty("java.class.path");
		int idx = jarPath.indexOf(";");
		if (idx > 0) jarPath = jarPath.substring(0, idx);
		if (!jarPath.endsWith(".jar")) jarPath = "";
		else jarPath = jarPath.substring(0, jarPath.lastIndexOf(File.separator)+1);
		//this.cachePath = new File(jarPath+".cache");
		//this.webConfigPath = new File(jarPath+"web");
		
		/** ePub3出力クラス */
		Epub3Writer epub3Writer;
		/** ePub3画像出力クラス */
		Epub3ImageWriter epub3ImageWriter;
		
		/** 設定ファイル */
		Properties props;
		/** 設定ファイル名 */
		String propFileName = "AozoraEpub3.ini";
		/** 出力先パス */
		File dstPath = null;
		
		String syntax = "AozoraEpub3 [-options] input_files(txt,zip,cbz)";
		String header = "version : "+VERSION;
		
		try {
			//コマンドライン オプション設定
			Options options = new Options();
			options.addOption("h", "help", false, "show usage");
			options.addOption("i", "ini", true, "指定したiniファイルから設定を読み込みます (コマンドラインオプション以外の設定)");
			options.addOption("t", true, "本文内の表題種別\n[0:表題→著者名] (default)\n[1:著者名→表題]\n[2:表題→著者名(副題優先)]\n[3:表題のみ(1行)]\n[4:表題+著者名のみ(2行)]\n[5:なし]");
			options.addOption("tf", false, "入力ファイル名を表題に利用");
			options.addOption("c", "cover", true, "表紙画像\n[0:先頭の挿絵]\n[1:ファイル名と同じ画像]\n[ファイル名 or URL]");
			options.addOption("ext", true, "出力ファイル拡張子\n[.epub] (default)\n[.kepub.epub]");
			options.addOption("of", false, "出力ファイル名を入力ファイル名に合せる");
			options.addOption("d", "dst", true, "出力先パス");
			options.addOption("enc", true, "入力ファイルエンコード\n[MS932] (default)\n[UTF-8]");
			//options.addOption("id", false, "栞用ID出力 (for Kobo)");
			//options.addOption("tcy", false, "自動縦中横有効");
			//options.addOption("g4", false, "4バイト文字変換");
			//options.addOption("tm", false, "表題を左右中央");
			//options.addOption("cp", false, "表紙画像ページ追加");
			options.addOption("hor", false, "横書き (指定がなければ縦書き)");
			options.addOption("device", true, "端末種別(指定した端末向けの例外処理を行う)\n[kindle]");
			options.addOption("url", true, "変換するURL (複数指定可)");
			options.addOption("interval", true, "ページ取得間隔(秒) [1.0] (デフォルト)");
			options.addOption("cache", true, "キャッシュパス [.cache]");
			options.addOption("narou", false, "narou.rb互換フォーマット設定(setting_narourb.ini)を適用");
			options.addOption("preview", "preview", false,
				"変換後の EPUB を既定ブラウザでプレビュー表示 (入力が .epub のみなら変換せずそのまま表示)\nブラウザを閉じるか Ctrl-C で終了します");
			// -preview は引数を取らないフラグなので、棚は別オプションにする。
			// -preview に省略可能な引数を持たせると "-preview book.epub" の book.epub を
			// 引数として食べてしまい、既存の使い方が壊れる
			options.addOption("library", "library", true,
				"本棚として開くフォルダ (複数指定可、最大 " + com.github.hmdev.preview.LibraryScanner.MAX_SHELVES + " 個)\n"
				+ "入力ファイルを省略すると本棚だけを開きます\n"
				+ "-preview と同様、ブラウザを閉じるか Ctrl-C まで待機します");

			CommandLine commandLine;
			try {
				commandLine = new DefaultParser().parse(options, args, true);
			} catch (ParseException e) {
				HelpFormatter.builder().get().printHelp(syntax, header, options, null, false);
				return 1;
			}
			//オプションの後ろをファイル名に設定
			String[] fileNames = commandLine.getArgs();
			//変換対象ファイル (コマンドライン引数 + -url で直接ダウンロードしたアーカイブ)
			List<String> targetFileNames = new ArrayList<>(Arrays.asList(fileNames));
			
			//ヘルプ出力（-h は入力ファイル無しで指定されるため、ファイル数チェックより先に処理する）
			if (commandLine.hasOption('h') ) {
				HelpFormatter.builder().get().printHelp(syntax, header, options, null, false);
				return 0;
			}
			String[] libraryDirs = commandLine.getOptionValues("library");
			if (fileNames.length == 0 && !commandLine.hasOption("url")) {
				//入力ファイルが無くても、本棚が指定されていれば棚だけを開く
				if (libraryDirs != null && libraryDirs.length > 0) return previewLibrary(libraryDirs);
				HelpFormatter.builder().get().printHelp(syntax, header, options, null, false);
				return 1;
			}
			//--library は「棚も一緒に開く」指定なので、単独でもプレビューを開く
			//(棚だけ読み込んで何も表示しないのでは意味がない)。
			//-preview と同じくブラウザを閉じるまで待機することはヘルプに明記してある
			boolean preview = commandLine.hasOption("preview") || (libraryDirs != null && libraryDirs.length > 0);
			//入力が EPUB だけなら変換せずそのままプレビューする。
			//-url が併用されている場合は変換対象があるので通常の変換フローに進める
			if (preview && fileNames.length > 0 && !commandLine.hasOption("url") && isAllEpub(fileNames)) {
				return previewFiles(fileNames, libraryDirs);
			}
			//iniファイル確認
			if (commandLine.hasOption("i")) {
				propFileName = commandLine.getOptionValue("i");
				File file = new File(propFileName);
				if (file == null || !file.isFile()) {
					LogAppender.error("-i : ini file not exist. "+file.getAbsolutePath());
					return 1;
				}
			}
			//出力パス確認
			if (commandLine.hasOption("d")) {
				dstPath = new File(commandLine.getOptionValue("d"));
				if (dstPath == null || !dstPath.isDirectory()) {
					LogAppender.error("-d : dst path not exist. "+dstPath.getAbsolutePath());
					return 1;
				}
			}
			//ePub出力クラス初期化
			epub3Writer = new Epub3Writer(jarPath+"template/");
			epub3ImageWriter = new Epub3ImageWriter(jarPath+"template/");
			
			//propsから読み込み
			props = new Properties();
			//-i の指定が無いときはカレントの ini を優先し、無ければ jar と同じ場所を見る (項目 26)
			File propFile = commandLine.hasOption("i")
				? new File(propFileName)
				: resolveDefaultIniFile(jarPath, propFileName);
			try {
				props.load(Files.newInputStream(propFile.toPath()));
				logger.info("設定ファイルを読み込みました: {}", propFile.getAbsolutePath());
			} catch (Exception e) { /* 意図的: 設定ファイル不在/I/O 失敗時は既定値で起動 */
				logger.info("設定ファイルが無いか読めないため既定値で起動します: {}", propFile.getAbsolutePath());
				logger.debug("設定ファイルの読み込みに失敗", e);
			}
			
			int titleIndex = 0; //try { titleIndex = Integer.parseInt(props.getProperty("TitleType")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }//表題
			
			//コマンドラインオプション以外
			//ini にキーが無いときは GUI と同じ既定値を使う。SettingDefaults に一元化してあるので
			//ここで props.getProperty を直接読まない (docs/code-audit-followups.md 項目 22 / 24)
			boolean coverPage = SettingDefaults.getBoolean(props, "CoverPage");//表紙追加
			int titlePage = BookInfo.TITLE_NONE;
			if (SettingDefaults.getBoolean(props, "TitlePageWrite")) {
				titlePage = SettingDefaults.getInt(props, "TitlePage");
			}
			boolean withMarkId = SettingDefaults.getBoolean(props, "MarkId");
			//boolean gaiji32 = "1".equals(props.getProperty("Gaiji32"));
			boolean commentPrint = SettingDefaults.getBoolean(props, "CommentPrint");
			boolean commentConvert = SettingDefaults.getBoolean(props, "CommentConvert");
			boolean autoYoko = SettingDefaults.getBoolean(props, "AutoYoko");
			boolean autoYokoNum1 = SettingDefaults.getBoolean(props, "AutoYokoNum1");
			boolean autoYokoNum3 = SettingDefaults.getBoolean(props, "AutoYokoNum3");
			boolean autoYokoEQ1 = SettingDefaults.getBoolean(props, "AutoYokoEQ1");
			int spaceHyp = SettingDefaults.getInt(props, "SpaceHyphenation");
			boolean tocPage = SettingDefaults.getBoolean(props, "TocPage");//目次追加
			boolean tocVertical = SettingDefaults.getBoolean(props, "TocVertical");//目次縦書き
			boolean coverPageToc = SettingDefaults.getBoolean(props, "CoverPageToc");
			int removeEmptyLine = SettingDefaults.getInt(props, "RemoveEmptyLine");
			int maxEmptyLine = SettingDefaults.getInt(props, "MaxEmptyLine");
			
			WriterConfigurator.apply(props, epub3Writer, epub3ImageWriter);
			
			
			//自動改ページ
			int forcePageBreakSize = 0;
			int forcePageBreakEmpty = 0;
			int forcePageBreakEmptySize = 0;
			int forcePageBreakChapter = 0;
			int forcePageBreakChapterSize = 0;
			if (SettingDefaults.getBoolean(props, "PageBreak")) {
				forcePageBreakSize = SettingDefaults.getInt(props, "PageBreakSize") * 1024;
				if (SettingDefaults.getBoolean(props, "PageBreakEmpty")) {
					forcePageBreakEmpty = SettingDefaults.getInt(props, "PageBreakEmptyLine");
					forcePageBreakEmptySize = SettingDefaults.getInt(props, "PageBreakEmptySize") * 1024;
				}
				if (SettingDefaults.getBoolean(props, "PageBreakChapter")) {
					forcePageBreakChapter = 1;
					forcePageBreakChapterSize = SettingDefaults.getInt(props, "PageBreakChapterSize") * 1024;
				}
			}
			//目次設定はキー不在時に GUI と同じ既定値を使う。SettingDefaults に一元化してあるので
			//ここで props.getProperty を直接読まない (docs/code-audit-followups.md 項目 22)
			//GUI が書くのは "MaxChapterNameLength"。CLI は別名を読んでいたため、
			//GUI で設定した目次の最大文字数が CLI に届かず 64 のままだった。
			//旧名 "ChapterNameLength" は SettingDefaults の表には載せず (GUI は書かない)、
			//手書きの ini 向けの互換読み出しとしてここだけで扱う
			int maxLength = SettingDefaults.getInt(props, "MaxChapterNameLength");
			if (!props.containsKey("MaxChapterNameLength")) {
				try { maxLength = Integer.parseInt(props.getProperty("ChapterNameLength")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
			}
			boolean insertTitleToc = SettingDefaults.getBoolean(props, "TitleToc");
			boolean chapterExclude = SettingDefaults.getBoolean(props, "ChapterExclude");
			boolean chapterUseNextLine = SettingDefaults.getBoolean(props, "ChapterUseNextLine");
			boolean chapterSection = SettingDefaults.getBoolean(props, "ChapterSection");
			boolean chapterH = SettingDefaults.getBoolean(props, "ChapterH");
			boolean chapterH1 = SettingDefaults.getBoolean(props, "ChapterH1");
			boolean chapterH2 = SettingDefaults.getBoolean(props, "ChapterH2");
			boolean chapterH3 = SettingDefaults.getBoolean(props, "ChapterH3");
			boolean sameLineChapter = SettingDefaults.getBoolean(props, "SameLineChapter");
			boolean chapterName = SettingDefaults.getBoolean(props, "ChapterName");
			boolean chapterNumOnly = SettingDefaults.getBoolean(props, "ChapterNumOnly");
			boolean chapterNumTitle = SettingDefaults.getBoolean(props, "ChapterNumTitle");
			boolean chapterNumParen = SettingDefaults.getBoolean(props, "ChapterNumParen");
			//GUI は "ChapterNumParenTitle" で書く。先頭の C が欠けており、CLI では永久に false だった
			boolean chapterNumParenTitle = SettingDefaults.getBoolean(props, "ChapterNumParenTitle");
			//ChapterPattern=1 だけを書いた手書き ini では ChapterPatternText が無く、
			//null のまま setChapterLevel に渡って「パターンが不正」の警告が出ていた (項目 23)
			String chapterPattern = "";
			if (SettingDefaults.getBoolean(props, "ChapterPattern")) {
				String patternText = props.getProperty("ChapterPatternText");
				if (patternText != null) chapterPattern = patternText;
			}
			
			//オプション指定を反映
			boolean useFileName = false;//表題に入力ファイル名利用
			String coverFileName = null;
			String encType = "MS932";
			String outExt = ".epub";
			boolean autoFileName = true; //ファイル名を表題に利用
			boolean vertical = true;
			String targetDevice = null;
			if(commandLine.hasOption("t")) try { titleIndex = Integer.parseInt(commandLine.getOptionValue("t")); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }//表題
			if(commandLine.hasOption("tf")) useFileName = true;
			if(commandLine.hasOption("c")) coverFileName = commandLine.getOptionValue("c");
			if(commandLine.hasOption("enc")) encType = commandLine.getOptionValue("enc");
			if(commandLine.hasOption("ext")) outExt = commandLine.getOptionValue("ext");
			if(commandLine.hasOption("of")) autoFileName = false;
			//if(commandLine.hasOption("id")) withMarkId = true;
			//if(commandLine.hasOption("tcy")) autoYoko = true;
			//if(commandLine.hasOption("g4")) gaiji32 = true;
			//if(commandLine.hasOption("tm")) middleTitle = true;
			//if(commandLine.hasOption("cb")) commentPrint = true;
			//if(commandLine.hasOption("cc")) commentConvert = true;
			//if(commandLine.hasOption("cp")) coverPage = true;
			if(commandLine.hasOption("hor")) vertical = false;
			if(commandLine.hasOption("device")) {
				targetDevice = commandLine.getOptionValue("device");
				if (targetDevice.equalsIgnoreCase("kindle")) {
					epub3Writer.setIsKindle(true);
				}
			}

			//変換クラス生成とパラメータ設定
			AozoraEpub3Converter  aozoraConverter = new AozoraEpub3Converter(epub3Writer, jarPath);
			//挿絵なし
			aozoraConverter.setNoIllust("1".equals(props.getProperty("NoIllust"))); 
			//栞用span出力
			aozoraConverter.setWithMarkId(withMarkId);
			//変換オプション設定
			aozoraConverter.setAutoYoko(autoYoko, autoYokoNum1, autoYokoNum3, autoYokoEQ1);
			//文字出力設定
			int dakutenType = SettingDefaults.getInt(props, "DakutenType");
			boolean printIvsBMP = "1".equals(props.getProperty("IvsBMP"));
			boolean printIvsSSP = "1".equals(props.getProperty("IvsSSP"));
			
			aozoraConverter.setCharOutput(dakutenType, printIvsBMP, printIvsSSP);
			//全角スペースの禁則
			aozoraConverter.setSpaceHyphenation(spaceHyp);
			//コメント
			aozoraConverter.setCommentPrint(commentPrint, commentConvert);
			
			aozoraConverter.setRemoveEmptyLine(removeEmptyLine, maxEmptyLine);
			
			//強制改ページ
			aozoraConverter.setForcePageBreak(forcePageBreakSize, forcePageBreakEmpty, forcePageBreakEmptySize, forcePageBreakChapter, forcePageBreakChapterSize);
			//目次設定
			aozoraConverter.setChapterLevel(maxLength, chapterExclude, chapterUseNextLine, chapterSection,
					chapterH, chapterH1, chapterH2, chapterH3, sameLineChapter,
					chapterName,
					chapterNumOnly, chapterNumTitle, chapterNumParen, chapterNumParenTitle,
					chapterPattern);
			
			////////////////////////////////
			//URL変換処理
			////////////////////////////////
			if (commandLine.hasOption("url")) {
				String[] urls = commandLine.getOptionValues("url");
				File cachePath = new File(jarPath + ".cache");
				if (commandLine.hasOption("cache")) cachePath = new File(commandLine.getOptionValue("cache"));
				Files.createDirectories(cachePath.toPath());

				int interval = 1000;
				if (commandLine.hasOption("interval")) {
					try { interval = (int)(Float.parseFloat(commandLine.getOptionValue("interval")) * 1000); } catch (Exception e) { /* 意図的: パース失敗時は既定値を維持 */ }
				}

				File webConfigPath = new File(jarPath + "web");

				for (String urlString : urls) {
					LogAppender.println("--------");
					LogAppender.append(urlString);
					LogAppender.println(" を読み込みます");
					//URL が zip / txtz / rar を直接指している場合はスクレイピングせずダウンロードし、
					//ローカルファイルと同じ変換経路 (下の各ファイル変換処理) に流す
					if (ArchiveUrlUtils.isArchiveUrl(urlString)) {
						File archiveDstPath = (dstPath != null) ? dstPath : new File(".");
						try {
							File archiveFile = ArchiveUrlUtils.downloadArchive(urlString, archiveDstPath);
							targetFileNames.add(archiveFile.getPath());
						} catch (Exception e) {
							logger.error("アーカイブ URL のダウンロードに失敗: {}", urlString, e);
							LogAppender.append(urlString);
							LogAppender.println(" は変換できませんでした");
							errorCount++;
						}
						continue;
					}
					try {
						WebAozoraConverter webConverter = WebAozoraConverter.createWebAozoraConverter(urlString, webConfigPath);
						if (webConverter == null) {
							LogAppender.append(urlString);
							LogAppender.println(" は変換できませんでした");
							errorCount++;
							continue;
						}

						if (commandLine.hasOption("narou")) {
							File settingFile = new File(jarPath + "setting_narourb.ini");
							File replaceFile = new File(jarPath + "replace_narourb.txt");
							NarouFormatSettings.generateDefaultIfMissing(settingFile);
							webConverter.loadFormatSettings(settingFile);
							webConverter.getFormatSettings().loadReplacePatterns(replaceFile);
						}

						File srcFile = webConverter.convertToAozoraText(
							urlString, cachePath, interval, 0f, false, false, false, 0);

						if (srcFile == null) {
							LogAppender.append(urlString);
							LogAppender.println(" は変換できませんでした");
							errorCount++;
							continue;
						}

						ImageInfoReader imageInfoReader = new ImageInfoReader(true, srcFile);
						BookInfo bookInfo = AozoraEpub3.getBookInfo(
							srcFile, "txt", 0, imageInfoReader, aozoraConverter, "UTF-8",
							BookInfo.TitleType.indexOf(titleIndex), false);
						if (bookInfo == null) {
							LogAppender.println("BookInfo取得失敗: " + urlString);
							errorCount++;
							continue;
						}
						bookInfo.vertical = vertical;
						bookInfo.insertTocPage = tocPage;
						bookInfo.setTocVertical(tocVertical);
						bookInfo.insertTitleToc = insertTitleToc;
						bookInfo.titlePageType = titlePage;
						bookInfo.insertCoverPageToc = coverPageToc;
						bookInfo.insertCoverPage = coverPage;
						aozoraConverter.vertical = vertical;
						if (!bookInfo.insertTitleToc && bookInfo.titleLine >= 0) {
							bookInfo.removeChapterLineInfo(bookInfo.titleLine);
						}

						File urlDstPath = (dstPath != null) ? dstPath : srcFile.getAbsoluteFile().getParentFile();
						File outFile = getOutFile(srcFile, urlDstPath, bookInfo, autoFileName, outExt);
						if (!AozoraEpub3.convertFile(srcFile, "txt", outFile, aozoraConverter, epub3Writer, "UTF-8", bookInfo, imageInfoReader, 0)) errorCount++;
					} catch (Exception e) {
						logger.error("URL 入力ファイルの変換に失敗: {}", urlString, e);
						LogAppender.println("エラーが発生しました : " + e.getMessage());
						errorCount++;
					}
				}
			}

			////////////////////////////////
			//各ファイルを変換処理
			////////////////////////////////
			for (String fileName : targetFileNames) {
				LogAppender.println("--------");
				File srcFile = new File(fileName);
				if (srcFile == null || !srcFile.isFile()) {
					LogAppender.error("file not exist. "+srcFile.getAbsolutePath());
					errorCount++;
					continue;
				}
				String ext = srcFile.getName();
				ext = ext.substring(ext.lastIndexOf('.')+1).toLowerCase();
				
				int coverImageIndex = -1;
				if (coverFileName != null) {
					if ("0".equals(coverFileName)) {
						coverImageIndex = 0;
						coverFileName = "";
					} else if ("1".equals(coverFileName)) {
						coverFileName = AozoraEpub3.getSameCoverFileName(srcFile); //入力ファイルと同じ名前+.jpg/.png
					}
				}
				
				//zipならzip内のテキストを検索
				int txtCount = 1;
				boolean imageOnly = false;
				boolean isFile = "txt".equals(ext);
				if("zip".equals(ext) || "txtz".equals(ext)) { 
					try {
						txtCount = ArchiveTextExtractor.countZipText(srcFile);
					} catch (IOException e) {
						logger.warn("ZIP 内テキスト数の取得に失敗、画像のみとして扱う: {}", srcFile, e);
					}
					if (txtCount == 0) { txtCount = 1; imageOnly = true; }
				} else if("rar".equals(ext)) { 
					try {
						txtCount = ArchiveTextExtractor.countRarText(srcFile);
					} catch (IOException e) {
						logger.warn("RAR 内テキスト数の取得に失敗、画像のみとして扱う: {}", srcFile, e);
					}
					if (txtCount == 0) { txtCount = 1; imageOnly = true; }
				} else if ("cbz".equals(ext)) {
					imageOnly = true;
				}
				for (int txtIdx=0; txtIdx<txtCount; txtIdx++) {
					ImageInfoReader imageInfoReader = new ImageInfoReader(isFile, srcFile);
					
					BookInfo bookInfo = null;
					if (!imageOnly) {
						bookInfo = AozoraEpub3.getBookInfo(srcFile, ext, txtIdx, imageInfoReader, aozoraConverter, encType, BookInfo.TitleType.indexOf(titleIndex), false);
						if (bookInfo == null) {
							//txt を含まない zip 等。そのまま進むと以降の参照で NPE になる
							LogAppender.error("入力ファイルから書籍情報が取得できませんでした : "+srcFile.getPath());
							errorCount++;
							continue;
						}
						bookInfo.vertical = vertical;
						bookInfo.insertTocPage = tocPage;
						bookInfo.setTocVertical(tocVertical);
						bookInfo.insertTitleToc = insertTitleToc;
						aozoraConverter.vertical = vertical;
						//表題ページ
						bookInfo.titlePageType = titlePage;
					}
					//表題の見出しが非表示で行が追加されていたら削除
					//imageOnly 時は bookInfo が null のままここに来る（後段の imageOnly 分岐で生成される）
					if (bookInfo != null && !bookInfo.insertTitleToc && bookInfo.titleLine >= 0) {
						bookInfo.removeChapterLineInfo(bookInfo.titleLine);
					}
					
					Epub3Writer writer = epub3Writer;
					if (!isFile) {
						if ("rar".equals(ext)) {
							imageInfoReader.loadRarImageInfos(srcFile, imageOnly);
						} else {
							imageInfoReader.loadZipImageInfos(srcFile, imageOnly);
						}
						if (imageOnly) {
							LogAppender.println("画像のみのePubファイルを生成します");
							//画像出力用のBookInfo生成
							bookInfo = new BookInfo(srcFile);
							bookInfo.imageOnly = true;
							//Writerを画像出力用派生クラスに入れ替え
							writer = epub3ImageWriter;
							
							if (imageInfoReader.countImageFileInfos() == 0) {
								LogAppender.error("画像がありませんでした");
								return 1;
							}
							//名前順で並び替え
							imageInfoReader.sortImageFileNames();
						}
					}
					//先頭からの場合で指定行数以降なら表紙無し
					if ("".equals(coverFileName)) {
						try {
							int maxCoverLine = SettingDefaults.getInt(props, "MaxCoverLine");
							if (maxCoverLine > 0 && bookInfo.firstImageLineNum >= maxCoverLine) {
								coverImageIndex = -1;
								coverFileName = null;
							}
						} catch (Exception e) { /* 意図的: MaxCoverLine 不正時は cover 設定変更せず */ }
					}
					
					//表紙設定
					bookInfo.insertCoverPageToc = coverPageToc;
					bookInfo.insertCoverPage = coverPage;
					bookInfo.coverImageIndex = coverImageIndex;
					if (coverFileName != null && !coverFileName.startsWith("http")) {
						File coverFile = new File(coverFileName);
						if (!coverFile.exists()) {
							coverFileName = srcFile.getParent()+"/"+coverFileName;
							if (!new File(coverFileName).exists()) {
								coverFileName = null;
								LogAppender.println("[WARN] 表紙画像ファイルが見つかりません : "+coverFile.getAbsolutePath());
							}
						}
					}
					bookInfo.coverFileName = coverFileName;
					
					String[] titleCreator = BookInfo.getFileTitleCreator(srcFile.getName());
					if (titleCreator != null) {
						if (useFileName) {
							if (titleCreator[0] != null && titleCreator[0].trim().length() >0) bookInfo.title = titleCreator[0];
							if (titleCreator[1] != null && titleCreator[1].trim().length() >0) bookInfo.creator = titleCreator[1];
						} else {
							//テキストから取得できていない場合
							if (bookInfo.title == null || bookInfo.title.length() == 0) bookInfo.title = titleCreator[0]==null?"":titleCreator[0];
							if (bookInfo.creator == null || bookInfo.creator.length() == 0) bookInfo.creator = titleCreator[1]==null?"":titleCreator[1];
						}
					}
					
					File outFile = getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);
					if (!AozoraEpub3.convertFile(
							srcFile, ext, outFile,
							aozoraConverter, writer,
							encType, bookInfo, imageInfoReader, txtIdx)) errorCount++;
				}
			}
			//変換後のプレビュー
			if (preview) {
				if (lastOutputFile == null || !lastOutputFile.isFile()) {
					LogAppender.println("プレビューできる EPUB がありません");
					//変換に失敗しても、棚が指定されていれば本棚だけは開く。
					//ただし変換の失敗は終了コードに残す (棚が開けたことで成功にしない)
					if (libraryDirs != null && libraryDirs.length > 0) {
						if (previewLibrary(libraryDirs) != 0) errorCount++;
					}
				} else if (openPreview(lastOutputFile, libraryDirs)) {
					awaitTermination();
				} else {
					//起動に失敗しているので待たずに終わる (待っても表示されない)
					errorCount++;
				}
			}
			//-preview 指定が無くても、ini の AutoPreview (GUI の「変換完了後に自動でプレビューを開く」)
			//が有効なら変換後にプレビューを開く。narou.rb 等の呼び出し側はフラグを付けてくれないため
			//ini だけで有効化できるようにする (docs/epub-preview-plan.md の起票セクション参照)。
			//本プロセスで開くと awaitTermination が呼び出し側をブロックするため、
			//自分自身を -preview 付きの別プロセスとして起動して即座に終了する。
			//lastOutputFile は .epub 出力のときだけ代入される (convertFile 後の拡張子ガード) ため、
			//-ext が .epub 以外でも子プロセスが isAllEpub を外れて変換フローに迷い込むことはない
			else if (SettingDefaults.getBoolean(props, "AutoPreview")
					&& lastOutputFile != null && lastOutputFile.isFile()) {
				spawnDetachedPreview(lastOutputFile);
			}
		} catch (Exception e) {
			logger.error("バッチ変換処理でエラー", e);
			return 1;
		}
		return errorCount > 0 ? 1 : 0;
	}

	/** 全ての入力ファイルが EPUB か */
	static boolean isAllEpub(String[] fileNames)
	{
		for (String fileName : fileNames) {
			if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".epub")) return false;
		}
		return true;
	}

	/** EPUB を変換せずにプレビューし、ブラウザが閉じられるか Ctrl-C まで待機する */
	static int previewFiles(String[] fileNames, String[] libraryDirs)
	{
		int errorCount = 0;
		boolean libraryLoaded = false;
		for (String fileName : fileNames) {
			File file = new File(fileName);
			if (!file.isFile()) {
				LogAppender.error("EPUB が見つかりません : "+file.getAbsolutePath());
				errorCount++;
				continue;
			}
			//本棚は最初に開いた本と一緒に読み込む。2 冊目以降で読み直す必要は無い。
			//「1 冊目かどうか」を errorCount で代用しないこと — 1 冊目が見つからないと
			//以降ずっと null が渡り、棚が一度も読み込まれない
			if (!openPreview(file, libraryLoaded ? null : libraryDirs)) errorCount++;
			else libraryLoaded = true;
		}
		if (errorCount < fileNames.length) awaitTermination();
		return errorCount > 0 ? 1 : 0;
	}

	/**
	 * ini の AutoPreview 用に、自分自身を -preview 付きで再起動するコマンドを組み立てる。
	 * -jar ではなく -cp + main クラスで起動する (開発時のクラスディレクトリ実行でも動くように
	 * java.class.path をそのまま渡す。パース・分解はしない)
	 */
	static List<String> buildAutoPreviewCommand(File epubFile)
	{
		//Windows では CreateProcess が .exe を補うため OS 判定は不要。
		//親の JVM オプション (-Xmx / -Dfile.encoding 等) は意図的に伝播しない
		//(-preview 経路は EPUB の展開と HTTP 配信だけで既定ヒープで足りる)
		String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		List<String> command = new ArrayList<>();
		command.add(javaBin);
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(AozoraEpub3.class.getName());
		command.add("-preview");
		command.add(epubFile.getAbsolutePath());
		return command;
	}

	/**
	 * プレビューを別プロセスで開き、待機せずに戻る。
	 * 子プロセス側は -preview の既存経路 (previewFiles) に入り、ブラウザのタブが
	 * 閉じられると heartbeat 途絶で自滅する。narou.rb 等のバッチ呼び出しを
	 * ブロックしないため、この親プロセスは起動だけして即座に終了する。
	 * プレビューは補助機能なので、起動に失敗しても変換自体は成功のまま扱う
	 */
	static void spawnDetachedPreview(File epubFile)
	{
		try {
			List<String> command = buildAutoPreviewCommand(epubFile);
			//子の出力は DISCARD で見えないため、切り分け用にコマンド列だけ残す
			logger.debug("AutoPreview spawn: {}", command);
			new ProcessBuilder(command)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			LogAppender.println("AutoPreview: プレビューを別プロセスで開きます : "+epubFile.getName());
		} catch (Exception e) {
			logger.error("AutoPreview の起動に失敗: {}", epubFile, e);
			LogAppender.error("AutoPreview の起動に失敗しました : "+e.getMessage());
		}
	}

	/**
	 * 本棚だけをプレビューし、ブラウザが閉じられるか Ctrl-C まで待機する。
	 * 入力ファイルを伴わない {@code --library} の経路。
	 */
	static int previewLibrary(String[] libraryDirs)
	{
		java.util.List<java.nio.file.Path> folders = new ArrayList<>();
		for (String dir : libraryDirs) {
			File file = new File(dir);
			if (!file.isDirectory()) {
				LogAppender.error("本棚のフォルダが見つかりません : "+file.getAbsolutePath());
				continue;
			}
			folders.add(file.toPath());
		}
		if (folders.isEmpty()) return 1;
		try {
			String url = com.github.hmdev.preview.PreviewLauncher.previewLibrary(folders);
			LogAppender.println("本棚を開きました : "+url);
		} catch (IOException e) {
			logger.error("本棚の起動に失敗: {}", folders, e);
			LogAppender.error("本棚の起動に失敗しました : "+e.getMessage());
			return 1;
		}
		awaitTermination();
		return 0;
	}

	/** 待機ループの間隔。終了条件そのものは PreviewServer が持つ */
	static final long PREVIEW_POLL_MILLIS = 2_000L;

	/**
	 * プレビューをブラウザで開く。成功したら true
	 *
	 * @param libraryDirs 併せて本棚に取り込むフォルダ。null / 空なら取り込まない。
	 *        <b>ブラウザを開く前に読み込むこと</b> — ビューアーは起動時の
	 *        {@code api/session} 一回で本棚ボタンを出すか決めるため、後から足すと出ない
	 */
	static boolean openPreview(File epubFile, String[] libraryDirs)
	{
		try {
			if (libraryDirs != null && libraryDirs.length > 0) loadLibraryBeforeOpen(libraryDirs);
			String url = com.github.hmdev.preview.PreviewLauncher.preview(epubFile);
			LogAppender.println("プレビューを開きました : "+url);
			return true;
		} catch (IOException e) {
			logger.error("プレビューの起動に失敗: {}", epubFile, e);
			LogAppender.error("プレビューの起動に失敗しました : "+e.getMessage());
			return false;
		}
	}

	/**
	 * ブラウザを開く前に本棚を読み込む。
	 * 棚は補助的な情報なので、読めなくても本のプレビュー自体は続ける。
	 */
	static void loadLibraryBeforeOpen(String[] libraryDirs)
	{
		java.util.List<java.nio.file.Path> folders = new ArrayList<>();
		for (String dir : libraryDirs) {
			File file = new File(dir);
			if (!file.isDirectory()) {
				LogAppender.error("本棚のフォルダが見つかりません : "+file.getAbsolutePath());
				continue;
			}
			folders.add(file.toPath());
		}
		if (folders.isEmpty()) return;
		try {
			int count = com.github.hmdev.preview.PreviewLauncher.loadLibraryInto(folders);
			LogAppender.println("本棚を読み込みました : "+count+" 冊");
		} catch (IOException e) {
			/* 意図的: 棚が読めなくても本のプレビューは続ける */
			logger.warn("本棚の読み込みに失敗: {}", folders, e);
			LogAppender.error("本棚を読み込めませんでした : "+e.getMessage());
		}
	}

	/**
	 * ブラウザが閉じられるか Ctrl-C まで待機する。
	 *
	 * <p>プレビューはローカル HTTP サーバでブラウザへ配信しているため、
	 * すぐ終了するとサーバも落ちてブラウザから読めなくなる。
	 * 一方でブラウザを閉じた後も待ち続けると、プロセスが裏に残り続けてしまう。
	 * 終了してよいかの判断は
	 * {@link com.github.hmdev.preview.PreviewServer#isViewerGone()} が持つ。</p>
	 */
	static void awaitTermination()
	{
		com.github.hmdev.preview.PreviewLauncher launcher =
			com.github.hmdev.preview.PreviewLauncher.getCurrent();
		if (launcher == null) return;

		LogAppender.println("プレビューを表示中です。ブラウザを閉じるか Ctrl-C で終了します。");
		com.github.hmdev.preview.PreviewServer server = launcher.getServer();
		try {
			while (!server.isViewerGone()) {
				Thread.sleep(PREVIEW_POLL_MILLIS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		LogAppender.println("ブラウザが閉じられたためプレビューを終了します。");
		com.github.hmdev.preview.PreviewLauncher.shutdown();
	}
	
	/**
	 * {@code -i} の指定が無いときに読む ini を決める。
	 *
	 * <p>従来どおり<b>カレントディレクトリを優先</b>し、そこに無ければ jar と同じ場所
	 * （配布フォルダの同梱 ini。{@code .exe} 起動の GUI が実質読み書きする場所）を見る。
	 * CLI は {@code template/} や {@code web/} を jar 基準で解決しているのに ini だけ
	 * カレント基準だったため、配布フォルダの外から
	 * {@code java -jar /path/to/AozoraEpub3.jar} を実行すると同梱 ini が無言で無視され、
	 * GUI で設定した内容も反映されなかった（docs/code-audit-followups.md 項目 26）。
	 *
	 * <p>jar 隣を先に見ないのは、配布フォルダには必ず同梱 ini があるため、
	 * jar 優先にすると<b>カレントに ini を置いて使い分けている運用が常に無視される</b>から。
	 * カレント優先 + jar フォールバックなら、どちらの使い方も壊れない。
	 *
	 * @param jarPath jar のあるディレクトリ。クラスパス実行時などは空文字
	 * @param fileName ini のファイル名
	 * @return 読み込む ini。どちらにも無ければカレント側の File（呼び出し側で存在チェックされる）
	 */
	static File resolveDefaultIniFile(String jarPath, String fileName)
	{
		return resolveDefaultIniFile(jarPath, fileName, null);
	}

	/**
	 * 作業ディレクトリを差し替えられるオーバーロード（テストがプロセスのカレントに依存しないため）。
	 * @param workingDir カレントディレクトリとして扱う場所。null ならプロセスのカレント
	 *  （返す File もカレント相対のまま）
	 */
	static File resolveDefaultIniFile(String jarPath, String fileName, File workingDir)
	{
		File inWorkingDir = workingDir == null ? new File(fileName) : new File(workingDir, fileName);
		File besideJar = jarPath != null && jarPath.length() > 0 ? new File(jarPath, fileName) : null;
		if (inWorkingDir.isFile()) {
			//両方に ini があるときはどちらを読んだか分かるように残す（項目 26 の本質は無言で無視されること）
			if (besideJar != null && besideJar.isFile()
					&& !besideJar.getAbsoluteFile().equals(inWorkingDir.getAbsoluteFile())) {
				logger.info("jar と同じ場所の ini は使用しません: {}", besideJar.getAbsolutePath());
			}
			return inWorkingDir;
		}
		if (besideJar != null && besideJar.isFile()) return besideJar;
		return inWorkingDir;
	}

	/** 出力ファイルを生成 */
	static File getOutFile(File srcFile, File dstPath, BookInfo bookInfo, boolean autoFileName, String outExt) throws IOException
	{
		//出力ファイル
		if (dstPath == null) dstPath = srcFile.getAbsoluteFile().getParentFile();
		String outFileName = "";
		if (autoFileName && (bookInfo.creator != null || bookInfo.title != null)) {
			outFileName = dstPath.getAbsolutePath()+"/";
			if (bookInfo.creator != null && bookInfo.creator.length() > 0) {
				String str = bookInfo.creator.replaceAll("[\\\\|\\/|\\:|\\*|\\?|\\<|\\>|\\||\\\"|\t]", "");
				if (str.length() > 64) str = str.substring(0, 64);
				outFileName += "["+str+"] ";
			}
			if (bookInfo.title != null) {
				outFileName += bookInfo.title.replaceAll("[\\\\|\\/|\\:|\\*|\\!|\\?|\\<|\\>|\\||\\\"|\t]", "");
			}
			if (outFileName.length() > 250) outFileName = outFileName.substring(0, 250);
		} else {
			outFileName = dstPath.getAbsolutePath()+"/"+srcFile.getName().replaceFirst("\\.[^\\.]+$", "");
		}
		if (outExt.length() == 0) outExt = ".epub";
		// Windows MAX_PATH (260) 対策: フルパス長を拡張子込みで制限
		int maxPath = 259; // 260 - 1 (null terminator)
		if (outFileName.length() + outExt.length() > maxPath) {
			outFileName = outFileName.substring(0, maxPath - outExt.length());
		}
		File outFile = new File(outFileName + outExt);
		// パストラバーサル対策: 出力パスが dstPath 配下にあることを検証 (PR #22/#23 の 2 段階パターン)
		Path dstPathNio = dstPath.toPath();
		Path canonicalDst = Files.exists(dstPathNio) ? dstPathNio.toRealPath() : dstPathNio.toAbsolutePath().normalize();
		Path outFileNio = outFile.toPath();
		Path canonicalOut = Files.exists(outFileNio) ? outFileNio.toRealPath() : outFileNio.toAbsolutePath().normalize();
		if (!canonicalOut.startsWith(canonicalDst)) {
			throw new IOException("出力パスが許可されたディレクトリ外です: " + canonicalOut);
		}
		outFile = canonicalOut.toFile();
		outFile.setWritable(true);

		return outFile;
	}
	
	/** 前処理で一度読み込んでタイトル等の情報を取得 */
	static public BookInfo getBookInfo(File srcFile, String ext, int txtIdx, ImageInfoReader imageInfoReader, AozoraEpub3Converter aozoraConverter,
			String encType, BookInfo.TitleType titleType, boolean pubFirst)
	{
		try {
			String[] textEntryName = new String[1];
			InputStream is = ArchiveTextExtractor.getTextInputStream(srcFile, ext, imageInfoReader, textEntryName, txtIdx);
			if (is == null) return null;
			
			//タイトル、画像注記、左右中央注記、目次取得
			BufferedReader src = new BufferedReader(new InputStreamReader(is, (String)encType));
			BookInfo bookInfo = aozoraConverter.getBookInfo(srcFile, src, imageInfoReader, titleType, pubFirst);
			is.close();
			bookInfo.textEntryName = textEntryName[0];
			return bookInfo;
			
		} catch (Exception e) {
			logger.error("BookInfo の生成に失敗: {}", srcFile, e);
			LogAppender.append("エラーが発生しました : ");
			LogAppender.println(e.getMessage());
		}
		return null;
	}
	
	/** ファイルを変換
	 * @param srcFile 変換するファイル
	 * @param dstPath 出力先パス
	 * @return 変換に成功したら true。失敗時は false（出力途中の EPUB は削除済み） */
	static public boolean convertFile(File srcFile, String ext, File outFile, AozoraEpub3Converter aozoraConverter, Epub3Writer epubWriter,
			String encType, BookInfo bookInfo, ImageInfoReader imageInfoReader, int txtIdx)
	{
		try {
			long time = System.currentTimeMillis();
			LogAppender.append("変換開始 : ");
			LogAppender.println(srcFile.getPath());
			
			//入力Stream再オープン
			BufferedReader src = null;
			if (!bookInfo.imageOnly) {
				//zip 内に txt が無い場合等は null が返る。そのまま渡すと NPE メッセージになる
				InputStream is = ArchiveTextExtractor.getTextInputStream(srcFile, ext, null, null, txtIdx);
				if (is == null) {
					LogAppender.println("入力ファイルからテキストが取得できませんでした : "+srcFile.getPath());
					return false;
				}
				src = new BufferedReader(new InputStreamReader(is, encType));
			}
			
			//ePub書き出し srcは中でクローズされる
			epubWriter.write(aozoraConverter, src, srcFile, ext, outFile, bookInfo, imageInfoReader);

			//キャンセル時は例外を投げずに戻り、出力途中のファイルも削除済みなので
			//「変換完了」とは報告しない
			boolean canceled = epubWriter.isCanceled();
			if (canceled) {
				LogAppender.println("変換を中止しました : "+srcFile.getPath());
			} else {
				LogAppender.append("変換完了["+(((System.currentTimeMillis()-time)/100)/10f)+"s] : ");
				LogAppender.println(outFile.getPath());
				//プレビュー対象として最後の出力を覚えておく (CLI の --preview / GUI のプレビューボタン用)
				if (outFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".epub")) {
					lastOutputFile = outFile;
				}
			}

			// アーカイブキャッシュをクリア（メモリ解放）
			if (!"txt".equals(ext)) {
				ArchiveTextExtractor.clearCache(srcFile);
			}

			return !canceled;
		} catch (Exception e) {
			logger.error("EPUB 変換に失敗: {}", srcFile, e);
			LogAppender.println("エラーが発生しました : "+e.getMessage());
			//LogAppender.printStaclTrace(e);
			return false;
		}
	}
	
	/** 入力ファイルと同じ名前の画像を取得
	 * png, jpg, jpegの順で探す  */
	static public String getSameCoverFileName(File srcFile)
	{
		String baseFileName = srcFile.getPath();
		baseFileName = baseFileName.substring(0, baseFileName.lastIndexOf('.')+1);
		for (String ext : new String[]{"png","jpg","jpeg","PNG","JPG","JPEG","Png","Jpg","Jpeg"}) {
			String coverFileName = baseFileName+ext; 
			if (Files.exists(Path.of(coverFileName))) return coverFileName;
		}
		return null;
	}
}
