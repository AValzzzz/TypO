package com.example.view;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

public abstract class BoxOverlay extends Group implements Layerable {
    private static final double ROTATE_HANDLE_OFFSET = 30;
    private static final double ROTATE_SNAP_DEGREES = 15;
    private static final MenuSlot MENU = new MenuSlot();

    protected final Node content;
    private final double minSize;
    private final Region resizeHandle = Handles.sizedSquare(Cursor.SE_RESIZE);
    private final Region rotateHandle = Handles.round(Handles.GREEN, Cursor.HAND);
    private final Line rotateLine = new Line();
    private final MoveDrag move = new MoveDrag(this, () -> this.snap);
    private SnapGuides snap;
    private Runnable onDelete;

    private double width, height;
    private double rotation;
    private boolean selected, resizing, rotating, handleSuppressed;
    private double resizeStartLocalX, resizeStartLocalY, resizeStartWidth, resizeStartHeight;

    protected BoxOverlay(Node content, double x, double y, double width, double height, double minSize) {
        this.content = content;
        this.minSize = minSize;

        rotateLine.setStroke(Color.web(Handles.GREEN));
        rotateLine.setMouseTransparent(true);
        rotateLine.setVisible(false);

        getChildren().addAll(content, rotateLine, resizeHandle, rotateHandle);
        setLayoutX(x);
        setLayoutY(y);
        setSize(width, height);

        setCursor(Cursor.MOVE);
        setFocusTraversable(true);
        installMoveHandlers();
        installResizeHandlers();
        installRotateHandlers();

        Overlays.onOutsidePress(this, () -> {
            if (selected)
                setSelected(false);
        });
        Overlays.deleteOnKey(this, e -> onDelete != null, this::delete);
        setOnContextMenuRequested(e -> {
            setSelected(true);
            requestFocus();
            MENU.show(createMenu(), this, e);
            e.consume();
        });
    }

    protected abstract void resizeContent(double width, double height);

    protected abstract ContextMenu createMenu();

    protected final void setSize(double w, double h) {
        width = w;
        height = h;
        resizeContent(w, h);
        resizeHandle.setLayoutX(w - Handles.SIZE / 2);
        resizeHandle.setLayoutY(h - Handles.SIZE / 2);
        double cx = w / 2;
        rotateHandle.setLayoutX(cx - Handles.SIZE / 2);
        rotateHandle.setLayoutY(-ROTATE_HANDLE_OFFSET - Handles.SIZE / 2);
        rotateLine.setStartX(cx);
        rotateLine.setStartY(0);
        rotateLine.setEndX(cx);
        rotateLine.setEndY(-ROTATE_HANDLE_OFFSET);
    }

    protected final double width() {
        return width;
    }

    protected final double height() {
        return height;
    }

    public void setRotation(double degrees) {
        rotation = ((degrees % 360) + 360) % 360;
        content.setRotate(rotation);
    }

    protected final double rotation() {
        return rotation;
    }

    @Override
    public void setSelected(boolean value) {
        selected = value;
        updateHandleVisibility();
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setHandleSuppressed(boolean suppressed) {
        handleSuppressed = suppressed;
        updateHandleVisibility();
    }

    public void setSnap(SnapGuides snap) {
        this.snap = snap;
    }

    public void setOnDelete(Runnable r) {
        onDelete = r;
    }

    public void delete() {
        if (onDelete != null)
            onDelete.run();
    }

    private void updateHandleVisibility() {
        boolean show = !handleSuppressed && (selected || resizing || rotating);
        Motion.fadeVisible(resizeHandle, show, handleSuppressed);
        Motion.fadeVisible(rotateHandle, show, handleSuppressed);
        Motion.fadeVisible(rotateLine, show, handleSuppressed);
    }

    private void installMoveHandlers() {
        content.setOnMousePressed(e -> {
            setSelected(true);
            requestFocus();
            if (e.isPrimaryButtonDown())
                move.begin(e);
            e.consume();
        });
        content.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown())
                move.update(e);
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
            if (e.isControlDown())
                resizeProportionally(dx, dy);
            else
                resizeFreely(dx, dy, e);
            e.consume();
        });
        resizeHandle.setOnMouseReleased(e -> endGesture(e));
    }

    private void resizeProportionally(double dx, double dy) {
        if (snap != null)
            snap.clear();
        double scale = Math.max((resizeStartWidth + dx) / resizeStartWidth,
                (resizeStartHeight + dy) / resizeStartHeight);
        scale = Math.max(scale, Math.max(minSize / resizeStartWidth, minSize / resizeStartHeight));
        setSize(resizeStartWidth * scale, resizeStartHeight * scale);
    }

    private void resizeFreely(double dx, double dy, MouseEvent e) {
        double w = Math.max(minSize, resizeStartWidth + dx);
        double h = Math.max(minSize, resizeStartHeight + dy);
        if (snap != null) {
            double[] s = snap.resize(this, w, h, true, true, e.isAltDown());
            w = Math.max(minSize, s[0]);
            h = Math.max(minSize, s[1]);
        }
        setSize(w, h);
    }

    private void installRotateHandlers() {
        rotateHandle.setOnMousePressed(e -> {
            rotating = true;
            updateHandleVisibility();
            e.consume();
        });
        rotateHandle.setOnMouseDragged(e -> {
            Point2D center = localToScene(width / 2, height / 2);
            double angle = Math.toDegrees(Math.atan2(e.getSceneX() - center.getX(), -(e.getSceneY() - center.getY())));
            if (e.isControlDown())
                angle = Math.round(angle / ROTATE_SNAP_DEGREES) * ROTATE_SNAP_DEGREES;
            setRotation(angle);
            e.consume();
        });
        rotateHandle.setOnMouseReleased(e -> endGesture(e));
    }

    private void endGesture(MouseEvent e) {
        resizing = false;
        rotating = false;
        updateHandleVisibility();
        e.consume();
    }
}
