package com.example.controller;

import java.nio.file.Path;
import java.util.List;

import com.example.model.Page;
import com.example.model.i18n.I18n;
import com.example.model.io.DocumentExporter;
import com.example.model.io.DocumentSession;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class UnsavedChanges {
    private static final String APP_NAME = "TypO";

    private final List<Page> pages;
    private final DocumentSession session;
    private final Runnable save;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(300));
    private Stage stage;
    private boolean baselinePending = true;

    public UnsavedChanges(List<Page> pages, DocumentSession session, Runnable save) {
        this.pages = pages;
        this.session = session;
        this.save = save;
        debounce.setOnFinished(e -> refresh());
    }

    public void install(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscard())
                e.consume();
        });
        stage.getScene().addEventFilter(KeyEvent.KEY_RELEASED, e -> schedule());
        stage.getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, e -> schedule());
        Window.getWindows().addListener((ListChangeListener<Window>) c -> schedule());
        settled();
    }

    public void expectBaseline() {
        baselinePending = true;
    }

    public void settled() {
        if (baselinePending) {
            baselinePending = false;
            session.markSaved(DocumentExporter.snapshot(pages));
            refresh();
        } else {
            schedule();
        }
    }

    public void schedule() {
        debounce.playFromStart();
    }

    public void refresh() {
        debounce.stop();
        updateTitle(isModified());
    }

    public boolean confirmDiscard() {
        if (!isModified())
            return true;

        ButtonType saveButton = new ButtonType(I18n.t("dialog.unsaved.save"), ButtonData.YES);
        ButtonType discardButton = new ButtonType(I18n.t("dialog.unsaved.discard"), ButtonData.NO);
        ButtonType cancelButton = new ButtonType(I18n.t("dialog.unsaved.cancel"), ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.t("dialog.unsaved.content"),
                saveButton, discardButton, cancelButton);
        alert.setTitle(I18n.t("dialog.unsaved.title"));
        alert.setHeaderText(I18n.t("dialog.unsaved.header"));
        if (stage != null)
            alert.initOwner(stage);

        ButtonType choice = alert.showAndWait().orElse(cancelButton);
        if (choice == discardButton)
            return true;
        if (choice != saveButton)
            return false;

        save.run();
        boolean modified = isModified();
        updateTitle(modified);
        return !modified;
    }

    private boolean isModified() {
        return !baselinePending && session.isModified(DocumentExporter.snapshot(pages));
    }

    private void updateTitle(boolean modified) {
        if (stage == null)
            return;
        Path file = session.getCurrentFile();
        String title = file != null ? APP_NAME + " — " + file.getFileName() : APP_NAME;
        stage.setTitle(modified ? title + " *" : title);
    }
}
