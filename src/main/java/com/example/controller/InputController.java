package com.example.controller;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.richtext.StyleClassedTextArea;

import com.example.model.Page;
import com.example.model.actions.Help;
import com.example.model.actions.NewPage;
import com.example.model.actions.Save;
import com.example.model.actions.SaveAs;
import com.example.model.actions.Settings;

import javafx.fxml.FXML;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class InputController {
    @FXML
    private StackPane stackPane;
    
    @FXML
    private VBox pagesContainer;

    @FXML 
    private Pane whitePane;

    @FXML 
    private StyleClassedTextArea textEditor;

    private final List<Page> pages = new ArrayList<>();

    private static final double MIN_SCALE = 0.8;
    private static final double MAX_SCALE = 3.0;
    private static final double ZOOM_SENSITIVITY = 0.002;

    private static final double PAN_SENSITIVITY = 1.5;

    @FXML
    public void initialize() {
        pages.add(new Page(whitePane, textEditor));
        stackPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                double zoomFactor = Math.exp(delta*ZOOM_SENSITIVITY);

                double newScaleX = pagesContainer.getScaleX() * zoomFactor;
                double newScaleY = pagesContainer.getScaleY() * zoomFactor;

                newScaleX = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleX));
                newScaleY = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleY));

                pagesContainer.setScaleX(newScaleX);
                pagesContainer.setScaleY(newScaleY);
                
            } else if (event.isShiftDown()) {
                double deltaX = event.getDeltaX();
                pagesContainer.setTranslateX(pagesContainer.getTranslateX() + deltaX * PAN_SENSITIVITY);
            } else {
                double deltaY = event.getDeltaY();
                pagesContainer.setTranslateY(pagesContainer.getTranslateY() + deltaY * PAN_SENSITIVITY);
            }
            clampTranslate();
            event.consume();
        });
    }
    
    private void clampTranslate() {

        double viewportWidth = stackPane.getWidth();
        double viewportHeight = stackPane.getHeight();

        double contentWidth = pagesContainer.prefWidth(-1);
        double contentHeight = pagesContainer.prefHeight(-1);

        double scaledWidth = contentWidth * pagesContainer.getScaleX();
        double scaledHeight = contentHeight * pagesContainer.getScaleY();

        double maxTranslateX = Math.max(0, (scaledWidth - viewportWidth) / 2.0) + 45;
        double maxTranslateY = Math.max(0, (scaledHeight - viewportHeight) / 2.0) + 45;

        double clampedX = Math.max(-maxTranslateX, Math.min(maxTranslateX, pagesContainer.getTranslateX()));
        pagesContainer.setTranslateX(clampedX);

        double clampedY = Math.max(-maxTranslateY, Math.min(maxTranslateY, pagesContainer.getTranslateY()));
        pagesContainer.setTranslateY(clampedY);
    }

    @FXML
    private void handleSave() {
        new Save().execute();
    }

    @FXML
    private void handleSaveAs() {
        new SaveAs().execute();
    }

    @FXML 
    private void handleNewPage() {
        NewPage action = new NewPage(pagesContainer);
        action.execute();
        pages.add(action.getCreatedPage());
    }

    @FXML
    private void handleHelp() {
        new Help().execute();
    }

    @FXML
    private void handleSettings() {
        new Settings().execute();
    }
}
