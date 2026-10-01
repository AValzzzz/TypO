package com.example.view;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;

public class ArrowOverlay extends Group {
    private static final double HANDLE_SIZE = 12;
    private static final double HEAD_LENGTH = 14;
    private static final double HEAD_WIDTH = 10;

    private double startX, startY, endX, endY, controlX, controlY;

    private final QuadCurve curve;
    private final QuadCurve hitArea; 
    private final Polygon arrowHead;
    private final Region startHandle, endHandle, controlHandle;

    private Color strokeColor = Color.BLACK;
    private double strokeOpacity = 1.0;
    private double strokeWidth = 2.5;

    private boolean selected = false;
    private boolean dragging = false;
    private boolean handleSuppressed = false;
    private Runnable onDelete;
    private static ContextMenu openMenu;

    private double pressSceneX, pressSceneY;
    private double pressStartX, pressStartY, pressEndX, pressEndY, pressControlX, pressControlY;

    public ArrowOverlay(double startX, double startY, double endX, double endY, double controlX, double controlY) {
        this.startX = startX; this.startY = startY;
        this.endX = endX; this.endY = endY;
        this.controlX = controlX; this.controlY = controlY;

        curve = new QuadCurve();
        curve.setFill(null);
        curve.setMouseTransparent(true);

        hitArea = new QuadCurve();
        hitArea.setFill(null);
        hitArea.setStroke(Color.TRANSPARENT);
        hitArea.setStrokeWidth(14);

        arrowHead = new Polygon();
        arrowHead.setMouseTransparent(true);

        startHandle = makeHandle("#3399ff", Cursor.MOVE);
        endHandle = makeHandle("#3399ff", Cursor.MOVE);
        controlHandle = makeHandle("#33cc66", Cursor.HAND);

        getChildren().addAll(curve, hitArea, arrowHead, startHandle, endHandle, controlHandle);

        applyStyle();
        updateGeometry();

        setFocusTraversable(true);
        installLineDrag();
        installHandleDrag(startHandle, true, false);
        installHandleDrag(endHandle, false, true);
        installControlDrag();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
            if (newScene != null) newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
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
            if (openMenu != null && openMenu.isShowing()) openMenu.hide();
            openMenu = new ArrowFormatMenu(this);
            openMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private final javafx.event.EventHandler<MouseEvent> deselectFilter = e -> {
        if (selected && !isInside(e.getTarget())) setSelected(false);
    };

    private Region makeHandle(String color, Cursor cursor) {
        Region r = new Region();
        r.setPrefSize(HANDLE_SIZE, HANDLE_SIZE);
        r.setStyle("-fx-background-color: " + color + "; -fx-border-color: white; -fx-border-width: 1; "
                + "-fx-background-radius: 6; -fx-border-radius: 6;");
        r.setCursor(cursor);
        r.setVisible(false);
        return r;
    }

    private void updateGeometry() {
        curve.setStartX(startX); curve.setStartY(startY);
        curve.setEndX(endX); curve.setEndY(endY);
        curve.setControlX(controlX); curve.setControlY(controlY);

        hitArea.setStartX(startX); hitArea.setStartY(startY);
        hitArea.setEndX(endX); hitArea.setEndY(endY);
        hitArea.setControlX(controlX); hitArea.setControlY(controlY);

        double dx = endX - controlX;
        double dy = endY - controlY;
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) { dx = endX - startX; dy = endY - startY; }
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double ux = dx / len, uy = dy / len;
        double px = -uy, py = ux;

        double backX = endX - ux * HEAD_LENGTH;
        double backY = endY - uy * HEAD_LENGTH;
        arrowHead.getPoints().setAll(
                endX, endY,
                backX + px * HEAD_WIDTH / 2, backY + py * HEAD_WIDTH / 2,
                backX - px * HEAD_WIDTH / 2, backY - py * HEAD_WIDTH / 2
        );

        layoutHandle(startHandle, startX, startY);
        layoutHandle(endHandle, endX, endY);
        layoutHandle(controlHandle, controlX, controlY);
    }

    private void layoutHandle(Region handle, double x, double y) {
        handle.setLayoutX(x - HANDLE_SIZE / 2);
        handle.setLayoutY(y - HANDLE_SIZE / 2);
    }

    private static Color withOpacity(Color c, double opacity) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(1, opacity)));
    }

    private void applyStyle() {
        Color c = withOpacity(strokeColor, strokeOpacity);
        curve.setStroke(c);
        curve.setStrokeWidth(strokeWidth);
        arrowHead.setFill(c);
    }

    public void setStrokeColor(Color v) { strokeColor = v; applyStyle(); }
    public void setStrokeOpacity(double v) { strokeOpacity = v; applyStyle(); }
    public void setStrokeWidth(double v) { strokeWidth = v; applyStyle(); }
    public Color getStrokeColor() { return strokeColor; }
    public double getStrokeOpacity() { return strokeOpacity; }
    public double getStrokeWidth() { return strokeWidth; }

    public void setSelected(boolean v) { selected = v; updateHandleVisibility(); }
    public boolean isSelected() { return selected; }

    public void setHandleSuppressed(boolean suppressed) {
        handleSuppressed = suppressed;
        updateHandleVisibility();
    }

    private void updateHandleVisibility() {
        boolean show = !handleSuppressed && (selected || dragging);
        startHandle.setVisible(show);
        endHandle.setVisible(show);
        controlHandle.setVisible(show);
    }

    private boolean isInside(Object target) {
        javafx.scene.Node n = target instanceof javafx.scene.Node node ? node : null;
        while (n != null) {
            if (n == this) return true;
            n = n.getParent();
        }
        return false;
    }

    public void setOnDelete(Runnable r) { onDelete = r; }
    public void delete() { if (onDelete != null) onDelete.run(); }

    private void installLineDrag() {
        hitArea.setOnMousePressed(e -> {
            setSelected(true);
            requestFocus();
            pressSceneX = e.getSceneX();
            pressSceneY = e.getSceneY();
            pressStartX = startX; pressStartY = startY;
            pressEndX = endX; pressEndY = endY;
            pressControlX = controlX; pressControlY = controlY;
            e.consume();
        });
        hitArea.setOnMouseDragged(e -> {
            Point2D p0 = getParent().sceneToLocal(pressSceneX, pressSceneY);
            Point2D p1 = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();
            startX = pressStartX + dx; startY = pressStartY + dy;
            endX = pressEndX + dx; endY = pressEndY + dy;
            controlX = pressControlX + dx; controlY = pressControlY + dy;
            updateGeometry();
            e.consume();
        });
    }

    private void installHandleDrag(Region handle, boolean isStart, boolean isEnd) {
        handle.setOnMousePressed(e -> {
            dragging = true;
            updateHandleVisibility();
            pressSceneX = e.getSceneX();
            pressSceneY = e.getSceneY();
            pressStartX = startX; pressStartY = startY;
            pressEndX = endX; pressEndY = endY;
            pressControlX = controlX; pressControlY = controlY;
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            Point2D p0 = getParent().sceneToLocal(pressSceneX, pressSceneY);
            Point2D p1 = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();

            if (isStart) {
                startX = pressStartX + dx; startY = pressStartY + dy;
                controlX = pressControlX + dx; controlY = pressControlY + dy;
            }
            if (isEnd) {
                endX = pressEndX + dx; endY = pressEndY + dy;
                controlX = pressControlX + dx; controlY = pressControlY + dy;
            }
            updateGeometry();
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            dragging = false;
            updateHandleVisibility();
            e.consume();
        });
    }

    private void installControlDrag() {
        controlHandle.setOnMousePressed(e -> {
            dragging = true;
            updateHandleVisibility();
            e.consume();
        });
        controlHandle.setOnMouseDragged(e -> {
            Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            controlX = p.getX();
            controlY = p.getY();
            updateGeometry();
            e.consume();
        });
        controlHandle.setOnMouseReleased(e -> {
            dragging = false;
            updateHandleVisibility();
            e.consume();
        });
    }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
    public double getControlX() { return controlX; }
    public double getControlY() { return controlY; }
}