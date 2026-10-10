package com.example.model.actions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.model.Page;
import com.example.model.i18n.I18n;
import com.example.model.io.DocumentExporter;
import com.example.model.io.DocumentSession;
import com.example.view.Toast;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

public class Save implements AppAction {
    private final List<Page> pages;
    private final Window owner;
    private final DocumentSession session;

    public Save(List<Page> pages, Window owner, DocumentSession session) {
        this.pages = pages;
        this.owner = owner;
        this.session = session;
    }

    @Override
    public void execute() {
        Path target = session.getCurrentFile();
        if (target == null) {
            new SaveAs(pages, owner, session).execute();
            return;
        }

        try {
            DocumentExporter.writeDocx(pages, target);
            Toast.success(I18n.t("toast.saved"));
        } catch (IOException e) {
            new Alert(AlertType.ERROR, I18n.t("file.error.save", e.getMessage())).showAndWait();
        }
    }
}