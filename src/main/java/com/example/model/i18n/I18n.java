package com.example.model.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class I18n {
    private static final String BASE = "/com/example/i18n/messages_";
    private static final String PREF_KEY = "language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);

    private static final Properties MESSAGES = new Properties();
    private static final Properties FALLBACK = new Properties();
    private static final AppLanguage ACTIVE;

    static {
        ACTIVE = selected();
        load(FALLBACK, AppLanguage.EN);
        load(MESSAGES, ACTIVE);
        Locale.setDefault(ACTIVE.locale());
    }

    private static final ResourceBundle BUNDLE = new ResourceBundle() {
        @Override
        protected Object handleGetObject(String key) {
            return t(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            Set<String> keys = new HashSet<>(FALLBACK.stringPropertyNames());
            keys.addAll(MESSAGES.stringPropertyNames());
            return Collections.enumeration(keys);
        }
    };

    private I18n() {
    }

    public static AppLanguage active() {
        return ACTIVE;
    }

    public static AppLanguage selected() {
        AppLanguage saved = AppLanguage.fromCode(PREFS.get(PREF_KEY, null));
        return saved != null ? saved : AppLanguage.system();
    }

    public static void setSelected(AppLanguage language) {
        PREFS.put(PREF_KEY, language.code());
        try {
            PREFS.flush();
        } catch (BackingStoreException e) {
            System.err.println("Could not save the language: " + e.getMessage());
        }
    }

    public static String t(String key) {
        String v = MESSAGES.getProperty(key);
        if (v == null)
            v = FALLBACK.getProperty(key);
        return v != null ? v : key;
    }

    public static String t(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++)
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    public static ResourceBundle bundle() {
        return BUNDLE;
    }

    private static void load(Properties target, AppLanguage lang) {
        try (InputStream in = I18n.class.getResourceAsStream(BASE + lang.code() + ".properties")) {
            if (in == null)
                return;
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                target.load(reader);
            }
        } catch (IOException e) {
            System.err.println("Could not load the " + lang.code() + " messages: " + e.getMessage());
        }
    }
}