package com.example.view;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.DialogPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public final class Backdrop {
    private static final double BLUR = 5;
    private static final double BRIGHTNESS = -0.14;

    private Backdrop() {
    }

    public static void install() {
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next())
                if (c.wasAdded())
                    for (Window w : c.getAddedSubList())
                        if (w instanceof Stage stage && stage.getModality() != Modality.NONE)
                            onDialog(stage);
        });
    }

    private static void onDialog(Stage dialog) {
        if (dialog.getScene() != null && dialog.getScene().getRoot() instanceof DialogPane pane)
            Theme.apply(pane);

        Node target = findAppRoot();
        if (target == null)
            return;

        Effect previous = target.getEffect();
        ColorAdjust dim = new ColorAdjust();
        GaussianBlur blur = new GaussianBlur(0);
        blur.setInput(dim);
        target.setEffect(blur);
        animate(blur, dim, BLUR, BRIGHTNESS, 220, null);

        dialog.showingProperty().addListener((obs, was, showing) -> {
            if (!showing)
                animate(blur, dim, 0, 0, 160, () -> target.setEffect(previous));
        });
    }

    private static Node findAppRoot() {
        for (Window w : Window.getWindows())
            if (w.getScene() != null && w.getScene().getRoot().getStyleClass().contains("app-root"))
                return w.getScene().getRoot();
        return null;
    }

    private static void animate(GaussianBlur blur, ColorAdjust dim, double radius, double brightness, int ms,
            Runnable after) {
        if (Motion.isReduced()) {
            blur.setRadius(radius);
            dim.setBrightness(brightness);
            if (after != null)
                after.run();
            return;
        }
        Timeline t = new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(blur.radiusProperty(), radius, Motion.EASE_OUT),
                new KeyValue(dim.brightnessProperty(), brightness, Motion.EASE_OUT)));
        if (after != null)
            t.setOnFinished(e -> after.run());
        t.play();
    }
}