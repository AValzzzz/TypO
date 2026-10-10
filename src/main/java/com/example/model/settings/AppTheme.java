package com.example.model.settings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.example.model.io.ColorUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javafx.scene.paint.Color;

public final class AppTheme {
    public static final String[] REQUIRED = { "background", "surface", "overlay", "text", "accent" };
    public static final String[] OPTIONAL = { "muted", "subtle", "highlightLow", "highlightMed", "highlightHigh",
            "success", "error", "warning", "onAccent", "selection", "card", "backdropTop", "backdropBottom",
            "shadow" };

    private final String name;
    private final Map<String, Color> colors;

    private AppTheme(String name, Map<String, Color> colors) {
        this.name = name;
        this.colors = colors;
    }

    public static AppTheme parse(String json) {
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject())
                throw new IllegalArgumentException("a theme must be a JSON object");
            root = el.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e);
        }

        String name = root.has("name") ? root.get("name").getAsString().trim() : "";
        if (name.isEmpty())
            throw new IllegalArgumentException("missing \"name\"");

        Map<String, Color> c = new HashMap<>();
        for (String key : REQUIRED) {
            Color color = read(root, key);
            if (color == null)
                throw new IllegalArgumentException("missing color \"" + key + "\"");
            c.put(key, color);
        }
        for (String key : OPTIONAL) {
            Color color = read(root, key);
            if (color != null)
                c.put(key, color);
        }
        fillDefaults(c);
        return new AppTheme(name, c);
    }

    private static Color read(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull())
            return null;
        String value = root.get(key).getAsString().trim();
        try {
            return Color.web(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + key + "\" is not a color: " + value, e);
        }
    }

    private static void fillDefaults(Map<String, Color> c) {
        Color bg = c.get("background");
        Color text = c.get("text");
        Color accent = c.get("accent");
        boolean dark = isDark(bg);

        c.putIfAbsent("muted", bg.interpolate(text, 0.5));
        c.putIfAbsent("subtle", bg.interpolate(text, 0.72));
        c.putIfAbsent("highlightLow", bg.interpolate(text, 0.04));
        c.putIfAbsent("highlightMed", bg.interpolate(text, 0.13));
        c.putIfAbsent("highlightHigh", bg.interpolate(text, 0.2));
        c.putIfAbsent("success", Color.web(dark ? "#9ccfd8" : "#286983"));
        c.putIfAbsent("error", Color.web(dark ? "#eb6f92" : "#b4637a"));
        c.putIfAbsent("warning", Color.web(dark ? "#f6c177" : "#b35c00"));
        c.putIfAbsent("onAccent", isDark(accent) ? Color.WHITE : Color.web("#1e1e2e"));
        c.putIfAbsent("selection", ColorUtil.withOpacity(accent, 0.35));
        c.putIfAbsent("card", c.get("surface"));
        c.putIfAbsent("backdropTop", c.get("overlay"));
        c.putIfAbsent("backdropBottom", c.get("overlay").interpolate(Color.BLACK, 0.06));
        c.putIfAbsent("shadow", dark ? Color.BLACK : text);
    }

    private static boolean isDark(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue() < 0.5;
    }

    public String getName() {
        return name;
    }

    public Color color(String key) {
        return colors.get(key);
    }

    public Color getBackground() {
        return colors.get("background");
    }

    public Color getSelection() {
        return colors.get("selection");
    }

    public String toCss() {
        Color accent = colors.get("accent");
        Color shadow = colors.get("shadow");
        StringBuilder css = new StringBuilder(".root {\n");
        define(css, "-c-base", colors.get("background"));
        define(css, "-c-surface", colors.get("surface"));
        define(css, "-c-overlay", colors.get("overlay"));
        define(css, "-c-muted", colors.get("muted"));
        define(css, "-c-subtle", colors.get("subtle"));
        define(css, "-c-text", colors.get("text"));
        define(css, "-c-accent", accent);
        define(css, "-c-success", colors.get("success"));
        define(css, "-c-error", colors.get("error"));
        define(css, "-c-warning", colors.get("warning"));
        define(css, "-c-hl-low", colors.get("highlightLow"));
        define(css, "-c-hl-med", colors.get("highlightMed"));
        define(css, "-c-hl-high", colors.get("highlightHigh"));
        define(css, "-c-on-accent", colors.get("onAccent"));
        define(css, "-c-selection", colors.get("selection"));
        define(css, "-c-card", colors.get("card"));
        define(css, "-c-backdrop-top", colors.get("backdropTop"));
        define(css, "-c-backdrop-bottom", colors.get("backdropBottom"));
        define(css, "-c-toolbar", ColorUtil.withOpacity(colors.get("surface"), 0.88));
        define(css, "-c-toolbar-border", ColorUtil.withOpacity(colors.get("highlightHigh"), 0.7));
        define(css, "-c-accent-faint", ColorUtil.withOpacity(accent, 0.18));
        define(css, "-c-accent-glow", ColorUtil.withOpacity(accent, 0.40));
        define(css, "-c-accent-strong", ColorUtil.withOpacity(accent, 0.45));
        define(css, "-c-accent-thumb", ColorUtil.withOpacity(accent, 0.75));
        define(css, "-c-shadow-faint", ColorUtil.withOpacity(shadow, 0.08));
        define(css, "-c-shadow-soft", ColorUtil.withOpacity(shadow, 0.14));
        define(css, "-c-shadow", ColorUtil.withOpacity(shadow, 0.20));
        define(css, "-c-shadow-page", ColorUtil.withOpacity(shadow, 0.26));
        define(css, "-c-shadow-strong", ColorUtil.withOpacity(shadow, 0.28));
        define(css, "-c-shadow-toast", ColorUtil.withOpacity(shadow, 0.35));
        define(css, "-c-scroll-thumb", ColorUtil.withOpacity(colors.get("text"), 0.28));
        return css.append("}\n").toString();
    }

    private static void define(StringBuilder css, String name, Color color) {
        css.append("    ").append(name).append(": ").append(rgba(color)).append(";\n");
    }

    private static String rgba(Color c) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255), c.getOpacity());
    }

    @Override
    public String toString() {
        return name;
    }
}
