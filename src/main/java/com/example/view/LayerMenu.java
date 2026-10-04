package com.example.view;

import java.util.List;

import javafx.scene.control.MenuItem;

public class LayerMenu {
    private LayerMenu() {
    }

    public static List<MenuItem> items(Layerable layerable) {
        Layerable.Control control = layerable.getLayerControl();

        MenuItem up = new MenuItem("Avancer d'un niveau");
        MenuItem down = new MenuItem("Reculer d'un niveau");
        up.setDisable(control == null || !control.canMoveUp());
        down.setDisable(control == null || !control.canMoveDown());
        up.setOnAction(e -> control.moveUp());
        down.setOnAction(e -> control.moveDown());
        return List.of(up, down);
    }
}
