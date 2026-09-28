package com.example.model.actions;

import com.example.model.Page;
import com.example.model.language.shapes.ShapeType;

public class InsertShape implements AppAction{
    private static final double DEFAULT_SIZE = 100;

    private final Page page;
    private final ShapeType type;

    public InsertShape(Page page, ShapeType type) {
        this.page = page;
        this.type = type;
    }

    @Override
    public void execute() {
        double x = (page.getPane().getWidth() - DEFAULT_SIZE) / 2;
        double y = (page.getPane().getHeight() - DEFAULT_SIZE) / 2;
        page.addShapeOverlay(type, x, y, DEFAULT_SIZE, DEFAULT_SIZE);
    }
}
