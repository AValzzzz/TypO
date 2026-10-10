package com.example.view;

import java.util.List;
import java.util.function.Consumer;

import com.example.model.i18n.I18n;
import com.example.model.io.ColorUtil;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

abstract class FormatMenu extends ContextMenu {

    protected MenuItem colorRow(String label, Color current, double opacity, Consumer<Color> onChange) {
        ColorPicker picker = new ColorPicker(ColorUtil.withOpacity(current, opacity));
        picker.setOnAction(e -> onChange.accept(picker.getValue()));
        picker.showingProperty().addListener((obs, was, is) -> setAutoHide(!is));
        return row(label, picker);
    }

    protected MenuItem colorWithOpacityRow(String label, Color current, Slider opacity, Consumer<Color> setColor) {
        return colorRow(label, current, opacity.getValue() / 100, c -> {
            setColor.accept(ColorUtil.opaque(c));
            if (c.getOpacity() < 1)
                opacity.setValue(c.getOpacity() * 100);
        });
    }

    protected static Slider percentSlider(double fraction) {
        return new Slider(0, 100, fraction * 100);
    }

    protected static MenuItem percentRow(String label, Slider slider, Consumer<Double> onChange) {
        return sliderRow(label, slider, v -> onChange.accept(v / 100));
    }

    protected static MenuItem sliderRow(String label, Slider slider, Consumer<Double> onChange) {
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

    protected static CustomMenuItem row(String label, Node control) {
        HBox box = new HBox(8, new Label(label), control);
        box.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(box, false);
    }

    protected static List<MenuItem> layerItems(Layerable layerable) {
        Layerable.Control control = layerable.getLayerControl();
        MenuItem up = new MenuItem(I18n.t("layer.forward"));
        MenuItem down = new MenuItem(I18n.t("layer.backward"));
        up.setDisable(control == null || !control.canMoveUp());
        down.setDisable(control == null || !control.canMoveDown());
        up.setOnAction(e -> control.moveUp());
        down.setOnAction(e -> control.moveDown());
        return List.of(up, down);
    }
}
