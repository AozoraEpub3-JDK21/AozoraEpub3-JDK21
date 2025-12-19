package com.github.hmdev.epubcheck;

public enum EpubErrorType {
    RSC("RSC"), OPF("OPF"), NAV("NAV"), PKG("PKG"), CSS("CSS"), OTHER("OTHER");

    private final String category;

    EpubErrorType(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public static EpubErrorType fromCode(String code) {
        if (code == null || code.length() < 3) return OTHER;
        String prefix = code.substring(0, 3).toUpperCase();
        switch (prefix) {
            case "RSC": return RSC;
            case "OPF": return OPF;
            case "NAV": return NAV;
            case "PKG": return PKG;
            case "CSS": return CSS;
            default: return OTHER;
        }
    }
}
