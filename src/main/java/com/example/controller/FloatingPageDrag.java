package com.example.controller;

import java.util.List;
import java.util.function.Consumer;

import com.example.model.Page;
import com.example.model.io.PageContent.FloatingContent;
import com.example.view.Layerable;
import com.example.view.Motion;
import com.example.view.Nodes;
import com.example.view.RichTextArea;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

final class FloatingPageDrag {
    private final List<Page> pages;
    private final Consumer<RichTextArea> cellSetup;

    private Layerable layer;
    private Page source;
    private Bounds pressBounds;

    FloatingPageDrag(List<Page> pages, Consumer<RichTextArea> cellSetup) {
        this.pages = pages;
        this.cellSetup = cellSetup;
    }

    void install(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> released());
    }

    private void pressed(MouseEvent e) {
        end();
        if (e.getButton() != MouseButton.PRIMARY)
            return;
        Layerable l = Nodes.ancestor(e.getTarget(), Layerable.class);
        Page p = l == null ? null : Page.owning(l.node(), pages);
        if (p == null)
            return;
        layer = l;
        source = p;
        pressBounds = l.node().getBoundsInParent();
        source.getPane().setViewOrder(-1);
    }

    private void released() {
        Layerable l = layer;
        Page from = source;
        Bounds before = pressBounds;
        end();
        if (l == null || !pages.contains(from) || Page.owning(l.node(), pages) != from)
            return;
        Bounds b = l.node().getBoundsInParent();
        if (b.equals(before))
            return;
        Point2D center = from.getPane().localToScene(b.getCenterX(), b.getCenterY());
        Page to = pageAt(center);
        if (to == null || to == from)
            return;
        hop(l, from, to);
    }

    private void end() {
        if (source != null)
            source.getPane().setViewOrder(0);
        layer = null;
        source = null;
        pressBounds = null;
    }

    private void hop(Layerable l, Page from, Page to) {
        Point2D origin = to.getPane().sceneToLocal(from.getPane().localToScene(0, 0));
        FloatingContent content = l.capture().translated(origin.getX(), origin.getY());

        Layerable moved;
        Motion.setQuiet(true);
        try {
            moved = content.createOn(to, cellSetup);
        } finally {
            Motion.setQuiet(false);
        }
        if (moved == null)
            return;
        from.remove(l);
        for (Layerable other : to.getLayers())
            other.setSelected(other == moved);
        moved.node().requestFocus();
    }
    private Page pageAt(Point2D scenePoint) {
        for (Page p : pages) {
            Point2D local = p.getPane().sceneToLocal(scenePoint);
            if (local != null && p.getPane().getLayoutBounds().contains(local))
                return p;
        }
        return null;
    }
}
