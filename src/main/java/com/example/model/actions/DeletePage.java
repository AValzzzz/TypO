package com.example.model.actions;

import java.util.List;
import java.util.Optional;

import com.example.model.Page;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;

public class DeletePage implements AppAction {
    private final Page page;
    private final VBox pagesContainer;
    private final List<Page> pages;
    
    public DeletePage (Page page, VBox pagesContainer, List<Page> pages) {
        this.page = page;
        this.pagesContainer = pagesContainer;
        this.pages = pages;
    }

    @Override
    public void execute() {
        String text = page.getText();

        if(text != null && !text.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Page");
            alert.setHeaderText("This page contains text.");
            alert.setContentText("Are you sure you want to delete it? This cannot be undone");
            
            Optional<ButtonType> result = alert.showAndWait();
            if(result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        pagesContainer.getChildren().remove(page.getPane());
        pages.remove(page);
    }
}
