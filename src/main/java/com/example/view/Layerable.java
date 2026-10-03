package com.example.view;

import javafx.scene.Node;

public interface Layerable {
    String LEVEL_KEY = "layer.level";
    String CONTROL_KEY = "layer.control";

    interface Control {
        boolean canMoveUp();

        boolean canMoveDown();

        void moveUp();

        void moveDown();
    }


    default Node node() {
        return (Node) this;
    }

    default int getLevel() {
        Object v = node().getProperties().get(LEVEL_KEY);
        return v instanceof Integer i ? i : 0;
    }

    default void setLevel(int level) {
        node().getProperties().put(LEVEL_KEY, level);
    }

    default Control getLayerControl() {
        Object c = node().getProperties().get(CONTROL_KEY);
        return c instanceof Control control ? control : null;
    }

    default void setLayerControl(Control control) {
        node().getProperties().put(CONTROL_KEY, control);
    }
}
