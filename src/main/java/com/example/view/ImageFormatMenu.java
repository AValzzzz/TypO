package com.example.view;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;

public class ImageFormatMenu extends ContextMenu {
    public ImageFormatMenu(ImageOverlay image) {
        Slider opacity = new Slider(0, 100, image.getImageOpacity() * 100);

        getItems().add(sliderRow("Opacité", opacity, v -> image.setImageOpacity(v / 100)));
        getItems().add(new SeparatorMenuItem());
        getItems().addAll(LayerMenu.items(image));
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