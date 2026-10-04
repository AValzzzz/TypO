package com.example.model.actions;

import java.util.Optional;
import java.util.function.Consumer;

import com.example.model.Page;
import com.example.model.settings.AppSettings;
import com.example.view.RichTextArea;
import com.example.view.TableOverlay;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;

public class InsertTable implements AppAction {
    private final Page page;
    private final Consumer<RichTextArea> cellSetup;

    public InsertTable(Page page, Consumer<RichTextArea> cellSetup) {
        this.page = page;
        this.cellSetup = cellSetup;
    }

    @Override
    public void execute() {
        double x = AppSettings.cmToPx(AppSettings.getInstance().getMarginLeft());
        double y = AppSettings.cmToPx(AppSettings.getInstance().getMarginTop());

        Optional<Bounds> caret = page.getEditor().getCaretBounds();
        if (caret.isPresent()) {
            Point2D p = page.getPane().screenToLocal(caret.get().getMinX(), caret.get().getMaxY() + 4);
            if (p != null)
                y = Math.max(y, p.getY());
        }

        TableOverlay table = page.addTableOverlay(x, y, 2, 2, cellSetup);
        Platform.runLater(table::focusFirstCell);
    }
}