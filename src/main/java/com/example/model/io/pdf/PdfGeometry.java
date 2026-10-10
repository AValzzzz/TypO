package com.example.model.io.pdf;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.CornerRadii;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.scene.shape.VLineTo;

final class PdfGeometry {
    private static final double KAPPA = 0.5522847498;

    private PdfGeometry() {
    }

    static List<PathElement> of(Shape s) {
        return switch (s) {
            case Rectangle r -> roundRect(r.getX(), r.getY(), r.getWidth(), r.getHeight(),
                    Math.min(r.getArcWidth(), r.getArcHeight()) / 2);
            case Circle c -> ellipse(c.getCenterX(), c.getCenterY(), c.getRadius(), c.getRadius());
            case Ellipse e -> ellipse(e.getCenterX(), e.getCenterY(), e.getRadiusX(), e.getRadiusY());
            case Line l -> List.of(new MoveTo(l.getStartX(), l.getStartY()), new LineTo(l.getEndX(), l.getEndY()));
            case Polygon p -> polyline(p.getPoints(), true);
            case Polyline p -> polyline(p.getPoints(), false);
            case QuadCurve q -> List.of(new MoveTo(q.getStartX(), q.getStartY()),
                    new QuadCurveTo(q.getControlX(), q.getControlY(), q.getEndX(), q.getEndY()));
            case CubicCurve c -> List.of(new MoveTo(c.getStartX(), c.getStartY()),
                    new CubicCurveTo(c.getControlX1(), c.getControlY1(), c.getControlX2(), c.getControlY2(),
                            c.getEndX(), c.getEndY()));
            case Path p -> p.getElements().stream().allMatch(PdfGeometry::supported) ? p.getElements() : null;
            default -> null;
        };
    }

    static List<PathElement> forStroke(Shape s, List<PathElement> geometry) {
        if (s.getStrokeType() == StrokeType.CENTERED)
            return geometry;
        double d = (s.getStrokeType() == StrokeType.INSIDE ? -1 : 1) * s.getStrokeWidth() / 2;
        return switch (s) {
            case Rectangle r -> roundRect(r.getX() - d, r.getY() - d, r.getWidth() + 2 * d, r.getHeight() + 2 * d,
                    Math.max(0, Math.min(r.getArcWidth(), r.getArcHeight()) / 2 + d));
            case Circle c -> ellipse(c.getCenterX(), c.getCenterY(), c.getRadius() + d, c.getRadius() + d);
            case Ellipse e -> ellipse(e.getCenterX(), e.getCenterY(), e.getRadiusX() + d, e.getRadiusY() + d);
            default -> geometry;
        };
    }

    private static boolean supported(PathElement e) {
        return e instanceof MoveTo || e instanceof LineTo || e instanceof HLineTo || e instanceof VLineTo
                || e instanceof QuadCurveTo || e instanceof CubicCurveTo || e instanceof ClosePath;
    }

    static List<PathElement> rect(double x, double y, double w, double h) {
        return List.of(new MoveTo(x, y), new LineTo(x + w, y), new LineTo(x + w, y + h), new LineTo(x, y + h),
                new ClosePath());
    }

    static List<PathElement> roundRect(double x, double y, double w, double h, double r) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0)
            return rect(x, y, w, h);
        double k = r * (1 - KAPPA);
        return List.of(new MoveTo(x + r, y), new LineTo(x + w - r, y),
                new CubicCurveTo(x + w - k, y, x + w, y + k, x + w, y + r), new LineTo(x + w, y + h - r),
                new CubicCurveTo(x + w, y + h - k, x + w - k, y + h, x + w - r, y + h), new LineTo(x + r, y + h),
                new CubicCurveTo(x + k, y + h, x, y + h - k, x, y + h - r), new LineTo(x, y + r),
                new CubicCurveTo(x, y + k, x + k, y, x + r, y), new ClosePath());
    }

    static List<PathElement> ellipse(double cx, double cy, double rx, double ry) {
        double kx = rx * KAPPA, ky = ry * KAPPA;
        return List.of(new MoveTo(cx + rx, cy),
                new CubicCurveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
                new CubicCurveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
                new CubicCurveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
                new CubicCurveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy), new ClosePath());
    }

    static List<PathElement> polyline(List<Double> pts, boolean closed) {
        List<PathElement> out = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i += 2)
            out.add(i == 0 ? new MoveTo(pts.get(i), pts.get(i + 1)) : new LineTo(pts.get(i), pts.get(i + 1)));
        if (closed && !out.isEmpty())
            out.add(new ClosePath());
        return out;
    }

    static double radius(CornerRadii r, double w, double h) {
        if (r == null || r.isUniform() && r.getTopLeftHorizontalRadius() == 0)
            return 0;
        double v = r.getTopLeftHorizontalRadius();
        return r.isTopLeftHorizontalRadiusAsPercentage() ? v * Math.min(w, h) : v;
    }
}
