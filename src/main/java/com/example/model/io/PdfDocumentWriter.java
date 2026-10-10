package com.example.model.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.SelectionPath;

import com.example.view.ImageOverlay;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.scene.shape.VLineTo;
import javafx.scene.text.Font;
import javafx.scene.text.LayoutInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.TextLineInfo;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

public final class PdfDocumentWriter {
    private static final String NO_EXPORT = "noExport";
    private static final double KAPPA = 0.5522847498;

    private PDDocument doc;
    private PDPageContentStream cs;
    private Transform sceneToPage;
    private final Map<String, PDFont> fonts = new HashMap<>();
    private final Map<Font, Double> ascents = new HashMap<>();
    private final Map<String, PDExtendedGraphicsState> alphas = new HashMap<>();
    private final Map<Image, PDImageXObject> images = new IdentityHashMap<>();

    public void write(List<? extends Region> pages, Path target) throws IOException {
        try (PDDocument d = new PDDocument()) {
            doc = d;
            for (Region page : pages)
                renderPage(page);
            d.save(target.toFile());
        } finally {
            doc = null;
            cs = null;
            fonts.clear();
            alphas.clear();
            images.clear();
        }
    }

    private void renderPage(Region pane) throws IOException {
        float w = (float) pane.getWidth(), h = (float) pane.getHeight();
        PDPage page = new PDPage(new PDRectangle(w, h));
        doc.addPage(page);
        try {
            sceneToPage = pane.getLocalToSceneTransform().createInverse();
        } catch (NonInvertibleTransformException e) {
            throw new IOException(e);
        }
        try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            cs = stream;
            cs.transform(new Matrix(1, 0, 0, -1, 0, h));
            render(pane, 1);
        }
    }

    private void render(Node n, double opacity) throws IOException {
        if (!n.isVisible() || n.getProperties().containsKey(NO_EXPORT)
                || n instanceof SelectionPath || n instanceof CaretNode)
            return;
        double op = opacity * n.getOpacity();
        if (op <= 0.001)
            return;

        boolean clipped = n.getClip() instanceof Shape clip && beginClip(n, clip);
        try {
            if (n instanceof Region r)
                drawRegion(r, op);
            switch (n) {
                case TextFlow flow -> renderFlow(flow, op);
                case Text t -> drawText(t, new TextLayout(t), op);
                case Shape s -> drawShape(s, op);
                case ImageView iv -> drawImage(iv, op);
                case Parent p -> {
                    for (Node child : p.getChildrenUnmodifiable())
                        render(child, op);
                }
                default -> {
                }
            }
        } finally {
            if (clipped)
                cs.restoreGraphicsState();
        }
    }

    private Transform toPage(Node n) {
        return sceneToPage.createConcatenation(n.getLocalToSceneTransform());
    }

    private boolean beginClip(Node n, Shape clip) throws IOException {
        List<PathElement> geometry = geometry(clip);
        if (geometry == null)
            return false;
        cs.saveGraphicsState();
        emit(geometry, toPage(n).createConcatenation(clip.getLocalToParentTransform()));
        cs.clip();
        return true;
    }

    private final class TextLayout {
        final LayoutInfo info;
        final List<TextLineInfo> lines;
        final double[] ascent;
        final Transform toPage;
        final Map<Text, Integer> offsets = new IdentityHashMap<>();

        TextLayout(Text standalone) {
            info = standalone.getLayoutInfo();
            lines = info.getTextLines(false);
            ascent = new double[lines.size()];
            Arrays.fill(ascent, ascentOf(standalone.getFont()));
            toPage = toPage(standalone);
            offsets.put(standalone, 0);
        }

        TextLayout(TextFlow flow) {
            info = flow.getLayoutInfo();
            lines = info.getTextLines(false);
            ascent = new double[lines.size()];
            toPage = toPage(flow);
            int offset = 0;
            for (Node c : flow.getChildren()) {
                if (!c.isManaged())
                    continue;
                if (c instanceof Text t) {
                    offsets.put(t, offset);
                    int end = offset + t.getText().length();
                    double a = ascentOf(t.getFont());
                    for (int i = 0; i < lines.size(); i++)
                        if (lines.get(i).start() < end && lines.get(i).end() > offset)
                            ascent[i] = Math.max(ascent[i], a);
                    offset = end;
                } else {
                    int i = lineAt(offset);
                    if (i >= 0)
                        ascent[i] = Math.max(ascent[i],
                                c.getLayoutY() + c.getBaselineOffset() - lines.get(i).bounds().getMinY());
                    offset++;
                }
            }
        }

        int lineAt(int offset) {
            for (int i = 0; i < lines.size(); i++)
                if (offset >= lines.get(i).start() && offset < lines.get(i).end())
                    return i;
            return lines.isEmpty() ? -1 : lines.size() - 1;
        }

        double caretX(int offset, boolean leading) {
            return info.caretInfoAt(offset, leading).getSegmentAt(0).getMinX();
        }
    }

    private record Word(String text, double x, double baseline, double width) {
    }

    private void renderFlow(TextFlow flow, double op) throws IOException {
        TextLayout layout = new TextLayout(flow);
        for (Node child : flow.getChildrenUnmodifiable()) {
            if (child instanceof Text t && layout.offsets.containsKey(t)) {
                if (t.isVisible() && !t.getProperties().containsKey(NO_EXPORT))
                    drawText(t, layout, op * t.getOpacity());
            } else {
                render(child, op);
            }
        }
    }

    private double ascentOf(Font font) {
        return ascents.computeIfAbsent(font, f -> {
            Text probe = new Text("Hg");
            probe.setFont(f);
            return probe.getBaselineOffset();
        });
    }

    private void drawText(Text t, TextLayout layout, double op) throws IOException {
        Color color = color(t.getFill(), op);
        String s = t.getText();
        if (color == null || s == null || s.isBlank())
            return;
        int start = layout.offsets.get(t);
        boolean span = t.getParent() instanceof TextFlow;
        double dx = span ? t.getTranslateX() : 0, dy = span ? t.getTranslateY() : 0;

        List<Word> words = new ArrayList<>();
        for (int li = 0; li < layout.lines.size(); li++) {
            TextLineInfo line = layout.lines.get(li);
            int from = Math.max(start, line.start()), to = Math.min(start + s.length(), line.end());
            double baseline = line.bounds().getMinY() + layout.ascent[li] + dy;
            int i = from;
            while (i < to) {
                while (i < to && Character.isWhitespace(s.charAt(i - start)))
                    i++;
                int j = i;
                while (j < to && !Character.isWhitespace(s.charAt(j - start)))
                    j++;
                if (j > i) {
                    double x1 = layout.caretX(i, true), x2 = layout.caretX(j - 1, false);
                    words.add(new Word(s.substring(i - start, j - start), x1 + dx, baseline, x2 - x1));
                }
                i = j;
            }
        }

        PDFont font = fontFor(t.getFont());
        if (font == null || !encodable(font, words)) {
            drawOutline(t, color);
            return;
        }

        float size = (float) t.getFont().getSize();
        cs.saveGraphicsState();
        setAlpha(color.getOpacity(), 1);
        cs.setNonStrokingColor(awt(color));
        cs.beginText();
        cs.setFont(font, size);
        for (Word w : words) {
            Transform m = layout.toPage.createConcatenation(new Translate(w.x(), w.baseline()))
                    .createConcatenation(new Scale(1, -1));
            cs.setTextMatrix(matrix(m));
            double natural = font.getStringWidth(w.text()) / 1000 * size;
            cs.setHorizontalScaling(natural > 0 && w.width() > 0 ? (float) (100 * w.width() / natural) : 100);
            cs.showText(w.text());
        }
        cs.endText();
        if (t.isUnderline() || t.isStrikethrough()) {
            int end = start + s.length();
            List<Rectangle2D> rects = new ArrayList<>();
            if (t.isUnderline())
                rects.addAll(layout.info.getUnderlineGeometry(start, end));
            if (t.isStrikethrough())
                rects.addAll(layout.info.getStrikeThroughGeometry(start, end));
            for (Rectangle2D r : rects)
                emit(rect(r.getMinX() + dx, r.getMinY() + dy, r.getWidth(), r.getHeight()), layout.toPage);
            cs.fill();
        }
        cs.restoreGraphicsState();
    }

    private static boolean encodable(PDFont font, List<Word> words) {
        try {
            for (Word w : words)
                font.encode(w.text());
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private void drawOutline(Text t, Color color) throws IOException {
        if (!(Shape.union(t, new Rectangle()) instanceof javafx.scene.shape.Path outline))
            return;
        cs.saveGraphicsState();
        setAlpha(color.getOpacity(), 1);
        cs.setNonStrokingColor(awt(color));
        emit(outline.getElements(), sceneToPage);
        fill(outline.getFillRule());
        cs.restoreGraphicsState();
    }

    private PDFont fontFor(Font f) {
        if (fonts.containsKey(f.getName()))
            return fonts.get(f.getName());
        PDFont font = null;
        String family = f.getFamily().replace(" ", "");
        String style = f.getStyle().replace(" ", "");
        List<String> names = new ArrayList<>(List.of(family + "-" + style, f.getName().replace(" ", "")));
        if (style.equalsIgnoreCase("Regular"))
            names.add(family);
        for (String name : names) {
            FontMapping<TrueTypeFont> m = FontMappers.instance().getTrueTypeFont(name, null);
            if (m == null || m.isFallback() || !sameFamily(m.getFont(), f))
                continue;
            try {
                font = PDType0Font.load(doc, m.getFont(), true);
                break;
            } catch (IOException | RuntimeException ignored) {
            }
        }
        fonts.put(f.getName(), font);
        return font;
    }

    private static boolean sameFamily(TrueTypeFont ttf, Font f) {
        try {
            NamingTable naming = ttf.getNaming();
            String family = naming == null ? null : naming.getFontFamily();
            return family != null && family.replace(" ", "").equalsIgnoreCase(f.getFamily().replace(" ", ""));
        } catch (IOException e) {
            return false;
        }
    }

    private void drawRegion(Region r, double op) throws IOException {
        double w = r.getWidth(), h = r.getHeight();
        if (w <= 0 || h <= 0)
            return;
        Transform t = toPage(r);
        Background bg = r.getBackground();
        if (bg != null)
            for (BackgroundFill f : bg.getFills()) {
                Color c = color(f.getFill(), op);
                Insets in = f.getInsets();
                double fw = w - in.getLeft() - in.getRight(), fh = h - in.getTop() - in.getBottom();
                if (c == null || fw <= 0 || fh <= 0)
                    continue;
                cs.saveGraphicsState();
                setAlpha(c.getOpacity(), 1);
                cs.setNonStrokingColor(awt(c));
                emit(roundRect(in.getLeft(), in.getTop(), fw, fh, radius(f.getRadii(), fw, fh)), t);
                cs.fill();
                cs.restoreGraphicsState();
            }
        Border border = r.getBorder();
        if (border != null)
            for (BorderStroke s : border.getStrokes())
                drawBorder(s, w, h, t, op);
    }

    private void drawBorder(BorderStroke s, double w, double h, Transform t, double op) throws IOException {
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
        double radius = radius(s.getRadii(), bw, bh);
        boolean uniform = ct != null && ct.equals(cr) && ct.equals(cb) && ct.equals(cl)
                && top == right && top == bottom && top == left;
        if (uniform && (radius > 0 || !s.getTopStyle().getDashArray().isEmpty())) {
            cs.saveGraphicsState();
            setAlpha(1, ct.getOpacity());
            cs.setStrokingColor(awt(ct));
            cs.setLineWidth((float) (top * scaleOf(t)));
            dash(s.getTopStyle().getDashArray(), s.getTopStyle().getDashOffset(), scaleOf(t));
            emit(roundRect(x + top / 2, y + top / 2, bw - top, bh - top, Math.max(0, radius - top / 2)), t);
            cs.stroke();
            cs.restoreGraphicsState();
            return;
        }
        sideRect(ct, x, y, bw, top, t);
        sideRect(cb, x, y + bh - bottom, bw, bottom, t);
        sideRect(cl, x, y + top, left, bh - top - bottom, t);
        sideRect(cr, x + bw - right, y + top, right, bh - top - bottom, t);
    }

    private Color side(Paint p, BorderStrokeStyle style, double op) {
        return style == null || style == BorderStrokeStyle.NONE ? null : color(p, op);
    }

    private void sideRect(Color c, double x, double y, double w, double h, Transform t) throws IOException {
        if (c == null || w <= 0 || h <= 0)
            return;
        cs.saveGraphicsState();
        setAlpha(c.getOpacity(), 1);
        cs.setNonStrokingColor(awt(c));
        emit(rect(x, y, w, h), t);
        cs.fill();
        cs.restoreGraphicsState();
    }

    private static double radius(CornerRadii r, double w, double h) {
        if (r == null || r.isUniform() && r.getTopLeftHorizontalRadius() == 0)
            return 0;
        double v = r.getTopLeftHorizontalRadius();
        return r.isTopLeftHorizontalRadiusAsPercentage() ? v * Math.min(w, h) : v;
    }

    private void drawShape(Shape s, double op) throws IOException {
        Color fill = color(s.getFill(), op);
        Color stroke = s.getStrokeWidth() > 0 ? color(s.getStroke(), op) : null;
        if (s instanceof Line || s instanceof Polyline)
            fill = null;
        if (fill == null && stroke == null)
            return;

        List<PathElement> geometry = geometry(s);
        if (geometry == null) {
            if (Shape.union(s, new Rectangle()) instanceof javafx.scene.shape.Path area) {
                Color c = fill != null ? fill : stroke;
                cs.saveGraphicsState();
                setAlpha(c.getOpacity(), 1);
                cs.setNonStrokingColor(awt(c));
                emit(area.getElements(), sceneToPage);
                fill(area.getFillRule());
                cs.restoreGraphicsState();
            }
            return;
        }

        Transform t = toPage(s);
        cs.saveGraphicsState();
        setAlpha(fill != null ? fill.getOpacity() : 1, stroke != null ? stroke.getOpacity() : 1);
        if (fill != null) {
            cs.setNonStrokingColor(awt(fill));
            emit(geometry, t);
            fill(s instanceof javafx.scene.shape.Path p ? p.getFillRule() : FillRule.NON_ZERO);
        }
        if (stroke != null) {
            double scale = scaleOf(t);
            cs.setStrokingColor(awt(stroke));
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
            dash(s.getStrokeDashArray(), s.getStrokeDashOffset(), scale);
            emit(strokeGeometry(s, geometry), t);
            cs.stroke();
        }
        cs.restoreGraphicsState();
    }

    private static List<PathElement> geometry(Shape s) {
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
            case javafx.scene.shape.Path p -> p.getElements().stream().allMatch(PdfDocumentWriter::supported)
                    ? p.getElements()
                    : null;
            default -> null;
        };
    }

    private static boolean supported(PathElement e) {
        return e instanceof MoveTo || e instanceof LineTo || e instanceof HLineTo || e instanceof VLineTo
                || e instanceof QuadCurveTo || e instanceof CubicCurveTo || e instanceof ClosePath;
    }

    private static List<PathElement> strokeGeometry(Shape s, List<PathElement> geometry) {
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

    private void fill(FillRule rule) throws IOException {
        if (rule == FillRule.EVEN_ODD)
            cs.fillEvenOdd();
        else
            cs.fill();
    }

    private void dash(List<Double> array, double offset, double scale) throws IOException {
        if (array == null || array.isEmpty())
            return;
        float[] d = new float[array.size()];
        for (int i = 0; i < d.length; i++)
            d[i] = (float) (array.get(i) * scale);
        cs.setLineDashPattern(d, (float) (offset * scale));
    }

    private void drawImage(ImageView iv, double op) throws IOException {
        Image image = iv.getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            return;
        var b = iv.getLayoutBounds();
        if (b.getWidth() <= 0 || b.getHeight() <= 0)
            return;
        PDImageXObject x = imageFor(iv);
        if (x == null)
            return;
        Transform m = toPage(iv).createConcatenation(new Translate(b.getMinX(), b.getMaxY()))
                .createConcatenation(new Scale(b.getWidth(), -b.getHeight()));
        cs.saveGraphicsState();
        setAlpha(op, 1);
        cs.drawImage(x, matrix(m));
        cs.restoreGraphicsState();
    }

    private PDImageXObject imageFor(ImageView iv) throws IOException {
        Image image = iv.getImage();
        Rectangle2D vp = iv.getViewport();
        if (vp == null && images.containsKey(image))
            return images.get(image);

        PDImageXObject x = null;
        if (vp == null && ancestor(iv, ImageOverlay.class) instanceof ImageOverlay o && o.getBase64() != null
                && o.getFormat() != null && o.getFormat().toLowerCase().matches("jpe?g")) {
            try {
                x = JPEGFactory.createFromByteArray(doc, Base64.getDecoder().decode(o.getBase64()));
            } catch (IOException | IllegalArgumentException ignored) {
            }
        }
        if (x == null) {
            BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
            if (buffered == null)
                return null;
            if (vp != null)
                buffered = buffered.getSubimage(
                        clamp(vp.getMinX(), buffered.getWidth()), clamp(vp.getMinY(), buffered.getHeight()),
                        Math.max(1, clamp(vp.getWidth(), buffered.getWidth() - clamp(vp.getMinX(), buffered.getWidth()))),
                        Math.max(1, clamp(vp.getHeight(), buffered.getHeight() - clamp(vp.getMinY(), buffered.getHeight()))));
            x = LosslessFactory.createFromImage(doc, buffered);
        }
        if (vp == null)
            images.put(image, x);
        return x;
    }

    private static int clamp(double v, int max) {
        return (int) Math.max(0, Math.min(max, Math.round(v)));
    }

    private static Node ancestor(Node n, Class<?> type) {
        for (Node p = n; p != null; p = p.getParent())
            if (type.isInstance(p))
                return p;
        return null;
    }

    private void emit(List<PathElement> elements, Transform t) throws IOException {
        double cx = 0, cy = 0, sx = 0, sy = 0;
        for (PathElement e : elements) {
            boolean rel = !e.isAbsolute();
            double ox = rel ? cx : 0, oy = rel ? cy : 0;
            switch (e) {
                case MoveTo m -> {
                    cx = sx = ox + m.getX();
                    cy = sy = oy + m.getY();
                    var p = t.transform(cx, cy);
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
        var p = t.transform(x, y);
        cs.lineTo((float) p.getX(), (float) p.getY());
    }

    private void curveTo(Transform t, double x1, double y1, double x2, double y2, double x3, double y3)
            throws IOException {
        var a = t.transform(x1, y1);
        var b = t.transform(x2, y2);
        var c = t.transform(x3, y3);
        cs.curveTo((float) a.getX(), (float) a.getY(), (float) b.getX(), (float) b.getY(), (float) c.getX(),
                (float) c.getY());
    }

    private static List<PathElement> rect(double x, double y, double w, double h) {
        return List.of(new MoveTo(x, y), new LineTo(x + w, y), new LineTo(x + w, y + h), new LineTo(x, y + h),
                new ClosePath());
    }

    private static List<PathElement> roundRect(double x, double y, double w, double h, double r) {
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

    private static List<PathElement> ellipse(double cx, double cy, double rx, double ry) {
        double kx = rx * KAPPA, ky = ry * KAPPA;
        return List.of(new MoveTo(cx + rx, cy),
                new CubicCurveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
                new CubicCurveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
                new CubicCurveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
                new CubicCurveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy), new ClosePath());
    }

    private static List<PathElement> polyline(List<Double> pts, boolean closed) {
        List<PathElement> out = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i += 2)
            out.add(i == 0 ? new MoveTo(pts.get(i), pts.get(i + 1)) : new LineTo(pts.get(i), pts.get(i + 1)));
        if (closed && !out.isEmpty())
            out.add(new ClosePath());
        return out;
    }

    private static double scaleOf(Transform t) {
        return Math.sqrt(Math.abs(t.getMxx() * t.getMyy() - t.getMxy() * t.getMyx()));
    }

    private static Matrix matrix(Transform t) {
        return new Matrix((float) t.getMxx(), (float) t.getMyx(), (float) t.getMxy(), (float) t.getMyy(),
                (float) t.getTx(), (float) t.getTy());
    }

    private static Color color(Paint p, double op) {
        Color c = switch (p) {
            case Color col -> col;
            case javafx.scene.paint.LinearGradient g when !g.getStops().isEmpty() -> g.getStops().get(0).getColor();
            case javafx.scene.paint.RadialGradient g when !g.getStops().isEmpty() -> g.getStops().get(0).getColor();
            case null, default -> null;
        };
        if (c == null || c.getOpacity() * op <= 0.001)
            return null;
        return op >= 1 ? c : Color.color(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity() * op);
    }

    private static java.awt.Color awt(Color c) {
        return new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }

    private void setAlpha(double fill, double stroke) throws IOException {
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
}
