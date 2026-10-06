package com.example.view;

import java.util.Optional;

import org.fxmisc.richtext.Caret;

import com.example.model.Page;
import com.example.model.settings.AppSettings;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class SmoothCaret {
    private static final double WIDTH = 2;
    private static final Color TEXT_COLOR = Color.web("#26233a");

    private final RichTextArea editor;
    private final Pane pane;
    private final Rectangle caret = new Rectangle(WIDTH, 18);
    private final ChangeListener<Boolean> enabledListener = (obs, was, on) -> apply(on);

    private Timeline move;
    private Timeline blink;
    private boolean scheduled;

    public SmoothCaret(Page page) {
        this.editor = page.getEditor();
        this.pane = page.getPane();

        caret.setManaged(false);
        caret.setMouseTransparent(true);
        caret.setVisible(false);
        caret.setArcWidth(2);
        caret.setArcHeight(2);
        caret.getProperties().put(Page.SHADOW_KEY, Boolean.TRUE);
        caret.getProperties().put("noExport", Boolean.TRUE);
        pane.getChildren().add(caret);

        editor.caretBoundsProperty().addListener((obs, was, is) -> schedule());
        editor.focusedProperty().addListener((obs, was, is) -> schedule());
        editor.selectionProperty().addListener((obs, was, is) -> schedule());
        editor.richChanges().subscribe(c -> schedule());

        Motion.smoothCaretProperty().addListener(new WeakChangeListener<>(enabledListener));
        apply(enabled());
    }

    private static boolean enabled() {
        return Motion.smoothCaretProperty().get();
    }

    private void apply(boolean on) {
        editor.setShowCaret(on ? Caret.CaretVisibility.OFF : Caret.CaretVisibility.AUTO);
        if (on) {
            schedule();
        } else {
            stop(move);
            stop(blink);
            caret.setVisible(false);
        }
    }

    private void schedule() {
        if (!enabled() || scheduled)
            return;
        scheduled = true;
        Platform.runLater(() -> {
            scheduled = false;
            update();
        });
    }

    private void update() {
        if (!enabled())
            return;
        Optional<Bounds> bounds = editor.getCaretBounds();
        if (!editor.isFocused() || editor.getSelection().getLength() > 0 || bounds.isEmpty()) {
            caret.setVisible(false);
            stop(blink);
            return;
        }
        Point2D tl = pane.screenToLocal(bounds.get().getMinX(), bounds.get().getMinY());
        Point2D br = pane.screenToLocal(bounds.get().getMaxX(), bounds.get().getMaxY());
        if (tl == null || br == null) {
            caret.setVisible(false);
            return;
        }

        double x = tl.getX() - WIDTH / 2;
        double y = tl.getY();
        double h = Math.max(8, br.getY() - tl.getY());
        caret.setFill(color());
        caret.setHeight(h);

        stop(move);
        if (!caret.isVisible() || Motion.isReduced()) {
            caret.setLayoutX(x);
            caret.setLayoutY(y);
            caret.setWidth(WIDTH);
            caret.setVisible(true);
        } else {
            double distance = Math.hypot(x - caret.getLayoutX(), y - caret.getLayoutY());
            caret.setWidth(Math.min(WIDTH + distance * 0.06, 6)); 
            move = new Timeline(new KeyFrame(Duration.millis(120),
                    new KeyValue(caret.layoutXProperty(), x, Motion.EASE_OUT),
                    new KeyValue(caret.layoutYProperty(), y, Motion.EASE_OUT),
                    new KeyValue(caret.widthProperty(), WIDTH, Motion.EASE_OUT)));
            move.play();
        }
        restartBlink();
    }

    private void restartBlink() {
        stop(blink);
        caret.setOpacity(1);
        if (Motion.isReduced())
            return;
        blink = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(caret.opacityProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(520), new KeyValue(caret.opacityProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(680), new KeyValue(caret.opacityProperty(), 0.05, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1020), new KeyValue(caret.opacityProperty(), 0.05, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1180), new KeyValue(caret.opacityProperty(), 1, Interpolator.EASE_BOTH)));
        blink.setCycleCount(Animation.INDEFINITE);
        blink.play();
    }

    private Color color() {
        return editor.getStyleClass().contains("code-caret-active")
                ? AppSettings.getInstance().codeThemeProperty().get().getCaretColor()
                : TEXT_COLOR;
    }

    private static void stop(Timeline t) {
        if (t != null)
            t.stop();
    }
}