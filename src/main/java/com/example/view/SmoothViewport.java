package com.example.view;

import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.StackPane;

public final class SmoothViewport {
    private static final double MIN_SCALE = 0.8;
    private static final double MAX_SCALE = 3.0;
    private static final double ZOOM_SENSITIVITY = 0.002;
    private static final double PAN_SENSITIVITY = 1.5;
    private static final double PAN_MARGIN = 45;
    private static final double TAU = 0.08;

    private final StackPane viewport;
    private final Node content;
    private final ScrollBar hBar, vBar;

    private double targetScale = 1, targetX, targetY;
    private boolean updatingBars;
    private boolean running;
    private long last;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            step(now);
        }
    };

    public SmoothViewport(StackPane viewport, Node content, ScrollBar hBar, ScrollBar vBar) {
        this.viewport = viewport;
        this.content = content;
        this.hBar = hBar;
        this.vBar = vBar;
    }

    public void install() {
        viewport.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                zoomAt(e.getSceneX(), e.getSceneY(), Math.exp(e.getDeltaY() * ZOOM_SENSITIVITY));
            } else if (e.isShiftDown()) {
                double dx = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
                panBy(dx * PAN_SENSITIVITY, 0);
            } else {
                panBy(e.getDeltaX() * PAN_SENSITIVITY, e.getDeltaY() * PAN_SENSITIVITY);
            }
            e.consume();
        });
        viewport.addEventFilter(ZoomEvent.ZOOM, e -> {
            zoomAt(e.getSceneX(), e.getSceneY(), e.getZoomFactor());
            e.consume();
        });

        hBar.valueProperty().addListener((obs, o, n) -> {
            if (!updatingBars)
                jumpTo(-n.doubleValue(), content.getTranslateY());
        });
        vBar.valueProperty().addListener((obs, o, n) -> {
            if (!updatingBars)
                jumpTo(content.getTranslateX(), -n.doubleValue());
        });

        viewport.widthProperty().addListener((obs, o, n) -> refresh());
        viewport.heightProperty().addListener((obs, o, n) -> refresh());

        viewport.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null)
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        });
        if (viewport.getScene() != null)
            viewport.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    }

    private void onKey(KeyEvent e) {
        if (!e.isShortcutDown() || e.isAltDown())
            return;
        switch (e.getCode()) {
            case DIGIT0, NUMPAD0 -> resetZoom();
            case EQUALS, PLUS, ADD -> zoomAtCenter(1.2);
            case MINUS, SUBTRACT -> zoomAtCenter(1 / 1.2);
            default -> {
                return;
            }
        }
        e.consume();
    }

    public void panBy(double dx, double dy) {
        targetX += dx;
        targetY += dy;
        clampTargets();
        kick();
    }

    public void scrollTo(double x, double y) {
        targetX = x;
        targetY = y;
        clampTargets();
        kick();
    }

    public void zoomAt(double sceneX, double sceneY, double factor) {
        double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale * factor));
        if (newScale == targetScale)
            return;
        Point2D p = viewport.sceneToLocal(sceneX, sceneY);
        if (p == null)
            return;

        Bounds lb = content.getLayoutBounds();
        double cx = content.getLayoutX() + lb.getMinX() + lb.getWidth() / 2;
        double cy = content.getLayoutY() + lb.getMinY() + lb.getHeight() / 2;
        double k = 1 - newScale / targetScale;
        targetX += k * (p.getX() - cx - targetX);
        targetY += k * (p.getY() - cy - targetY);
        targetScale = newScale;
        clampTargets();
        kick();
    }

    public void zoomAtCenter(double factor) {
        Point2D c = viewport.localToScene(viewport.getWidth() / 2, viewport.getHeight() / 2);
        zoomAt(c.getX(), c.getY(), factor);
    }

    public void resetZoom() {
        zoomAtCenter(1 / targetScale);
    }

    public void refresh() {
        if (!running) {
            targetScale = content.getScaleX();
            targetX = content.getTranslateX();
            targetY = content.getTranslateY();
        }
        clampTargets();
        if (!running) {
            content.setTranslateX(targetX);
            content.setTranslateY(targetY);
        }
        updateBars();
    }

    private void jumpTo(double x, double y) {
        stopTimer();
        targetScale = content.getScaleX();
        targetX = x;
        targetY = y;
        clampTargets();
        content.setTranslateX(targetX);
        content.setTranslateY(targetY);
        updateBars();
    }

    private void kick() {
        if (Motion.isReduced()) {
            stopTimer();
            apply(targetScale, targetX, targetY);
            return;
        }
        if (!running) {
            running = true;
            last = 0;
            timer.start();
        }
    }

    private void stopTimer() {
        if (running) {
            timer.stop();
            running = false;
            last = 0;
        }
    }

    private void step(long now) {
        if (last == 0) {
            last = now;
            return;
        }
        double dt = Math.min((now - last) / 1e9, 0.05);
        last = now;
        double a = 1 - Math.exp(-dt / TAU);

        double s = content.getScaleX() + (targetScale - content.getScaleX()) * a;
        double x = content.getTranslateX() + (targetX - content.getTranslateX()) * a;
        double y = content.getTranslateY() + (targetY - content.getTranslateY()) * a;

        boolean done = Math.abs(targetScale - s) < 0.0005 && Math.abs(targetX - x) < 0.1
                && Math.abs(targetY - y) < 0.1;
        if (done) {
            s = targetScale;
            x = targetX;
            y = targetY;
        }
        apply(s, x, y);
        if (done)
            stopTimer();
    }

    private void apply(double s, double x, double y) {
        content.setScaleX(s);
        content.setScaleY(s);
        content.setTranslateX(x);
        content.setTranslateY(y);
        updateBars();
    }

    private void clampTargets() {
        double sw = content.prefWidth(-1) * targetScale;
        double sh = content.prefHeight(-1) * targetScale;
        targetX = clamp(targetX, maxTranslate(sw, viewport.getWidth()));
        targetY = clamp(targetY, maxTranslate(sh, viewport.getHeight()));
    }

    private void updateBars() {
        double vw = viewport.getWidth(), vh = viewport.getHeight();
        double sw = content.prefWidth(-1) * content.getScaleX();
        double sh = content.prefHeight(-1) * content.getScaleY();
        double maxX = maxTranslate(sw, vw), maxY = maxTranslate(sh, vh);

        updatingBars = true;
        try {
            configure(hBar, maxX, vw, sw, content.getTranslateX());
            configure(vBar, maxY, vh, sh, content.getTranslateY());
        } finally {
            updatingBars = false;
        }
    }

    private static void configure(ScrollBar bar, double max, double viewportSize, double scaled, double translate) {
        bar.setMin(-max);
        bar.setMax(max);
        bar.setVisibleAmount(max > 0
                ? Math.min(2 * max, viewportSize * (2 * max) / Math.max(scaled, 1))
                : 2 * max);
        bar.setValue(-translate);
        bar.setDisable(max <= 0);
    }

    private static double maxTranslate(double scaled, double viewportSize) {
        return Math.max(0, (scaled - viewportSize) / 2.0) + PAN_MARGIN;
    }

    private static double clamp(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }
}