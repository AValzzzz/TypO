package com.example.view;

import java.util.List;

import com.example.model.i18n.I18n;

import javafx.scene.control.MenuItem;

public class LayerMenu {
    private LayerMenu() {
    }

    public static List<MenuItem> items(Layerable layerable) {
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
