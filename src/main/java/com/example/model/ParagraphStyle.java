package com.example.model;

import com.example.model.settings.CodeTheme;

import javafx.scene.text.TextAlignment;

public record ParagraphStyle(CodeTheme codeTheme, TextAlignment alignment) {
    public static final ParagraphStyle DEFAULT = new ParagraphStyle(null, TextAlignment.LEFT);

    public ParagraphStyle {
        if (alignment == null)
            alignment = TextAlignment.LEFT;
    }

    public boolean codeBlock() {
        return codeTheme != null;
    }

    public ParagraphStyle withAlignment(TextAlignment v) {
        return new ParagraphStyle(codeTheme, v);
    }

    public ParagraphStyle withCodeTheme(CodeTheme v) {
        return new ParagraphStyle(v, alignment);
    }

    public static ParagraphStyle orDefault(ParagraphStyle s) {
        return s != null ? s : DEFAULT;
    }
}
