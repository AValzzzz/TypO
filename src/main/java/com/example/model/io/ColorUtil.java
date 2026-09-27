package com.example.model.io;

import javafx.scene.paint.Color;

public class ColorUtil {
    private ColorUtil() {}

    public static String toHex(Color c) {
        if(c == null)
            return null;

        return String.format("%02X%02X%02X",
            Math.round(c.getRed() * 255),
            Math.round(c.getGreen() * 255),
            Math.round(c.getBlue() * 255));
    }

    public static Color fromHex(String hex) {
        if(hex == null || hex.isEmpty() || "auto".equalsIgnoreCase(hex))
            return null;

        return Color.web("#" + hex);
    }
}
