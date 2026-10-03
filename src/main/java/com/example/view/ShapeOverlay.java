package com.example.view;

import com.example.model.language.shapes.ShapeType;

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

public class ShapeOverlay extends Group implements Layerable {
    private static final double MIN_SIZE = 10;
    private static final double ROTATE_HANDLE_OFFSET = 30;
    private static final double ROTATE_SNAP_DEGREE = 15;

    private final ShapeType type;
    private final Shape shape;
    private final Region resizeHandle;
    private final Region rotateHandle;
    private final Line rotateLine;
    private static ContextMenu openMenu;

    private double width, height;
    private double rotation = 0;
    private Color fillColor = Color.CORNFLOWERBLUE;
    private Color strokeColor = Color.BLACK;
    private double fillOpacity = 0.4;
    private double strokeOpacity = 1.0;
    private double strokeWidth = 2.0;

    private Runnable onDelete;
    private boolean selected = false;
    private boolean resizing = false;
    private boolean rotating = false;
    private boolean handleSuppressed = false;

    private double pressParentX, pressParentY, pressLayoutX, pressLayoutY;
    private double resizeStartLocalX, resizeStartLocalY, resizeStartWidth, resizeStartHeight;

    private final EventHandler<MouseEvent> deselectFilter = e -> {
        if (selected && !isInside(e.getTarget()))
            setSelected(false);
    };

    public ShapeOverlay(ShapeType type, double x, double y, double width, double height) {
        this.type = type;
        this.shape = switch (type) {
            case CIRCLE -> new Ellipse();
            case SQUARE -> new Rectangle();
            case TRIANGLE -> new Polygon();
        };

        resizeHandle = new Region();
        resizeHandle.setPrefSize(12, 12);
        resizeHandle.setStyle("-fx-background-color: #3399ff; -fx-border-color: white; -fx-border-width: 1;");
        resizeHandle.setCursor(Cursor.SE_RESIZE);
        resizeHandle.setVisible(false);

        rotateHandle = new Region();
        rotateHandle.setPrefSize(12, 12);
        rotateHandle.setStyle("-fx-background-color: #33cc66; -fx-border-color: white; "
                + "-fx-border-width: 1; -fx-background-radius: 6; -fx-border-radius: 6;");
        rotateHandle.setCursor(Cursor.HAND);
        rotateHandle.setVisible(false);

        rotateLine = new Line();
        rotateLine.setStroke(Color.web("#33cc66"));
        rotateLine.setMouseTransparent(true);
        rotateLine.setVisible(false);

        getChildren().addAll(shape, rotateLine, resizeHandle, rotateHandle);
        setLayoutX(x);
        setLayoutY(y);
        applySize(width, height);
        applyStyle();

        setCursor(Cursor.MOVE);
        setFocusTraversable(true);
        installDragHandlers();
        installResizeHandlers();
        installRotateHandlers();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null)
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
            if (newScene != null)
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                delete();
                e.consume();
            }
        });

        setOnContextMenuRequested(e -> {
            setSelected(true);
            requestFocus();
            if (openMenu != null && openMenu.isShowing())
                openMenu.hide();
            openMenu = new ShapeFormatMenu(this);
            openMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void applySize(double w, double h) {
        this.width = w;
        this.height = h;
        if (shape instanceof Ellipse el) {
            el.setCenterX(w / 2);
            el.setCenterY(h / 2);
            el.setRadiusX(w / 2);
            el.setRadiusY(h / 2);
        } else if (shape instanceof Rectangle r) {
            r.setWidth(w);
            r.setHeight(h);
        } else if (shape instanceof Polygon p) {
            p.getPoints().setAll(w / 2, 0.0, w, h, 0.0, h);
        }
        resizeHandle.setLayoutX(w - resizeHandle.getPrefWidth() / 2);
        resizeHandle.setLayoutY(h - resizeHandle.getPrefWidth() / 2);
        layoutRotateHandle();
    }

    private void layoutRotateHandle() {
        double cx = width / 2;
        rotateHandle.setLayoutX(cx - rotateHandle.getPrefWidth() / 2);
        rotateHandle.setLayoutY(-ROTATE_HANDLE_OFFSET - rotateHandle.getPrefHeight() / 2);
        rotateLine.setStartX(cx);
        rotateLine.setStartY(0);
        rotateLine.setEndX(cx);
        rotateLine.setEndY(-ROTATE_HANDLE_OFFSET);
    }

    private static Color withOpacity(Color c, double opacity) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(1, opacity)));
    }

    private void applyStyle() {
        shape.setFill(withOpacity(fillColor, fillOpacity));
        if (strokeWidth <= 0) {
            shape.setStroke(null);
            shape.setStrokeWidth(0);
        } else {
            shape.setStroke(withOpacity(strokeColor, strokeOpacity));
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

    public void setRotation(double degrees) {
        this.rotation = ((degrees % 360) + 360) % 360;
        shape.setRotate(this.rotation);
    }

    public double getShapeRotation() {
        return rotation;
    }

    public void setSelected(boolean value) {
        this.selected = value;
        updateHandleVisibility();
    }

    public boolean isSelected() {
        return selected;
    }

    private boolean isInside(Object target) {
        Node n = target instanceof Node node ? node : null;
        while (n != null) {
            if (n == this)
                return true;
            n = n.getParent();
        }
        return false;
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    public void delete() {
        if (onDelete != null)
            onDelete.run();
    }

    private void updateHandleVisibility() {
        boolean show = !handleSuppressed && (selected || resizing || rotating);
        resizeHandle.setVisible(show);
        rotateHandle.setVisible(show);
        rotateLine.setVisible(show);
    }

    public void setHandleSuppressed(boolean suppressed) {
        this.handleSuppressed = suppressed;
        updateHandleVisibility();
    }

    private void installDragHandlers() {
        shape.setOnMousePressed(e -> {
            setSelected(true);
            requestFocus();
            if (!e.isPrimaryButtonDown())
                return;

            Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            pressParentX = p.getX();
            pressParentY = p.getY();
            pressLayoutX = getLayoutX();
            pressLayoutY = getLayoutY();
            e.consume();
        });

        shape.setOnMouseDragged(e -> {
            if (!e.isPrimaryButtonDown())
                return;
            Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            setLayoutX(pressLayoutX + (p.getX() - pressParentX));
            setLayoutY(pressLayoutY + (p.getY() - pressParentY));
            e.consume();
        });
    }

    private void installResizeHandlers() {
        resizeHandle.setOnMousePressed(e -> {
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            resizeStartLocalX = p.getX();
            resizeStartLocalY = p.getY();
            resizeStartWidth = width;
            resizeStartHeight = height;
            resizing = true;
            e.consume();
        });

        resizeHandle.setOnMouseDragged(e -> {
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - resizeStartLocalX;
            double dy = p.getY() - resizeStartLocalY;

            double newWidth, newHeight;
            if (e.isControlDown()) {
                double scale = Math.max((resizeStartWidth + dx) / resizeStartWidth,
                        (resizeStartHeight + dy) / resizeStartHeight);
                scale = Math.max(scale, Math.max(MIN_SIZE / resizeStartWidth, MIN_SIZE / resizeStartHeight));
                newWidth = resizeStartWidth * scale;
                newHeight = resizeStartHeight * scale;
            } else {
                newWidth = Math.max(MIN_SIZE, resizeStartWidth + dx);
                newHeight = Math.max(MIN_SIZE, resizeStartHeight + dy);
            }
            applySize(newWidth, newHeight);
            e.consume();
        });
        resizeHandle.setOnMouseReleased(e -> {
            resizing = false;
            updateHandleVisibility();
            e.consume();
        });
    }

    private void installRotateHandlers() {
        rotateHandle.setOnMousePressed(e -> {
            rotating = true;
            updateHandleVisibility();
            e.consume();
        });

        rotateHandle.setOnMouseDragged(e -> {
            Point2D center = localToScene(width / 2, height / 2);
            double dx = e.getSceneX() - center.getX();
            double dy = e.getSceneY() - center.getY();

            double angle = Math.toDegrees(Math.atan2(dx, -dy));
            if (e.isControlDown()) {
                angle = Math.round(angle / ROTATE_SNAP_DEGREE) * ROTATE_SNAP_DEGREE;
            }
            setRotation(angle);
            e.consume();
        });

        rotateHandle.setOnMouseReleased(e -> {
            rotating = false;
            updateHandleVisibility();
            e.consume();
        });
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
        return width;
    }

    public double getShapeHeight() {
        return height;
    }
}
