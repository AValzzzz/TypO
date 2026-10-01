package com.example.model.io;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

import javafx.scene.text.TextAlignment;

public class ParagraphAlignments {
    private ParagraphAlignments() {
    }

    public static ParagraphAlignment toPoi(TextAlignment a) {
        return switch (a == null ? TextAlignment.LEFT : a) {
            case LEFT -> ParagraphAlignment.LEFT;
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case JUSTIFY -> ParagraphAlignment.BOTH;
        };
    }

    public static TextAlignment fromPoi(ParagraphAlignment a) {
        if (a == null)
            return TextAlignment.LEFT;
        return switch (a) {
            case CENTER -> TextAlignment.CENTER;
            case RIGHT -> TextAlignment.RIGHT;
            case BOTH, DISTRIBUTE -> TextAlignment.JUSTIFY;
            default -> TextAlignment.LEFT;
        };
    }
}
