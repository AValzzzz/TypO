package com.example.view;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

public class ImageOverlay extends Group {
    private static final double MIN_SIZE = 20;

    private final ImageView imageView;
    private final Region resizeHandle;
    private final String format;
    private final String base64;

    private boolean resizing = false;
    private boolean handleSuppressed = false;

    private double pressParentX, pressParentY, pressLayoutX, pressLayoutY;
    private double resizeStartLocalX, resizeStartLocalY, resizeStartWidth, resizeStartHeight;

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

        getChildren().addAll(imageView, resizeHandle);
        setLayoutX(x);
        setLayoutY(y);
        layoutHandle();

        setCursor(Cursor.MOVE);
        hoverProperty().addListener((obs, was, is) -> updateHandleVisibility());
        installDragHandlers();
        installResizeHandlers();
    }

    private void layoutHandle() {
        resizeHandle.setLayoutX(imageView.getFitWidth() - resizeHandle.getPrefWidth() / 2);
        resizeHandle.setLayoutY(imageView.getFitHeight() - resizeHandle.getPrefHeight() / 2);
    }

    private void updateHandleVisibility() {
        resizeHandle.setVisible(!handleSuppressed && (isHover() || resizing));
    }

    public void setHandleSuppressed(boolean suppressed) {
        this.handleSuppressed = suppressed;
        updateHandleVisibility();
    }

    private void installDragHandlers() {
        imageView.setOnMousePressed(e -> {
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

    public double getImageX() { return getLayoutX(); }
    public double getImageY() { return getLayoutY(); }
    public double getImageWidth() { return imageView.getFitWidth(); }
    public double getImageHeight() { return imageView.getFitHeight(); }
    public String getFormat() { return format; }
    public String getBase64() { return base64; }
}