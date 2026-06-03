package com.jabcodex.uiporter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppSettings {

    private static final Path SETTINGS_FILE =
            Path.of(System.getProperty("user.home"), ".uiporter", "settings.properties");

    private static final Properties PROPS = new Properties();

    static {
        load();
    }

    private AppSettings() {}


    public static void load() {
        if (Files.exists(SETTINGS_FILE)) {
            try (Reader r = Files.newBufferedReader(SETTINGS_FILE)) {
                PROPS.load(r);
            } catch (IOException ignored) {}
        }
    }

    public static void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(SETTINGS_FILE)) {
                PROPS.store(w, "UIPorter settings - do not edit manually");
            }
        } catch (IOException ignored) {}
    }


    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String v = PROPS.getProperty(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    public static void set(String key, String value) {
        if (value == null) value = "";
        PROPS.setProperty(key, value);
        save();
    }

    public static void setBool(String key, boolean value) {
        set(key, String.valueOf(value));
    }
}
