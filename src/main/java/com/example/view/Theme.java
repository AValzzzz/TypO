package com.example.view;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;

public final class Theme {
    private static final String CSS = Objects
            .requireNonNull(Theme.class.getResource("/com/example/view/theme.css"), "theme.css not found")
            .toExternalForm();
    private static final String[] FONTS = { "Inter-Regular", "Inter-Bold", "Inter-Italic",
            "JetBrainsMono-Regular", "JetBrainsMono-Bold" };
    private static boolean fontsLoaded;

    private Theme() {
    }

    public static synchronized void loadFonts() {
        if (fontsLoaded)
            return;
        fontsLoaded = true;
        for (String name : FONTS) {
            try (InputStream in = Theme.class.getResourceAsStream("/com/example/fonts/" + name + ".ttf")) {
                if (in != null)
                    Font.loadFont(in, 13);
            } catch (IOException ignored) {
            }
        }
    }

    public static void apply(Scene scene) {
        loadFonts();
        if (!scene.getStylesheets().contains(CSS))
            scene.getStylesheets().add(CSS);
    }

    public static void apply(DialogPane pane) {
        if (!pane.getStylesheets().contains(CSS))
            pane.getStylesheets().add(CSS);
    }
}