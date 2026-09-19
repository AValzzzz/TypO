package com.example.model;

import org.fxmisc.richtext.StyleClassedTextArea;

import javafx.scene.layout.Pane;

public class Page {
    private final Pane pane;
    private final StyleClassedTextArea editor;

    public Page(Pane pane, StyleClassedTextArea editor) {
        this.pane = pane;
        this.editor = editor;
    }

    public Pane getPane() {
        return pane;
    }

    public StyleClassedTextArea getEditor() {
        return editor;
    }

    // public String getText() {
    //     return editor.getText();
    // }
}
