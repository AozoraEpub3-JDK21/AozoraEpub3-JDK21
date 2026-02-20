package com.github.hmdev.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import com.github.hmdev.util.CharUtils;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.web.ExtractInfo.ExtractId;
import com.github.hmdev.web.api.NarouApiClient;
import com.github.hmdev.web.api.exception.ApiException;
import com.github.hmdev.web.api.model.NovelMetadata;

/** HTMLを青空txtに変換 */
public class WebAozoraConverter
{
	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	
	/** Singletonインスタンス格納 keyはFQDN */
	static HashMap<String, WebAozoraConverter> converters = new HashMap<String, WebAozoraConverter>();
	
	//設定ファイルから読み込むパラメータ
	/** リストページ抽出対象 HashMap<String key, String[]{cssQuery1, cssQuery2}> キーとJsoupのcssQuery(or配列) */
	HashMap<ExtractId, ExtractInfo[]> queryMap;
	
	/** 出力文字列置換情報 */
	HashMap<ExtractId, Vector<String[]>> replaceMap;
	
	/** テキスト出力先パス 末尾は/ */
	String dstPath;
	
	/** DnDされたページのURL文字列 */
	String urlString = null;
	
	/** http?://fqdn/ の文字列 */
	String baseUri;
	
	/** 変換中のHTMLファイルのあるパス 末尾は/ */
	String pageBaseUri;
	
	////////////////////////////////
	//変換設定
	/** 取得間隔 ミリ秒（なろう等のレート制限対策: 最低1秒推奨） */
	int interval = 1500;
	
	/** 未更新時は変換スキップ */
	boolean convertUpdated = false;
	
	/** 追加更新分のみ出力する */
	boolean convertModifiedOnly = false;
	/** 最新話から連続した追加更新分のみ出力 */
	boolean convertModifiedTail = false;
	/** 更新分に追加で変換する話数 */
	int beforeChapter = 0;
	/** この時間前までに取得された追加更新話を変換する */
	float modifiedExpire = 24;
	
	////////////////////////////////
	//キャンセルリクエストされたらtrue
	boolean canceled = false;
	//更新有りフラグ
	boolean updated = false;

	////////////////////////////////
	// 段階的レート制限 (narou.rbパターン: 10話ごとに長い休止)
	/** ダウンロードカウンター (段階的待機用) */
	private static int downloadCounter = 0;
	/** 長い休止を入れる間隔 (話数) */
	private static final int LONG_PAUSE_INTERVAL = 10;
	/** 長い休止の時間 (ミリ秒) */
	private static final int LONG_PAUSE_MS = 5000;

	////////////////////////////////
	// なろうAPI関連
	/** なろうAPI使用フラグ */
	private boolean useApi = false;
	/** API失敗時のHTMLフォールバック有効フラグ */
	private boolean apiFallbackEnabled = true;
	/** なろうAPIクライアント */
	private NarouApiClient apiClient = null;

	////////////////////////////////
	// narou.rb互換フォーマット設定
	/** narou.rb互換フォーマット設定 */
	private NarouFormatSettings formatSettings = new NarouFormatSettings();
	/** 作品タイトル (章見出しで柱に使用) */
	private String bookTitle = null;
	/** 英文保護用リスト（記号全角化処理で使用） */
	private java.util.ArrayList<String> englishSentences = new java.util.ArrayList<>();
	/** 漢数字退避用リスト（数字の漢数字化処理で使用） */
	private java.util.ArrayList<String> kanjiNumbers = new java.util.ArrayList<>();

	////////////////////////////////////////////////////////////////
	/** fqdnに対応したインスタンスを生成してキャッシュして変換実行 */
	public static WebAozoraConverter createWebAozoraConverter(String urlString, File configPath) throws IOException
	{
		urlString = urlString.trim();
		String baseUri = urlString.substring(0, urlString.indexOf('/', urlString.indexOf("//")+2));
		String fqdn = baseUri.substring(baseUri.indexOf("//")+2);
		WebAozoraConverter converter = converters.get(fqdn);
		if (converter == null) {
			converter = new WebAozoraConverter(fqdn, configPath);
			if (!converter.isValid()) {
				LogAppender.println("サイトの定義がありません: "+configPath.getName()+"/"+fqdn);
				return null;
			}
			converters.put(fqdn, converter);
		}
		return converter;
		//return converter._convertToAozoraText(urlString, baseUri, fqdn, cachePath);
	}
	
	////////////////////////////////////////////////////////////////
	/** fqdnに対応したパラメータ取得 
	 * @throws IOException */
	WebAozoraConverter(String fqdn, File configPath) throws IOException
	{
		if (configPath.isDirectory()) {
			for (File file : configPath.listFiles()) {
				if (file.isDirectory() && file.getName().equals(fqdn)) {
					
					//抽出情報
					File extractInfoFile = new File(configPath.getAbsolutePath()+"/"+fqdn+"/extract.txt");
					if (!extractInfoFile.isFile()) return;
					this.queryMap = new HashMap<ExtractId, ExtractInfo[]>();
					String line;
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(extractInfoFile), "UTF-8"));
					try {
						while ((line = br.readLine()) != null) {
							if (line.length() == 0 || line.charAt(0) == '#') continue;
							String[] values = line.split("\t", -1);
							if (values.length > 1) {
								ExtractId extractId = ExtractId.valueOf(values[0]);
								String[] queryStrings = values[1].split(",");
								Pattern pattern = values.length > 2 ? Pattern.compile(values[2]) : null; //ExtractInfoが複数でも同じ値を設定
								String replaceValue = values.length > 3 ? values[3] : null; //ExtractInfoが複数でも同じ値を設定
								ExtractInfo[] extractInfos = new ExtractInfo[queryStrings.length];
								for (int i=0; i<queryStrings.length; i++) extractInfos[i] = new ExtractInfo(queryStrings[i], pattern, replaceValue);
								this.queryMap.put(extractId, extractInfos);
							}
						}
					} finally{
						br.close();
					}
					
					//置換情報
					this.replaceMap = new HashMap<ExtractId, Vector<String[]>>();
					File replaceInfoFile = new File(configPath.getAbsolutePath()+"/"+fqdn+"/replace.txt");
					if (replaceInfoFile.isFile()) {
						br = new BufferedReader(new InputStreamReader(new FileInputStream(replaceInfoFile), "UTF-8"));
						try {
							while ((line = br.readLine()) != null) {
								if (line.length() == 0 || line.charAt(0) == '#') continue;
								String[] values = line.split("\t");
								if (values.length > 1) {
									ExtractId extractId = ExtractId.valueOf(values[0]);
									Vector<String[]> vecReplace = this.replaceMap.get(extractId);
									if (vecReplace == null) {
										vecReplace = new Vector<String[]>();
										this.replaceMap.put(extractId, vecReplace);
									}
									vecReplace.add(new String[]{values[1], values.length==2?"":values[2]});
								}
							}
						} finally{
							br.close();
						}
					}
					return;
				}
			}
		}
	}
	
	////////////////////////////////////////////////////////////////
	private boolean isValid()
	{
		return this.queryMap != null;
	}
	
	public void canceled()
	{
		this.canceled = true;
	}
	public boolean isCanceled()
	{
		return this.canceled;
	}
	public boolean isUpdated()
	{
		return this.updated;
	}
	
	////////////////////////////////////////////////////////////////
	// なろうAPI関連メソッド
	
	/**
	 * なろうAPI使用を設定
	 * @param useApi API使用フラグ
	 */
	public void setUseApi(boolean useApi) {
		this.useApi = useApi;
		if (useApi && apiClient == null) {
			apiClient = new NarouApiClient();
			LogAppender.println("なろうAPI: 有効化");
		}
	}
	
	/**
	 * APIフォールバック設定
	 * @param enabled フォールバック有効フラグ
	 */
	public void setApiFallbackEnabled(boolean enabled) {
		this.apiFallbackEnabled = enabled;
	}

	/**
	 * narou.rb互換フォーマット設定をロード
	 * @param settingFile 設定ファイル (setting_narourb.ini または narou.rbのsetting.ini)
	 */
	public void loadFormatSettings(File settingFile) {
		try {
			formatSettings.load(settingFile);
			LogAppender.println("フォーマット設定読み込み: " + settingFile.getName());
		} catch (IOException e) {
			LogAppender.error("フォーマット設定読み込み失敗: " + e.getMessage());
		}
	}

	/**
	 * フォーマット設定を取得
	 */
	public NarouFormatSettings getFormatSettings() {
		return formatSettings;
	}
	
	/**
	 * URLからNコードを抽出
	 * 例: https://ncode.syosetu.com/n0001a/ → n0001a
	 * @param url 作品URL
	 * @return Nコード (小文字)、抽出できない場合はnull
	 */
	private String extractNcode(String url) {
		if (url == null || !url.contains("syosetu.com")) {
			return null;
		}
		// ncode.syosetu.com/n1234ab/ または /n1234ab/123/ のパターン
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*/([nN]\\d{4}[a-zA-Z]+)/?.*");
		java.util.regex.Matcher matcher = pattern.matcher(url);
		if (matcher.matches()) {
			return matcher.group(1).toLowerCase();
		}
		return null;
	}

	/**
	 * R18サイト (novel18.syosetu.com) のURLかどうかを判定
	 */
	private boolean isR18Url(String url) {
		return url != null && url.contains("novel18.syosetu.com");
	}
	
	/**
	 * APIからメタデータを取得してキャッシュ
	 * @param ncode Nコード
	 * @param cachePath キャッシュディレクトリ
	 * @param urlString 元URL (R18判定に使用)
	 * @return NovelMetadata 取得成功時、nullは失敗
	 */
	private NovelMetadata fetchAndCacheMetadata(String ncode, File cachePath, String urlString) {
		if (apiClient == null) return null;

		try {
			boolean r18 = isR18Url(urlString);
			LogAppender.println("なろうAPI: メタデータ取得中... " + ncode + (r18 ? " (R18)" : ""));
			NovelMetadata metadata = apiClient.getNovelMetadata(ncode, r18);
			
			// メタデータをキャッシュに保存
			File metadataFile = new File(cachePath, "metadata.json");
			saveMetadataToCache(metadata, metadataFile);
			
			LogAppender.println("なろうAPI: 取得成功 - " + metadata.getTitle());
			return metadata;
			
		} catch (ApiException e) {
			LogAppender.println("なろうAPI: エラー - " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * メタデータをJSONファイルとして保存
	 */
	private void saveMetadataToCache(NovelMetadata metadata, File file) {
		try (BufferedWriter writer = new BufferedWriter(
			new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
			
			// 簡易JSON形式で保存
			writer.write("{\n");
			writer.write("  \"ncode\": \"" + escapeJson(metadata.getNcode()) + "\",\n");
			writer.write("  \"title\": \"" + escapeJson(metadata.getTitle()) + "\",\n");
			writer.write("  \"writer\": \"" + escapeJson(metadata.getWriter()) + "\",\n");
			writer.write("  \"story\": \"" + escapeJson(metadata.getStory()) + "\",\n");
			writer.write("  \"general_all_no\": " + metadata.getGeneralAllNo() + ",\n");
			writer.write("  \"length\": " + metadata.getLength() + ",\n");
			writer.write("  \"novel_type\": " + metadata.getNovelType() + ",\n");
			writer.write("  \"end\": " + metadata.getEnd() + ",\n");
			writer.write("  \"global_point\": " + metadata.getGlobalPoint() + ",\n");
			writer.write("  \"fav_novel_cnt\": " + metadata.getFavNovelCnt() + ",\n");
			writer.write("  \"timestamp\": " + System.currentTimeMillis() + "\n");
			writer.write("}\n");
			
		} catch (IOException e) {
			LogAppender.println("メタデータキャッシュ保存エラー: " + e.getMessage());
		}
	}
	
	/**
	 * JSON用文字列エスケープ
	 */
	private String escapeJson(String str) {
		if (str == null) return "";
		return str.replace("\\", "\\\\")
				  .replace("\"", "\\\"")
				  .replace("\n", "\\n")
				  .replace("\r", "\\r")
				  .replace("\t", "\\t");
	}
	
	/**
	 * 段階的待機 (narou.rbパターン)
	 * 通常はintervalミリ秒待機し、LONG_PAUSE_INTERVAL話ごとに長い休止を入れる
	 */
	private void sleepForDownload() throws InterruptedException {
		downloadCounter++;
		if (downloadCounter % LONG_PAUSE_INTERVAL == 0) {
			LogAppender.println("レート制限: " + downloadCounter + "話取得、" + (LONG_PAUSE_MS / 1000) + "秒休止");
			Thread.sleep(LONG_PAUSE_MS);
		} else {
			Thread.sleep(this.interval);
		}
	}

	////////////////////////////////////////////////////////////////
	/** 変換実行
	 * @param urlString
	 * @param cachePath
	 * @param interval
	 * @param modifiedExpire この時間以内のキャッシュを更新分として扱う
	 * @param convertUpdated 更新時のみ出力
	 * @param convertModifiedOnly 追加更新分のみ変換
	 * @param convertModifiedTail 最新話から連続したもののみ変換
	 * @param beforeChapter 指定話数のみ変換 0は指定無し
	 * @return 変換スキップやキャンセルならnullを返す */
	public File convertToAozoraText(String urlString, File cachePath, int interval, float modifiedExpire,
		boolean convertUpdated, boolean convertModifiedOnly, boolean convertModifiedTail, int beforeChapter) throws IOException
	{
		return convertToAozoraText(urlString, cachePath, interval, modifiedExpire, convertUpdated, convertModifiedOnly, convertModifiedTail, beforeChapter, null);
	}
	
	/** 変換実行 (ファイル名指定あり) */
	public File convertToAozoraText(String urlString, File cachePath, int interval, float modifiedExpire,
		boolean convertUpdated, boolean convertModifiedOnly, boolean convertModifiedTail, int beforeChapter, String outFileName) throws IOException
	{
		this.canceled = false;
		//日付一覧が取得できない場合は常に更新
		this.updated = true;
		
		// なろう等のレート制限対策: 最低1秒間隔
		this.interval = Math.max(1000, interval);
		this.modifiedExpire = Math.max(0, modifiedExpire);
		this.convertUpdated = convertUpdated;
		this.convertModifiedOnly = convertModifiedOnly;
		this.convertModifiedTail = convertModifiedTail;
		this.beforeChapter = beforeChapter;
		
		//末尾の / をリダイレクトで取得
		urlString = urlString.trim();
		if (!urlString.endsWith("/") && !urlString.endsWith(".html") && !urlString.endsWith(".htm") && urlString.indexOf("?") == -1 ) {
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) new URI(urlString+"/").toURL().openConnection();
				if (connection.getResponseCode() == 200) {
					urlString += "/";
					LogAppender.println("URL修正 : "+urlString);
				}
			} catch (Exception e) {
			} finally {
				if (connection != null) connection.disconnect();
			}
		}
		
		this.urlString = urlString;
		
		this.baseUri = urlString.substring(0, urlString.indexOf('/', urlString.indexOf("//")+2));
		//String fqdn = baseUri.substring(baseUri.indexOf("//")+2);
		String listBaseUrl = urlString.substring(0, urlString.lastIndexOf('/')+1);
		this.pageBaseUri = listBaseUrl;
		//http://を除外
		String urlFilePath = CharUtils.escapeUrlToFile(urlString.substring(urlString.indexOf("//")+2));
		//http://を除外した文字列で比較
		/*ExtractInfo[] extractInfos = this.queryMap.get(ExtractId.PAGE_REGEX);
		if(extractInfos != null) {
			if (!extractInfos[0].matches(urlString)) {
				LogAppender.println("読み込み可能なURLではありません");
				return null;
			}
		}*/
		
		String urlParentPath = urlFilePath;
		boolean isPath = false;
		if (urlFilePath.endsWith("/")) { isPath = true; urlFilePath += "index.html"; }
		else urlParentPath = urlFilePath.substring(0, urlFilePath.lastIndexOf('/')+1);
		
		//変換結果
		this.dstPath = cachePath.getAbsolutePath()+"/";
		if (isPath) this.dstPath += urlParentPath;
		else this.dstPath += urlFilePath+"_converted/";
		
		//urlStringのファイルをキャッシュ
		File cacheFile = new File(cachePath.getAbsolutePath()+"/"+urlFilePath);
		
		// なろうAPI処理: メタデータ取得を試行
		NovelMetadata apiMetadata = null;
		if (useApi) {
			String ncode = extractNcode(urlString);
			if (ncode != null) {
				LogAppender.println("なろうAPI: Nコード検出 - " + ncode);
				apiMetadata = fetchAndCacheMetadata(ncode, new File(this.dstPath).getParentFile(), urlString);
				
				if (apiMetadata == null && !apiFallbackEnabled) {
					LogAppender.println("なろうAPI: 取得失敗（フォールバック無効）");
					return null;
				}
			} else {
				LogAppender.println("なろうAPI: Nコード未検出、HTML取得を継続");
			}
		}
		
		try {
			LogAppender.append(urlString);
			cacheFile(urlString, cacheFile, null);
			LogAppender.println(" : List Loaded.");
		} catch (Exception e) {
			e.printStackTrace();
			LogAppender.println("一覧ページの取得に失敗しました。 ");
			LogAppender.println("エラー詳細: " + e.getClass().getName() + " - " + e.getMessage());
			if (!cacheFile.exists()) return null;
			
			LogAppender.println("キャッシュファイルを利用します。");
		}
		
		//パスならlist.txtの情報を元にキャッシュ後に青空txt変換して改ページで繋げて出力
		Document doc = Jsoup.parse(cacheFile, null);
		
		//タイトル
		boolean hasTitle = false;
		String series = getExtractText(doc, this.queryMap.get(ExtractId.SERIES));
		// APIメタデータがあればタイトルを優先使用
		String title;
		if (apiMetadata != null && apiMetadata.getTitle() != null && !apiMetadata.getTitle().isEmpty()) {
			title = apiMetadata.getTitle();
			LogAppender.println("なろうAPI: タイトル使用 - " + title);
		} else {
			title = getExtractText(doc, this.queryMap.get(ExtractId.TITLE));
		}
		if (title != null) {
			hasTitle = true;
			// 作品タイトルを保存 (章見出しの柱で使用)
			this.bookTitle = title;
		}
		if (!hasTitle && series != null) {
			title = series;
			hasTitle = true;
		}
		if (!hasTitle) {
			LogAppender.println("SERIES/TITLE : タイトルがありません");
			return null;
		}

		//著者
		// APIメタデータがあれば著者を優先使用
		String author;
		if (apiMetadata != null && apiMetadata.getWriter() != null && !apiMetadata.getWriter().isEmpty()) {
			author = apiMetadata.getWriter();
			LogAppender.println("なろうAPI: 著者使用 - " + author);
		} else {
			author = getExtractText(doc, this.queryMap.get(ExtractId.AUTHOR));
		}
		
		String fileName = outFileName;
		if (fileName == null) {
			fileName = "converted.txt";
			if (title != null && !title.isEmpty()) {
				String safeTitle = title.replaceAll("[\\\\|\\/|\\:|\\*|\\!|\\?|\\<|\\>|\\||\\\"|\t]", "");
				if (author != null && !author.isEmpty()) {
					String safeAuthor = author.replaceAll("[\\\\|\\/|\\:|\\*|\\!|\\?|\\<|\\>|\\||\\\"|\t]", "");
					fileName = "[" + safeAuthor + "] " + safeTitle + ".txt";
				} else {
					fileName = safeTitle + ".txt";
				}
			}
		} else {
			if (!fileName.toLowerCase().endsWith(".txt")) fileName += ".txt";
		}
		File txtFile = new File(this.dstPath + fileName);
		//表紙画像（narou.rb互換: cover.jpg で保存）
		File coverImageFile = new File(this.dstPath+"cover.jpg");
		//更新情報格納先
		File updateInfoFile = new File(this.dstPath+"update.txt");
		
		//フォルダ以外がすでにあったら削除
		File parentFile = txtFile.getParentFile();
		if (parentFile.exists() && !parentFile.isDirectory()) {
			parentFile.delete();
		}
		parentFile.mkdirs();
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(txtFile), "UTF-8"));
		try {
			
			//表紙画像
			Elements images = getExtractElements(doc, this.queryMap.get(ExtractId.COVER_IMG));
			if (images != null) {
				printImage(null, images.get(0), coverImageFile);
			}
			
			if (series != null) {
				printText(bw, series);
				bw.append('\n');
			}
			if (title != null) {
				printText(bw, title);
				bw.append('\n');
			}

			// APIメタデータから全話数をログ出力
			if (apiMetadata != null && apiMetadata.getGeneralAllNo() > 0) {
				LogAppender.println("なろうAPI: 全" + apiMetadata.getGeneralAllNo() + "話"
					+ (apiMetadata.getEnd() == 0 ? "（連載中）" : "（完結）"));
			}

			if (author != null) {
				printText(bw, author);
			}
			bw.append('\n');

			// narou.rb互換: 表紙挿絵注記を挿入
			if (coverImageFile.exists()) {
				bw.append("［＃挿絵（cover.jpg）入る］\n");
				bw.append('\n');
			}

			// narou.rb互換: 掲載URL表示
			if (formatSettings.isIncludeTocUrl()) {
				bw.append("［＃区切り線］\n");
				bw.append('\n');
				bw.append("掲載ページ:\n");
				bw.append("<a href=\"");
				bw.append(urlString);
				bw.append("\">");
				bw.append(urlString);
				bw.append("</a>\n");
				bw.append("［＃区切り線］\n");
				bw.append('\n');
			}
			//説明 (あらすじ)
			// narou.rb互換: include_story設定で制御
			if (formatSettings.isIncludeStory()) {
				// APIメタデータのあらすじがあれば優先使用
				if (apiMetadata != null && apiMetadata.getStory() != null && !apiMetadata.getStory().isEmpty()) {
					if (!formatSettings.isIncludeTocUrl()) {
						bw.append('\n');
						bw.append("［＃区切り線］\n");
						bw.append('\n');
					}
					bw.append("あらすじ：\n");
					bw.append("［＃ここから２字下げ］\n");
					bw.append("［＃ここから２字上げ］\n");
					// APIのあらすじはプレーンテキスト（改行は\nのみ）
					String story = apiMetadata.getStory().replace("\r\n", "\n").replace("\r", "\n");
					for (String line : story.split("\n")) {
						printText(bw, line);
						bw.append('\n');
					}
					bw.append("［＃ここで字上げ終わり］\n");
					bw.append("［＃ここで字下げ終わり］\n");
					bw.append('\n');
					bw.append("［＃区切り線］\n");
					bw.append('\n');
					LogAppender.println("なろうAPI: あらすじ使用");
				} else {
					Element description = getExtractFirstElement(doc, this.queryMap.get(ExtractId.DESCRIPTION));
					if (description != null) {
						if (!formatSettings.isIncludeTocUrl()) {
							bw.append('\n');
							bw.append("［＃区切り線］\n");
							bw.append('\n');
						}
						bw.append("あらすじ：\n");
						bw.append("［＃ここから２字下げ］\n");
						bw.append("［＃ここから２字上げ］\n");
						printNode(bw, description, true);
						bw.append('\n');
						bw.append("［＃ここで字上げ終わり］\n");
						bw.append("［＃ここで字下げ終わり］\n");
						bw.append('\n');
						bw.append("［＃区切り線］\n");
						bw.append('\n');
					}
				}
			}
			
			String contentsUpdate = getExtractText(doc, this.queryMap.get(ExtractId.UPDATE));
			
			//章名称 変わった場合に出力
			String preChapterTitle = "";
			
			//各話のURL(フルパス)を格納
			Vector<String> chapterHrefs = new Vector<String>();
			
			Elements hrefs = getExtractElements(doc, this.queryMap.get(ExtractId.HREF));
			if (hrefs == null && this.queryMap.containsKey(ExtractId.HREF)) {
				LogAppender.println("HREF : 各話のリンク先URLが取得できません");
			}
			
			Vector<String> subtitles = getExtractStrings(doc, this.queryMap.get(ExtractId.SUBTITLE_LIST), true);
			if (subtitles == null && this.queryMap.containsKey(ExtractId.SUBTITLE_LIST)) {
				LogAppender.println("SUBTITLE_LIST : 各話タイトルが取得できません");
			}
			
			//更新のない各話のURL(フルパス)を格納
			//nullならキャッシュ更新無しで、空ならすべて更新される
			HashSet<String> noUpdateUrls = null;
			String[] postDateList = null;
			if (hrefs == null) {
				//ページ番号取得
				String pageNumString = getExtractText(doc, this.queryMap.get(ExtractId.PAGE_NUM));
				if (pageNumString == null && this.queryMap.containsKey(ExtractId.PAGE_NUM)) {
					LogAppender.println("PAGE_NUM : ページ数が取得できません");
				}
				int pageNum = -1;
				try { pageNum = Integer.parseInt(pageNumString); } catch (Exception e) {}
				Element pageUrlElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.PAGE_URL));
				if (pageUrlElement == null && this.queryMap.containsKey(ExtractId.PAGE_URL)) {
					LogAppender.println("PAGE_URL : ページ番号用のURLが取得できません");
				}
				if (pageNum > 0 && pageUrlElement != null) {
					ExtractInfo pageUrlExtractInfo = this.queryMap.get(ExtractId.PAGE_URL)[0];
					//リンク生成 1～ページ番号まで
					for (int i=1; i<=pageNum; i++) {
						String pageUrl = pageUrlElement.attr("href");
						if (pageUrl != null) {
							pageUrl = pageUrlExtractInfo.replace(pageUrl+"\t"+i);
							if (pageUrl != null) {
								if (!pageUrl.startsWith("http")) {
									if (pageUrl.charAt(0) == '/') pageUrl = baseUri+pageUrl;
									else pageUrl = listBaseUrl+pageUrl;
								}
								chapterHrefs.add(pageUrl);
							}
						}
					}
				} else {
					Elements contentDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE));
					if (contentDivs != null) {
						//一覧のリンクはないが本文がある場合
						docToAozoraText(bw, doc, false, null, null);
					} else {
						LogAppender.println("一覧のURLが取得できませんでした");
						return null;
					}
				}
			} else {
				//更新分のみ取得するようにするためhrefに対応した日付タグの文字列(innerHTML)を取得して保存しておく
				Elements updates = getExtractElements(doc, this.queryMap.get(ExtractId.SUB_UPDATE));
				if (updates == null && this.queryMap.containsKey(ExtractId.SUB_UPDATE)) {
					LogAppender.println("SUB_UPDATE : 更新確認情報が取得できません");
				}
				if (updates != null) {
					//更新しないURLのチェック用
					noUpdateUrls = createNoUpdateUrls(updateInfoFile, urlString, listBaseUrl, contentsUpdate, hrefs, updates);
				}
				//一覧のhrefをすべて取得
				for (Element href : hrefs) {
					String hrefString = href.attr("href");
					if (hrefString == null || hrefString.length() == 0) continue;
					//パターンがあればマッチング
					ExtractInfo extractInfo = this.queryMap.get(ExtractId.HREF)[0];
					if (!extractInfo.hasPattern() || extractInfo.matches(hrefString)) {
						String chapterHref = hrefString;
						if (!hrefString.startsWith("http")) {
							if (hrefString.charAt(0) == '/') chapterHref = baseUri+hrefString;
							else chapterHref = listBaseUrl+hrefString;
						}
						chapterHrefs.add(chapterHref);
					}
				}
				
				postDateList = getPostDateList(doc, this.queryMap.get(ExtractId.CONTENT_UPDATE_LIST));
				if (postDateList == null && this.queryMap.containsKey(ExtractId.CONTENT_UPDATE_LIST)) {
					LogAppender.println("CONTENT_UPDATE_LIST : 一覧ページの更新日時情報が取得できません");
				}
			}
			

			// __NEXT_DATA__ JSON 繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ: HTML縺ｮhrefs莉ｶ謨ｰ繧医ｊ螟壹＞蝣ｴ蜷医↓菴ｿ逕ｨ (繧ｫ繧ｯ繝ｨ繝縺ｪ縺ｩ Next.js 繧ｵ繧､繝亥ｯｾ蠢・
			List<String> nextDataEpisodes = extractEpisodesFromNextData(doc, urlString);
			if (nextDataEpisodes != null && nextDataEpisodes.size() > chapterHrefs.size()) {
				LogAppender.println("__NEXT_DATA__ JSON 縺九ｉ繧ｨ繝斐た繝ｼ繝我ｸ隕ｧ蜿門ｾ・ " + nextDataEpisodes.size() + "隧ｱ");
				chapterHrefs.clear();
				chapterHrefs.addAll(nextDataEpisodes);
			}
	
			if (chapterHrefs.size() > 0) {
				//全話で更新や追加があるかチェック
				updated = false;
				
				//追加更新対象の期限 これより大きければ追加更新
				long expire = System.currentTimeMillis()-(long)(this.modifiedExpire*3600000);
				//追加更新分のみ出力時に利用
				HashSet<Integer> modifiedChapterIdx = null;
				//更新されていない最後の話数 0～
				int lastNoModifiedChapterIdx = -1;
				if (this.convertModifiedOnly) {
					modifiedChapterIdx = new HashSet<Integer>();
				}
				
				int chapterIdx = 0;
				for (String chapterHref : chapterHrefs) {
					if (this.canceled) return null;
					
					if (chapterHref != null && chapterHref.length() > 0) {
						//画像srcをフルパスにするときに使うページのパス
						this.pageBaseUri = chapterHref;
						if (!chapterHref.endsWith("/")) {
							int idx = chapterHref.indexOf('/', 7);
							if (idx > -1) this.pageBaseUri = chapterHref.substring(0, idx);
						}
						
						//キャッシュ取得 ロードされたらWait 500ms
						String chapterPath = CharUtils.escapeUrlToFile(chapterHref.substring(chapterHref.indexOf("//")+2));
						File chapterCacheFile = new File(cachePath.getAbsolutePath()+"/"+chapterPath+(chapterPath.endsWith("/")?"index.html":""));
						//hrefsのときは更新分のみurlsに入っている
						boolean loaded = false;
						
						//更新対象ならtrueに変更
						boolean reload = false;
						//nullでなく更新無しに含まれなければ再読込
						if (noUpdateUrls != null && !noUpdateUrls.contains(chapterHref)) reload = true;
						
						if (reload || !chapterCacheFile.exists()) {
							LogAppender.append("["+(chapterIdx+1)+"/"+chapterHrefs.size()+"] "+chapterHref);
							try {
								try { sleepForDownload(); } catch (InterruptedException e) { }
								cacheFile(chapterHref, chapterCacheFile, urlString);
								LogAppender.println(" : Loaded.");
								//ファイルがロードされたら更新有り
								this.updated = true;
								loaded = true;
							} catch (Exception e) {
								e.printStackTrace();
								LogAppender.println("htmlファイルが取得できませんでした : "+chapterHref);
							}
						}
						//キャッシュされているファイルが指定時間内なら更新扱い
						if (!loaded) {
							if (this.modifiedExpire > 0 && (this.convertModifiedOnly || this.convertUpdated) && chapterCacheFile.lastModified() >= expire) {
								LogAppender.append("["+(chapterIdx+1)+"/"+chapterHrefs.size()+"] "+chapterHref);
								LogAppender.println(" : Modified.");
								this.updated = true;
							}
						}
						//更新分のみ出力時のチェック
						if (this.convertModifiedOnly) {
							//ファイルの更新日時で比較
							if (chapterCacheFile.lastModified() >= expire) {
								modifiedChapterIdx.add(chapterIdx);
							} else {
								if (this.convertModifiedTail) {
									//最新から連続していない話は除外
									modifiedChapterIdx.clear();
								}
								lastNoModifiedChapterIdx = chapterIdx;
							}
						}
					}
					chapterIdx++;
				}
				//更新が無くて変換もなければ終了
				if (!this.updated) {
					LogAppender.append("「"+title+"」");
					LogAppender.println("の更新はありません");
					if (this.convertUpdated) return null;
				}
				
				if (this.convertModifiedOnly) {
					//更新前の話数を追加 昇順で重複もはじく
					if (this.beforeChapter > 0) {
						int startIdx = Math.max(0, lastNoModifiedChapterIdx-this.beforeChapter+1);
						if (modifiedChapterIdx.size() == 0) {
							//追加分なし
							int idx = chapterHrefs.size()-1;
							for (int i=0; i<this.beforeChapter; i++) {
								modifiedChapterIdx.add(idx--);
							}
						} else {
							//追加分あり
							for (int i=startIdx; i<=lastNoModifiedChapterIdx; i++) {
								modifiedChapterIdx.add(i);
							}
						}
					}
					if (modifiedChapterIdx.size() == 0) {
						LogAppender.println("追加更新分はありません");
						this.updated = false;
						return null;
					}
				} else {
					//最新話数指定
					if (this.beforeChapter > 0) {
						int idx = chapterHrefs.size()-1;
						modifiedChapterIdx = new HashSet<Integer>();
						for (int i=0; i<this.beforeChapter; i++) {
							modifiedChapterIdx.add(idx--);
						}
					}
				}
				
				//変換実行
				chapterIdx = 0;
				for (String chapterHref : chapterHrefs) {
					if (this.canceled) return null;
					
					if (modifiedChapterIdx == null || modifiedChapterIdx.contains(chapterIdx)) {
						//キャッシュファイル取得
						String chapterPath = CharUtils.escapeUrlToFile(chapterHref.substring(chapterHref.indexOf("//")+2));
						File chapterCacheFile = new File(cachePath.getAbsolutePath()+"/"+chapterPath+(chapterPath.endsWith("/")?"index.html":""));
						//シリーズタイトルを出力
						Document chapterDoc = Jsoup.parse(chapterCacheFile, null);
						String chapterTitle = getExtractText(chapterDoc, this.queryMap.get(ExtractId.CONTENT_CHAPTER));
						boolean newChapter = false;
						if (chapterTitle != null && !preChapterTitle.equals(chapterTitle)) {
							newChapter = true;
							preChapterTitle = chapterTitle;
							bw.append("\n［＃改ページ］\n");
							// narou.rb互換: 章中表紙のレイアウト
							if (formatSettings.isChapterUseCenterPage()) {
								bw.append("［＃ページの左右中央］\n");
							}
							if (formatSettings.isChapterUseHashira() && this.bookTitle != null) {
								bw.append("［＃ここから柱］");
								printText(bw, this.bookTitle);
								bw.append("［＃ここで柱終わり］\n");
							}
							bw.append("［＃" + formatSettings.getIndent() + "字下げ］［＃大見出し］");
							printText(bw, preChapterTitle);
							bw.append("［＃大見出し終わり］\n");
							bw.append('\n');
						}
						//更新日時を一覧から取得
						String postDate = null;
						if (postDateList != null && postDateList.length > chapterIdx) {
							postDate = postDateList[chapterIdx];
						}
						String subTitle = null;
						if (subtitles != null && subtitles.size() > chapterIdx) subTitle = subtitles.get(chapterIdx);
						
						docToAozoraText(bw, chapterDoc, newChapter, subTitle, postDate);
					}
					chapterIdx++;
				}
				
				//出力話数を表示
				if (modifiedChapterIdx != null) {
					StringBuilder buf = new StringBuilder();
					int preIdx = -1;
					boolean idxConnected = false;
					//出力話数生成
					for (int idx=0; idx<chapterHrefs.size(); idx++) {
						if (modifiedChapterIdx.contains(idx)) {
							if (buf.length() == 0) buf.append((idx+1));
							else {
								if (preIdx == idx-1) {
									idxConnected = true;
								} else {
									if (idxConnected) buf.append("-"+(preIdx+1));
									idxConnected = false;
									buf.append(","+(idx));
								}
							}
							preIdx = idx;
						}
					}
					if (idxConnected) buf.append("-"+(preIdx+1));
					LogAppender.println(buf+"話を変換します");
				}
			}
			//底本にURL追加
			bw.append("\n［＃改ページ］\n");
			bw.append("底本： ");
			bw.append("<a href=\"");
			bw.append(urlString);
			bw.append("\">");
			bw.append(urlString);
			bw.append("</a>");
			bw.append('\n');
			bw.append("変換日時： ");
			bw.append(dateFormat.format(new Date()));
			bw.append('\n');

			// narou.rb互換: 本の終了マーカー
			if (formatSettings.isEnableDisplayEndOfBook()) {
				bw.append("\n");
				bw.append("［＃ここから地付き］［＃小書き］（本を読み終わりました）［＃小書き終わり］［＃ここで地付き終わり］\n");
			}

		} finally {
			bw.close();
		}

		// ファイナライズ処理: 文章全体の後処理
		// (前書き・後書き検出、自動字下げ、改ページ後見出し化、括弧チェック)
		try {
			AozoraTextFinalizer finalizer = new AozoraTextFinalizer(this.formatSettings);
			finalizer.finalize(txtFile);
		} catch (IOException e) {
			LogAppender.println("警告: ファイナライズ処理中にエラーが発生しました: " + e.getMessage());
			e.printStackTrace();
			// エラーが発生してもファイルは返す（ファイナライズ処理は付加的な処理のため）
		}

		this.canceled = false;
		return txtFile;
	}
	
	/** 更新情報の生成と保存 */
	private HashSet<String> createNoUpdateUrls(File updateInfoFile, String urlString, String listBaseUrl, String contentsUpdate, Elements hrefs, Elements updates) throws IOException
	{
		HashMap<String, String> updateStringMap = new HashMap<String, String>();
		
		if (hrefs == null || updates == null || hrefs.size() != updates.size()) {
			
			return null;
			
		} else {
			if (updateInfoFile.exists()) {
				//前回の更新情報を取得して比較
				BufferedReader updateBr = new BufferedReader(new InputStreamReader(new FileInputStream(updateInfoFile), "UTF-8"));
				try {
					String line;
					while ((line=updateBr.readLine()) != null) {
						int idx = line.indexOf("\t");
						if (idx > 0) {
							updateStringMap.put(line.substring(0, idx), line.substring(idx+1));
						}
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					updateBr.close();
				}
			}
		}
		
		if (contentsUpdate != null || updates != null) {
			//ファイルに出力
			BufferedWriter updateBw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(updateInfoFile), "UTF-8"));
			try {
				if (contentsUpdate != null) {
					updateBw.append(urlString);
					updateBw.append('\t');
					updateBw.append(contentsUpdate);
					updateBw.append('\n');
				}
				if (updates != null) {
					int i = 0;
					for (Element update : updates) {
						updateBw.append(hrefs.get(i++).attr("href"));
						updateBw.append('\t');
						updateBw.append(update.html().replaceAll("\n", " "));
						updateBw.append('\n');
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				updateBw.close();
			}
		}
		
		HashSet<String> noUpdateUrls = new HashSet<String>();
		int i = -1;
		for (Element href : hrefs) {
			i++;
			String hrefString = href.attr("href");
			if (hrefString == null || hrefString.length() == 0) continue;
			String updateString = updateStringMap.get(hrefString);
			String html  = updates.get(i).html().replaceAll("\n", " ");
			if (updateString != null && updateString.equals(html)) {
				String chapterHref = hrefString;
				if (!hrefString.startsWith("http")) {
					if (hrefString.charAt(0) == '/') chapterHref = baseUri+hrefString;
					else chapterHref = listBaseUrl+hrefString;
				}
				noUpdateUrls.add(chapterHref);
			}
		}
		
		
		return noUpdateUrls;
	}
	
	/** 一覧から更新日時を取得 */
	private String[] getPostDateList(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements == null || elements.size() == 0) continue;
			String[] postDateList = new String[elements.size()];
			for (int i=0; i<postDateList.length; i++) {
				postDateList[i] = extractInfo.replace(elements.get(i).html());
			}
			return postDateList;
		}
		return null;
	}
	
	/** 各話のHTMLの変換
	 * @param listSubTitle 一覧側で取得したタイトル */
	private void docToAozoraText(BufferedWriter bw, Document doc, boolean newChapter, String listSubTitle, String postDate) throws IOException
	{
		// 英文保護リストをクリア（各話ごとに初期化）
		englishSentences.clear();
		// 漢数字退避リストをクリア（各話ごとに初期化）
		kanjiNumbers.clear();

		Elements contentDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE));
		if (contentDivs == null || contentDivs.size() == 0) {
			LogAppender.println("CONTENT_ARTICLE : 本文が取得できません");
		} else {
			if (!newChapter) bw.append("\n［＃改ページ］\n");
			String subTitle = getExtractText(doc, this.queryMap.get(ExtractId.CONTENT_SUBTITLE));
			if (subTitle == null) subTitle = listSubTitle; //一覧のタイトルを設定
			if (subTitle != null) {
				// narou.rb互換: 横書き時は1字下げ、縦書き時は3字下げ
				bw.append("［＃" + formatSettings.getIndent() + "字下げ］［＃中見出し］");
				printText(bw, subTitle);
				bw.append("［＃中見出し終わり］\n");
			}
			//公開日付
			// narou.rb互換: show_post_date設定で制御
			if (formatSettings.isShowPostDate()) {
				String coutentUpdate = getExtractText(doc, this.queryMap.get(ExtractId.CONTENT_UPDATE));
				if (coutentUpdate != null && coutentUpdate.length() > 0) postDate = coutentUpdate;
				if (postDate != null) {
					bw.append("［＃ここから地から１字上げ］\n［＃ここから１段階小さな文字］\n");
					printText(bw, postDate);
					bw.append('\n');
					bw.append("［＃ここで小さな文字終わり］\n［＃ここで字上げ終わり］\n");
				}
			}
			
			bw.append('\n');
			
			//画像 前に表示
			Elements images = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_IMG));
			if (images != null) printImage(bw, images.get(0));
			
			//前書き
			Elements preambleDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE));
			if (preambleDivs != null) {
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE_END));
				// narou.rb互換: author_comment_styleで分岐
				String style = formatSettings.getAuthorCommentStyle();
				if ("css".equals(style)) {
					// css: ［＃ここから前書き］マーカーで囲む
					bw.append("［＃ここから前書き］\n");
					for (Element elem : preambleDivs) printNode(bw, elem, startElement, endElement, true);
					bw.append("［＃ここで前書き終わり］\n");
				} else if ("simple".equals(style)) {
					// simple: 8字下げ＋2段階小さな文字
					bw.append("\n");
					bw.append("［＃ここから８字下げ］\n");
					bw.append("［＃ここから２段階小さな文字］\n");
					for (Element elem : preambleDivs) printNode(bw, elem, startElement, endElement, true);
					bw.append("\n");
					bw.append("［＃ここで小さな文字終わり］\n");
					bw.append("［＃ここで字下げ終わり］\n");
				} else {
					// plain: 区切り線のみ
					bw.append("\n\n");
					for (Element elem : preambleDivs) printNode(bw, elem, startElement, endElement, true);
					bw.append("\n\n");
					bw.append("［＃区切り線］\n");
					bw.append("\n");
				}
			}
			//本文
			String separator = null;
			ExtractInfo[] separatorInfo = this.queryMap.get(ExtractId.CONTENT_ARTICLE_SEPARATOR);
			if (separatorInfo != null && separatorInfo.length > 0) {
				separator = separatorInfo[0].query;
			}
			boolean first = true;
			for (Element elem : contentDivs) {
				//複数のDivの場合は間に改行追加
				if (first) first = false;
				else {
					bw.append("\n");
					if (separator != null) bw.append(separator);
				}
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE_END));
				printNode(bw, elem, startElement, endElement, false);
			}
			
			//後書き
			Elements appendixDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX));
			if (appendixDivs != null) {
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX_END));
				bw.append("\n\n");
				// narou.rb互換: author_comment_styleで分岐
				String style = formatSettings.getAuthorCommentStyle();
				if ("css".equals(style)) {
					// css: ［＃ここから後書き］マーカーで囲む
					bw.append("［＃ここから後書き］\n");
					for (Element elem : appendixDivs) printNode(bw, elem, startElement, endElement, true);
					bw.append("［＃ここで後書き終わり］\n");
				} else if ("simple".equals(style)) {
					// simple: 8字下げ＋2段階小さな文字
					bw.append("［＃ここから８字下げ］\n");
					bw.append("［＃ここから２段階小さな文字］\n");
					for (Element elem : appendixDivs) printNode(bw, elem, startElement, endElement, true);
					bw.append("\n");
					bw.append("［＃ここで小さな文字終わり］\n");
					bw.append("［＃ここで字下げ終わり］\n");
				} else {
					// plain: 区切り線のみ
					bw.append("［＃区切り線］\n");
					bw.append("\n");
					for (Element elem : appendixDivs) printNode(bw, elem, startElement, endElement, true);
				}
			}
		}
	}
	
	Node startElement = null;
	Node endElement = null;
	boolean noHr = false;
	/** ノードを出力 子ノード内のテキストも出力 */
	private void printNode(BufferedWriter bw, Node parent, boolean noHr) throws IOException
	{
		printNode(bw, parent, null, null, noHr);
	}
	/** ノードを出力 子ノード内のテキストも出力 */
	private void printNode(BufferedWriter bw, Node parent, Node start, Node end, boolean noHr) throws IOException
	{
		this.startElement = start;
		this.endElement = end;
		this.noHr = noHr;
		_printNode(bw, parent);
	}
	/** ノードを出力 再帰用 */
	private void _printNode(BufferedWriter bw, Node parent) throws IOException
	{
		for (Node node : parent.childNodes()) {
			if (startElement != null) {
				if (node.equals(startElement)) {
					startElement = null;
					continue;
				}
				if (node instanceof Element) _printNode(bw, node);
				continue;
			}
			if (endElement != null && node.equals(endElement)) {
				return;
			}
			if (node instanceof TextNode) printText(bw, ((TextNode)node).getWholeText());
			else if (node instanceof Element) {
				Element elem = (Element)node;
				if ("br".equals(elem.tagName())) {
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("div".equals(elem.tagName())) {
					if (elem.previousSibling() != null && !isBlockNode(elem.previousSibling())) bw.append('\n');
					_printNode(bw, node); //子を出力
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("p".equals(elem.tagName())) {
					if (elem.previousSibling() != null && !isBlockNode(elem.previousSibling())) bw.append('\n');
					_printNode(bw, node); //子を出力
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("ruby".equals(elem.tagName())) {
					//ルビ注記出力
					printRuby(bw, elem);
				} else if ("img".equals(elem.tagName())) {
					//画像をキャッシュして注記出力
					printImage(bw, elem);
				} else if ("hr".equals(elem.tagName()) && !this.noHr) {
					bw.append("［＃区切り線］\n");
				} else if ("b".equals(elem.tagName())) {
					bw.append("［＃ここから太字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃ここで太字終わり］");
				} else if ("sup".equals(elem.tagName())) {
					bw.append("［＃上付き小文字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃上付き小文字終わり］");
				} else if ("sub".equals(elem.tagName())) {
					bw.append("［＃下付き小文字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃下付き小文字終わり］");
				} else if ("strike".equals(elem.tagName()) || "s".equals(elem.tagName()) ) {
					bw.append("［＃取消線］");
					_printNode(bw, node); //子を出力
					bw.append("［＃取消線終わり］");
				} else if ("tr".equals(elem.tagName())) {
					_printNode(bw, node); //子を出力
					bw.append('\n');
				} else {
					_printNode(bw, node); //子を出力
				}
			} else {
				System.out.println(node.getClass().getName());
			}
		}
	}
	/** 前がブロック注記かどうか */
	private boolean isBlockNode(Node node)
	{
		if (node instanceof Element) {
			Element elem = (Element)node;
			String tagName = elem.tagName();
			if ("br".equals(tagName)) return true;
			if ("div".equals(tagName))  return true;
			if ("p".equals(tagName))  return true;
			if ("hr".equals(tagName))  return true;
			if ("table".equals(tagName))  return true;
		}
		return false;
	}
	
	/** ルビを青空ルビにして出力
	 * narou.rb互換: <rb>タグの有無に関わらず処理 */
	private void printRuby(BufferedWriter bw, Element ruby) throws IOException
	{
		String rubyHtml = ruby.html();

		// <rt>タグで分割（大文字小文字無視）
		String[] parts = rubyHtml.split("(?i)<rt>", 2);
		if (parts.length < 2) {
			// <rt>タグがない場合は、タグを削除してテキストのみ出力
			String text = parts[0].replaceAll("<[^>]+>", "");
			printText(bw, text);
			return;
		}

		// 親文字: <rt>の前の部分から<rp>を除去してタグ削除
		String rubyBase = parts[0]
			.replaceAll("(?i)<rp>.*?</rp>", "")  // <rp>タグ削除
			.replaceAll("<[^>]+>", "");           // 残りのタグ削除

		// 振り仮名: <rt>の後の部分から</rt>までを抽出し、<rp>を除去してタグ削除
		String rtContent = parts[1].split("(?i)</rt>", 2)[0];
		String rubyText = rtContent
			.replaceAll("(?i)<rp>.*?</rp>", "")  // <rp>タグ削除
			.replaceAll("<[^>]+>", "");           // 残りのタグ削除

		// 青空文庫形式で出力
		bw.append('｜');
		printText(bw, rubyBase);
		bw.append('《');
		printText(bw, rubyText);
		bw.append('》');
	}
	/** 画像をキャッシュして相対パスの注記にする */
	private void printImage(BufferedWriter bw, Element img) throws IOException
	{
		this.printImage(bw, img, null);
	}
	/** 画像をキャッシュして相対パスの注記にする
	 * @param bw nullなら注記文字列は出力しない
	 * @param img imgタグ
	 * @param imageOutFile null出なければこのファイルに画像を出力 */
	private void printImage(BufferedWriter bw, Element img, File imageOutFile) throws IOException
	{
		String src = img.attr("src");
		if (src == null || src.length() == 0) return;
		
		String imagePath = null;
		int idx = src.indexOf("//");
		if (idx > 0) {
			imagePath = CharUtils.escapeUrlToFile(src.substring(idx+2));
		} else if (src.charAt(0) == '/') {
			imagePath = "_"+CharUtils.escapeUrlToFile(src);
			src = this.baseUri+src;
		}
		else {
			imagePath = "__/"+CharUtils.escapeUrlToFile(src);
			if (this.pageBaseUri.endsWith("/")) src = this.pageBaseUri+src;
			else src = this.pageBaseUri+"/"+src;
		}
		
		if (imagePath.endsWith("/")) imagePath += "image.png";
		
		File imageFile = new File(this.dstPath+"images/"+imagePath);
		
		try {
			if (imageOutFile != null) {
				if (imageOutFile.exists()) imageOutFile.delete();
				cacheFile(src, imageOutFile, this.urlString);
			} else if (!imageFile.exists()) {
				cacheFile(src, imageFile, this.urlString);
			}
		} catch (Exception e) {
			e.printStackTrace();
			LogAppender.println("画像が取得できませんでした : "+src);
		}
		if (bw != null) {
			bw.append("［＃挿絵（");
			bw.append("images/"+imagePath);
			bw.append("）入る］\n");
		}
	}
	
	/**
	 * 行頭のかぎ括弧類に二分アキを追加（narou.rb互換）
	 */
	private String addHalfIndentBracket(String text) {
		if (!formatSettings.isEnableHalfIndentBracket()) {
			return text;
		}

		// 行頭の空白（全角・半角・タブ）+ かぎ括弧類
		// 空白を削除して［＃二分アキ］に置換
		Pattern pattern = Pattern.compile("^[ 　\\t]*([「『〔（【〈《≪〝])", Pattern.MULTILINE);
		return pattern.matcher(text).replaceAll(phChuki("二分アキ") + "$1");
	}

	/**
	 * 英文保護 + 短い英単語の全角化（narou.rb互換）
	 * 参考: narou-3.9.1/lib/converterbase.rb:496-520 alphabet_to_zenkaku
	 *
	 * 3分岐:
	 * 1. 複数単語（スペース区切り2語以上）→ プレースホルダーで保護
	 * 2. 長い単語（8文字以上でアルファベット含む）→ 半角のまま保持
	 * 3. それ以外 → アルファベットを全角化
	 */
	private String protectEnglishSentences(String text) {
		Pattern pattern = Pattern.compile("[\\w.,!?'\" &:;_-]+");
		java.util.regex.Matcher matcher = pattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String match = matcher.group();
			if (isMultiWordEnglish(match)) {
				// 複数単語の英文 → プレースホルダーで保護（半角のまま）
				englishSentences.add(match);
				matcher.appendReplacement(result,
					Matcher.quoteReplacement(phChuki("英文＝" + (englishSentences.size() - 1))));
			} else if (shouldKeepHankaku(match)) {
				// 長い英単語 → 半角のまま保持（変換も保護もしない）
				matcher.appendReplacement(result,
					java.util.regex.Matcher.quoteReplacement(match));
			} else {
				// 短い英単語 → アルファベットを全角化
				String zenkaku = alphabetToZenkaku(match);
				matcher.appendReplacement(result,
					java.util.regex.Matcher.quoteReplacement(zenkaku));
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/** 半角アルファベットを全角に変換 */
	private String alphabetToZenkaku(String text) {
		StringBuilder sb = new StringBuilder();
		for (char textChar : text.toCharArray()) {
			if (textChar >= 'a' && textChar <= 'z') {
				sb.append((char) ('ａ' + (textChar - 'a')));
			} else if (textChar >= 'A' && textChar <= 'Z') {
				sb.append((char) ('Ａ' + (textChar - 'A')));
			} else {
				sb.append(textChar);
			}
		}
		return sb.toString();
	}

	/** 英文保護の最小文字数（narou.rb: should_word_be_hankaku? のしきい値） */
	private static final int ENGLISH_SENTENCES_MIN_LENGTH = 8;

	// 英字検出用パターン（事前コンパイルして ReDoS を回避）
	private static final java.util.regex.Pattern ASCII_LETTER_PATTERN = java.util.regex.Pattern.compile("[a-zA-Z]");

	/**
	 * 複数単語の英文かを判定（narou.rb互換: sentence?）
	 * スペース区切りで2単語以上ならtrue
	 * Ruby の split(" ") は先頭・末尾の空白を無視するため、trim() して判定
	 */
	private boolean isMultiWordEnglish(String str) {
		String trimmed = str.trim();
		if (trimmed.isEmpty()) return false;
		return trimmed.split("\\s+").length >= 2;
	}

	/**
	 * 半角のまま保持すべきかを判定（narou.rb互換: should_word_be_hankaku?）
	 * 一定文字数以上でアルファベットを含む場合はtrue
	 */
	private boolean shouldKeepHankaku(String str) {
		// 長さ条件を満たし、文字列内に英字が含まれるかを事前コンパイル済みの
		// パターンで判定することで、パターンの再コンパイルやバックトラッキング
		// による ReDoS のリスクを排除します。
		return str.length() >= ENGLISH_SENTENCES_MIN_LENGTH &&
		       ASCII_LETTER_PATTERN.matcher(str).find();
	}

	/**
	 * 保護した英文を復元（narou.rb互換）
	 */
	private String rebuildEnglishSentences(String text) {
		for (int i = 0; i < englishSentences.size(); i++) {
			text = text.replace(phChuki("英文＝" + i), englishSentences.get(i));
		}
		return text;
	}

	/**
	 * 縦中横処理（narou.rb互換）
	 * 2～3個の感嘆符・疑問符を縦中横化
	 */
	/**
	 * 縦中横処理（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:384-423
	 *
	 * 感嘆符・疑問符の組み合わせを縦中横化:
	 * - ！が3個: !!!
	 * - ！が4個以上: 偶数個に調整して2個ずつ縦中横（!!)(!!）...
	 * - ！？の2～3個の組み合わせ: 特定パターンのみ
	 */
	private String convertTatechuyoko(String text) {
		String tcyOpen = phChuki("縦中横");
		String tcyClose = phChuki("縦中横終わり");

		// ！の連続（4個以上の場合は2個ずつ縦中横化）
		Pattern pattern4Plus = Pattern.compile("！{4,}");
		Matcher matcher = pattern4Plus.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			int len = matcher.group().length();
			// 偶数に調整
			if (len % 2 == 1) len++;
			// 2個ずつ縦中横化
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < len / 2; i++) {
				result.append(tcyOpen).append("!!").append(tcyClose);
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(result.toString()));
		}
		matcher.appendTail(sb);
		text = sb.toString();

		// ！が3個（4個以上の処理後に実行）
		text = text.replaceAll("！{3}", Matcher.quoteReplacement(tcyOpen + "!!!" + tcyClose));

		// ？の連続（4個以上の場合は2個ずつ縦中横化）
		Pattern pattern4PlusQ = Pattern.compile("？{4,}");
		Matcher matcherQ = pattern4PlusQ.matcher(text);
		StringBuffer sbQ = new StringBuffer();
		while (matcherQ.find()) {
			int len = matcherQ.group().length();
			if (len % 2 == 1) len++;
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < len / 2; i++) {
				result.append(tcyOpen).append("??").append(tcyClose);
			}
			matcherQ.appendReplacement(sbQ, Matcher.quoteReplacement(result.toString()));
		}
		matcherQ.appendTail(sbQ);
		text = sbQ.toString();

		// ？が3個（4個以上の処理後に実行）
		text = text.replaceAll("？{3}", Matcher.quoteReplacement(tcyOpen + "???" + tcyClose));

		// 3個の混合組み合わせ（特定パターンのみ）
		text = text.replaceAll("！！？", Matcher.quoteReplacement(tcyOpen + "!!?" + tcyClose));
		text = text.replaceAll("？！！", Matcher.quoteReplacement(tcyOpen + "?!!" + tcyClose));

		// 2個の組み合わせ
		text = text.replaceAll("！{2}", Matcher.quoteReplacement(tcyOpen + "!!" + tcyClose));
		text = text.replaceAll("！？", Matcher.quoteReplacement(tcyOpen + "!?" + tcyClose));
		text = text.replaceAll("？！", Matcher.quoteReplacement(tcyOpen + "?!" + tcyClose));
		text = text.replaceAll("？{2}", Matcher.quoteReplacement(tcyOpen + "??" + tcyClose));

		return text;
	}

	/**
	 * 数字の漢数字化処理（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:93-232
	 *
	 * 処理フロー:
	 * 1. 既存の漢数字を一時退避
	 * 2. カンマ区切りの数字は半角保護
	 * 3. アラビア数字を漢数字に変換
	 * 4. 単位化（enable_kanji_num_with_units=trueの場合）
	 * 5. 退避した漢数字を復元
	 */
	private String convertNumbersToKanji(String text) {
		// 1. 既存の漢数字を一時退避
		text = stashKanjiNum(text);

		// 2. カンマ区切りの数字は半角保護（全角化対策）
		text = text.replaceAll("([0-9]{1,3}(?:,[0-9]{3})+)",
			Matcher.quoteReplacement(String.valueOf(PH_BRACKET_OPEN) + PH_HASH + "半角数字＝") + "$1" +
			Matcher.quoteReplacement(String.valueOf(PH_BRACKET_CLOSE)));

		// 3. アラビア数字を漢数字に変換
		text = arabicToKanji(text);

		// 4. 単位化（千・万などの単位を追加）
		if (formatSettings.isEnableKanjiNumWithUnits()) {
			text = addKanjiUnits(text);
		}

		// 5. 退避した漢数字を復元
		text = rebuildKanjiNum(text);

		return text;
	}

	/**
	 * 既存の漢数字を一時退避する。
	 * 既に存在する漢数字を保護し、後で復元できるようにする。
	 */
	private String stashKanjiNum(String text) {
		// 漢数字のパターン（一、二、三...）
		// プレースホルダー内の漢数字は除外（PH_HASH直後の位置をスキップ）
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
			"(?<!" + Pattern.quote(String.valueOf(PH_HASH)) + ")[一二三四五六七八九十百千万億兆]+"
		);
		java.util.regex.Matcher matcher = pattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String kanjiNum = matcher.group();
			kanjiNumbers.add(kanjiNum);
			matcher.appendReplacement(result,
				Matcher.quoteReplacement(phChuki("漢数字＝" + (kanjiNumbers.size() - 1))));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * アラビア数字を漢数字に変換する。
	 * 例: 123 → 一二三
	 */
	private String arabicToKanji(String text) {
		// プレースホルダー内の数字を変換しないよう、PH領域をスキップしつつ数字を変換
		// PH_BRACKET_OPEN...PH_BRACKET_CLOSE の範囲はそのまま保持
		StringBuilder result = new StringBuilder();
		int i = 0;
		int len = text.length();
		while (i < len) {
			char ch = text.charAt(i);
			if (ch == PH_BRACKET_OPEN) {
				// プレースホルダー領域をスキップ（閉じ括弧まで）
				int end = text.indexOf(PH_BRACKET_CLOSE, i);
				if (end >= 0) {
					result.append(text, i, end + 1);
					i = end + 1;
				} else {
					result.append(ch);
					i++;
				}
			} else if (ch >= '0' && ch <= '9') {
				// 連続する数字を漢数字に変換
				int start = i;
				while (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
					i++;
				}
				result.append(convertNumberStringToKanji(text.substring(start, i)));
			} else {
				result.append(ch);
				i++;
			}
		}
		return result.toString();
	}

	/**
	 * 数字文字列を漢数字に変換する。
	 * 例: "123" → "一二三"
	 */
	private String convertNumberStringToKanji(String numStr) {
		StringBuilder sb = new StringBuilder();
		for (char c : numStr.toCharArray()) {
			switch (c) {
				case '0': sb.append("〇"); break;
				case '1': sb.append("一"); break;
				case '2': sb.append("二"); break;
				case '3': sb.append("三"); break;
				case '4': sb.append("四"); break;
				case '5': sb.append("五"); break;
				case '6': sb.append("六"); break;
				case '7': sb.append("七"); break;
				case '8': sb.append("八"); break;
				case '9': sb.append("九"); break;
				default: sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * 漢数字に単位を追加する。
	 * 例: 一〇〇〇 → 千、一〇〇〇〇 → 一万
	 *
	 * kanji_num_with_units_lower_digit_zero の設定により、
	 * 下位桁ゼロ数で単位化の閾値を決定。
	 */
	/** 漢数字→数字変換テーブル */
	private static final String KANJI_NUM_STR = "〇一二三四五六七八九";

	/** 位の配列（1の位, 10の位, 100の位, 1000の位） */
	private static final String[] KANJI_KURAI = {"", "十", "百", "千"};

	/** 万以上の単位 */
	private static final String[] KANJI_MAN_UNITS = {"", "万", "億", "兆", "京"};

	/**
	 * 漢数字に単位を追加する（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:212-243
	 *
	 * 漢数字文字列を一度整数に変換し、4桁区切りで
	 * 千・万・億等の単位を付与して再構築する。
	 */
	private String addKanjiUnits(String text) {
		int lowerDigitZero = formatSettings.getKanjiNumWithUnitsLowerDigitZero();

		Pattern kanjiNumPattern = Pattern.compile("[〇一二三四五六七八九]+");
		Matcher matcher = kanjiNumPattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String kanjiStr = matcher.group();
			String converted = convertKanjiNumWithUnit(kanjiStr, lowerDigitZero);
			matcher.appendReplacement(result, Matcher.quoteReplacement(converted));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * 漢数字文字列を単位付き表現に変換する。
	 * narou.rb の convert_kanji_num_with_unit のコアロジック。
	 */
	private String convertKanjiNumWithUnit(String kanjiStr, int lowerDigitZero) {
		// 漢数字を整数に変換
		long total = kanjiToLong(kanjiStr);
		if (total < 0) return kanjiStr; // 変換不能

		// 整数を漢数字文字列に（〇一二...形式）
		String numStr = String.valueOf(total);
		StringBuilder kanjiDigits = new StringBuilder();
		for (char c : numStr.toCharArray()) {
			kanjiDigits.append(KANJI_NUM_STR.charAt(c - '0'));
		}
		String m1 = kanjiDigits.toString();

		// 末尾のゼロ数がlowerDigitZero未満なら単位化しない
		int trailingZeros = 0;
		for (int i = m1.length() - 1; i >= 0; i--) {
			if (m1.charAt(i) == '〇') trailingZeros++;
			else break;
		}
		if (trailingZeros < lowerDigitZero) {
			return kanjiStr; // 元のまま
		}

		// 4桁区切りで単位を付ける
		// 下の桁から4桁ずつ区切る
		java.util.List<String> digits = new java.util.ArrayList<>();
		for (int i = m1.length(); i > 0; i -= 4) {
			int start = Math.max(0, i - 4);
			digits.add(0, m1.substring(start, i));
		}

		int keta = digits.size() - 1;
		if (keta >= KANJI_MAN_UNITS.length) return kanjiStr; // 京以上は未対応

		StringBuilder sb = new StringBuilder();
		for (int ketaI = 0; ketaI < digits.size(); ketaI++) {
			String nums = digits.get(ketaI);
			StringBuilder fourDigit = new StringBuilder();
			for (int di = 0; di < nums.length(); di++) {
				char d = nums.charAt(di);
				if (d == '〇') continue;
				int kurai = nums.length() - di - 1; // 0=一の位, 1=十, 2=百, 3=千
				String kuraiStr = KANJI_KURAI[kurai];
				String dStr = String.valueOf(d);
				if (d == '一') {
					// 4桁の千の前は一は不要、5桁以上の千の前には一をつける
					if (!kuraiStr.isEmpty() && !(keta > 0 && "千".equals(kuraiStr))) {
						dStr = "";
					}
				}
				fourDigit.append(dStr).append(kuraiStr);
			}
			if (fourDigit.length() > 0) {
				sb.append(fourDigit).append(KANJI_MAN_UNITS[keta - ketaI]);
			}
		}

		return sb.length() > 0 ? sb.toString() : kanjiStr;
	}

	/** 漢数字（〇一二...）のみの文字列を整数に変換 */
	private long kanjiToLong(String kanjiStr) {
		long result = 0;
		for (char c : kanjiStr.toCharArray()) {
			int idx = KANJI_NUM_STR.indexOf(c);
			if (idx < 0) return -1;
			result = result * 10 + idx;
		}
		return result;
	}

	/**
	 * 退避した漢数字を復元する。
	 */
	private String rebuildKanjiNum(String text) {
		for (int i = 0; i < kanjiNumbers.size(); i++) {
			text = text.replace(phChuki("漢数字＝" + i), kanjiNumbers.get(i));
		}
		return text;
	}

	/**
	 * 記号の全角化と特殊変換（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:340-383
	 *
	 * 注意: 英文保護エリア（［＃英文＝N］）内は変換しない
	 */
	private String convertSymbolsToZenkaku(String text) {
		// 基本的な記号の全角化（narou.rb互換）
		// 参考: narou-3.8.2/lib/converterbase.rb:340-383
		text = text.replace("-", "－");   // ハイフン
		text = text.replace("=", "＝");   // イコール
		text = text.replace("+", "＋");   // プラス
		text = text.replace("/", "／");   // スラッシュ
		text = text.replace("*", "＊");   // アスタリスク
		text = text.replace("%", "％");   // パーセント
		text = text.replace("$", "＄");   // ドル記号
		text = text.replace("#", "＃");   // シャープ記号
		text = text.replace("&", "＆");   // アンパサンド
		text = text.replace("!", "！");   // エクスクラメーション
		text = text.replace("?", "？");   // クエスチョン
		text = text.replace("<", "〈");   // 小なり →始め山括弧
		text = text.replace(">", "〉");   // 大なり → 終わり山括弧
		text = text.replace("(", "（");   // 左丸括弧
		text = text.replace(")", "）");   // 右丸括弧
		text = text.replace("|", "｜");   // 縦線
		text = text.replace(",", "，");   // カンマ
		text = text.replace(".", "．");   // ピリオド
		text = text.replace("_", "＿");   // アンダースコア
		text = text.replace(";", "；");   // セミコロン
		text = text.replace(":", "：");   // コロン
		text = text.replace("[", "［");   // 左角括弧
		text = text.replace("]", "］");   // 右角括弧
		text = text.replace("{", "｛");   // 左波括弧
		text = text.replace("}", "｝");   // 右波括弧
		text = text.replace("\\", "￥");  // バックスラッシュ → 円記号

		// 特殊な記号の変換
		text = text.replace("≪", PH_KOME + phChuki("始め二重山括弧"));
		text = text.replace("≫", PH_KOME + phChuki("終わり二重山括弧"));
		text = text.replace("※※", PH_KOME + phChuki("米印、1-2-8"));

		return text;
	}

	/**
	 * ローマ数字の変換（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:331-338
	 *
	 * ユニコードのローマ数字を通常のアルファベットに変換
	 * 例: Ⅰ → I, Ⅱ → II, ⅲ → iii
	 */
	/** ローマ数字変換: アルファベット表記 → Unicodeローマ数字 (narou.rb互換) */
	private static final String[][] ROME_NUM_PAIRS = {
		// narou.rb: ROME_NUM_ALPHABET → ROME_NUM
		// 長いパターンを先に処理（IIIをIIより先に）
		{"VIII", "Ⅷ"}, {"VII", "Ⅶ"}, {"III", "Ⅲ"},
		{"II", "Ⅱ"}, {"IV", "Ⅳ"}, {"VI", "Ⅵ"}, {"IX", "Ⅸ"},
		{"viii", "ⅷ"}, {"vii", "ⅶ"}, {"iii", "ⅲ"},
		{"ii", "ⅱ"}, {"iv", "ⅳ"}, {"vi", "ⅵ"}, {"ix", "ⅸ"},
	};

	/**
	 * ローマ数字っぽいアルファベットをUnicodeローマ数字に変換（narou.rb互換）
	 * 参考: narou-3.9.1/lib/converterbase.rb:324-335
	 *
	 * 非アルファベット文字に囲まれた場合のみ変換
	 * narou.rb: /([^a-zA-Z])#{rome}([^a-zA-Z])/
	 */
	private String convertRomanNumerals(String text) {
		for (String[] pair : ROME_NUM_PAIRS) {
			String alpha = pair[0];
			String roman = pair[1];
			text = text.replaceAll(
				"(^|[^a-zA-Z])" + Pattern.quote(alpha) + "([^a-zA-Z]|$)",
				"$1" + Matcher.quoteReplacement(roman) + "$2"
			);
		}
		return text;
	}

	/**
	 * 分数変換（narou.rb互換）
	 * 参考: narou-3.8.2/lib/converterbase.rb:265-329
	 */
	private String convertFractions(String text) {
		Pattern datePattern = Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})");
		java.util.ArrayList<String> dates = new java.util.ArrayList<>();
		Matcher dateMatcher = datePattern.matcher(text);
		StringBuffer dateSb = new StringBuffer();
		while (dateMatcher.find()) {
			dates.add(dateMatcher.group());
			dateMatcher.appendReplacement(dateSb, "［＃日付退避＝" + (dates.size() - 1) + "］");
		}
		dateMatcher.appendTail(dateSb);
		text = dateSb.toString();

		Pattern fractionPattern = Pattern.compile("(\\d+)/(\\d+)");
		Matcher fractionMatcher = fractionPattern.matcher(text);
		StringBuffer fractionSb = new StringBuffer();
		while (fractionMatcher.find()) {
			String numerator = fractionMatcher.group(1);
			String denominator = fractionMatcher.group(2);
			fractionMatcher.appendReplacement(fractionSb,
				Matcher.quoteReplacement(denominator + "分の" + numerator));
		}
		fractionMatcher.appendTail(fractionSb);
		text = fractionSb.toString();

		for (int i = 0; i < dates.size(); i++) {
			text = text.replace("［＃日付退避＝" + i + "］", dates.get(i));
		}
		return text;
	}

	/**
	 * 日付変換（narou.rb互換）
	 */
	private String convertDates(String text) {
		String fmt = formatSettings.getDateFormat();
		Pattern datePattern = Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})");
		Matcher matcher = datePattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String year = matcher.group(1);
			String month = matcher.group(2);
			String day = matcher.group(3);
			String replacement = fmt
				.replace("%Y", year)
				.replace("%m", month)
				.replace("%d", day);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 三点リーダー変換（narou.rb互換）
	 */
	private String convertHorizontalEllipsis(String text) {
		Pattern pattern = Pattern.compile("・{3,}");
		Matcher matcher = pattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			int len = matcher.group().length();
			int ellipsisCount = len / 3;
			if (ellipsisCount < 1) ellipsisCount = 1;
			StringBuilder replacement = new StringBuilder();
			for (int i = 0; i < ellipsisCount; i++) {
				replacement.append("…");
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 濁点フォントの処理（narou.rb互換）
	 */
	private String convertDakutenFont(String text) {
		Pattern pattern = Pattern.compile("([\\u3040-\\u309F\\u30A0-\\u30FF])[゛ﾞ]");
		Matcher matcher = pattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String base = matcher.group(1);
			String replacement = phChuki("濁点") + base + phChuki("濁点終わり");
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 長音記号の変換（narou.rb互換）
	 */
	private String convertProlongedSoundMark(String text) {
		Pattern pattern = Pattern.compile("ー{2,}");
		Matcher matcher = pattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			int len = matcher.group().length();
			StringBuilder replacement = new StringBuilder();
			for (int i = 0; i < len; i++) {
				replacement.append("―");
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * なろう独自タグの処理（narou.rb互換）
	 */
	private String convertNarouTags(String text) {
		text = text.replace("[newpage]", phChuki("改ページ"));
		StringBuilder out = new StringBuilder();
		int idx = 0;
		String key = "[chapter:";
		while (true) {
			int start = text.indexOf(key, idx);
			if (start == -1) {
				out.append(text, idx, text.length());
				break;
			}
			out.append(text, idx, start);
			int titleStart = start + key.length();
			int end = text.indexOf(']', titleStart);
			if (end == -1) {
				out.append(text, start, text.length());
				break;
			}
			String title = text.substring(titleStart, end);
			String replacement = phChuki(formatSettings.getIndent() + "字下げ") +
				phChuki("中見出し") + title + phChuki("中見出し終わり");
			out.append(replacement);
			idx = end + 1;
		}
		text = out.toString();

		StringBuilder out2 = new StringBuilder();
		idx = 0;
		String jkey = "[jump:";
		while (true) {
			int jstart = text.indexOf(jkey, idx);
			if (jstart == -1) {
				out2.append(text, idx, text.length());
				break;
			}
			out2.append(text, idx, jstart);
			int urlStart = jstart + jkey.length();
			int jend = text.indexOf(']', urlStart);
			if (jend == -1) {
				out2.append(text, jstart, text.length());
				break;
			}
			String url = text.substring(urlStart, jend);
			String anchor = "<a href=\"" + url + "\">" + url + "</a>";
			out2.append(anchor);
			idx = jend + 1;
		}
		text = out2.toString();
		return text;
	}

	/**
	 * 感嘆符・疑問符の直後に全角アキを挿入（narou.rb互換）
	 */
	private String insertSeparateSpace(String text) {
		Pattern pattern = Pattern.compile("([!?！？]+)([^!?！？])");
		Matcher matcher = pattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String marks = matcher.group(1);
			String next = matcher.group(2);
			if (next.matches("[」］｝\\]\\}』】〉》〕＞>≫)）\u201d\u201c\u2019〟　☆★♪［―]")) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(marks + next));
			} else if (next.matches("[ 、。]")) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(marks + "　"));
			} else {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(marks + "　" + next));
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 小説ルールに沿う変換（narou.rb互換）
	 */
	private String convertNovelRule(String text) {
		text = text.replaceAll("。([」』）])", "$1");
		Pattern ellipsis = Pattern.compile("…+");
		Matcher em = ellipsis.matcher(text);
		StringBuffer sb1 = new StringBuffer();
		while (em.find()) {
			int len = em.group().length();
			if (len % 2 != 0) len++;
			em.appendReplacement(sb1, Matcher.quoteReplacement("…".repeat(len)));
		}
		em.appendTail(sb1);
		text = sb1.toString();
		Pattern doubleDot = Pattern.compile("‥+");
		Matcher dm = doubleDot.matcher(text);
		StringBuffer sb2 = new StringBuffer();
		while (dm.find()) {
			int len = dm.group().length();
			if (len % 2 != 0) len++;
			dm.appendReplacement(sb2, Matcher.quoteReplacement("‥".repeat(len)));
		}
		dm.appendTail(sb2);
		text = sb2.toString();
		return text;
	}

	/**
	 * かぎ括弧内の自動連結（narou.rb互換）
	 */
	private String autoJoinInBrackets(String text) {
		boolean changed = true;
		while (changed) {
			String before = text;
			text = text.replaceAll("「([^「」]*)\n([^「」]*)」", "「$1　$2」");
			text = text.replaceAll("『([^『』]*)\n([^『』]*)』", "『$1　$2』");
			changed = !text.equals(before);
		}
		return text;
	}

	/**
	 * 行末読点での自動連結（narou.rb互換）
	 */
	private String autoJoinLine(String text) {
		text = text.replaceAll("、\n", "、");
		return text;
	}

	// パイプライン生成注記のプレースホルダー文字列
	private static final char PH_BRACKET_OPEN = '\uE000';
	private static final char PH_BRACKET_CLOSE = '\uE001';
	private static final char PH_HASH = '\uE002';
	private static final char PH_KOME = '\uE003';

	static String phChuki(String content) {
		return String.valueOf(PH_BRACKET_OPEN) + PH_HASH + content +
			String.valueOf(PH_BRACKET_CLOSE);
	}

	private static String restorePlaceholders(String text) {
		return text.replace(PH_BRACKET_OPEN, '［')
		           .replace(PH_BRACKET_CLOSE, '］')
		           .replace(PH_HASH, '＃')
		           .replace(PH_KOME, '※');
	}

	/** 文字を出力 特殊文字は注記に変換 */
	private void printText(BufferedWriter bw, String text) throws IOException
	{
		text = text.replaceAll("[\r\n]+", "");
		if (formatSettings.isEnableAutoJoinInBrackets()) text = autoJoinInBrackets(text);
		if (formatSettings.isEnableAutoJoinLine()) text = autoJoinLine(text);
		if (formatSettings.isEnableNarouTag()) text = convertNarouTags(text);
		text = addHalfIndentBracket(text);
		text = convertRomanNumerals(text);
		text = protectEnglishSentences(text);
		if (formatSettings.isEnableConvertNumToKanji()) text = convertNumbersToKanji(text);
		if (formatSettings.isEnableTransformFraction()) text = convertFractions(text);
		if (formatSettings.isEnableTransformDate()) text = convertDates(text);
		if (formatSettings.isEnableConvertSymbolsToZenkaku()) text = convertSymbolsToZenkaku(text);
		if (formatSettings.isEnableProlongedSoundMarkToDash()) text = convertProlongedSoundMark(text);
		if (formatSettings.isEnableConvertHorizontalEllipsis()) text = convertHorizontalEllipsis(text);
		if (formatSettings.isEnableDakutenFont()) text = convertDakutenFont(text);
		text = insertSeparateSpace(text);
		text = convertTatechuyoko(text);
		text = convertNovelRule(text);

		StringBuilder sb = new StringBuilder();
		char[] chars = text.toCharArray();
		for (char ch : chars) {
			switch (ch) {
			case '《': sb.append("※［＃始め二重山括弧］"); break;
			case '》': sb.append("※［＃終わり二重山括弧］"); break;
			case '※': sb.append("※［＃米印、1-2-8］"); break;
			case '\t': sb.append(' '); break;
			default: sb.append(ch);
			}
		}
		String result = rebuildEnglishSentences(sb.toString());
		result = restorePlaceholders(result);
		bw.append(result);
	}
	
	////////////////////////////////////////////////////////////////
	
	String getExtractText(Document doc, ExtractInfo[] extractInfos)
	{
		return getExtractText(doc, extractInfos, true);
	}
	String getExtractText(Document doc, ExtractInfo[] extractInfos, boolean replace)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			String text = null;
			Elements elements = doc.select(extractInfo.query);
			if (elements == null || elements.size() == 0) continue;
			StringBuilder buf = new StringBuilder();
			if (extractInfo.idx == null) {
				for (Element element : elements) {
					String html = element.html();
					if (html != null) buf.append(" ").append(replaceHtmlText(html, replace?extractInfo:null));
				}
			} else {
				for (int i=0; i<extractInfo.idx.length; i++) {
					if (elements.size() > extractInfo.idx[i]) {
						int pos = extractInfo.idx[i];
						if (pos < 0) pos = elements.size()+pos;
						if (pos >= 0 && elements.size() > pos) {
							String html = elements.get(pos).html();
							if (html != null) buf.append(" ").append(replaceHtmlText(html, replace?extractInfo:null));
						}
					}
				}
				if (buf.length() > 0) text = buf.deleteCharAt(0).toString();
			}
			if (text != null && text.length() > 0) return text;
		}
		return null;
	}
	
	Vector<String> getExtractStrings(Document doc, ExtractInfo[] extractInfos, boolean replace)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements == null || elements.size() == 0) continue;
			Vector<String> vecString = new Vector<String>();
			if (extractInfo.idx == null) {
				for (Element element : elements) {
					String html = element.html();
					if (html != null) vecString.add(replaceHtmlText(html, replace?extractInfo:null));
				}
			} else {
				for (int i=0; i<extractInfo.idx.length; i++) {
					if (elements.size() > extractInfo.idx[i]) {
						int pos = extractInfo.idx[i];
						if (pos < 0) pos = elements.size()+pos;
						if (pos >= 0 && elements.size() > pos) {
							String html = elements.get(pos).html();
							if (html != null) vecString.add(replaceHtmlText(html, replace?extractInfo:null));
						}
					}
				}
			}
			return vecString;
		}
		return null;
	}
	
	String getQueryText(Document doc, String[] queries)
	{
		if (queries == null) return null;
		for (String query : queries) {
			String text  = getFirstText(doc.select(query).first());
			if (text != null && text.length() > 0) return text;
		}
		return null;
	}
	
	Elements getExtractElements(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements == null || elements.size() == 0) continue;
			if (extractInfo.idx == null) return elements;
			Elements e2 = new Elements();
			for (int i=0; i<extractInfo.idx.length; i++) {
				int pos = extractInfo.idx[i];
				if (pos < 0) pos = elements.size()+pos;
				if (pos >= 0 && elements.size() > pos) e2.add(elements.get(pos));
			}
			if (e2.size() >0) return e2;
		}
		return null;
	}
	
	/** cssQueryに対応するノードを取得 前のQueryを優先 */
	Element getExtractFirstElement(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements == null || elements.size() == 0) continue;
			int pos = extractInfo.idx[0];
			if (pos < 0) pos = elements.size()+pos;//負の値なら後ろから
			if (pos >= 0 && elements.size() > pos) return elements.get(pos);
		}
		return null;
	}
	
	String getFirstText(Element elem)
	{
		if (elem != null) {
			List<TextNode> nodes = elem.textNodes();
			for (Node node : nodes) {
				if (node instanceof TextNode) {
					String text = ((TextNode) node).getWholeText();
					if (text != null && text.length() > 0) {
						text = text.replaceAll("[\n|\r]", "").replaceAll("\t", " ").trim();
						if (text.length() > 0) return text;
					}
				}
			}
		}
		return null;
	}
	

	/**
	 * __NEXT_DATA__ JSON からエピソードURLリストを抽出する (カクヨムなど Next.js サイト対応)
	 * Apollo GraphQL キャッシュの "Episode:ID" エントリを出現順・重複なしで収集する
	 */
	private List<String> extractEpisodesFromNextData(Document doc, String urlString) {
		Elements scripts = doc.select("script#__NEXT_DATA__");
		if (scripts.isEmpty()) return null;
		String json = scripts.get(0).html();
		// URLから作品IDを取得
		Pattern workPattern = Pattern.compile("/works/(\\d+)");
		Matcher workMatcher = workPattern.matcher(urlString);
		if (!workMatcher.find()) return null;
		String workId = workMatcher.group(1);
		// "Episode:ID" を出現順・重複なしで収集
		Pattern epPattern = Pattern.compile("\"Episode:(\\d+)\"");
		Matcher epMatcher = epPattern.matcher(json);
		Vector<String> result = new Vector<String>();
		HashSet<String> seen = new HashSet<String>();
		while (epMatcher.find()) {
			String epId = epMatcher.group(1);
			if (seen.add(epId)) {
				result.add(this.baseUri + "/works/" + workId + "/episodes/" + epId);
			}
		}
		return result.isEmpty() ? null : result;
	}

	String replaceHtmlText(String text, ExtractInfo extractInfo) {
		text = text.replaceAll("[\n|\r]", "").replaceAll("\t", " ");
		if (extractInfo != null) text = extractInfo.replace(text);
		return Jsoup.parse(removeTag(text)).text();
	}
	
	String removeTag(String text)
	{
		return text.replaceAll("<br ?/?>", " ").replaceAll("<rt>.+?</rt>", "").replaceAll("<[^>]+>", "");
	}
	
	private boolean cacheFile(String urlString, File cacheFile, String referer) throws IOException
	{
		try { if (cacheFile.isDirectory()) cacheFile.delete(); } catch (Exception e) {}
		if (cacheFile.isDirectory()) { LogAppender.println("フォルダがあるためキャッシュできません : "+cacheFile.getAbsolutePath()); }
		File parentFile = cacheFile.getParentFile();
		if (parentFile.exists() && !parentFile.isDirectory()) {
			parentFile.delete();
		}
		cacheFile.getParentFile().mkdirs();
		URLConnection conn;
		try {
			conn = new java.net.URI(urlString).toURL().openConnection();
		} catch (java.net.URISyntaxException e) {
			throw new IOException(e);
		}
		ExtractInfo[] cookie = this.queryMap.get(ExtractId.COOKIE);
		if (cookie != null && cookie.length > 0) conn.setRequestProperty("Cookie", cookie[0].query);
		if (referer != null) conn.setRequestProperty("Referer", referer);
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
		conn.setConnectTimeout(10000);
		if (conn instanceof java.net.HttpURLConnection) {
			int responseCode = ((java.net.HttpURLConnection)conn).getResponseCode();
			LogAppender.println("HTTP Response Code: " + responseCode);
		}
		BufferedInputStream bis = new BufferedInputStream(conn.getInputStream(), 8192);
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
		try {
			byte[] buf = new byte[8192];
			int len;
			while ((len = bis.read(buf)) > 0) {
				bos.write(buf, 0, len);
			}
		} finally {
			bos.close();
			bis.close();
		}
		return true;
	}
}
