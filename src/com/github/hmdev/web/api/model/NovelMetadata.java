package com.github.hmdev.web.api.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * なろう小説APIのレスポンスデータを格納するモデルクラス
 */
public class NovelMetadata {
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	// 基本情報
	private String ncode;              // Nコード (n0001a)
	private String title;              // タイトル
	private String writer;             // 作者名
	private int userid;                // 作者ID
	private String story;              // あらすじ
	
	// ジャンル
	private int biggenre;              // 大ジャンル (1:恋愛, 2:ファンタジー等)
	private int genre;                 // ジャンル (101:異世界〔恋愛〕等)
	private String keyword;            // キーワード
	
	// 日時情報
	private LocalDateTime generalFirstup;   // 初回掲載日
	private LocalDateTime generalLastup;    // 最終掲載日
	private LocalDateTime novelupdatedAt;   // 作品更新日時
	
	// 作品情報
	private int novelType;             // 1:連載, 2:短編
	private int end;                   // 0:完結, 1:連載中
	private int generalAllNo;          // 全話数
	private int length;                // 作品文字数
	private int time;                  // 読了時間(分)
	private int isstop;                // 長期停止中フラグ
	
	// 評価情報
	private int globalPoint;           // 総合評価ポイント
	private int favNovelCnt;           // ブックマーク数
	private int reviewCnt;             // レビュー数
	
	// Getters and Setters
	
	public String getNcode() { return ncode; }
	public void setNcode(String ncode) { this.ncode = ncode; }
	
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	
	public String getWriter() { return writer; }
	public void setWriter(String writer) { this.writer = writer; }
	
	public int getUserid() { return userid; }
	public void setUserid(int userid) { this.userid = userid; }
	
	public String getStory() { return story; }
	public void setStory(String story) { this.story = story; }
	
	public int getBiggenre() { return biggenre; }
	public void setBiggenre(int biggenre) { this.biggenre = biggenre; }
	
	public int getGenre() { return genre; }
	public void setGenre(int genre) { this.genre = genre; }
	
	public String getKeyword() { return keyword; }
	public void setKeyword(String keyword) { this.keyword = keyword; }
	
	public LocalDateTime getGeneralFirstup() { return generalFirstup; }
	public void setGeneralFirstup(String dateStr) {
		if (dateStr != null && !dateStr.isEmpty()) {
			this.generalFirstup = LocalDateTime.parse(dateStr, DATE_FORMATTER);
		}
	}
	
	public LocalDateTime getGeneralLastup() { return generalLastup; }
	public void setGeneralLastup(String dateStr) {
		if (dateStr != null && !dateStr.isEmpty()) {
			this.generalLastup = LocalDateTime.parse(dateStr, DATE_FORMATTER);
		}
	}
	
	public LocalDateTime getNovelupdatedAt() { return novelupdatedAt; }
	public void setNovelupdatedAt(String dateStr) {
		if (dateStr != null && !dateStr.isEmpty()) {
			this.novelupdatedAt = LocalDateTime.parse(dateStr, DATE_FORMATTER);
		}
	}
	
	public int getNovelType() { return novelType; }
	public void setNovelType(int novelType) { this.novelType = novelType; }
	
	public int getEnd() { return end; }
	public void setEnd(int end) { this.end = end; }
	
	public int getGeneralAllNo() { return generalAllNo; }
	public void setGeneralAllNo(int generalAllNo) { this.generalAllNo = generalAllNo; }
	
	public int getLength() { return length; }
	public void setLength(int length) { this.length = length; }
	
	public int getTime() { return time; }
	public void setTime(int time) { this.time = time; }
	
	public int getIsstop() { return isstop; }
	public void setIsstop(int isstop) { this.isstop = isstop; }
	
	public int getGlobalPoint() { return globalPoint; }
	public void setGlobalPoint(int globalPoint) { this.globalPoint = globalPoint; }
	
	public int getFavNovelCnt() { return favNovelCnt; }
	public void setFavNovelCnt(int favNovelCnt) { this.favNovelCnt = favNovelCnt; }
	
	public int getReviewCnt() { return reviewCnt; }
	public void setReviewCnt(int reviewCnt) { this.reviewCnt = reviewCnt; }
	
	@Override
	public String toString() {
		return "NovelMetadata{" +
				"ncode='" + ncode + '\'' +
				", title='" + title + '\'' +
				", writer='" + writer + '\'' +
				", generalAllNo=" + generalAllNo +
				", length=" + length +
				", novelType=" + (novelType == 1 ? "連載" : "短編") +
				", end=" + (end == 0 ? "完結" : "連載中") +
				'}';
	}
}
