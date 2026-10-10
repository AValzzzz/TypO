package com.example.model.io;

import java.util.Locale;

import javafx.scene.paint.Color;

public class ColorUtil {
    private ColorUtil() {
    }

    public static String toHex(Color c) {
        if (c == null)
            return null;

        return String.format("%02X%02X%02X",
                Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255));
    }

    public static Color fromHex(String hex) {
        if (hex == null || hex.isEmpty() || "auto".equalsIgnoreCase(hex))
            return null;

        return Color.web("#" + hex);
    }

    public static String toCssRgba(Color c) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255), c.getOpacity());
    }

    public static String toCssHex(Color c) {
        return "#" + toHex(c);
    }

    public static Color withOpacity(Color c, double opacity) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(1, opacity)));
    }

    public static Color opaque(Color c) {
        return withOpacity(c, 1);
    }
}
