package com.example.model.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.model.Page;
import com.example.model.io.DocumentSession;
import com.example.model.io.DocxDocumentWriter;
import com.example.model.io.PageContent;
import com.example.model.io.PdfDocumentWriter;
import com.example.model.io.PdfDocumentWriter.PageSnapshot;
import com.example.view.ImageOverlay;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;

public class SaveAs implements AppAction {

    private static final double PDF_RENDER_SCALE = 2.0;

    private final List<Page> pages;
    private final Window owner;
    private final DocumentSession session;

    public SaveAs(List<Page> pages, Window owner, DocumentSession session) {
        this.pages = pages;
        this.owner = owner;
        this.session = session;
    }

    @Override
    public void execute() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer sous");
        ExtensionFilter docxFilter = new ExtensionFilter("Document Word (*.docx)", "*.docx");
        ExtensionFilter pdfFilter = new ExtensionFilter("Document PDF (*.pdf)", "*.pdf");
        chooser.getExtensionFilters().addAll(docxFilter, pdfFilter);
        chooser.setSelectedExtensionFilter(docxFilter);

        File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) {
            return;
        }

        boolean wantsPdf = chooser.getSelectedExtensionFilter() == pdfFilter;
        Path target = ensureExtension(chosen, wantsPdf);

        try {
            if (target.toString().toLowerCase().endsWith(".pdf")) {
                new PdfDocumentWriter().write(captureSnapshots(), target);
            } else {
                new DocxDocumentWriter().write(captureContent(), target);
                session.setCurrentFile(target);
            }
        } catch (IOException e) {
            new Alert(AlertType.ERROR, "Échec de l'enregistrement : " + e.getMessage()).showAndWait();
        }
    }

    private List<PageContent> captureContent() {
        List<PageContent> content = new ArrayList<>();
        for (Page page : pages) {
            content.add(PageContent.capture(page));
        }
        return content;
    }

    private List<PageSnapshot> captureSnapshots() {
        List<PageSnapshot> snapshots = new ArrayList<>();
        for (Page page : pages) {
            Pane pane = page.getPane();
            boolean landscape = pane.getWidth() > pane.getHeight();

            for (ImageOverlay overlay : page.getImageOverlays())
                overlay.setHandleSuppressed(true);

            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setTransform(new Scale(PDF_RENDER_SCALE, PDF_RENDER_SCALE));
                params.setFill(Color.WHITE);

                WritableImage fxImage = pane.snapshot(params, null);
                snapshots.add(new PageSnapshot(SwingFXUtils.fromFXImage(fxImage, null), landscape));
            } finally {
                for (ImageOverlay overlay : page.getImageOverlays())
                    overlay.setHandleSuppressed(false);
            }
        }
        return snapshots;
    }

    private Path ensureExtension(File file, boolean wantsPdf) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf") || name.endsWith(".docx")) {
            return file.toPath();
        }
        String ext = wantsPdf ? ".pdf" : ".docx";
        return new File(file.getParentFile(), file.getName() + ext).toPath();
    }
}