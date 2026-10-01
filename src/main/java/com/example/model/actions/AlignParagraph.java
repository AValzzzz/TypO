package com.example.model.actions;

import com.example.model.Page;

import javafx.scene.text.TextAlignment;

public class AlignParagraph implements AppAction {
    private final Page page;
    private final TextAlignment alignment;

    public AlignParagraph(Page page, TextAlignment alignment) {
        this.page = page;
        this.alignment = alignment;
    }

    @Override
    public void execute() {
        page.getEditor().updateSelectionParagraphStyle(s -> s.withAlignment(alignment));
        page.getEditor().requestLayout();
    }
}
