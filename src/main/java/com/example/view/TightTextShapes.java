package com.example.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fxmisc.richtext.BackgroundPath;
import org.fxmisc.richtext.SelectionPath;
import org.fxmisc.richtext.UnderlinePath;

import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

final class TightTextShapes {
    private static final String INSTALLED = "tightShapes.installed";
    private static final Map<Font, double[]> METRICS = new HashMap<>();

    private final TextFlow flow;
    private boolean adjusting;

    private TightTextShapes(TextFlow flow) {
        this.flow = flow;
    }

    static void track(Node segment) {
        segment.parentProperty().addListener((obs, old, parent) -> {
            if (parent instanceof TextFlow f && f.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) == null)
                new TightTextShapes(f).install();
        });
    }

    private void install() {
        flow.getChildren().forEach(this::watch);
        flow.getChildren().addListener((ListChangeListener<Node>) c -> {
            while (c.next())
                c.getAddedSubList().forEach(this::watch);
        });
    }

    private void watch(Node n) {
        if (!(n instanceof SelectionPath || n instanceof BackgroundPath || n instanceof UnderlinePath))
            return;
        Path path = (Path) n;
        if (path.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null)
            return;
        path.getElements().addListener((ListChangeListener<PathElement>) c -> refit(path));
        refit(path);
    }

    private void refit(Path path) {
        if (adjusting || path.getElements().isEmpty())
            return;
        adjusting = true;
        try {
            Map<Double, Line> lines = measure();
            List<PathElement> fitted = path instanceof UnderlinePath
                    ? underline(path.getElements(), lines)
                    : boxes(path.getElements(), lines);
            if (fitted != null)
                path.getElements().setAll(fitted);
        } finally {
            adjusting = false;
        }
    }

    private record Piece(double x1, double x2, double top, double bottom, double underline) {
    }

    private static final class Line {
        final double top, bottom;
        double ascent;
        final List<double[]> runs = new ArrayList<>(); 
        final List<Piece> pieces = new ArrayList<>();

        Line(double top, double bottom) {
            this.top = top;
            this.bottom = bottom;
        }
    }

    @SuppressWarnings("deprecation")
    private Map<Double, Line> measure() {
        Map<Double, Line> lines = new TreeMap<>();
        List<Object[]> objects = new ArrayList<>();
        Insets in = flow.getInsets();
        int offset = 0;
        for (Node n : flow.getChildren()) {
            if (!n.isManaged())
                continue;
            if (n instanceof Text t) {
                int len = t.getText().length();
                double[] m = metrics(t.getFont());
                for (double[] r : rects(flow.rangeShape(offset, offset + len)))
                    line(lines, r).runs.add(new double[] { r[0], r[2], m[0], m[1] });
                offset += len;
            } else {
                double[][] r = rects(flow.rangeShape(offset, offset + 1));
                if (r.length > 0)
                    objects.add(new Object[] { n, line(lines, r[0]) });
                offset += 1;
            }
        }
        for (Line l : lines.values())
            for (double[] r : l.runs)
                l.ascent = Math.max(l.ascent, r[2]);
        for (Object[] o : objects) {
            Node n = (Node) o[0];
            Line l = (Line) o[1];
            double baseline = n.getLayoutY() - in.getTop() + n.getBaselineOffset();
            l.ascent = Math.max(l.ascent, baseline - l.top);
        }
        for (Line l : lines.values()) {
            double baseline = l.top + l.ascent;
            for (double[] r : l.runs)
                l.pieces.add(new Piece(r[0], r[1], baseline - r[2], baseline + r[3],
                        baseline + Math.max(1, r[3] * 0.3)));
        }
        for (Object[] o : objects) {
            Bounds b = ((Node) o[0]).getBoundsInParent();
            ((Line) o[1]).pieces.add(new Piece(b.getMinX() - in.getLeft(), b.getMaxX() - in.getLeft(),
                    b.getMinY() - in.getTop(), b.getMaxY() - in.getTop(), Double.NaN));
        }
        return lines;
    }

    private static Line line(Map<Double, Line> lines, double[] r) {
        double key = Math.round(r[1] * 100) / 100.0;
        return lines.computeIfAbsent(key, k -> new Line(r[1], r[3]));
    }

    private static double[] metrics(Font font) {
        return METRICS.computeIfAbsent(font, f -> {
            Text probe = new Text("Hg");
            probe.setFont(f);
            double ascent = probe.getBaselineOffset();
            return new double[] { ascent, probe.getLayoutBounds().getHeight() - ascent };
        });
    }

    private static double[][] rects(PathElement[] elements) {
        return rects(List.of(elements));
    }

    private static double[][] rects(List<PathElement> elements) {
        List<double[]> out = new ArrayList<>();
        double[] cur = null;
        for (PathElement e : elements) {
            double x, y;
            if (e instanceof MoveTo m) {
                x = m.getX();
                y = m.getY();
                cur = new double[] { x, y, x, y };
                out.add(cur);
                continue;
            } else if (e instanceof LineTo l && cur != null) {
                x = l.getX();
                y = l.getY();
            } else {
                continue;
            }
            cur[0] = Math.min(cur[0], x);
            cur[1] = Math.min(cur[1], y);
            cur[2] = Math.max(cur[2], x);
            cur[3] = Math.max(cur[3], y);
        }
        return out.toArray(double[][]::new);
    }

    private static Line lineAt(Map<Double, Line> lines, double y) {
        for (Line l : lines.values())
            if (y >= l.top - 1 && y <= l.bottom + 1)
                return l;
        return null;
    }


    private static List<PathElement> boxes(List<PathElement> original, Map<Double, Line> lines) {
        List<PathElement> out = new ArrayList<>();
        for (double[] r : rects(original)) {
            Line line = lineAt(lines, (r[1] + r[3]) / 2);
            boolean fitted = false;
            if (line != null) {
                for (Piece p : line.pieces) {
                    double x1 = Math.max(r[0], p.x1()), x2 = Math.min(r[2], p.x2());
                    if (x2 - x1 < 0.1)
                        continue;
                    rectangle(out, x1, p.top(), x2, p.bottom());
                    fitted = true;
                }
            }
            if (!fitted) 
                rectangle(out, r[0], r[1], r[2], r[3]);
        }
        return out;
    }

    private static List<PathElement> underline(List<PathElement> original, Map<Double, Line> lines) {
        List<PathElement> out = new ArrayList<>();
        for (double[] r : rects(original)) {
            Line line = lineAt(lines, r[1]);
            double y = Double.NaN;
            if (line != null)
                for (Piece p : line.pieces)
                    if (!Double.isNaN(p.underline()) && Math.min(r[2], p.x2()) - Math.max(r[0], p.x1()) > 0.1)
                        y = Double.isNaN(y) ? p.underline() : Math.max(y, p.underline());
            if (Double.isNaN(y))
                y = r[1];
            out.add(new MoveTo(r[0], y));
            out.add(new LineTo(r[2], y));
        }
        return out;
    }

    private static void rectangle(List<PathElement> out, double x1, double y1, double x2, double y2) {
        out.add(new MoveTo(x1, y1));
        out.add(new LineTo(x2, y1));
        out.add(new LineTo(x2, y2));
        out.add(new LineTo(x1, y2));
        out.add(new LineTo(x1, y1));
    }
}
