package com.github.hmdev.web;

import java.io.IOException;

/** サイトが 400 以上を返した。本棚の更新で、掲載元で消えた（404・410）のを見分けるのに使う */
@SuppressWarnings("serial")
public class HttpStatusException extends IOException
{
	/** HTTP の状態 */
	public final int status;

	public HttpStatusException(int status, String urlString)
	{
		super("Server returned HTTP response code: " + status + " for URL: " + urlString);
		this.status = status;
	}
}
