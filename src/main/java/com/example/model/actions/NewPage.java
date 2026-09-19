package com.example.model.actions;

import org.fxmisc.richtext.StyleClassedTextArea;

import com.example.model.Page;

import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class NewPage implements AppAction {
    private static final double PAGE_WIDTH = 595.0;
    private static final double PAGE_HEIGHT = 842.0;

    private final VBox pagesContainer;
    private Page createdPage;

    public NewPage(VBox pagesContainer) {
        this.pagesContainer = pagesContainer;
    }

    @Override
    public void execute() {
        Pane pane = new Pane();
        pane.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        pane.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        pane.setStyle("-fx-background-color: #FFFFFF;");

        StyleClassedTextArea editor = new StyleClassedTextArea();
        editor.setWrapText(true);
        editor.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        editor.relocate(0, 0);
        editor.setStyle("-fx-background-color: transparent; -fx-padding: 20; -fx-font-size: 14px;");
        
        pane.getChildren().add(editor);
        pagesContainer.getChildren().add(pane);

        this.createdPage = new Page(pane, editor);
    }

    public Page getCreatedPage() {
        return createdPage;
    }
}
