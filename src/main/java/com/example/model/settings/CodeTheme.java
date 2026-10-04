package com.example.model.settings;

import com.example.model.syntax.TokenType;

import javafx.scene.paint.Color;

public enum CodeTheme {
    DARK("Dark", Color.rgb(30, 30, 30), Color.rgb(212, 212, 212), Color.WHITE,
            "#C586C0", "#4EC9B0", "#DCDCAA", "#CE9178", "#6A9955", "#B5CEA8", "#9CDCFE"),
    LIGHT("Light", Color.rgb(245, 245, 245), Color.rgb(30, 30, 30), Color.BLACK,
            "#AF00DB", "#267F99", "#795E26", "#A31515", "#008000", "#098658", "#001080"),
    SOLARIZED("Solarized", Color.rgb(0, 43, 54), Color.rgb(131, 148, 150), Color.rgb(211, 174, 0),
            "#859900", "#B58900", "#268BD2", "#2AA198", "#586E75", "#CB4B16", "#6C71C4");

    private final String label;
    private final Color background;
    private final Color textColor;
    private final Color caretColor;
    private final Color[] tokens;

    CodeTheme(String label, Color background, Color textColor, Color caretColor, String... tokenHex) {
        this.label = label;
        this.background = background;
        this.textColor = textColor;
        this.caretColor = caretColor;
        this.tokens = new Color[TokenType.values().length];
        for (int i = 0; i < tokens.length; i++)
            tokens[i] = Color.web(tokenHex[i]);
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

    public Color tokenColor(TokenType type) {
        return tokens[type.ordinal()];
    }

    public static Color toDarkPalette(Color c) {
        if (c == null)
            return null;
        for (CodeTheme theme : values())
            for (int i = 0; i < theme.tokens.length; i++)
                if (theme.tokens[i].equals(c))
                    return DARK.tokens[i];
        return c;
    }

    @Override
    public String toString() {
        return label;
    }
}