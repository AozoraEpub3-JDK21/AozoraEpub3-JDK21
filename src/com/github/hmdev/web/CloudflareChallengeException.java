package com.github.hmdev.web;

import java.io.IOException;

/**
 * サイトが自動での取得を Cloudflare の確認画面（403 + cf-mitigated: challenge）で止めている。
 * 同じサイトに取りに行き続けても通らないので、受けた側は変換を止める。
 */
@SuppressWarnings("serial")
public class CloudflareChallengeException extends IOException
{
	public CloudflareChallengeException(int responseCode, String urlString)
	{
		super("このサイトは自動での取得を Cloudflare の確認画面で止めているため、取得できません"
			+ " (HTTP " + responseCode + "): " + urlString);
	}
}
