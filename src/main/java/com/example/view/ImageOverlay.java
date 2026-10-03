package com.example.view;

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

public class ImageOverlay extends Group implements Layerable {
    private static final double MIN_SIZE = 20;
    private static final double ROTATE_HANDLE_OFFSET = 30;
    private static final double ROTATE_SNAP_DEGREES = 15;

    private final ImageView imageView;
    private final Region resizeHandle;
    private final Region rotateHandle;
    private final Line rotateLine;
    private final String format;
    private final String base64;

    private static ContextMenu openMenu;

    private double rotation = 0;
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

    public ImageOverlay(Image image, double x, double y, double width, double height, String format, String base64) {
        this.format = format;
        this.base64 = base64;

        imageView = new ImageView(image);
        imageView.setPreserveRatio(false);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);

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

        getChildren().addAll(imageView, rotateLine, resizeHandle, rotateHandle);
        setLayoutX(x);
        setLayoutY(y);
        layoutHandle();

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

        setOnContextMenuRequested(e -> {
            setSelected(true);
            requestFocus();
            if (openMenu != null && openMenu.isShowing())
                openMenu.hide();
            openMenu = new ContextMenu();
            openMenu.getItems().addAll(LayerMenu.items(this));
            openMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void layoutHandle() {
        double w = imageView.getFitWidth();
        double h = imageView.getFitHeight();
        resizeHandle.setLayoutX(w - resizeHandle.getPrefWidth() / 2);
        resizeHandle.setLayoutY(h - resizeHandle.getPrefHeight() / 2);
        layoutRotateHandle();
    }

    private void layoutRotateHandle() {
        double cx = imageView.getFitWidth() / 2;
        rotateHandle.setLayoutX(cx - rotateHandle.getPrefWidth() / 2);
        rotateHandle.setLayoutY(-ROTATE_HANDLE_OFFSET - rotateHandle.getPrefHeight() / 2);
        rotateLine.setStartX(cx);
        rotateLine.setStartY(0);
        rotateLine.setEndX(cx);
        rotateLine.setEndY(-ROTATE_HANDLE_OFFSET);
    }

    public void setRotation(double degrees) {
        this.rotation = ((degrees % 360) + 360) % 360;
        imageView.setRotate(this.rotation);
    }

    public double getImageRotation() {
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
        imageView.setOnMousePressed(e -> {
            setSelected(true);
            requestFocus();
            Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            pressParentX = p.getX();
            pressParentY = p.getY();
            pressLayoutX = getLayoutX();
            pressLayoutY = getLayoutY();
            e.consume();
        });
        imageView.setOnMouseDragged(e -> {
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
            resizeStartWidth = imageView.getFitWidth();
            resizeStartHeight = imageView.getFitHeight();
            resizing = true;
            e.consume();
        });
        resizeHandle.setOnMouseDragged(e -> {
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - resizeStartLocalX;
            double dy = p.getY() - resizeStartLocalY;

            double newWidth;
            double newHeight;
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

            imageView.setFitWidth(newWidth);
            imageView.setFitHeight(newHeight);
            layoutHandle();
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
            Point2D center = localToScene(imageView.getFitWidth() / 2, imageView.getFitHeight() / 2);
            double dx = e.getSceneX() - center.getX();
            double dy = e.getSceneY() - center.getY();

            double angle = Math.toDegrees(Math.atan2(dx, -dy));
            if (e.isControlDown()) {
                angle = Math.round(angle / ROTATE_SNAP_DEGREES) * ROTATE_SNAP_DEGREES;
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

    public double getImageX() {
        return getLayoutX();
    }

    public double getImageY() {
        return getLayoutY();
    }

    public double getImageWidth() {
        return imageView.getFitWidth();
    }

    public double getImageHeight() {
        return imageView.getFitHeight();
    }

    public String getFormat() {
        return format;
    }

    public String getBase64() {
        return base64;
    }
}