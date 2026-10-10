package com.example.view;

import com.example.model.io.ColorUtil;
import com.example.model.io.PageContent;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;

public class ArrowOverlay extends Group implements Layerable {
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
    private static final MenuSlot MENU = new MenuSlot();

    private double pressSceneX, pressSceneY;
    private double pressStartX, pressStartY, pressEndX, pressEndY, pressControlX, pressControlY;

    public ArrowOverlay(double startX, double startY, double endX, double endY, double controlX, double controlY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.controlX = controlX;
        this.controlY = controlY;

        curve = new QuadCurve();
        curve.setFill(null);
        curve.setMouseTransparent(true);

        hitArea = new QuadCurve();
        hitArea.setFill(null);
        hitArea.setStroke(Color.TRANSPARENT);
        hitArea.setStrokeWidth(14);

        arrowHead = new Polygon();
        arrowHead.setMouseTransparent(true);

        startHandle = Handles.round(Handles.BLUE, Cursor.MOVE);
        endHandle = Handles.round(Handles.BLUE, Cursor.MOVE);
        controlHandle = Handles.round(Handles.GREEN, Cursor.HAND);

        getChildren().addAll(curve, hitArea, arrowHead, startHandle, endHandle, controlHandle);

        applyStyle();
        updateGeometry();

        setFocusTraversable(true);
        installLineDrag();
        installEndpointDrag(startHandle, true);
        installEndpointDrag(endHandle, false);
        installControlDrag();

        Overlays.onOutsidePress(this, () -> {
            if (selected)
                setSelected(false);
        });
        Overlays.deleteOnKey(this, e -> true, this::delete);
        setOnContextMenuRequested(e -> {
            setSelected(true);
            requestFocus();
            MENU.show(new ArrowFormatMenu(this), this, e);
            e.consume();
        });
    }

    private void updateGeometry() {
        curve.setStartX(startX);
        curve.setStartY(startY);
        curve.setEndX(endX);
        curve.setEndY(endY);
        curve.setControlX(controlX);
        curve.setControlY(controlY);

        hitArea.setStartX(startX);
        hitArea.setStartY(startY);
        hitArea.setEndX(endX);
        hitArea.setEndY(endY);
        hitArea.setControlX(controlX);
        hitArea.setControlY(controlY);

        double dx = endX - controlX;
        double dy = endY - controlY;
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
            dx = endX - startX;
            dy = endY - startY;
        }
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double ux = dx / len, uy = dy / len;
        double px = -uy, py = ux;

        double backX = endX - ux * HEAD_LENGTH;
        double backY = endY - uy * HEAD_LENGTH;
        arrowHead.getPoints().setAll(
                endX, endY,
                backX + px * HEAD_WIDTH / 2, backY + py * HEAD_WIDTH / 2,
                backX - px * HEAD_WIDTH / 2, backY - py * HEAD_WIDTH / 2);

        layoutHandle(startHandle, startX, startY);
        layoutHandle(endHandle, endX, endY);
        layoutHandle(controlHandle, controlX, controlY);
    }

    private void layoutHandle(Region handle, double x, double y) {
        handle.setLayoutX(x - Handles.SIZE / 2);
        handle.setLayoutY(y - Handles.SIZE / 2);
    }

    private void applyStyle() {
        Color c = ColorUtil.withOpacity(strokeColor, strokeOpacity);
        curve.setStroke(c);
        curve.setStrokeWidth(strokeWidth);
        arrowHead.setFill(c);
    }

    public void setStrokeColor(Color v) {
        strokeColor = v;
        applyStyle();
    }

    public void setStrokeOpacity(double v) {
        strokeOpacity = v;
        applyStyle();
    }

    public void setStrokeWidth(double v) {
        strokeWidth = v;
        applyStyle();
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

    public void setSelected(boolean v) {
        selected = v;
        updateHandleVisibility();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setHandleSuppressed(boolean suppressed) {
        handleSuppressed = suppressed;
        updateHandleVisibility();
    }

    private void updateHandleVisibility() {
        boolean show = !handleSuppressed && (selected || dragging);
        Motion.fadeVisible(startHandle, show, handleSuppressed);
        Motion.fadeVisible(endHandle, show, handleSuppressed);
        Motion.fadeVisible(controlHandle, show, handleSuppressed);
    }

    public void setOnDelete(Runnable r) {
        onDelete = r;
    }

    public void delete() {
        if (onDelete != null)
            onDelete.run();
    }

    private void installLineDrag() {
        hitArea.setOnMousePressed(e -> {
            setSelected(true);
            requestFocus();
            rememberPress(e);
            e.consume();
        });
        hitArea.setOnMouseDragged(e -> {
            dragBy(e, true, true);
            e.consume();
        });
    }

    private void installEndpointDrag(Region handle, boolean start) {
        handle.setOnMousePressed(e -> {
            setDragging(true);
            rememberPress(e);
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            dragBy(e, start, !start);
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            setDragging(false);
            e.consume();
        });
    }

    private void installControlDrag() {
        controlHandle.setOnMousePressed(e -> {
            setDragging(true);
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
            setDragging(false);
            e.consume();
        });
    }

    private void setDragging(boolean value) {
        dragging = value;
        updateHandleVisibility();
    }

    private void rememberPress(MouseEvent e) {
        pressSceneX = e.getSceneX();
        pressSceneY = e.getSceneY();
        pressStartX = startX;
        pressStartY = startY;
        pressEndX = endX;
        pressEndY = endY;
        pressControlX = controlX;
        pressControlY = controlY;
    }

    private void dragBy(MouseEvent e, boolean moveStart, boolean moveEnd) {
        Point2D p0 = getParent().sceneToLocal(pressSceneX, pressSceneY);
        Point2D p1 = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();
        if (moveStart) {
            startX = pressStartX + dx;
            startY = pressStartY + dy;
        }
        if (moveEnd) {
            endX = pressEndX + dx;
            endY = pressEndY + dy;
        }
        controlX = pressControlX + dx;
        controlY = pressControlY + dy;
        updateGeometry();
    }

    @Override
    public PageContent.FloatingArrowContent capture() {
        return PageContent.capture(this);
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }

    public double getControlX() {
        return controlX;
    }

    public double getControlY() {
        return controlY;
    }
}