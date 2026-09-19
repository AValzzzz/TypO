package com.example.controller;

import com.example.model.actions.Help;
import com.example.model.actions.Save;
import com.example.model.actions.SaveAs;
import com.example.model.actions.Settings;

import javafx.fxml.FXML;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class InputController {
    @FXML
    private StackPane stackPane;

    @FXML
    private Pane whitePane;

    private static final double MIN_SCALE = 0.8;
    private static final double MAX_SCALE = 3.0;
    private static final double ZOOM_SENSITIVITY = 0.002;

    private static final double PAN_SENSITIVITY = 1.5;

    @FXML
    public void initialize() {
        stackPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                double zoomFactor = Math.exp(delta*ZOOM_SENSITIVITY);

                double newScaleX = whitePane.getScaleX() * zoomFactor;
                double newScaleY = whitePane.getScaleY() * zoomFactor;

                newScaleX = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleX));
                newScaleY = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleY));

                whitePane.setScaleX(newScaleX);
                whitePane.setScaleY(newScaleY);
                
            } else if (event.isShiftDown()) {
                double deltaX = event.getDeltaX();
                whitePane.setTranslateX(whitePane.getTranslateX() + deltaX * PAN_SENSITIVITY);
            } else {
                double deltaY = event.getDeltaY();
                whitePane.setTranslateY(whitePane.getTranslateY() + deltaY * PAN_SENSITIVITY);
            }
            clampTranslate();
            event.consume();
        });
    }
    
    private void clampTranslate() {
        double prefHeight = whitePane.getPrefHeight();
        double scaledHeight = prefHeight * whitePane.getScaleY();
        double overflowY = Math.max(0, (scaledHeight - prefHeight)/2.0);
        double maxTranslateY = overflowY;

        double clampedY = Math.max(-maxTranslateY, Math.min(maxTranslateY, whitePane.getTranslateY()));
        whitePane.setTranslateY(clampedY);

        double prefWidth = whitePane.getPrefWidth();
        double scaledWidth = prefWidth * whitePane.getScaleX();
        double overflowX = Math.max(0, (scaledWidth - prefWidth) /2.0);
        double maxTranslateX = overflowX;

        double clampedX = Math.max(-maxTranslateX, Math.min(maxTranslateX, whitePane.getTranslateX()));
        whitePane.setTranslateX(clampedX);
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
    private void handleHelp() {
        new Help().execute();
    }

    @FXML
    private void handleSettings() {
        new Settings().execute();
    }
}
