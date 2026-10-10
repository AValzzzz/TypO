package com.example.model.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.model.Page;
import com.example.model.i18n.I18n;
import com.example.model.io.DocumentSession;
import com.example.model.io.DocxDocumentWriter;
import com.example.model.io.PageContent;
import com.example.model.io.PdfDocumentWriter;
import com.example.view.Toast;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;

public class SaveAs implements AppAction {

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
        chooser.setTitle(I18n.t("file.saveAs.title"));
        ExtensionFilter docxFilter = new ExtensionFilter(I18n.t("file.filter.docx"), "*.docx");
        ExtensionFilter pdfFilter = new ExtensionFilter(I18n.t("file.filter.pdf"), "*.pdf");
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
                exportPdf(target);
                Toast.success(I18n.t("toast.exportedPdf"));
            } else {
                new DocxDocumentWriter().write(captureContent(), target);
                session.setCurrentFile(target);
                Toast.success(I18n.t("toast.saved"));
            }
        } catch (IOException e) {
            new Alert(AlertType.ERROR, I18n.t("file.error.save", e.getMessage())).showAndWait();
        }
    }

    private List<PageContent> captureContent() {
        List<PageContent> content = new ArrayList<>();
        for (Page page : pages) {
            content.add(PageContent.capture(page));
        }
        return content;
    }

    private void exportPdf(Path target) throws IOException {
        setHandlesSuppressed(true);
        try {
            List<Pane> panes = new ArrayList<>();
            for (Page page : pages) {
                page.getPane().applyCss();
                page.getPane().layout();
                panes.add(page.getPane());
            }
            new PdfDocumentWriter().write(panes, target);
        } finally {
            setHandlesSuppressed(false);
        }
    }

    private void setHandlesSuppressed(boolean suppressed) {
        for (Page page : pages) {
            page.getImageOverlays().forEach(o -> o.setHandleSuppressed(suppressed));
            page.getShapeOverlays().forEach(o -> o.setHandleSuppressed(suppressed));
            page.getArrowOverlays().forEach(o -> o.setHandleSuppressed(suppressed));
            page.getTableOverlays().forEach(o -> o.setHandleSuppressed(suppressed));
            page.getTextBoxOverlays().forEach(o -> o.setHandleSuppressed(suppressed));
        }
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