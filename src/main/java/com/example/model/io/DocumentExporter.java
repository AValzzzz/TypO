package com.example.model.io;

import java.io.IOException;
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
        List<PageContent> content = new ArrayList<>();
        for (Page page : pages)
            content.add(PageContent.capture(page));
        new DocxDocumentWriter().write(content, target);
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
