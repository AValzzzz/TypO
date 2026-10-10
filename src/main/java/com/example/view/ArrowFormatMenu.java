package com.example.view;

import com.example.model.i18n.I18n;

import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;

public class ArrowFormatMenu extends FormatMenu {
    public ArrowFormatMenu(ArrowOverlay arrow) {
        Slider opacity = percentSlider(arrow.getStrokeOpacity());
        getItems().addAll(
                colorWithOpacityRow(I18n.t("arrow.color"), arrow.getStrokeColor(), opacity, arrow::setStrokeColor),
                percentRow(I18n.t("common.opacity"), opacity, arrow::setStrokeOpacity),
                sliderRow(I18n.t("arrow.thickness"), new Slider(1, 20, arrow.getStrokeWidth()), arrow::setStrokeWidth),
                new SeparatorMenuItem());
        getItems().addAll(layerItems(arrow));
    }
}
