package com.example.model.settings;

import javafx.scene.paint.Color;

public enum CodeTheme {
    DARK("Dark", Color.rgb(30, 30, 30), Color.rgb(212, 212, 212), Color.WHITE),
    LIGHT("Light", Color.rgb(245, 245, 245), Color.rgb(30, 30, 30), Color.BLACK),
    SOLARIZED("Solarized", Color.rgb(0, 43, 54), Color.rgb(131, 148, 150), Color.rgb(211, 174, 0));

    private final String label;
    private final Color background;
    private final Color textColor;
    private final Color caretColor;

    CodeTheme(String label, Color background, Color textColor, Color caretColor) {
        this.label = label;
        this.background = background;
        this.textColor = textColor;
        this.caretColor = caretColor;
    }

    public String getLabel() {
        return label;
    }

    public Color getBackground() {
        return background;
    }

    public Color getTextColor() {
        return textColor;
    }

    public Color getCaretColor() {
        return caretColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
