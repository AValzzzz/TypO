package com.example.model.i18n;

import java.util.Locale;

public enum AppLanguage {
    EN("en", "English"),
    FR("fr", "Français");

    private final String code;
    private final String label;

    AppLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    @Override
    public String toString() {
        return label;
    }

    public static AppLanguage fromCode(String code) {
        for (AppLanguage l : values())
            if (l.code.equals(code))
                return l;
        return null;
    }

    public static AppLanguage system() {
        return "fr".equals(Locale.getDefault().getLanguage()) ? FR : EN;
    }
}