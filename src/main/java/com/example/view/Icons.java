package com.example.view;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;

public final class Icons {
    public static final String OPEN = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z";
    public static final String SAVE = "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z "
            + "M17 21v-8H7v8 M7 3v5h8";
    public static final String SAVE_AS = "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3";
    public static final String NEW_PAGE = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z "
            + "M14 2v6h6 M12 18v-6 M9 15h6";
    public static final String HELP = "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z "
            + "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3 M12 17h0.01";
    public static final String SETTINGS = "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 "
            + "M1 14h6 M9 8h6 M17 16h6";

    private Icons() {
    }

    public static Node of(String path) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("icon");
        p.setScaleX(0.75);
        p.setScaleY(0.75);
        return new Group(p);
    }

    public static void decorate(Parent root, String selector, String path) {
        if (!(root.lookup(selector) instanceof Button b))
            return;
        String text = b.getText();
        b.setGraphic(of(path));
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (text != null && !text.isBlank())
            b.setTooltip(new Tooltip(text));
    }
}