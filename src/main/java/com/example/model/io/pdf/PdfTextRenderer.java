package com.example.model.io.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.LayoutInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.TextLineInfo;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

final class PdfTextRenderer {
    interface ChildRenderer {
        void render(Node child, double opacity) throws IOException;
    }

    private record Word(String text, double x, double baseline, double width) {
    }

    private static final class Layout {
        final LayoutInfo info;
        final List<TextLineInfo> lines;
        final double[] ascent;
        final Transform toPage;
        final Map<Text, Integer> offsets = new IdentityHashMap<>();

        Layout(Text standalone, Transform toPage) {
            info = standalone.getLayoutInfo();
            lines = info.getTextLines(false);
            ascent = new double[lines.size()];
            Arrays.fill(ascent, PdfFonts.ascent(standalone.getFont()));
            this.toPage = toPage;
            offsets.put(standalone, 0);
        }

        Layout(TextFlow flow, Transform toPage) {
            info = flow.getLayoutInfo();
            lines = info.getTextLines(false);
            ascent = new double[lines.size()];
            this.toPage = toPage;
            int offset = 0;
            for (Node c : flow.getChildren()) {
                if (!c.isManaged())
                    continue;
                if (c instanceof Text t) {
                    offsets.put(t, offset);
                    int end = offset + t.getText().length();
                    double a = PdfFonts.ascent(t.getFont());
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

    private final PdfFonts fonts;

    PdfTextRenderer(PdfFonts fonts) {
        this.fonts = fonts;
    }

    void renderFlow(PdfCanvas canvas, TextFlow flow, double op, ChildRenderer others) throws IOException {
        Layout layout = new Layout(flow, canvas.toPage(flow));
        for (Node child : flow.getChildrenUnmodifiable()) {
            if (child instanceof Text t && layout.offsets.containsKey(t)) {
                if (PdfDocumentWriter.exported(t))
                    draw(canvas, t, layout, op * t.getOpacity());
            } else {
                others.render(child, op);
            }
        }
    }

    void renderText(PdfCanvas canvas, Text text, double op) throws IOException {
        draw(canvas, text, new Layout(text, canvas.toPage(text)), op);
    }

    private void draw(PdfCanvas canvas, Text t, Layout layout, double op) throws IOException {
        Color color = PdfCanvas.color(t.getFill(), op);
        String s = t.getText();
        if (color == null || s == null || s.isBlank())
            return;
        int start = layout.offsets.get(t);
        boolean span = t.getParent() instanceof TextFlow;
        double dx = span ? t.getTranslateX() : 0, dy = span ? t.getTranslateY() : 0;
        List<Word> words = words(t, s, start, layout, dx, dy);

        PDFont font = fonts.resolve(t.getFont());
        if (font == null || !encodable(font, words)) {
            drawOutline(canvas, t, color);
            return;
        }

        PDPageContentStream cs = canvas.stream();
        float size = (float) t.getFont().getSize();
        canvas.save();
        canvas.alpha(color.getOpacity(), 1);
        canvas.fillColor(color);
        cs.beginText();
        cs.setFont(font, size);
        for (Word w : words) {
            Transform m = layout.toPage.createConcatenation(new Translate(w.x(), w.baseline()))
                    .createConcatenation(new Scale(1, -1));
            cs.setTextMatrix(PdfCanvas.matrix(m));
            double natural = font.getStringWidth(w.text()) / 1000 * size;
            cs.setHorizontalScaling(natural > 0 && w.width() > 0 ? (float) (100 * w.width() / natural) : 100);
            cs.showText(w.text());
        }
        cs.endText();
        drawDecorations(canvas, t, layout, start, start + s.length(), dx, dy);
        canvas.restore();
    }

    private static List<Word> words(Text t, String s, int start, Layout layout, double dx, double dy) {
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
        return words;
    }

    private static void drawDecorations(PdfCanvas canvas, Text t, Layout layout, int start, int end, double dx,
            double dy) throws IOException {
        if (!t.isUnderline() && !t.isStrikethrough())
            return;
        List<Rectangle2D> rects = new ArrayList<>();
        if (t.isUnderline())
            rects.addAll(layout.info.getUnderlineGeometry(start, end));
        if (t.isStrikethrough())
            rects.addAll(layout.info.getStrikeThroughGeometry(start, end));
        for (Rectangle2D r : rects)
            canvas.path(PdfGeometry.rect(r.getMinX() + dx, r.getMinY() + dy, r.getWidth(), r.getHeight()),
                    layout.toPage);
        canvas.fill(FillRule.NON_ZERO);
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

    private static void drawOutline(PdfCanvas canvas, Text t, Color color) throws IOException {
        if (Shape.union(t, new Rectangle()) instanceof Path outline) {
            List<PathElement> elements = outline.getElements();
            canvas.fill(color, elements, canvas.sceneToPage(), outline.getFillRule());
        }
    }
}
