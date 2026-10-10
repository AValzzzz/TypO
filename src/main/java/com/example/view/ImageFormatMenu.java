package com.example.view;

import com.example.model.i18n.I18n;

import javafx.scene.control.SeparatorMenuItem;

public class ImageFormatMenu extends FormatMenu {
    public ImageFormatMenu(ImageOverlay image) {
        getItems().addAll(
                percentRow(I18n.t("common.opacity"), percentSlider(image.getImageOpacity()), image::setImageOpacity),
                new SeparatorMenuItem());
        getItems().addAll(layerItems(image));
    }
}
