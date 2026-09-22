package com.example.model;

import com.example.view.RichTextArea;

import javafx.scene.layout.Pane;

public class Page {
    private final Pane pane;
    private final RichTextArea editor;

    public Page(Pane pane, RichTextArea textEditor) {
        this.pane = pane;
        this.editor = textEditor;
    }

    public Pane getPane() {
        return pane;
    }

    public RichTextArea getEditor() {
        return editor;
    }

    public String getText() {
        return editor.getText();
    }

    public boolean hasSelection() {
        return editor.hasSelection();
    }
}
