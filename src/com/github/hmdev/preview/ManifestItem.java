package com.github.hmdev.preview;

/**
 * OPF manifest の 1 項目。
 *
 * @param id manifest の id 属性
 * @param href OPF からの相対パス
 * @param path EPUB ルートからの相対パス (href を OPF のディレクトリで解決したもの)
 * @param mediaType media-type 属性
 * @param properties properties 属性 (未指定なら空文字)
 */
public record ManifestItem(String id, String href, String path, String mediaType, String properties)
{
	/** properties に指定の値が含まれるか */
	public boolean hasProperty(String name)
	{
		if (this.properties == null || this.properties.isEmpty()) return false;
		for (String p : this.properties.trim().split("\\s+")) {
			if (p.equals(name)) return true;
		}
		return false;
	}
}
