package com.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.example.model.language.shapes.ShapeType;
import com.example.model.settings.AppSettings;
import com.example.view.ArrowOverlay;
import com.example.view.ImageOverlay;
import com.example.view.Layerable;
import com.example.view.Motion;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;
import com.example.view.SnapGuides;
import com.example.view.TableOverlay;
import com.example.view.TextBoxOverlay;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

public class Page {
    public static final String SHADOW_KEY = "pageShadow";

    private final Pane pane;
    private final RichTextArea editor;
    private final SnapGuides snapGuides;
    private static final double FOOTER_OFFSET = 22;
    private final Label pageNumberLabel = new Label();
    private final List<ImageOverlay> imageOverlays = new ArrayList<>();
    private final List<ShapeOverlay> shapeOverlays = new ArrayList<>();
    private final List<ArrowOverlay> arrowOverlays = new ArrayList<>();
    private final List<TableOverlay> tableOverlays = new ArrayList<>();
    private final List<TextBoxOverlay> textBoxOverlays = new ArrayList<>();
    private final List<Layerable> layers = new ArrayList<>();
    private Runnable onLayersChanged;

    public Page(Pane pane, RichTextArea textEditor) {
        this.pane = pane;
        this.editor = textEditor;

        installShadow(pane);

        pageNumberLabel.setStyle("-fx-text-fill: BLACK; -fx-font-size: 10px;");
        pageNumberLabel.setAlignment(Pos.CENTER);
        pageNumberLabel.setMouseTransparent(true);
        pageNumberLabel.prefWidthProperty().bind(pane.widthProperty());
        pageNumberLabel.layoutYProperty().bind(pane.heightProperty().subtract(FOOTER_OFFSET));
        pageNumberLabel.visibleProperty().bind(AppSettings.getInstance().showPageNumbersProperty());
        pane.getChildren().add(pageNumberLabel);
        snapGuides = new SnapGuides(pane, this::snapRectsExcept);
    }

    private static void installShadow(Pane pane) {
        Rectangle shadow = new Rectangle();
        shadow.getStyleClass().add("page-shadow");
        shadow.setManaged(false);
        shadow.setMouseTransparent(true);
        shadow.widthProperty().bind(pane.widthProperty());
        shadow.heightProperty().bind(pane.heightProperty());
        shadow.getProperties().put(SHADOW_KEY, Boolean.TRUE);
        shadow.getProperties().put("noExport", Boolean.TRUE);
        pane.getChildren().add(0, shadow);
    }

    public Pane getPane() {
        return pane;
    }

    public RichTextArea getEditor() {
        return editor;
    }

    public String getText() {
        return editor.getText();
    }

    public boolean hasSelection() {
        return editor.hasSelection();
    }

    public ImageOverlay addImageOverlay(Image image, double x, double y, double width, double height,
            String format, String base64) {
        ImageOverlay overlay = new ImageOverlay(image, x, y, width, height, format, base64);
        overlay.setSnap(snapGuides);
        addLayer(overlay);
        imageOverlays.add(overlay);
        Motion.spawn(overlay);
        return overlay;
    }

    public ShapeOverlay addShapeOverlay(ShapeType type, double x, double y, double width, double height) {
        ShapeOverlay overlay = new ShapeOverlay(type, x, y, width, height);
        overlay.setOnDelete(() -> remove(overlay));
        overlay.setSnap(snapGuides);
        addLayer(overlay);
        shapeOverlays.add(overlay);
        Motion.spawn(overlay);
        return overlay;
    }

    public ArrowOverlay addArrowOverlay(double startX, double startY, double endX, double endY,
            double controlX, double controlY) {
        ArrowOverlay overlay = new ArrowOverlay(startX, startY, endX, endY, controlX, controlY);
        overlay.setOnDelete(() -> remove(overlay));
        addLayer(overlay);
        arrowOverlays.add(overlay);
        Motion.spawn(overlay);
        return overlay;
    }

    public TableOverlay addTableOverlay(double x, double y, int rows, int cols, Consumer<RichTextArea> cellSetup) {
        TableOverlay overlay = new TableOverlay(rows, cols, x, y, cellSetup);
        overlay.setOnDelete(() -> {
            remove(overlay);
            editor.requestFocus();
        });
        overlay.setSnap(snapGuides);
        addLayer(overlay);
        tableOverlays.add(overlay);
        Motion.spawn(overlay);
        return overlay;
    }

    public TextBoxOverlay addTextBoxOverlay(double x, double y, double width, Consumer<RichTextArea> cellSetup) {
        TextBoxOverlay overlay = new TextBoxOverlay(x, y, width, cellSetup);
        overlay.setOnDelete(() -> remove(overlay));
        overlay.setSnap(snapGuides);
        addLayer(overlay);
        textBoxOverlays.add(overlay);
        Motion.spawn(overlay);
        return overlay;
    }

    public void applyMargins() {
        AppSettings s = AppSettings.getInstance();
        editor.setPadding(new Insets(
                AppSettings.cmToPx(s.getMarginTop()),
                AppSettings.cmToPx(s.getMarginRight()),
                AppSettings.cmToPx(s.getMarginBottom()),
                AppSettings.cmToPx(s.getMarginLeft())));
    }

    private void addLayer(Layerable overlay) {
        pane.getChildren().add(overlay.node());
        layers.add(overlay);
        overlay.setLayerControl(new Layerable.Control() {
            @Override
            public boolean canMoveUp() {
                return layers.indexOf(overlay) < layers.size() - 1;
            }

            @Override
            public boolean canMoveDown() {
                return layers.indexOf(overlay) > 0;
            }

            @Override
            public void moveUp() {
                moveLayer(overlay, 1);
            }

            @Override
            public void moveDown() {
                moveLayer(overlay, -1);
            }
        });
        applyLayers();
        fireLayersChanged();
    }

    public void remove(Layerable overlay) {
        imageOverlays.remove(overlay);
        shapeOverlays.remove(overlay);
        arrowOverlays.remove(overlay);
        tableOverlays.remove(overlay);
        textBoxOverlays.remove(overlay);
        pane.getChildren().remove(overlay.node());
        if (layers.remove(overlay)) {
            applyLayers();
            fireLayersChanged();
        }
    }

    public List<Layerable> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public void setLayerOrder(List<Layerable> order) {
        layers.sort(Comparator.comparingInt((Layerable l) -> {
            int i = order.indexOf(l);
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        applyLayers();
    }

    public void setOnLayersChanged(Runnable listener) {
        this.onLayersChanged = listener;
    }

    private void fireLayersChanged() {
        if (onLayersChanged != null)
            onLayersChanged.run();
    }

    private void moveLayer(Layerable overlay, int delta) {
        int i = layers.indexOf(overlay);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= layers.size())
            return;
        Collections.swap(layers, i, j);
        applyLayers();
        fireLayersChanged();
    }

    private void applyLayers() {
        for (int i = 0; i < layers.size(); i++) {
            Layerable l = layers.get(i);
            l.setLevel(i);
            l.node().toFront();
        }
        snapGuides.toFront();
    }

    private List<SnapGuides.Rect> snapRectsExcept(Node self) {
        List<SnapGuides.Rect> out = new ArrayList<>();
        for (Layerable l : layers) {
            Node n = l.node();
            if (n == self)
                continue;
            SnapGuides.Rect r = SnapGuides.rectOf(n);
            if (r != null)
                out.add(r);
        }
        return out;
    }

    public void orderLayers(Map<Layerable, Integer> savedLevels) {
        layers.sort(Comparator.comparingInt((Layerable l) -> savedLevels.getOrDefault(l, 0)));
        applyLayers();
    }

    public List<ImageOverlay> getImageOverlays() {
        return imageOverlays;
    }

    public List<ShapeOverlay> getShapeOverlays() {
        return shapeOverlays;
    }

    public List<ArrowOverlay> getArrowOverlays() {
        return arrowOverlays;
    }

    public List<TableOverlay> getTableOverlays() {
        return tableOverlays;
    }

    public List<TextBoxOverlay> getTextBoxOverlays() {
        return textBoxOverlays;
    }

    public void setPageNumber(int number) {
        pageNumberLabel.setText(String.valueOf(number));
    }
}