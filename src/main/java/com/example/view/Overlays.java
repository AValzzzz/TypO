package com.example.view;

import java.util.function.Predicate;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

final class Overlays {
    private Overlays() {
    }

    static void onOutsidePress(Node owner, Runnable action) {
        EventHandler<MouseEvent> filter = e -> {
            if (!Nodes.isWithin(e.getTarget(), owner))
                action.run();
        };
        owner.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null)
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, filter);
            if (newScene != null)
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, filter);
        });
    }

    static void deleteOnKey(Node owner, Predicate<KeyEvent> allowed, Runnable delete) {
        owner.setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && allowed.test(e)) {
                delete.run();
                e.consume();
            }
        });
    }
}
