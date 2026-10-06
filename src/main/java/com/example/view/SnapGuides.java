package com.example.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;

public class SnapGuides {
    public record Rect(double x, double y, double w, double h) {
    }

    private static final double THRESHOLD_PX = 5;
    private static final Color GUIDE_COLOR = Color.web("#FF2D75");

    private final Pane pane;
    private final Function<Node, List<Rect>> others;
    private final Group layer = new Group();
    private Timeline fade;

    public SnapGuides(Pane pane, Function<Node, List<Rect>> others) {
        this.pane = pane;
        this.others = others;
        layer.setMouseTransparent(true);
        layer.setManaged(false);
        layer.getProperties().put("noExport", Boolean.TRUE);
        pane.getChildren().add(layer);
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> release());
    }

    public void toFront() {
        layer.toFront();
    }

    public void clear() {
        stopFade();
        if (!layer.getChildren().isEmpty())
            layer.getChildren().clear();
    }

    private void stopFade() {
        if (fade != null) {
            fade.stop();
            fade = null;
        }
        layer.setOpacity(1);
    }

    private void release() {
        if (layer.getChildren().isEmpty())
            return;
        if (Motion.isReduced()) {
            clear();
            return;
        }
        stopFade();
        fade = new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(layer.opacityProperty(), 0, Motion.EASE_OUT)));
        fade.setOnFinished(e -> {
            fade = null;
            layer.getChildren().clear();
            layer.setOpacity(1);
        });
        fade.play();
    }

    public static Rect rectOf(Node n) {
        if (n instanceof ShapeOverlay s)
            return new Rect(s.getLayoutX(), s.getLayoutY(), s.getShapeWidth(), s.getShapeHeight());
        if (n instanceof ImageOverlay i)
            return new Rect(i.getLayoutX(), i.getLayoutY(), i.getImageWidth(), i.getImageHeight());
        if (n instanceof TableOverlay t)
            return new Rect(t.getLayoutX(), t.getLayoutY(), t.prefWidth(-1), t.prefHeight(-1));
        if (n instanceof TextBoxOverlay b)
            return new Rect(b.getLayoutX(), b.getLayoutY(), b.getBoxWidth(), b.prefHeight(-1));
        return null;
    }

    public double[] move(Node self, double x, double y, boolean disabled) {
        Rect r = rectOf(self);
        if (disabled || r == null) {
            clear();
            return new double[] { x, y };
        }
        List<Rect> others = this.others.apply(self);
        double thr = threshold();
        List<Double> xt = targets(others, pane.getWidth(), true);
        List<Double> yt = targets(others, pane.getHeight(), false);

        double[] bx = best(new double[] { x, x + r.w() / 2, x + r.w() }, xt, thr);
        double[] by = best(new double[] { y, y + r.h() / 2, y + r.h() }, yt, thr);
        double nx = bx == null ? x : x + bx[0];
        double ny = by == null ? y : y + by[0];

        show(new double[] { nx, nx + r.w() / 2, nx + r.w() }, xt,
                new double[] { ny, ny + r.h() / 2, ny + r.h() }, yt);
        return new double[] { nx, ny };
    }

    public double[] resize(Node self, double w, double h, boolean snapW, boolean snapH, boolean disabled) {
        if (disabled) {
            clear();
            return new double[] { w, h };
        }
        double x = self.getLayoutX();
        double y = self.getLayoutY();
        List<Rect> others = this.others.apply(self);
        double thr = threshold();
        List<Double> xt = targets(others, pane.getWidth(), true);
        List<Double> yt = targets(others, pane.getHeight(), false);

        double nw = w, nh = h;
        double[] ownX = new double[0], ownY = new double[0];
        if (snapW) {
            double[] b = best(new double[] { x + w / 2, x + w }, xt, thr);
            if (b != null)
                nw = b[1] == 0 ? w + 2 * b[0] : w + b[0];
            ownX = new double[] { x + nw / 2, x + nw };
        }
        if (snapH) {
            double[] b = best(new double[] { y + h / 2, y + h }, yt, thr);
            if (b != null)
                nh = b[1] == 0 ? h + 2 * b[0] : h + b[0];
            ownY = new double[] { y + nh / 2, y + nh };
        }
        show(ownX, xt, ownY, yt);
        return new double[] { nw, nh };
    }

    private double scale() {
        var a = pane.localToScene(0, 0);
        var b = pane.localToScene(1, 0);
        double s = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
        return s > 0.0001 ? s : 1;
    }

    private double threshold() {
        return THRESHOLD_PX / scale();
    }

    private static List<Double> targets(List<Rect> others, double pageSize, boolean horizontal) {
        List<Double> t = new ArrayList<>();
        t.add(0.0);
        t.add(pageSize / 2);
        t.add(pageSize);
        for (Rect o : others) {
            double start = horizontal ? o.x() : o.y();
            double size = horizontal ? o.w() : o.h();
            t.add(start);
            t.add(start + size / 2);
            t.add(start + size);
        }
        return t;
    }

    private static double[] best(double[] own, List<Double> targets, double thr) {
        double[] best = null;
        for (int i = 0; i < own.length; i++) {
            for (double t : targets) {
                double d = t - own[i];
                if (Math.abs(d) <= thr && (best == null || Math.abs(d) < Math.abs(best[0])))
                    best = new double[] { d, i };
            }
        }
        return best;
    }

    private void show(double[] ownX, List<Double> xt, double[] ownY, List<Double> yt) {
        clear();
        double width = 1 / scale();
        List<Double> drawn = new ArrayList<>();
        for (double t : xt) {
            if (matches(ownX, t) && !contains(drawn, t)) {
                drawn.add(t);
                addLine(t, 0, t, pane.getHeight(), width);
            }
        }
        drawn.clear();
        for (double t : yt) {
            if (matches(ownY, t) && !contains(drawn, t)) {
                drawn.add(t);
                addLine(0, t, pane.getWidth(), t, width);
            }
        }
    }

    private static boolean matches(double[] own, double target) {
        for (double p : own)
            if (Math.abs(p - target) < 0.01)
                return true;
        return false;
    }

    private static boolean contains(List<Double> values, double v) {
        for (double d : values)
            if (Math.abs(d - v) < 0.01)
                return true;
        return false;
    }

    private void addLine(double x1, double y1, double x2, double y2, double width) {
        Line line = new Line(x1, y1, x2, y2);
        line.setStroke(GUIDE_COLOR);
        line.setStrokeWidth(width);
        layer.getChildren().add(line);
    }
}