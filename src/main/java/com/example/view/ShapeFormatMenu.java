package com.example.view;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class ShapeFormatMenu extends ContextMenu {
    public ShapeFormatMenu(ShapeOverlay shape) {
        MenuItem delete = new MenuItem("Supprimer la forme");
        delete.setOnAction(e-> shape.delete());

        getItems().addAll(
            colorRow("Couleur du fond", shape.getFillColor(), shape::setFillColor),
            sliderRow("Opacité du fond", 0, 100, shape.getFillOpacity() * 100, v-> shape.setFillOpacity(v/100)),
            new SeparatorMenuItem(),
            colorRow("Couleur de la bordure", shape.getStrokeColor(), shape::setStrokeColor),
            sliderRow("Opacité de la bordure", 0, 100, shape.getStrokeOpacity() * 100, v-> shape.setStrokeOpacity(v/100)),
            sliderRow("Épaisseur de la bordure", 0, 30, shape.getStrokeWidth(), shape::setStrokeWidth),
            new SeparatorMenuItem());
    }

    private MenuItem colorRow(String label, Color current, Consumer<Color> onChange) {
        ColorPicker picker = new ColorPicker(current);
        picker.setOnAction(e-> onChange.accept(picker.getValue()));
        picker.showingProperty().addListener((obs, was, is)->setAutoHide(!is));

        HBox row = new HBox(8, new Label(label), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }

    private MenuItem sliderRow(String label, double min, double max, double value, Consumer<Double> onChange) {
        Slider slider = new Slider(min, max, value);
        slider.setPrefWidth(120);
        Label valueLabel = new Label(String.valueOf(Math.round(value)));
        valueLabel.setMinWidth(28);
        slider.valueProperty().addListener((obs, o, n) -> {
            valueLabel.setText(String.valueOf(Math.round(n.doubleValue())));
            onChange.accept(n.doubleValue());
        });

        HBox row = new HBox(8, new Label(label), slider, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }
}
