package com.github.hmdev.epubcheck;

import java.util.HashMap;
import java.util.Map;

import com.github.hmdev.util.I18n;

/**
 * Provides human-readable hints for epubcheck codes and maps codes to categories.
 */
public final class EpubcheckDictionary {
    private static final Map<String, EpubErrorType> CODE_TO_TYPE = new HashMap<>();

    static {
        // Resource/structure (RSC)
        register("RSC-001", EpubErrorType.RSC);
        register("RSC-004", EpubErrorType.RSC);
        register("RSC-005", EpubErrorType.RSC);
        register("RSC-006", EpubErrorType.RSC);
        register("RSC-007", EpubErrorType.RSC);
        register("RSC-008", EpubErrorType.RSC);
        register("RSC-009", EpubErrorType.RSC);
        register("RSC-010", EpubErrorType.RSC);
        register("RSC-011", EpubErrorType.RSC);
        register("RSC-012", EpubErrorType.RSC);
        register("RSC-017", EpubErrorType.RSC);

        // Package (PKG)
        register("PKG-003", EpubErrorType.PKG);
        register("PKG-007", EpubErrorType.PKG);
        register("PKG-012", EpubErrorType.PKG);

        // OPF/package document
        register("OPF-001", EpubErrorType.OPF);
        register("OPF-003", EpubErrorType.OPF);
        register("OPF-004", EpubErrorType.OPF);
        register("OPF-005", EpubErrorType.OPF);
        register("OPF-008", EpubErrorType.OPF);
        register("OPF-010", EpubErrorType.OPF);
        register("OPF-014", EpubErrorType.OPF);

        // Navigation
        register("NAV-001", EpubErrorType.NAV);
        register("NAV-002", EpubErrorType.NAV);
        register("NAV-003", EpubErrorType.NAV);

        // CSS
        register("CSS-001", EpubErrorType.CSS);
        register("CSS-002", EpubErrorType.CSS);
    }

    private EpubcheckDictionary() {}

    private static void register(String code, EpubErrorType type) {
        CODE_TO_TYPE.put(code, type);
    }

    /** Returns a localized hint for a given code, or null if none is defined. */
    public static String messageFor(String code) {
        if (code == null) return null;
        String key = "epubcheck.code." + code;
        String translated = I18n.t(key);
        if (translated.equals(key)) return null; // missing
        return translated;
    }

    /** Returns a specific error category for a given code, defaulting to prefix-based detection. */
    public static EpubErrorType typeFor(String code) {
        if (code == null) return EpubErrorType.OTHER;
        EpubErrorType t = CODE_TO_TYPE.get(code);
        if (t != null) return t;
        return EpubErrorType.fromCode(code);
    }
}
