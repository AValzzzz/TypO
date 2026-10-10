package com.example.view;

import com.example.model.i18n.I18n;
import com.example.model.io.ColorUtil;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.SeparatorMenuItem;

public class TextBoxFormatMenu extends FormatMenu {
    public TextBoxFormatMenu(TextBoxOverlay box) {
        CheckMenuItem border = new CheckMenuItem(I18n.t("textbox.borderVisible"));
        border.setSelected(box.isBorderVisible());
        CheckMenuItem fill = new CheckMenuItem(I18n.t("textbox.bgVisible"));
        fill.setSelected(box.isBackgroundVisible());
        fill.setOnAction(e -> box.setBackgroundVisible(fill.isSelected()));

        getItems().addAll(
                border,
                colorRow(I18n.t("textbox.borderColor"), box.getBorderColor(), 1.0, c -> {
                    box.setBorderColor(ColorUtil.opaque(c));
                    box.setBorderVisible(true);
                    border.setSelected(true);
                }),
                new SeparatorMenuItem(),
                fill,
                colorRow(I18n.t("textbox.bgColor"), box.getBackgroundColor(), box.getBackgroundOpacity(), c -> {
                    box.setBackgroundColor(ColorUtil.opaque(c));
                    box.setBackgroundOpacity(c.getOpacity());
                    box.setBackgroundVisible(true);
                    fill.setSelected(true);
                }),
                new SeparatorMenuItem());
        getItems().addAll(layerItems(box));
    }
}
