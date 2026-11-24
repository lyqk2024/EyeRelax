package com.eye.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

/** App-owned messages only. Persisted user names, paths and identifiers are never translated. */
public final class I18n {
    private static final Properties ENGLISH = loadEnglish();
    private static volatile String language = "zh-CN";
    private I18n() {}
    public static void configure(String tag) { language = "en".equals(tag) ? "en" : "zh-CN"; }
    public static boolean isEnglish() { return "en".equals(language); }
    public static String text(String chinese) { return isEnglish() ? ENGLISH.getProperty(chinese, chinese) : chinese; }
    public static String month(Month month) { return isEnglish() ? month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) : month.getValue()+"月"; }
    private static Properties loadEnglish() {
        Properties messages = new Properties();
        try (InputStream stream = I18n.class.getResourceAsStream("english.properties")) {
            if (stream != null) messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) { throw new UncheckedIOException("Cannot load English messages", ex); }
        return messages;
    }
}
