package com.example.model;

import java.util.ArrayList;
import java.util.List;

import com.example.model.language.shapes.ShapeType;
import com.example.view.ImageOverlay;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;

import javafx.scene.image.Image;
import javafx.scene.layout.Pane;

public class Page {
    private final Pane pane;
    private final RichTextArea editor;
    private final List<ImageOverlay> imageOverlays = new ArrayList<>();
    private final List<ShapeOverlay> shapeOverlays = new ArrayList<>();

    public Page(Pane pane, RichTextArea textEditor) {
        this.pane = pane;
        this.editor = textEditor;
    }

    public Pane getPane() { return pane; }
    public RichTextArea getEditor() { return editor; }
    public String getText() { return editor.getText(); }
    public boolean hasSelection() { return editor.hasSelection(); }

    public ImageOverlay addImageOverlay(Image image, double x, double y, double width, double height,
            String format, String base64) {
        ImageOverlay overlay = new ImageOverlay(image, x, y, width, height, format, base64);
        pane.getChildren().add(overlay);
        imageOverlays.add(overlay);
        return overlay;
    }

    public ShapeOverlay addShapeOverlay(ShapeType type, double x, double y, double width, double height) {
        ShapeOverlay overlay = new ShapeOverlay(type, x, y, width, height);
        overlay.setOnDelete(() -> {
            pane.getChildren().remove(overlay);
            shapeOverlays.remove(overlay);
        });
        pane.getChildren().add(overlay);
        shapeOverlays.add(overlay);
        return overlay;
    }

    public List<ImageOverlay> getImageOverlays() { return imageOverlays; }
    public List<ShapeOverlay> getShapeOverlays() {return shapeOverlays;}
}