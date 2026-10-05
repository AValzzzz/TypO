package com.example.model.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

import com.example.model.i18n.I18n;
import com.example.model.io.DocxDocumentWriter;
import com.example.model.io.PageContent;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;

public class OpenFile implements AppAction {

    private final Window owner;
    private final BiConsumer<List<PageContent>, Path> onLoaded;

    public OpenFile(Window owner, BiConsumer<List<PageContent>, Path> onLoaded) {
        this.owner = owner;
        this.onLoaded = onLoaded;
    }

    @Override
    public void execute() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("file.open.title"));
        chooser.getExtensionFilters().add(new ExtensionFilter(I18n.t("file.filter.docx"), "*.docx"));

        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        try {
            List<PageContent> pages = new DocxDocumentWriter().read(file.toPath());
            onLoaded.accept(pages, file.toPath());
        } catch (IOException e) {
            new Alert(AlertType.ERROR, I18n.t("file.error.open", e.getMessage())).showAndWait();
        }
    }
}