package com.example.view;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class TableFormatMenu extends ContextMenu {

    public TableFormatMenu(TableOverlay table) {
        this(table, List.of());
    }

    public TableFormatMenu(TableOverlay table, List<MenuItem> extra) {
        TextField rows = field(table.getRowCount());
        TextField cols = field(table.getColumnCount());

        Runnable apply = () -> {
            try {
                int r = Integer.parseInt(rows.getText().trim());
                int c = Integer.parseInt(cols.getText().trim());
                if (r < 1 || r > TableOverlay.MAX_ROWS || c < 1 || c > TableOverlay.MAX_COLS)
                    throw new NumberFormatException();
                table.resizeTable(r, c);
                rows.setStyle("");
                cols.setStyle("");
            } catch (NumberFormatException ex) {
                rows.setStyle("-fx-border-color: red");
                cols.setStyle("-fx-border-color: red");
            }
        };
        rows.setOnAction(e -> apply.run());
        cols.setOnAction(e -> apply.run());

        if (!extra.isEmpty()) {
            getItems().addAll(extra);
            getItems().add(new SeparatorMenuItem());
        }
        getItems().addAll(
                row("Lignes (1-" + TableOverlay.MAX_ROWS + ")", rows),
                row("Colonnes (1-" + TableOverlay.MAX_COLS + ")", cols));
    }

    private static TextField field(int value) {
        TextField f = new TextField(String.valueOf(value));
        f.setPrefColumnCount(4);
        return f;
    }

    private static CustomMenuItem row(String label, Node control) {
        HBox box = new HBox(8, new Label(label), control);
        box.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(box, false);
    }
}