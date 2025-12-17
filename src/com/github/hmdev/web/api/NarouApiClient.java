package com.github.hmdev.web.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.github.hmdev.web.api.exception.ApiException;
import com.github.hmdev.web.api.model.NovelMetadata;

/**
 * なろう小説API クライアント (プロトタイプ版)
 * 
 * 参考: https://dev.syosetu.com/man/api/
 */
public class NarouApiClient {
	/** APIエンドポイント */
	private static final String API_ENDPOINT = "https://api.syosetu.com/novelapi/api/";
	
	/** タイムアウト (ミリ秒) */
	private static final int CONNECT_TIMEOUT = 10000;
	private static final int READ_TIMEOUT = 30000;
	
	/** リクエストカウント (簡易版) */
	private int requestCount = 0;
	private long bytesTransferred = 0;
	
	/**
	 * Nコードから作品メタデータを取得
	 * @param ncode 作品コード (例: "n0001a")
	 * @return NovelMetadata 作品情報
	 * @throws ApiException API呼び出し失敗
	 */
	public NovelMetadata getNovelMetadata(String ncode) throws ApiException {
		// Nコードを小文字に正規化
		ncode = ncode.toLowerCase();
		
		// URLパラメータ構築
		Map<String, String> params = new HashMap<>();
		params.put("ncode", ncode);
		params.put("out", "json");  // JSON形式で取得
		params.put("of", "t-n-u-w-s-bg-g-k-gf-gl-nt-e-ga-l-ti-i-gp-f-r-nu");  // 必要項目のみ
		
		String jsonResponse = makeApiRequest(params);
		return parseJsonToMetadata(jsonResponse);
	}
	
	/**
	 * APIリクエスト実行
	 */
	private String makeApiRequest(Map<String, String> params) throws ApiException {
		HttpURLConnection connection = null;
		try {
			// URL構築
			StringBuilder urlBuilder = new StringBuilder(API_ENDPOINT);
			urlBuilder.append("?");
			boolean first = true;
			for (Map.Entry<String, String> entry : params.entrySet()) {
				if (!first) urlBuilder.append("&");
				urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
				urlBuilder.append("=");
				urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
				first = false;
			}
			
			String url = urlBuilder.toString();
			System.out.println("[API] リクエスト: " + url);
			
			// HTTP接続
			connection = (HttpURLConnection) new URI(url).toURL().openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			connection.setRequestProperty("User-Agent", "AozoraEpub3/1.2.1");
			
			// レスポンス取得
			int responseCode = connection.getResponseCode();
			if (responseCode != 200) {
				throw new ApiException("API呼び出しエラー: HTTP " + responseCode);
			}
			
			// レスポンス読み込み
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();
			
			// 統計更新
			requestCount++;
			bytesTransferred += response.length();
			
			System.out.println("[API] レスポンス取得成功 (" + response.length() + " bytes)");
			return response.toString();
			
		} catch (IOException e) {
			throw new ApiException("ネットワークエラー: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new ApiException("APIリクエストエラー: " + e.getMessage(), e);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
	
	/**
	 * JSONレスポンスをNovelMetadataにパース (簡易実装)
	 * 
	 * 注: 本実装ではJacksonやGsonなどのライブラリ使用を推奨
	 */
	private NovelMetadata parseJsonToMetadata(String json) throws ApiException {
		try {
			// JSONレスポンスは配列形式: [{"allcount":1}, {...作品データ...}]
			// 2番目の要素を抽出
			int startIndex = json.indexOf("{", json.indexOf("{") + 1);
			int endIndex = json.lastIndexOf("}");
			if (startIndex == -1 || endIndex == -1) {
				throw new ApiException("作品が見つかりませんでした");
			}
			
			String dataJson = json.substring(startIndex, endIndex + 1);
			
			NovelMetadata metadata = new NovelMetadata();
			
			// 各フィールドを抽出 (簡易パーサー)
			metadata.setTitle(extractJsonString(dataJson, "title"));
			metadata.setNcode(extractJsonString(dataJson, "ncode"));
			metadata.setWriter(extractJsonString(dataJson, "writer"));
			metadata.setStory(extractJsonString(dataJson, "story"));
			metadata.setKeyword(extractJsonString(dataJson, "keyword"));
			
			metadata.setUserid(extractJsonInt(dataJson, "userid"));
			metadata.setBiggenre(extractJsonInt(dataJson, "biggenre"));
			metadata.setGenre(extractJsonInt(dataJson, "genre"));
			metadata.setNovelType(extractJsonInt(dataJson, "novel_type"));
			metadata.setEnd(extractJsonInt(dataJson, "end"));
			metadata.setGeneralAllNo(extractJsonInt(dataJson, "general_all_no"));
			metadata.setLength(extractJsonInt(dataJson, "length"));
			metadata.setTime(extractJsonInt(dataJson, "time"));
			metadata.setIsstop(extractJsonInt(dataJson, "isstop"));
			metadata.setGlobalPoint(extractJsonInt(dataJson, "global_point"));
			metadata.setFavNovelCnt(extractJsonInt(dataJson, "fav_novel_cnt"));
			metadata.setReviewCnt(extractJsonInt(dataJson, "review_cnt"));
			
			metadata.setGeneralFirstup(extractJsonString(dataJson, "general_firstup"));
			metadata.setGeneralLastup(extractJsonString(dataJson, "general_lastup"));
			metadata.setNovelupdatedAt(extractJsonString(dataJson, "novelupdated_at"));
			
			return metadata;
			
		} catch (Exception e) {
			throw new ApiException("JSONパースエラー: " + e.getMessage(), e);
		}
	}
	
	/**
	 * JSON文字列から文字列値を抽出
	 */
	private String extractJsonString(String json, String key) {
		String searchKey = "\"" + key + "\":\"";
		int startIndex = json.indexOf(searchKey);
		if (startIndex == -1) return "";
		
		startIndex += searchKey.length();
		int endIndex = json.indexOf("\"", startIndex);
		if (endIndex == -1) return "";
		
		String value = json.substring(startIndex, endIndex);
		return unescapeJson(value);
	}
	
	/**
	 * JSONエスケープ文字列をデコード
	 */
	private String unescapeJson(String str) {
		if (str == null || str.isEmpty()) return str;
		
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '\\' && i + 1 < str.length()) {
				char next = str.charAt(i + 1);
				switch (next) {
					case 'n': result.append('\n'); i++; break;
					case 'r': result.append('\r'); i++; break;
					case 't': result.append('\t'); i++; break;
					case '"': result.append('"'); i++; break;
					case '\\': result.append('\\'); i++; break;
					case 'u':
					// Unicode escape sequence (backslash u + 4 hex digits)
						if (i + 5 < str.length()) {
							try {
								String hex = str.substring(i + 2, i + 6);
								int code = Integer.parseInt(hex, 16);
								result.append((char) code);
								i += 5;
							} catch (NumberFormatException e) {
								result.append(c);
							}
						} else {
							result.append(c);
						}
						break;
					default:
						result.append(c);
				}
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}
	
	/**
	 * JSON文字列から整数値を抽出
	 */
	private int extractJsonInt(String json, String key) {
		String searchKey = "\"" + key + "\":";
		int startIndex = json.indexOf(searchKey);
		if (startIndex == -1) return 0;
		
		startIndex += searchKey.length();
		int endIndex = json.indexOf(",", startIndex);
		if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
		if (endIndex == -1) return 0;
		
		String valueStr = json.substring(startIndex, endIndex).trim();
		try {
			return Integer.parseInt(valueStr);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
	
	/**
	 * リクエスト統計を取得
	 */
	public String getStatistics() {
		return String.format("リクエスト: %d回, 転送量: %.2f KB", 
			requestCount, bytesTransferred / 1024.0);
	}
}
