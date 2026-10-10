package com.example.view;

import com.example.model.io.ColorUtil;
import com.example.model.io.PageContent;
import com.example.model.language.shapes.ShapeType;

import javafx.scene.control.ContextMenu;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

public class ShapeOverlay extends BoxOverlay {
    private static final double MIN_SIZE = 10;

    private final ShapeType type;
    private final Shape shape;

    private Color fillColor = Color.CORNFLOWERBLUE;
    private Color strokeColor = Color.BLACK;
    private double fillOpacity = 0.4;
    private double strokeOpacity = 1.0;
    private double strokeWidth = 2.0;

    public ShapeOverlay(ShapeType type, double x, double y, double width, double height) {
        super(shapeOf(type), x, y, width, height, MIN_SIZE);
        this.type = type;
        this.shape = (Shape) content;
        applyStyle();
    }

    private static Shape shapeOf(ShapeType type) {
        return switch (type) {
            case CIRCLE -> new Ellipse();
            case SQUARE -> new Rectangle();
            case TRIANGLE -> new Polygon();
        };
    }

    @Override
    protected void resizeContent(double w, double h) {
        switch (content) {
            case Ellipse el -> {
                el.setCenterX(w / 2);
                el.setCenterY(h / 2);
                el.setRadiusX(w / 2);
                el.setRadiusY(h / 2);
            }
            case Rectangle r -> {
                r.setWidth(w);
                r.setHeight(h);
            }
            case Polygon p -> p.getPoints().setAll(w / 2, 0.0, w, h, 0.0, h);
            default -> {
            }
        }
    }

    @Override
    protected ContextMenu createMenu() {
        return new ShapeFormatMenu(this);
    }

    @Override
    public PageContent.FloatingShapeContent capture() {
        return PageContent.capture(this);
    }

    private void applyStyle() {
        shape.setFill(ColorUtil.withOpacity(fillColor, fillOpacity));
        if (strokeWidth <= 0) {
            shape.setStroke(null);
            shape.setStrokeWidth(0);
        } else {
            shape.setStroke(ColorUtil.withOpacity(strokeColor, strokeOpacity));
            shape.setStrokeWidth(strokeWidth);
        }
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        applyStyle();
    }

    public void setFillOpacity(double fillOpacity) {
        this.fillOpacity = fillOpacity;
        applyStyle();
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor;
        applyStyle();
    }

    public void setStrokeOpacity(double strokeOpacity) {
        this.strokeOpacity = strokeOpacity;
        applyStyle();
    }

    public void setStrokeWidth(double strokeWidth) {
        this.strokeWidth = strokeWidth;
        applyStyle();
    }

    public Color getFillColor() {
        return fillColor;
    }

    public double getFillOpacity() {
        return fillOpacity;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public double getStrokeOpacity() {
        return strokeOpacity;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public double getShapeRotation() {
        return rotation();
    }

    public ShapeType getShapeType() {
        return type;
    }

    public double getShapeX() {
        return getLayoutX();
    }

    public double getShapeY() {
        return getLayoutY();
    }

    public double getShapeWidth() {
        return width();
    }

    public double getShapeHeight() {
        return height();
    }
}
