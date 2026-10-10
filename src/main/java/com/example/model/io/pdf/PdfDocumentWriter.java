package com.example.model.io.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.SelectionPath;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

public final class PdfDocumentWriter {
    private static final String NO_EXPORT = "noExport";

    private PdfFonts fonts;
    private PdfImages images;
    private PdfTextRenderer text;
    private final Map<String, PDExtendedGraphicsState> alphas = new HashMap<>();

    public void write(List<? extends Region> pages, Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            fonts = new PdfFonts(doc);
            images = new PdfImages(doc);
            text = new PdfTextRenderer(fonts);
            for (Region page : pages)
                renderPage(doc, page);
            doc.save(target.toFile());
        } finally {
            fonts = null;
            images = null;
            text = null;
            alphas.clear();
        }
    }

    static boolean exported(Node n) {
        return n.isVisible() && !n.getProperties().containsKey(NO_EXPORT);
    }

    private void renderPage(PDDocument doc, Region pane) throws IOException {
        float w = (float) pane.getWidth(), h = (float) pane.getHeight();
        PDPage page = new PDPage(new PDRectangle(w, h));
        doc.addPage(page);
        Transform sceneToPage;
        try {
            sceneToPage = pane.getLocalToSceneTransform().createInverse();
        } catch (NonInvertibleTransformException e) {
            throw new IOException(e);
        }
        try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            stream.transform(new Matrix(1, 0, 0, -1, 0, h));
            render(new PdfCanvas(stream, sceneToPage, alphas), pane, 1);
        }
    }

    private void render(PdfCanvas canvas, Node n, double opacity) throws IOException {
        if (!exported(n) || n instanceof SelectionPath || n instanceof CaretNode)
            return;
        double op = opacity * n.getOpacity();
        if (op <= 0.001)
            return;

        boolean clipped = n.getClip() instanceof Shape clip && beginClip(canvas, n, clip);
        try {
            if (n instanceof Region r)
                PdfShapeRenderer.drawRegion(canvas, r, op);
            switch (n) {
                case TextFlow flow -> text.renderFlow(canvas, flow, op, (child, o) -> render(canvas, child, o));
                case Text t -> text.renderText(canvas, t, op);
                case Shape s -> PdfShapeRenderer.drawShape(canvas, s, op);
                case ImageView iv -> drawImage(canvas, iv, op);
                case Parent p -> {
                    for (Node child : p.getChildrenUnmodifiable())
                        render(canvas, child, op);
                }
                default -> {
                }
            }
        } finally {
            if (clipped)
                canvas.restore();
        }
    }

    private static boolean beginClip(PdfCanvas canvas, Node n, Shape clip) throws IOException {
        List<PathElement> geometry = PdfGeometry.of(clip);
        if (geometry == null)
            return false;
        canvas.clip(geometry, canvas.toPage(n).createConcatenation(clip.getLocalToParentTransform()));
        return true;
    }

    private void drawImage(PdfCanvas canvas, ImageView iv, double op) throws IOException {
        Image image = iv.getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            return;
        Bounds b = iv.getLayoutBounds();
        if (b.getWidth() <= 0 || b.getHeight() <= 0)
            return;
        PDImageXObject x = images.of(iv);
        if (x == null)
            return;
        Transform m = canvas.toPage(iv).createConcatenation(new Translate(b.getMinX(), b.getMaxY()))
                .createConcatenation(new Scale(b.getWidth(), -b.getHeight()));
        canvas.save();
        canvas.alpha(op, 1);
        canvas.stream().drawImage(x, PdfCanvas.matrix(m));
        canvas.restore();
    }
}
