package com.github.hmdev.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * シンプルな国際化(多言語)ユーティリティ。
 *
 * - クラスパスの "i18n/messages_*.properties" をUTF-8で読み込み
 * - 外部ディレクトリの上書き(例: profiles/i18n/messages_*.properties)に対応
 * - MessageFormat による引数展開をサポート
 */
public final class I18n {
    private static ResourceBundle bundle;
    private static Locale currentLocale = Locale.getDefault();

    private I18n() {}

    /**
     * 初期化。まず外部パスのプロパティを試し、なければクラスパスから読み込みます。
     * @param locale 使用する言語ロケール
     * @param externalDir 外部 i18n ディレクトリ (null 可)
     */
    public static void init(Locale locale, Path externalDir) {
        currentLocale = (locale != null) ? locale : Locale.getDefault();

        // 外部の properties を優先
        ResourceBundle extBundle = loadExternal(externalDir, currentLocale);
        if (extBundle != null) {
            bundle = extBundle;
            return;
        }

        // クラスパスから読み込み
        bundle = loadFromClasspath(currentLocale);
    }

    /** 現在のロケールを返す */
    public static Locale getLocale() {
        return currentLocale;
    }

    /** 文言取得。キーが存在しない場合はキーをそのまま返します。*/
    public static String t(String key, Object... args) {
        if (bundle == null) {
            bundle = loadFromClasspath(currentLocale);
        }
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            pattern = key; // フォールバック: キーをそのまま表示
        }
        if (args != null && args.length > 0) {
            return MessageFormat.format(pattern, args);
        }
        return pattern;
    }

    private static ResourceBundle loadExternal(Path externalDir, Locale locale) {
        if (externalDir == null) return null;
        String baseName = "messages";
        String lang = locale.getLanguage();
        Path candidate = externalDir.resolve(baseName + "_" + lang + ".properties");
        if (!Files.exists(candidate)) return null;
        try (InputStream is = Files.newInputStream(candidate);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            PropertyResourceBundle prb = new PropertyResourceBundle(reader);
            return prb;
        } catch (IOException e) {
            return null;
        }
    }

    private static ResourceBundle loadFromClasspath(Locale locale) {
        String baseName = "i18n/messages";
        // UTF-8で読み込むために独自コントロールを使用
        return ResourceBundle.getBundle(baseName, locale, new UTF8Control());
    }

    /**
     * properties を UTF-8 として読み込むための Control 実装
     */
    private static class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            try (InputStream is = loader.getResourceAsStream(resourceName)) {
                if (is == null) return null;
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return new PropertyResourceBundle(reader);
                }
            }
        }
    }
}
