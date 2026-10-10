package com.example.view;

import com.example.model.io.PageContent;

import javafx.scene.control.ContextMenu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ImageOverlay extends BoxOverlay {
    private static final double MIN_SIZE = 20;

    private final String format;
    private final String base64;
    private double imageOpacity = 1.0;

    public ImageOverlay(Image image, double x, double y, double width, double height, String format, String base64) {
        super(view(image), x, y, width, height, MIN_SIZE);
        this.format = format;
        this.base64 = base64;
    }

    private static ImageView view(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        return view;
    }

    @Override
    protected void resizeContent(double width, double height) {
        ImageView view = (ImageView) content;
        view.setFitWidth(width);
        view.setFitHeight(height);
    }

    @Override
    protected ContextMenu createMenu() {
        return new ImageFormatMenu(this);
    }

    @Override
    public PageContent.FloatingImageContent capture() {
        return PageContent.capture(this);
    }

    public double getImageRotation() {
        return rotation();
    }

    public void setImageOpacity(double opacity) {
        imageOpacity = Math.max(0, Math.min(1, opacity));
        content.setOpacity(imageOpacity);
    }

    public double getImageOpacity() {
        return imageOpacity;
    }

    public double getImageX() {
        return getLayoutX();
    }

    public double getImageY() {
        return getLayoutY();
    }

    public double getImageWidth() {
        return width();
    }

    public double getImageHeight() {
        return height();
    }

    public String getFormat() {
        return format;
    }

    public String getBase64() {
        return base64;
    }
}
