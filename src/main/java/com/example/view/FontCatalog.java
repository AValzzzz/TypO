package com.example.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javafx.scene.text.Font;

public final class FontCatalog {
    private static final List<String> CURATED = List.of(
            "Inter", "JetBrains Mono",
            "Arial", "Arial Black", "Calibri", "Cambria", "Candara", "Century Gothic", "Comic Sans MS",
            "Consolas", "Constantia", "Corbel", "Courier New", "Franklin Gothic Medium", "Garamond", "Georgia",
            "Impact", "Lucida Console", "Palatino Linotype", "Book Antiqua", "Segoe UI", "Tahoma",
            "Times New Roman", "Trebuchet MS", "Verdana",
            "Helvetica", "Helvetica Neue", "Times", "Courier", "Avenir", "Baskerville", "Didot", "Futura",
            "Gill Sans", "Hoefler Text", "Menlo", "Optima", "Palatino",
            "Liberation Sans", "Liberation Serif", "Liberation Mono", "DejaVu Sans", "DejaVu Serif",
            "DejaVu Sans Mono", "Noto Sans", "Noto Serif", "Open Sans", "Roboto", "Lato", "Ubuntu");

    private static final List<String> UI_DEFAULTS = List.of("Inter", "Segoe UI", "Helvetica Neue", "Arial");

    private static List<String> available;
    private static String defaultFamily;

    private FontCatalog() {
    }

    public static synchronized List<String> available() {
        if (available == null) {
            Theme.loadFonts();
            Map<String, String> installed = new HashMap<>();
            for (String family : Font.getFamilies())
                installed.put(family.toLowerCase(Locale.ROOT), family);

            TreeSet<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String name : CURATED) {
                String real = installed.get(name.toLowerCase(Locale.ROOT));
                if (real != null)
                    found.add(real);
            }
            available = List.copyOf(new ArrayList<>(found));
        }
        return available;
    }

    public static synchronized String defaultFamily() {
        if (defaultFamily == null) {
            Theme.loadFonts();
            Map<String, String> installed = new HashMap<>();
            for (String family : Font.getFamilies())
                installed.put(family.toLowerCase(Locale.ROOT), family);
            defaultFamily = UI_DEFAULTS.stream()
                    .map(name -> installed.get(name.toLowerCase(Locale.ROOT)))
                    .filter(f -> f != null)
                    .findFirst()
                    .orElse(Font.getDefault().getFamily());
        }
        return defaultFamily;
    }
}