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

public class ArrowFormatMenu extends ContextMenu {
    public ArrowFormatMenu(ArrowOverlay arrow) {
        Slider opacity = new Slider(0, 100, arrow.getStrokeOpacity() * 100);

        getItems().addAll(
                colorRow("Couleur", arrow.getStrokeColor(), arrow.getStrokeOpacity(), c -> {
                    arrow.setStrokeColor(Color.color(c.getRed(), c.getGreen(), c.getBlue()));
                    if (c.getOpacity() < 1)
                        opacity.setValue(c.getOpacity() * 100);
                }),
                sliderRow("Opacité", opacity, v -> arrow.setStrokeOpacity(v / 100)),
                sliderRow("Épaisseur", new Slider(1, 20, arrow.getStrokeWidth()), arrow::setStrokeWidth));
        getItems().add(new SeparatorMenuItem());
        getItems().addAll(LayerMenu.items(arrow));
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

    private MenuItem sliderRow(String label, Slider slider, Consumer<Double> onChange) {
        slider.setPrefWidth(120);
        Label valueLabel = new Label(String.valueOf(Math.round(slider.getValue())));
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