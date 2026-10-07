package com.example.view;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

import com.example.model.settings.AppTheme;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

public final class Theme {
    private static final String CSS = Objects
            .requireNonNull(Theme.class.getResource("/com/example/view/theme.css"), "theme.css not found")
            .toExternalForm();
    private static final String[] FONTS = { "Inter-Regular", "Inter-Bold", "Inter-Italic",
            "JetBrainsMono-Regular", "JetBrainsMono-Bold" };
    private static final String[] BUILT_IN = { "rose-pine-dawn", "rose-pine-moon", "nord-snow" };
    private static final String PREF_KEY = "appTheme";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final Path USER_DIR = Path.of(System.getProperty("user.home"), ".typo", "themes");
    private static final String REFRESH_CLASS = "theme-refresh";

    private static final ObservableList<AppTheme> THEMES = FXCollections.observableArrayList();
    private static final ReadOnlyObjectWrapper<AppTheme> CURRENT = new ReadOnlyObjectWrapper<>();
    private static String themeCss;
    private static boolean fontsLoaded;

    static {
        for (String id : BUILT_IN)
            THEMES.add(loadBuiltIn(id));
        loadUserThemes();
        String saved = PREFS.get(PREF_KEY, null);
        CURRENT.set(THEMES.stream().filter(t -> t.getName().equals(saved)).findFirst().orElse(THEMES.get(0)));
        themeCss = dataUri(CURRENT.get().toCss());
        CURRENT.addListener((obs, old, theme) -> switchTo(theme));
    }

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
        install(scene.getStylesheets());
    }

    public static void apply(DialogPane pane) {
        install(pane.getStylesheets());
    }

    private static void install(List<String> sheets) {
        if (!sheets.contains(CSS))
            sheets.add(CSS);
        if (!sheets.contains(themeCss))
            sheets.add(sheets.indexOf(CSS) + 1, themeCss);
    }

    public static ObservableList<AppTheme> themes() {
        return FXCollections.unmodifiableObservableList(THEMES);
    }

    public static ReadOnlyObjectProperty<AppTheme> currentProperty() {
        return CURRENT.getReadOnlyProperty();
    }

    public static AppTheme current() {
        return CURRENT.get();
    }

    public static void select(AppTheme theme) {
        if (theme != null)
            CURRENT.set(theme);
    }

    public static AppTheme importTheme(Path file) throws IOException {
        AppTheme theme = AppTheme.parse(Files.readString(file, StandardCharsets.UTF_8));
        Files.createDirectories(USER_DIR);
        Files.copy(file, USER_DIR.resolve(fileNameFor(theme)), StandardCopyOption.REPLACE_EXISTING);
        register(theme);
        select(theme);
        return theme;
    }

    private static void register(AppTheme theme) {
        for (int i = 0; i < THEMES.size(); i++) {
            if (THEMES.get(i).getName().equals(theme.getName())) {
                THEMES.set(i, theme);
                return;
            }
        }
        THEMES.add(theme);
    }

    private static String fileNameFor(AppTheme theme) {
        String slug = theme.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (slug.isEmpty() ? "theme" : slug) + ".json";
    }

    private static AppTheme loadBuiltIn(String id) {
        try (InputStream in = Theme.class.getResourceAsStream("/com/example/themes/" + id + ".json")) {
            return AppTheme.parse(new String(Objects.requireNonNull(in, id).readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("built-in theme " + id + " is unreadable", e);
        }
    }

    private static void loadUserThemes() {
        if (!Files.isDirectory(USER_DIR))
            return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(USER_DIR, "*.json")) {
            for (Path file : files) {
                try {
                    register(AppTheme.parse(Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException | IllegalArgumentException e) {
                    System.err.println("Skipping theme " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Cannot read themes from " + USER_DIR + ": " + e.getMessage());
        }
    }

    private static void switchTo(AppTheme theme) {
        PREFS.put(PREF_KEY, theme.getName());
        String old = themeCss;
        themeCss = dataUri(theme.toCss());
        for (Window w : Window.getWindows()) {
            Scene scene = w.getScene();
            if (scene == null)
                continue;
            replace(scene.getStylesheets(), old);
            if (scene.getRoot() instanceof DialogPane pane)
                replace(pane.getStylesheets(), old);
            if (w instanceof PopupWindow)
                restyle(scene.getRoot());
        }
    }

    private static void replace(List<String> sheets, String old) {
        int i = sheets.indexOf(old);
        if (i >= 0)
            sheets.set(i, themeCss);
    }

    private static void restyle(Parent root) {
        if (root == null)
            return;
        root.getStyleClass().add(REFRESH_CLASS);
        root.getStyleClass().remove(REFRESH_CLASS);
    }

    private static String dataUri(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
