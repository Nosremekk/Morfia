package com.ffmpegstudio.ui.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internationalization helper backed by Java ResourceBundles (pt_BR and en).
 * Technical terms (codecs, FFmpeg flags, filter names) are never translated.
 */
public final class I18n {
    private static final String BUNDLE_BASE = "com.ffmpegstudio.i18n.messages";
    private static Locale currentLocale = Locale.forLanguageTag("pt-BR");
    private static ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);

    private I18n() {}

    public static synchronized void setLocale(String languageCode) {
        if ("en".equalsIgnoreCase(languageCode)) {
            currentLocale = Locale.ENGLISH;
        } else {
            currentLocale = Locale.forLanguageTag("pt-BR");
        }
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
    }

    public static synchronized Locale getCurrentLocale() {
        return currentLocale;
    }

    public static synchronized ResourceBundle getBundle() {
        return bundle;
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String format(String key, Object... args) {
        String pattern = get(key);
        return MessageFormat.format(pattern, args);
    }
}
