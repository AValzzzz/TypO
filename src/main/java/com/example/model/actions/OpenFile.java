package com.example.model.actions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.example.model.io.DocxDocumentWriter;
import com.example.model.io.PageContent;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;

public class OpenFile implements AppAction {

    private final Window owner;
    private final Consumer<List<PageContent>> onLoaded;

    public OpenFile(Window owner, Consumer<List<PageContent>> onLoaded) {
        this.owner = owner;
        this.onLoaded = onLoaded;
    }

    @Override
    public void execute() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ouvrir");
        chooser.getExtensionFilters().add(new ExtensionFilter("Document Word (*.docx)", "*.docx"));

        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        try {
            List<PageContent> pages = new DocxDocumentWriter().read(file.toPath());
            onLoaded.accept(pages);
        } catch (IOException e) {
            new Alert(AlertType.ERROR, "Échec de l'ouverture : " + e.getMessage()).showAndWait();
        }
    }
}