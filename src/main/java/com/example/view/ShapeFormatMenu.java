package com.example.view;

import com.example.model.i18n.I18n;

import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;

public class ShapeFormatMenu extends FormatMenu {
    public ShapeFormatMenu(ShapeOverlay shape) {
        Slider fillOpacity = percentSlider(shape.getFillOpacity());
        Slider strokeOpacity = percentSlider(shape.getStrokeOpacity());
        getItems().addAll(
                colorWithOpacityRow(I18n.t("shape.fillColor"), shape.getFillColor(), fillOpacity, shape::setFillColor),
                percentRow(I18n.t("shape.fillOpacity"), fillOpacity, shape::setFillOpacity),
                new SeparatorMenuItem(),
                colorWithOpacityRow(I18n.t("shape.borderColor"), shape.getStrokeColor(), strokeOpacity,
                        shape::setStrokeColor),
                percentRow(I18n.t("shape.borderOpacity"), strokeOpacity, shape::setStrokeOpacity),
                sliderRow(I18n.t("shape.borderWidth"), new Slider(0, 30, shape.getStrokeWidth()),
                        shape::setStrokeWidth),
                new SeparatorMenuItem());
        getItems().addAll(layerItems(shape));
    }
}
