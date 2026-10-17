package com.example.model.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.model.Page;
import com.example.model.io.pdf.PdfDocumentWriter;
import com.example.view.Layerable;

import javafx.scene.layout.Pane;

public final class DocumentExporter {
    private DocumentExporter() {
    }

    public static void writeDocx(List<Page> pages, Path target) throws IOException {
        new DocxDocumentWriter().write(capture(pages), target);
    }

    public static String snapshot(List<Page> pages) {
        WordXmlWriter w = new WordXmlWriter();
        String document = w.documentXml(capture(pages));
        return document + new String(w.sidecar().toXml(), StandardCharsets.UTF_8);
    }

    private static List<PageContent> capture(List<Page> pages) {
        List<PageContent> content = new ArrayList<>();
        for (Page page : pages)
            content.add(PageContent.capture(page));
        return content;
    }

    public static void writePdf(List<Page> pages, Path target) throws IOException {
        setHandlesSuppressed(pages, true);
        try {
            List<Pane> panes = new ArrayList<>();
            for (Page page : pages) {
                page.getPane().applyCss();
                page.getPane().layout();
                panes.add(page.getPane());
            }
            new PdfDocumentWriter().write(panes, target);
        } finally {
            setHandlesSuppressed(pages, false);
        }
    }

    private static void setHandlesSuppressed(List<Page> pages, boolean suppressed) {
        for (Page page : pages)
            for (Layerable layer : page.getLayers())
                layer.setHandleSuppressed(suppressed);
    }
}
