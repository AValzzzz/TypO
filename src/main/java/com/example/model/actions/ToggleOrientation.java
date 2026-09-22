package com.example.model.actions;

import com.example.model.Page;
import com.example.view.RichTextArea;

import javafx.scene.layout.Pane;

public class ToggleOrientation implements AppAction {
    private final Page page;

    public ToggleOrientation (Page page) {
        this.page = page;
    }

    @Override
    public void execute() {
        Pane pane = page.getPane();
        RichTextArea editor = page.getEditor();

        double currentWidth = pane.getPrefWidth();
        double currentHeight = pane.getPrefHeight();

        double newWidth = currentHeight;
        double newHeight = currentWidth;

        pane.setPrefSize(newWidth, newHeight);
        pane.setMinSize(newWidth, newHeight);
        pane.setMaxSize(newWidth, newHeight);

        editor.setPrefSize(newWidth, newHeight);
    }
}
