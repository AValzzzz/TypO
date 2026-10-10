package com.example.view;

import java.util.function.Supplier;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

final class MoveDrag {
    private final Node node;
    private final Supplier<SnapGuides> snap;
    private double pressParentX, pressParentY, pressLayoutX, pressLayoutY;

    MoveDrag(Node node, Supplier<SnapGuides> snap) {
        this.node = node;
        this.snap = snap;
    }

    void begin(MouseEvent e) {
        Point2D p = node.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        pressParentX = p.getX();
        pressParentY = p.getY();
        pressLayoutX = node.getLayoutX();
        pressLayoutY = node.getLayoutY();
    }

    void update(MouseEvent e) {
        Point2D p = node.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = pressLayoutX + (p.getX() - pressParentX);
        double ny = pressLayoutY + (p.getY() - pressParentY);
        SnapGuides guides = snap.get();
        if (guides != null) {
            double[] s = guides.move(node, nx, ny, e.isAltDown());
            nx = s[0];
            ny = s[1];
        }
        node.setLayoutX(nx);
        node.setLayoutY(ny);
    }
}
