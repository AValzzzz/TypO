package com.example.view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

public final class Toast {
    private static final int HOLD_MS = 2400;

    private static Pane host;
    private static Label current;
    private static PauseTransition hold;

    private Toast() {
    }

    public static void install(Pane rootPane) {
        host = rootPane;
    }

    public static void success(String message) {
        show("\u2713  " + message, "toast-success");
    }

    public static void info(String message) {
        show(message, null);
    }

    public static void error(String message) {
        show("\u2715  " + message, "toast-error");
    }

    private static void show(String message, String variant) {
        if (host == null)
            return;
        if (Platform.isFxApplicationThread())
            display(message, variant);
        else
            Platform.runLater(() -> display(message, variant));
    }

    private static void display(String message, String variant) {
        if (hold != null)
            hold.stop();
        if (current != null)
            host.getChildren().remove(current);

        Label toast = new Label(message);
        toast.getStyleClass().add("toast");
        if (variant != null)
            toast.getStyleClass().add(variant);
        toast.setMouseTransparent(true);
        toast.setManaged(false);
        toast.layoutXProperty().bind(host.widthProperty().subtract(toast.widthProperty()).divide(2));
        toast.layoutYProperty().bind(host.heightProperty().subtract(toast.heightProperty()).subtract(40));

        host.getChildren().add(toast);
        toast.applyCss();
        toast.autosize();
        toast.toFront();
        current = toast;
        Motion.fadeSlideIn(toast, 28, 0);

        hold = new PauseTransition(Duration.millis(HOLD_MS));
        hold.setOnFinished(e -> Motion.fadeOutThen(toast, () -> {
            host.getChildren().remove(toast);
            if (current == toast)
                current = null;
        }));
        hold.play();
    }
}