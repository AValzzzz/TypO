package com.example.model.io.pdf;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.VLineTo;
import javafx.scene.transform.Transform;

final class PdfCanvas {
    private final PDPageContentStream cs;
    private final Transform sceneToPage;
    private final Map<String, PDExtendedGraphicsState> alphas;

    PdfCanvas(PDPageContentStream cs, Transform sceneToPage, Map<String, PDExtendedGraphicsState> alphas) {
        this.cs = cs;
        this.sceneToPage = sceneToPage;
        this.alphas = alphas;
    }

    PDPageContentStream stream() {
        return cs;
    }

    Transform sceneToPage() {
        return sceneToPage;
    }

    Transform toPage(Node n) {
        return sceneToPage.createConcatenation(n.getLocalToSceneTransform());
    }

    void save() throws IOException {
        cs.saveGraphicsState();
    }

    void restore() throws IOException {
        cs.restoreGraphicsState();
    }

    void clip(List<PathElement> geometry, Transform t) throws IOException {
        save();
        path(geometry, t);
        cs.clip();
    }

    void fill(Color c, List<PathElement> geometry, Transform t, FillRule rule) throws IOException {
        save();
        alpha(c.getOpacity(), 1);
        cs.setNonStrokingColor(awt(c));
        path(geometry, t);
        fill(rule);
        restore();
    }

    void fill(FillRule rule) throws IOException {
        if (rule == FillRule.EVEN_ODD)
            cs.fillEvenOdd();
        else
            cs.fill();
    }

    void fillColor(Color c) throws IOException {
        cs.setNonStrokingColor(awt(c));
    }

    void strokeColor(Color c) throws IOException {
        cs.setStrokingColor(awt(c));
    }

    void alpha(double fill, double stroke) throws IOException {
        if (fill >= 0.999 && stroke >= 0.999)
            return;
        String key = Math.round(fill * 1000) + "/" + Math.round(stroke * 1000);
        PDExtendedGraphicsState gs = alphas.computeIfAbsent(key, k -> {
            PDExtendedGraphicsState s = new PDExtendedGraphicsState();
            s.setNonStrokingAlphaConstant((float) fill);
            s.setStrokingAlphaConstant((float) stroke);
            return s;
        });
        cs.setGraphicsStateParameters(gs);
    }

    void dash(List<Double> array, double offset, double scale) throws IOException {
        if (array == null || array.isEmpty())
            return;
        float[] d = new float[array.size()];
        for (int i = 0; i < d.length; i++)
            d[i] = (float) (array.get(i) * scale);
        cs.setLineDashPattern(d, (float) (offset * scale));
    }

    void path(List<PathElement> elements, Transform t) throws IOException {
        double cx = 0, cy = 0, sx = 0, sy = 0;
        for (PathElement e : elements) {
            boolean rel = !e.isAbsolute();
            double ox = rel ? cx : 0, oy = rel ? cy : 0;
            switch (e) {
                case MoveTo m -> {
                    cx = sx = ox + m.getX();
                    cy = sy = oy + m.getY();
                    Point2D p = t.transform(cx, cy);
                    cs.moveTo((float) p.getX(), (float) p.getY());
                }
                case LineTo l -> {
                    cx = ox + l.getX();
                    cy = oy + l.getY();
                    lineTo(t, cx, cy);
                }
                case HLineTo h -> {
                    cx = ox + h.getX();
                    lineTo(t, cx, cy);
                }
                case VLineTo v -> {
                    cy = oy + v.getY();
                    lineTo(t, cx, cy);
                }
                case QuadCurveTo q -> {
                    double qx = ox + q.getControlX(), qy = oy + q.getControlY();
                    double ex = ox + q.getX(), ey = oy + q.getY();
                    curveTo(t, cx + 2.0 / 3 * (qx - cx), cy + 2.0 / 3 * (qy - cy),
                            ex + 2.0 / 3 * (qx - ex), ey + 2.0 / 3 * (qy - ey), ex, ey);
                    cx = ex;
                    cy = ey;
                }
                case CubicCurveTo c -> {
                    double ex = ox + c.getX(), ey = oy + c.getY();
                    curveTo(t, ox + c.getControlX1(), oy + c.getControlY1(), ox + c.getControlX2(),
                            oy + c.getControlY2(), ex, ey);
                    cx = ex;
                    cy = ey;
                }
                case ClosePath c -> {
                    cs.closePath();
                    cx = sx;
                    cy = sy;
                }
                default -> {
                }
            }
        }
    }

    private void lineTo(Transform t, double x, double y) throws IOException {
        Point2D p = t.transform(x, y);
        cs.lineTo((float) p.getX(), (float) p.getY());
    }

    private void curveTo(Transform t, double x1, double y1, double x2, double y2, double x3, double y3)
            throws IOException {
        Point2D a = t.transform(x1, y1);
        Point2D b = t.transform(x2, y2);
        Point2D c = t.transform(x3, y3);
        cs.curveTo((float) a.getX(), (float) a.getY(), (float) b.getX(), (float) b.getY(), (float) c.getX(),
                (float) c.getY());
    }

    static Matrix matrix(Transform t) {
        return new Matrix((float) t.getMxx(), (float) t.getMyx(), (float) t.getMxy(), (float) t.getMyy(),
                (float) t.getTx(), (float) t.getTy());
    }

    static double scaleOf(Transform t) {
        return Math.sqrt(Math.abs(t.getMxx() * t.getMyy() - t.getMxy() * t.getMyx()));
    }

    static Color color(Paint p, double op) {
        Color c = switch (p) {
            case Color col -> col;
            case LinearGradient g when !g.getStops().isEmpty() -> g.getStops().get(0).getColor();
            case RadialGradient g when !g.getStops().isEmpty() -> g.getStops().get(0).getColor();
            case null, default -> null;
        };
        if (c == null || c.getOpacity() * op <= 0.001)
            return null;
        return op >= 1 ? c : Color.color(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity() * op);
    }

    private static java.awt.Color awt(Color c) {
        return new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }
}
