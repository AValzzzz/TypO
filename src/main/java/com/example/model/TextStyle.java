package com.example.model;

import java.util.Locale;

import javafx.scene.paint.Color;

public record TextStyle (
    boolean bold,
    boolean italic,
    boolean strikethrough,
    boolean underline,
    Color underlineColor,
    Color highlight,
    Color textColor,
    Integer fontSize,
    Double baselineShift) {
        
        public static final TextStyle DEFAULT = new TextStyle(false, false, false, false, null, null, null, 12, null);

        public TextStyle withBold(boolean v) {
            return new TextStyle(v, italic, strikethrough, underline, underlineColor, highlight, textColor, fontSize, baselineShift);
        }

        public TextStyle withItalic(boolean v) {
            return new TextStyle(bold, v, strikethrough, underline, underlineColor, highlight, textColor, fontSize, baselineShift);
        }

        public TextStyle withStrikethrough(boolean v) {
            return new TextStyle(bold, italic, v, underline, underlineColor, highlight, textColor, fontSize, baselineShift);
        }

        public TextStyle withUnderline(boolean v) {
            return new TextStyle(bold, italic, strikethrough, v, underlineColor, highlight, textColor, fontSize, baselineShift);
        }

        public TextStyle withUnderlineColor(Color v) {
            return new TextStyle(bold, italic, strikethrough, underline, v, highlight, textColor, fontSize, baselineShift);
        }

        public TextStyle withHighlight(Color v) {
            return new TextStyle(bold, italic, strikethrough, underline, underlineColor, v, textColor, fontSize, baselineShift);
        }

        public TextStyle withTextColor(Color v) {
            return new TextStyle(bold, italic, strikethrough, underline, underlineColor, highlight, v, fontSize, baselineShift);
        }

        public TextStyle withFontSize(Integer v) {
            return new TextStyle(bold, italic, strikethrough, underline, underlineColor, highlight, textColor, v, baselineShift);
        }

        public TextStyle withBaselineShift(Double v) {
            return new TextStyle(bold, italic, strikethrough, underline, underlineColor, highlight, textColor, fontSize, v);
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
                if (underlineColor == null)
                    css.append("-fx-underline: true;");
                else 
                    css.append("-rtfx-underline-color: ").append(toCss(underlineColor)).append(";").append("-rtfx-underline-width: 1;");
            }
            if (highlight != null)
                css.append("-rtfx-background-color: ").append(toCss(highlight)).append(";");
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
                Math.round(c.getGreen()*255),
                Math.round(c.getBlue()*255),
                c.getOpacity());
        }
    }