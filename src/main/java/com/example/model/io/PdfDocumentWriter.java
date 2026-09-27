package com.example.model.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class PdfDocumentWriter {

    public void write(List<PageSnapshot> pages, Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (PageSnapshot snapshot : pages) {
                renderPage(doc, snapshot);
            }
            doc.save(target.toFile());
        }
    }

    private void renderPage(PDDocument doc, PageSnapshot snapshot) throws IOException {
        BufferedImage image = snapshot.image;

        PDRectangle size = snapshot.landscape
                ? new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth())
                : PDRectangle.LETTER;
        PDPage page = new PDPage(size);
        doc.addPage(page);

        PDImageXObject pdImage = LosslessFactory.createFromImage(doc, image);

        float scale = Math.min(size.getWidth() / image.getWidth(), size.getHeight() / image.getHeight());
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float x = (size.getWidth() - w) / 2f;
        float y = (size.getHeight() - h) / 2f;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(pdImage, x, y, w, h);
        }
    }

    public static final class PageSnapshot {
        public final BufferedImage image;
        public final boolean landscape;

        public PageSnapshot(BufferedImage image, boolean landscape) {
            this.image = image;
            this.landscape = landscape;
        }
    }
}