package com.github.hmdev.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 青空文庫の作品の URL（作品の XHTML・図書カード）を、図書カードに載っているテキストの zip の URL に読み替える。
 *
 * <p>青空文庫の XHTML は、zip で配っている青空文庫形式のテキストから作られた物なので、HTML を読んで
 * 青空文庫形式に戻すと、傍点・字下げ・見出し・外字などが落ちる。元のテキストの zip を取り、ローカルの zip と
 * 同じ経路で変換する（internal の ADR 0001。#42・#44・#45）。
 *
 * <p>URL の形は青空文庫の決まり: 作品の XHTML は {@code cards/<作家ID>/files/<作品ID>_<ファイルID>.html}、
 * 図書カードは {@code cards/<作家ID>/card<作品ID>.html}。図書カードに、その作品のテキストの zip
 * （{@code ./files/<作品ID>_ruby_<ファイルID>.zip} など）が載っている。
 */
public final class AozoraBunkoUrl
{
	private AozoraBunkoUrl() {}

	/** 青空文庫のテキストの文字コード（青空文庫の決まり。zip の中の txt） */
	public static final String ENCODING = "MS932";

	/** 作品の XHTML。1: 作家ID 2: 作品ID */
	static final Pattern WORK_PAGE = Pattern.compile("^https?://(?:www\\.)?aozora\\.gr\\.jp/cards/(\\d+)/files/(\\d+)_\\d+\\.html?(?:[?#].*)?$");
	/** 図書カード。1: 作家ID 2: 作品ID */
	static final Pattern CARD_PAGE = Pattern.compile("^https?://(?:www\\.)?aozora\\.gr\\.jp/cards/(\\d+)/card(\\d+)\\.html?(?:[?#].*)?$");

	/** 青空文庫の作品の XHTML か図書カードの URL か */
	public static boolean isWorkUrl(String url)
	{
		return url != null && (WORK_PAGE.matcher(url).matches() || CARD_PAGE.matcher(url).matches());
	}

	/** 作品の URL の図書カードの URL（https）。作品の URL でなければ null */
	static String cardUrl(String url)
	{
		Matcher m = matcher(url);
		if (m == null) return null;
		//http で渡されても https で取る（URLConnection は http から https への転送をたどらない）
		return "https://www.aozora.gr.jp/cards/"+m.group(1)+"/card"+m.group(2)+".html";
	}

	/** 作品の URL の作品ID。作品の URL でなければ null */
	static String workId(String url)
	{
		Matcher m = matcher(url);
		return m == null ? null : m.group(2);
	}

	private static Matcher matcher(String url)
	{
		if (url == null) return null;
		Matcher m = WORK_PAGE.matcher(url);
		if (m.matches()) return m;
		m = CARD_PAGE.matcher(url);
		return m.matches() ? m : null;
	}

	/** 図書カードの HTML から、その作品のテキストの zip の URL を選ぶ。ルビ付き（_ruby_）を先に選ぶ。無ければ null
	 * @param card 図書カード（基準の URL つきで読んだ物。相対リンクを解決する）
	 * @param workId 作品ID（別の作品の zip を選ばない） */
	static String zipUrlFromCard(Document card, String workId)
	{
		Pattern own = Pattern.compile("/files/"+workId+"_[^/]*\\.zip$", Pattern.CASE_INSENSITIVE);
		String found = null;
		for (Element a : card.select("a[href]")) {
			String href = a.absUrl("href");
			if (!own.matcher(href).find()) continue;
			if (href.contains("_ruby_")) return href;
			if (found == null) found = href;
		}
		return found;
	}

	/** 作品の URL を、図書カードのテキストの zip の URL に読み替える（図書カードを 1 回取る）
	 * @throws IOException 図書カードを取れない・zip のリンクが無い。メッセージは利用者に出す理由 */
	public static String resolveZipUrl(String url) throws IOException
	{
		String cardUrl = cardUrl(url);
		if (cardUrl == null) throw new IOException("青空文庫の作品の URL ではありません: "+url);
		Document card;
		try (InputStream is = NetUtils.openStream(new java.net.URI(cardUrl).toURL())) {
			//文字コードは図書カードの meta に従う（今は UTF-8）
			card = Jsoup.parse(is, null, cardUrl);
		} catch (URISyntaxException e) {
			throw new IOException("図書カードの URL を作れません: "+cardUrl, e);
		} catch (java.io.FileNotFoundException e) {
			//URLConnection は 404 をこの例外にする（メッセージは URL だけ）
			throw new IOException("図書カードが見つかりません（作品の番号を確かめてください）: "+cardUrl, e);
		} catch (IOException e) {
			throw new IOException("図書カードを取れませんでした: "+cardUrl+" ("+e.getMessage()+")", e);
		}
		String zipUrl = zipUrlFromCard(card, workId(url));
		if (zipUrl == null) throw new IOException("図書カードにテキストの zip がありません: "+cardUrl);
		return zipUrl;
	}
}
