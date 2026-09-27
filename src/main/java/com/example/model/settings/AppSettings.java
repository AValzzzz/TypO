package com.example.model.settings;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

public class AppSettings {
    private static final AppSettings INSTANCE = new AppSettings();

    public static AppSettings getInstance() {
        return INSTANCE;
    }

    private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.rgb(211, 211, 211)); // current #D3D3D3
    private final ObjectProperty<Color> selectionColor = new SimpleObjectProperty<>(Color.rgb(51, 153, 255, 0.4)); // default JavaFX-ish blue
    private final ObjectProperty<CodeTheme> codeTheme = new SimpleObjectProperty<>(CodeTheme.DARK);

    private AppSettings(){}

    public ObjectProperty<Color> backgroundColorProperty() { return backgroundColor; }
    public ObjectProperty<Color> selectionColorProperty() { return selectionColor; }
    public ObjectProperty<CodeTheme> codeThemeProperty() { return codeTheme; }
}
