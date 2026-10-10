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
	/**
	 * 更新の結果。noUpdate は「更新分のみ」で更新が無かったこと。stop は守りで止めた理由
	 * （"gone": 掲載元で作品が見つからない、"shrunk": 話数が減った）。止めていなければ null
	 */
	record Result(boolean ok, boolean noUpdate, String message, String stop)
	{
		public Result(boolean ok, boolean noUpdate, String message)
		{
			this(ok, noUpdate, message, null);
		}
	}

	/**
	 * 掲載元の URL から取り直して、本棚の本（epubFile）を上書きする。呼ばれるのは本棚の仕事の列の 1 本のスレッドから
	 * @param sourceUrl 本の {@code dc:source}（本棚が確かめた http・https の URL）
	 * @param epubFile 本棚の本
	 */
	Result update(String sourceUrl, Path epubFile) throws Exception;

	/**
	 * 話数が減っていても続けるかを選んで更新する（利用者が「減ったまま更新」を選んだとき allowFewerEpisodes）。
	 * 守りを持たない実装は、ふつうの更新と同じ
	 */
	default Result update(String sourceUrl, Path epubFile, boolean allowFewerEpisodes) throws Exception
	{
		return update(sourceUrl, epubFile);
	}

	/**
	 * 掲載元の URL の作品を、Web 本棚に新しく落とす（internal #11 の案 A）。本は Web 本棚の直下に短い名前で、
	 * 記録とキャッシュは Web 本棚の {@code .aozora} に置く。落とせない実装は {@link UnsupportedOperationException}
	 */
	default Result download(String url, Path shelfDir) throws Exception
	{
		throw new UnsupportedOperationException();
	}
}
