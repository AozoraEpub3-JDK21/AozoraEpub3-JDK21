package com.github.hmdev.web;

/** 既定のパッケージの試験から、FQDN ごとに使い回すサイト定義を忘れさせる */
public final class WebAozoraConverterTestAccess {
	private WebAozoraConverterTestAccess() {}

	public static void forget(String fqdn) {
		WebAozoraConverter.converters.remove(fqdn);
	}
}
