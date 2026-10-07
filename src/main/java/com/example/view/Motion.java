package com.example.view;

import java.util.List;
import java.util.prefs.Preferences;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.transform.Scale;
import javafx.stage.Window;
import javafx.util.Duration;

public final class Motion {
    private static final String PREF_KEY = "reduceMotion";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Motion.class);
    private static final String INTERACTIVE_KEY = "motion.interactive";
    private static final String TIMELINE_KEY = "motion.timeline";

    private static final String FADE_KEY = "motion.fade";

    private static boolean reduced = PREFS.getBoolean(PREF_KEY, false);
    private static boolean quiet;

    private static final BooleanProperty SMOOTH_CARET = new SimpleBooleanProperty(
            PREFS.getBoolean("smoothCaret", false));

    static {
        SMOOTH_CARET.addListener((obs, was, is) -> PREFS.putBoolean("smoothCaret", is));
    }

    public static final Interpolator EASE_OUT = new Interpolator() {
        @Override
        protected double curve(double t) {
            double u = 1 - t;
            return 1 - u * u * u;
        }
    };

    public static final Interpolator OVERSHOOT = new Interpolator() {
        private static final double C1 = 1.3;

        @Override
        protected double curve(double t) {
            double u = t - 1;
            return 1 + (C1 + 1) * u * u * u + C1 * u * u;
        }
    };

    public static final Interpolator SPRING_SOFT = spring(5.85, 1.5 * Math.PI);

    public static final Interpolator SPRING_BOUNCY = spring(7.5, 2.5 * Math.PI);

    private Motion() {
    }

    public static BooleanProperty smoothCaretProperty() {
        return SMOOTH_CARET;
    }

    public static boolean isReduced() {
        return reduced;
    }

    public static void setReduced(boolean value) {
        reduced = value;
        PREFS.putBoolean(PREF_KEY, value);
    }

    public static void setQuiet(boolean value) {
        quiet = value;
    }

    private static boolean skip() {
        return reduced || quiet;
    }

    private static Interpolator spring(double damping, double frequency) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                if (t <= 0)
                    return 0;
                if (t >= 1)
                    return 1;
                return 1 - Math.exp(-damping * t) * Math.cos(frequency * t);
            }
        };
    }

    public static void popIn(Node node) {
        popIn(node, 0);
    }

    public static void popIn(Node node, int delayMs) {
        if (skip()) {
            node.setOpacity(1);
            node.setScaleX(1);
            node.setScaleY(1);
            return;
        }
        node.setOpacity(0);
        node.setScaleX(0.92);
        node.setScaleY(0.92);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 200), new KeyValue(node.opacityProperty(), 1, EASE_OUT)),
                new KeyFrame(Duration.millis(delayMs + 380),
                        new KeyValue(node.scaleXProperty(), 1, SPRING_BOUNCY),
                        new KeyValue(node.scaleYProperty(), 1, SPRING_BOUNCY)));
        t.play();
    }

    public static void spawn(Node node) {
        if (skip()) {
            node.setOpacity(1);
            node.setScaleX(1);
            node.setScaleY(1);
            return;
        }
        node.setOpacity(0);
        node.setScaleX(0.5);
        node.setScaleY(0.5);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(140), new KeyValue(node.opacityProperty(), 1, EASE_OUT)),
                new KeyFrame(Duration.millis(480),
                        new KeyValue(node.scaleXProperty(), 1, SPRING_BOUNCY),
                        new KeyValue(node.scaleYProperty(), 1, SPRING_BOUNCY)));
        t.play();
    }

    public static void fadeSlideIn(Node node, double dy, int delayMs) {
        if (skip()) {
            node.setOpacity(1);
            node.setTranslateY(0);
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(dy);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 180), new KeyValue(node.opacityProperty(), 1, EASE_OUT)),
                new KeyFrame(Duration.millis(delayMs + 360), new KeyValue(node.translateYProperty(), 0, SPRING_SOFT)));
        t.play();
    }

    public static void stagger(List<? extends Node> nodes, int stepMs) {
        int i = 0;
        for (Node n : nodes) {
            fadeSlideIn(n, 10, Math.min(i * stepMs, 360));
            i++;
        }
    }

    public static void fadeIn(Node node, int ms) {
        if (skip()) {
            node.setOpacity(1);
            return;
        }
        node.setOpacity(0);
        new Timeline(new KeyFrame(Duration.millis(ms), new KeyValue(node.opacityProperty(), 1, EASE_OUT))).play();
    }

    public static void fadeOutThen(Node node, Runnable after) {
        if (reduced) {
            after.run();
            return;
        }
        Timeline t = new Timeline(new KeyFrame(Duration.millis(140),
                new KeyValue(node.opacityProperty(), 0, EASE_OUT),
                new KeyValue(node.scaleXProperty(), 0.97, EASE_OUT),
                new KeyValue(node.scaleYProperty(), 0.97, EASE_OUT)));
        t.setOnFinished(e -> after.run());
        t.play();
    }

    public static void fadeVisible(Node node, boolean show) {
        fadeVisible(node, show, false);
    }

    public static void fadeVisible(Node node, boolean show, boolean immediate) {
        Object old = node.getProperties().get(FADE_KEY);
        if (old instanceof Animation a)
            a.stop();

        if (immediate || reduced) {
            node.setOpacity(1);
            node.setVisible(show);
            return;
        }
        if (show) {
            if (!node.isVisible())
                node.setOpacity(0);
            node.setVisible(true);
            Timeline t = new Timeline(
                    new KeyFrame(Duration.millis(160), new KeyValue(node.opacityProperty(), 1, EASE_OUT)));
            node.getProperties().put(FADE_KEY, t);
            t.play();
        } else {
            if (!node.isVisible()) {
                node.setOpacity(1);
                return;
            }
            Timeline t = new Timeline(
                    new KeyFrame(Duration.millis(140), new KeyValue(node.opacityProperty(), 0, EASE_OUT)));
            t.setOnFinished(e -> {
                node.setVisible(false);
                node.setOpacity(1);
            });
            node.getProperties().put(FADE_KEY, t);
            t.play();
        }
    }

    public static void installPopupAnimations() {
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next())
                if (c.wasAdded())
                    for (Window w : c.getAddedSubList())
                        if (w instanceof ContextMenu menu)
                            popMenu(menu);
        });
    }

    private static void popMenu(ContextMenu menu) {
        if (reduced || menu.getScene() == null)
            return;
        Node root = menu.getScene().getRoot();
        Scale s = new Scale(0.94, 0.94, 0, 0);
        root.getTransforms().add(s);
        root.setOpacity(0);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(140), new KeyValue(root.opacityProperty(), 1, EASE_OUT)),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(s.xProperty(), 1, SPRING_SOFT),
                        new KeyValue(s.yProperty(), 1, SPRING_SOFT)));
        t.setOnFinished(e -> {
            root.getTransforms().remove(s);
            root.setOpacity(1);
        });
        t.play();
    }

    public static void interactive(Node node) {
        if (node.getProperties().putIfAbsent(INTERACTIVE_KEY, Boolean.TRUE) != null)
            return;
        node.hoverProperty().addListener((obs, was, is) -> settle(node));
        node.pressedProperty().addListener((obs, was, is) -> settle(node));
        node.disableProperty().addListener((obs, was, is) -> settle(node));
    }

    private static void settle(Node node) {
        boolean active = !node.isDisable();
        double scale = active && node.isPressed() ? 0.96 : active && node.isHover() ? 1.03 : 1;
        double lift = active && node.isHover() && !node.isPressed() ? -1.5 : 0;

        Object old = node.getProperties().get(TIMELINE_KEY);
        if (old instanceof Animation a)
            a.stop();

        if (reduced) {
            node.setScaleX(scale);
            node.setScaleY(scale);
            node.setTranslateY(lift);
            return;
        }
        Timeline t = new Timeline(new KeyFrame(Duration.millis(node.isPressed() ? 90 : 300),
                new KeyValue(node.scaleXProperty(), scale, SPRING_BOUNCY),
                new KeyValue(node.scaleYProperty(), scale, SPRING_BOUNCY),
                new KeyValue(node.translateYProperty(), lift, SPRING_BOUNCY)));
        node.getProperties().put(TIMELINE_KEY, t);
        t.play();
    }
}