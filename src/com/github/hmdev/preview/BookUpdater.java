package com.github.hmdev.preview;

import java.nio.file.Path;

/**
 * 本棚の「続きを取る」で、本 1 冊を掲載元から取り直して上書きするもの（internal #11）。
 *
 * <p>変換の仕組み（GUI の設定・テンプレートの場所・キャッシュの場所）はプレビューの外にあるので、
 * 本棚を開く側（GUI・CLI）が渡す。渡されていなければ、本棚は更新できない。</p>
 */
public interface BookUpdater
{
	/** 更新の結果。noUpdate は「更新分のみ」で更新が無かったこと */
	record Result(boolean ok, boolean noUpdate, String message) {}

	/**
	 * 掲載元の URL から取り直して、本棚の本（epubFile）を上書きする。呼ばれるのは本棚の仕事の列の 1 本のスレッドから
	 * @param sourceUrl 本の {@code dc:source}（本棚が確かめた http・https の URL）
	 * @param epubFile 本棚の本
	 */
	Result update(String sourceUrl, Path epubFile) throws Exception;
}
