package com.example.view;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class TextBoxFormatMenu extends ContextMenu {
    public TextBoxFormatMenu(TextBoxOverlay box) {
        CheckMenuItem border = new CheckMenuItem("Bordure visible");
        border.setSelected(box.isBorderVisible());
        border.setOnAction(e -> box.setBorderVisible(border.isSelected()));

        CheckMenuItem fill = new CheckMenuItem("Fond visible");
        fill.setSelected(box.isBackgroundVisible());
        fill.setOnAction(e -> box.setBackgroundVisible(fill.isSelected()));

        getItems().addAll(
                border,
                colorRow("Couleur de la bordure", box.getBorderColor(), 1.0, c -> {
                    box.setBorderColor(opaque(c));
                    box.setBorderVisible(true);
                    border.setSelected(true);
                }),
                new SeparatorMenuItem(),
                fill,
                colorRow("Couleur du fond", box.getBackgroundColor(), box.getBackgroundOpacity(), c -> {
                    box.setBackgroundColor(opaque(c));
                    box.setBackgroundOpacity(c.getOpacity());
                    box.setBackgroundVisible(true);
                    fill.setSelected(true);
                }),
                new SeparatorMenuItem());
        getItems().addAll(LayerMenu.items(box));
    }

    private static Color opaque(Color c) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue());
    }

    private MenuItem colorRow(String label, Color current, double opacity, Consumer<Color> onChange) {
        ColorPicker picker = new ColorPicker(
                Color.color(current.getRed(), current.getGreen(), current.getBlue(), opacity));
        picker.setOnAction(e -> onChange.accept(picker.getValue()));
        picker.showingProperty().addListener((obs, was, is) -> setAutoHide(!is));

        HBox row = new HBox(8, new Label(label), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }
}
