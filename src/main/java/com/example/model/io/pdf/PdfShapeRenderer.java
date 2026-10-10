package com.example.model.io.pdf;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Transform;

final class PdfShapeRenderer {
    private PdfShapeRenderer() {
    }

    static void drawRegion(PdfCanvas canvas, Region r, double op) throws IOException {
        double w = r.getWidth(), h = r.getHeight();
        if (w <= 0 || h <= 0)
            return;
        Transform t = canvas.toPage(r);
        Background bg = r.getBackground();
        if (bg != null)
            for (BackgroundFill f : bg.getFills()) {
                Color c = PdfCanvas.color(f.getFill(), op);
                Insets in = f.getInsets();
                double fw = w - in.getLeft() - in.getRight(), fh = h - in.getTop() - in.getBottom();
                if (c == null || fw <= 0 || fh <= 0)
                    continue;
                canvas.fill(c, PdfGeometry.roundRect(in.getLeft(), in.getTop(), fw, fh,
                        PdfGeometry.radius(f.getRadii(), fw, fh)), t, FillRule.NON_ZERO);
            }
        Border border = r.getBorder();
        if (border != null)
            for (BorderStroke s : border.getStrokes())
                drawBorder(canvas, s, w, h, t, op);
    }

    private static void drawBorder(PdfCanvas canvas, BorderStroke s, double w, double h, Transform t, double op)
            throws IOException {
        Insets in = s.getInsets();
        double x = in.getLeft(), y = in.getTop();
        double bw = w - in.getLeft() - in.getRight(), bh = h - in.getTop() - in.getBottom();
        if (bw <= 0 || bh <= 0)
            return;
        double top = s.getWidths().getTop(), right = s.getWidths().getRight(),
                bottom = s.getWidths().getBottom(), left = s.getWidths().getLeft();
        Color ct = side(s.getTopStroke(), s.getTopStyle(), op), cr = side(s.getRightStroke(), s.getRightStyle(), op),
                cb = side(s.getBottomStroke(), s.getBottomStyle(), op),
                cl = side(s.getLeftStroke(), s.getLeftStyle(), op);
        double radius = PdfGeometry.radius(s.getRadii(), bw, bh);
        boolean uniform = ct != null && ct.equals(cr) && ct.equals(cb) && ct.equals(cl)
                && top == right && top == bottom && top == left;
        if (uniform && (radius > 0 || !s.getTopStyle().getDashArray().isEmpty())) {
            canvas.save();
            canvas.alpha(1, ct.getOpacity());
            canvas.strokeColor(ct);
            canvas.stream().setLineWidth((float) (top * PdfCanvas.scaleOf(t)));
            canvas.dash(s.getTopStyle().getDashArray(), s.getTopStyle().getDashOffset(), PdfCanvas.scaleOf(t));
            canvas.path(PdfGeometry.roundRect(x + top / 2, y + top / 2, bw - top, bh - top,
                    Math.max(0, radius - top / 2)), t);
            canvas.stream().stroke();
            canvas.restore();
            return;
        }
        sideRect(canvas, ct, x, y, bw, top, t);
        sideRect(canvas, cb, x, y + bh - bottom, bw, bottom, t);
        sideRect(canvas, cl, x, y + top, left, bh - top - bottom, t);
        sideRect(canvas, cr, x + bw - right, y + top, right, bh - top - bottom, t);
    }

    private static Color side(Paint p, BorderStrokeStyle style, double op) {
        return style == null || style == BorderStrokeStyle.NONE ? null : PdfCanvas.color(p, op);
    }

    private static void sideRect(PdfCanvas canvas, Color c, double x, double y, double w, double h, Transform t)
            throws IOException {
        if (c != null && w > 0 && h > 0)
            canvas.fill(c, PdfGeometry.rect(x, y, w, h), t, FillRule.NON_ZERO);
    }

    static void drawShape(PdfCanvas canvas, Shape s, double op) throws IOException {
        Color fill = PdfCanvas.color(s.getFill(), op);
        Color stroke = s.getStrokeWidth() > 0 ? PdfCanvas.color(s.getStroke(), op) : null;
        if (s instanceof Line || s instanceof Polyline)
            fill = null;
        if (fill == null && stroke == null)
            return;

        List<PathElement> geometry = PdfGeometry.of(s);
        if (geometry == null) {
            if (Shape.union(s, new Rectangle()) instanceof Path area)
                canvas.fill(fill != null ? fill : stroke, area.getElements(), canvas.sceneToPage(),
                        area.getFillRule());
            return;
        }

        Transform t = canvas.toPage(s);
        canvas.save();
        canvas.alpha(fill != null ? fill.getOpacity() : 1, stroke != null ? stroke.getOpacity() : 1);
        if (fill != null) {
            canvas.fillColor(fill);
            canvas.path(geometry, t);
            canvas.fill(s instanceof Path p ? p.getFillRule() : FillRule.NON_ZERO);
        }
        if (stroke != null) {
            applyStroke(canvas, s, stroke, PdfCanvas.scaleOf(t));
            canvas.path(PdfGeometry.forStroke(s, geometry), t);
            canvas.stream().stroke();
        }
        canvas.restore();
    }

    private static void applyStroke(PdfCanvas canvas, Shape s, Color stroke, double scale) throws IOException {
        PDPageContentStream cs = canvas.stream();
        canvas.strokeColor(stroke);
        cs.setLineWidth((float) (s.getStrokeWidth() * scale));
        cs.setLineCapStyle(switch (s.getStrokeLineCap()) {
            case BUTT -> 0;
            case ROUND -> 1;
            case null, default -> 2;
        });
        cs.setLineJoinStyle(switch (s.getStrokeLineJoin()) {
            case ROUND -> 1;
            case BEVEL -> 2;
            case null, default -> 0;
        });
        cs.setMiterLimit((float) Math.max(1, s.getStrokeMiterLimit()));
        canvas.dash(s.getStrokeDashArray(), s.getStrokeDashOffset(), scale);
    }
}
