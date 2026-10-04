package com.example.model;

import com.example.model.io.ColorUtil;
import com.example.model.settings.AppSettings;
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
        Double baselineShift, CodeTheme codeTheme,
        String link) {

    public static final String LINK_COLOR = "#0563C1";

    public static final TextStyle DEFAULT = new TextStyle(false, false, false, false, null, false, null, null, 12,
            null, null, null);

    public TextStyle withBold(boolean v) {
        return new TextStyle(v, italic, strikethrough, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withItalic(boolean v) {
        return new TextStyle(bold, v, strikethrough, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withStrikethrough(boolean v) {
        return new TextStyle(bold, italic, v, underline, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withUnderline(boolean v) {
        return new TextStyle(bold, italic, strikethrough, v, underlineColor, underlineDotted, highlight, textColor,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withUnderlineColor(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, v, underlineDotted, highlight, textColor, fontSize,
                baselineShift, codeTheme, link);
    }

    public TextStyle withUnderlineDotted(boolean v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, v, highlight, textColor, fontSize,
                baselineShift, codeTheme, link);
    }

    public TextStyle withHighlight(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, v, textColor,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withTextColor(Color v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight, v,
                fontSize, baselineShift, codeTheme, link);
    }

    public TextStyle withFontSize(Integer v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, v, baselineShift, codeTheme, link);
    }

    public TextStyle withBaselineShift(Double v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, fontSize, v, codeTheme, link);
    }

    public TextStyle withCodeTheme(CodeTheme v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, fontSize, baselineShift, v, link);
    }

    public TextStyle withLink(String v) {
        return new TextStyle(bold, italic, strikethrough, underline, underlineColor, underlineDotted, highlight,
                textColor, fontSize, baselineShift, codeTheme, v);
    }

    public boolean codeBlock() {
        return codeTheme != null;
    }

    public TextStyle withCodeBlock(boolean v) {
        if (v) {
            CodeTheme current = AppSettings.getInstance().codeThemeProperty().get();
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
            css.append("-rtfx-underline-color: ").append(ColorUtil.toCssRgba(uColor)).append(";")
                    .append("-rtfx-underline-width: 1;");
            if (underlineDotted) {
                css.append("-rtfx-underline-dash-array: 1 2;");
            }
        }
        if (highlight != null)
            css.append("-rtfx-background-color: ").append(ColorUtil.toCssRgba(highlight)).append(";");
        if (codeTheme != null) {
            css.append("-fx-font-family: 'Consolas, Monaco, monospace';");
            css.append("-rtfx-background-color: ").append(ColorUtil.toCssRgba(codeTheme.getBackground())).append(";");
            css.append("-rtfx-background-insets: -2 -4 -2 -4;");
            css.append("-rtfx-background-radius: 3;");
            if (textColor == null)
                css.append("-fx-fill: ").append(ColorUtil.toCssRgba(codeTheme.getTextColor())).append(";");
        }
        if (textColor != null)
            css.append("-fx-fill: ").append(ColorUtil.toCssRgba(textColor)).append(";");
        if (link != null) {
            css.append("-fx-fill: ").append(LINK_COLOR).append(";");
            css.append("-rtfx-underline-color: ").append(LINK_COLOR).append(";")
                    .append("-rtfx-underline-width: 1;");
        }
        if (fontSize != null)
            css.append("-fx-font-size: ").append(fontSize).append("px;");
        if (baselineShift != null) {
            css.append("-fx-translate-y: ").append(baselineShift).append(";");
        }
        return css.toString();
    }
}