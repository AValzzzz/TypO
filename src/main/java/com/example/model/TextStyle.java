package com.example.model;

import java.util.Locale;

import com.example.model.settings.CodeTheme;

import javafx.scene.paint.Color;

public record TextStyle(
        boolean bold,
        boolean italic,
        boolean strikethrough,
        boolean underline,
        Color underlineColor,
        boolean underlineDotted,
        Color highlight,
        Color textColor,
        Integer fontSize,
        Double baselineShift, CodeTheme codeTheme) {

    public static final TextStyle DEFAULT = new TextStyle(false, false, false, false, null, false, null, null, 12,
            null, null);

    public TextStyle withBold(boolean v) {
        return new TextStyle(v, italic, strikethrough, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withItalic(boolean v) {
        return new TextStyle(bold, v, strikethrough, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withStrikethrough(boolean v) {
        return new TextStyle(bold, italic, v, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withUnderline(boolean v) {
        return new TextStyle(bold, italic, strikethrough, v, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withUnderlineColor(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, v, underlineDotted, highlight, textColor, fontSize,
                baselineShift, codeTheme);
    }

    public TextStyle withUnderlineDotted(boolean v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, v, highlight, textColor, fontSize,
                baselineShift, codeTheme);
    }

    public TextStyle withHighlight(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, v, textColor,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withTextColor(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight, v,
                fontSize, baselineShift, codeTheme);
    }

    public TextStyle withFontSize(Integer v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, v, baselineShift, codeTheme);
    }

    public TextStyle withBaselineShift(Double v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, fontSize, v, codeTheme);
    }

    public TextStyle withCodeTheme(CodeTheme v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, fontSize, baselineShift, v);
    }

    public boolean codeBlock() {
        return codeTheme != null;
    }

    public TextStyle withCodeBlock(boolean v) {
        if (v) {
            CodeTheme current = com.example.model.settings.AppSettings.getInstance().codeThemeProperty().get();
            return withCodeTheme(current);
        }
        return withCodeTheme(null);
    }

    public String toCss() {
        StringBuilder css = new StringBuilder();

        if (bold)
            css.append("-fx-font-weight: bold;");
        if (italic)
            css.append("-fx-font-style: italic;");
        if (strikethrough)
            css.append("-fx-strikethrough: true;");
        if (underline) {
            Color uColor = underlineColor != null ? underlineColor : Color.BLACK;
            css.append("-rtfx-underline-color: ").append(toCss(uColor)).append(";")
                    .append("-rtfx-underline-width: 1;");
            if (underlineDotted) {
                css.append("-rtfx-underline-dash-array: 1 2;");
            }
        }
        if (highlight != null)
            css.append("-rtfx-background-color: ").append(toCss(highlight)).append(";");
        if (codeTheme != null) {
            css.append("-fx-font-family: 'Consolas, Monaco, monospace';");
            css.append("-rtfx-background-color: ").append(toCss(codeTheme.getBackground())).append(";");
            css.append("-rtfx-background-insets: -2 -4 -2 -4;");
            css.append("-rtfx-background-radius: 3;");
            if (textColor == null)
                css.append("-fx-fill: ").append(toCss(codeTheme.getTextColor())).append(";");
        }
        if (textColor != null)
            css.append("-fx-fill: ").append(toCss(textColor)).append(";");
        if (fontSize != null)
            css.append("-fx-font-size: ").append(fontSize).append("px;");
        if (baselineShift != null) {
            css.append("-fx-translate-y: ").append(baselineShift).append(";");
        }
        return css.toString();
    }

    private static String toCss(Color c) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)",
                Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255),
                c.getOpacity());
    }
}