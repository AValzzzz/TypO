package com.example.view;

import java.util.function.Consumer;

import org.fxmisc.richtext.CharacterHit;

import com.example.model.io.CellCodec;
import com.example.model.io.ColorUtil;
import com.example.model.io.PageContent;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class TextBoxOverlay extends Pane implements Layerable {
    public static final double PAD_H = 4;
    public static final double PAD_V = 2;
    private static final double MIN_WIDTH = 30;
    private static final double MIN_HEIGHT = 20;
    private static final double HANDLE = 14;
    private static final String HANDLE_STYLE = "-fx-background-color: #3399ff; -fx-border-color: white; -fx-border-width: 1;";

    private static ContextMenu openMenu;

    private final RichTextArea editor = new RichTextArea();
    private final Region background = new Region();
    private final Rectangle outline = new Rectangle();
    private final Region moveHandle = new Region();
    private final Region widthHandle = new Region();
    private SnapGuides snap;

    private double boxWidth;
    private boolean borderVisible = false;
    private Color borderColor = Color.BLACK;
    private boolean backgroundVisible = false;
    private Color backgroundColor = Color.WHITE;
    private double backgroundOpacity = 1.0;

    private boolean selected, editing, handleSuppressed, layoutScheduled, deleted;
    private double pressParentX, pressParentY, pressLayoutX, pressLayoutY;
    private double widthPressX, widthPressValue;
    private Runnable onDelete;

    private final EventHandler<MouseEvent> deselectFilter = e -> {
        if (!isInside(e.getTarget())) {
            if (selected)
                setSelected(false);
            if (editing)
                stopEditing();
        }
    };

    public TextBoxOverlay(double x, double y, double width, Consumer<RichTextArea> cellSetup) {
        this.boxWidth = Math.max(MIN_WIDTH, width);
        setLayoutX(x);
        setLayoutY(y);

        editor.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        editor.setPadding(new Insets(PAD_V, PAD_H, PAD_V, PAD_H));
        editor.setMouseTransparent(true);
        editor.totalHeightEstimateProperty().addListener((obs, o, n) -> scheduleLayout());
        editor.focusedProperty().addListener((obs, was, is) -> {
            if (!is && editing && (openMenu == null || !openMenu.isShowing()))
                stopEditing();
        });
        editor.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stopEditing();
                requestFocus();
                e.consume();
            }
        });
        editor.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            ContextMenu menu = editor.hasSelection()
                    ? new TextFormatMenu(editor,
                            change -> editor.updateSelectionStyle(change),
                            align -> editor.updateSelectionParagraphStyle(s -> s.withAlignment(align)))
                    : new TextBoxFormatMenu(this);
            showMenu(menu, e);
            e.consume();
        });
        cellSetup.accept(editor);

        outline.setFill(null);
        outline.setStroke(Color.web("#3399FF"));
        outline.getStrokeDashArray().setAll(4.0, 3.0);
        outline.setMouseTransparent(true);

        moveHandle.setStyle(HANDLE_STYLE);
        moveHandle.setCursor(Cursor.MOVE);
        widthHandle.setStyle(HANDLE_STYLE);
        widthHandle.setCursor(Cursor.E_RESIZE);

        getChildren().addAll(background, editor, outline, moveHandle, widthHandle);
        applyStyle();
        updateChrome();

        setCursor(Cursor.MOVE);
        setFocusTraversable(true);
        installHandlers();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null)
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
            if (newScene != null)
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, deselectFilter);
        });
    }

    private void installHandlers() {
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (editing)
                return;
            setSelected(true);
            requestFocus();
            if (e.getButton() != MouseButton.PRIMARY)
                return;
            if (e.getClickCount() == 2) {
                startEditing(e.getSceneX(), e.getSceneY());
            } else {
                beginMove(e);
            }
            e.consume();
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (editing || !e.isPrimaryButtonDown())
                return;
            doMove(e);
            e.consume();
        });
        setOnKeyPressed(e -> {
            if (!editing && e.getTarget() == this
                    && (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)) {
                delete();
                e.consume();
            }
        });
        setOnContextMenuRequested(e -> {
            setSelected(true);
            requestFocus();
            showMenu(new TextBoxFormatMenu(this), e);
            e.consume();
        });

        moveHandle.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY)
                return;
            setSelected(true);
            if (!editing)
                requestFocus();
            beginMove(e);
            e.consume();
        });
        moveHandle.setOnMouseDragged(e -> {
            doMove(e);
            e.consume();
        });

        widthHandle.setOnMousePressed(e -> {
            widthPressX = sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
            widthPressValue = boxWidth;
            e.consume();
        });
        widthHandle.setOnMouseDragged(e -> {
            double dx = sceneToLocal(e.getSceneX(), e.getSceneY()).getX() - widthPressX;
            double w = Math.max(MIN_WIDTH, widthPressValue + dx);
            if (snap != null)
                w = Math.max(MIN_WIDTH, snap.resize(this, w, 0, true, false, e.isAltDown())[0]);
            boxWidth = w;
            requestLayout();
            e.consume();
        });
    }

    private void beginMove(MouseEvent e) {
        Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        pressParentX = p.getX();
        pressParentY = p.getY();
        pressLayoutX = getLayoutX();
        pressLayoutY = getLayoutY();
    }

    private void doMove(MouseEvent e) {
        Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = pressLayoutX + (p.getX() - pressParentX);
        double ny = pressLayoutY + (p.getY() - pressParentY);
        if (snap != null) {
            double[] s = snap.move(this, nx, ny, e.isAltDown());
            nx = s[0];
            ny = s[1];
        }
        setLayoutX(nx);
        setLayoutY(ny);
    }

    private void startEditing(double sceneX, double sceneY) {
        editing = true;
        editor.setMouseTransparent(false);
        editor.requestFocus();
        Point2D p = editor.sceneToLocal(sceneX, sceneY);
        if (p != null) {
            CharacterHit hit = editor.hit(p.getX(), p.getY());
            editor.moveTo(Math.min(hit.getInsertionIndex(), editor.getLength()));
        }
        updateChrome();
    }

    private void stopEditing() {
        if (!editing)
            return;
        editing = false;
        editor.setMouseTransparent(true);
        editor.deselect();
        updateChrome();
        maybeRemove();
    }

    public void setSelected(boolean value) {
        this.selected = value;
        updateChrome();
        if (!value)
            maybeRemove();
    }

    public boolean isSelected() {
        return selected;
    }

    private void maybeRemove() {
        if (!selected && !editing && editor.getLength() == 0)
            delete();
    }

    private void updateChrome() {
        boolean show = !handleSuppressed && (selected || editing);
        outline.setVisible(show);
        moveHandle.setVisible(show);
        widthHandle.setVisible(show);
    }

    public void setHandleSuppressed(boolean suppressed) {
        this.handleSuppressed = suppressed;
        updateChrome();
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

    private void showMenu(ContextMenu menu, ContextMenuEvent e) {
        if (openMenu != null && openMenu.isShowing())
            openMenu.hide();
        openMenu = menu;
        menu.setOnHidden(ev -> {
            if (editing && !editor.isFocused())
                stopEditing();
        });
        menu.show(this, e.getScreenX(), e.getScreenY());
    }

    private double contentHeight() {
        Double est = editor.totalHeightEstimateProperty().getValue();
        double need = (est != null && !est.isNaN()) ? est + 2 * PAD_V : 0;
        return Math.max(MIN_HEIGHT, need);
    }

    private void scheduleLayout() {
        if (layoutScheduled)
            return;
        layoutScheduled = true;
        Platform.runLater(() -> {
            layoutScheduled = false;
            requestLayout();
        });
    }

    @Override
    protected double computePrefWidth(double height) {
        return boxWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return contentHeight();
    }

    @Override
    protected void layoutChildren() {
        double w = boxWidth;
        double h = contentHeight();
        background.resizeRelocate(0, 0, w, h);
        editor.resizeRelocate(0, 0, w, h);
        outline.setX(0);
        outline.setY(0);
        outline.setWidth(w);
        outline.setHeight(h);
        moveHandle.resizeRelocate(-HANDLE, -HANDLE, HANDLE, HANDLE);
        widthHandle.resizeRelocate(w - 5, h / 2 - 6, 10, 12);
    }

    private void applyStyle() {
        StringBuilder css = new StringBuilder();
        if (backgroundVisible) {
            Color c = Color.color(backgroundColor.getRed(), backgroundColor.getGreen(),
                    backgroundColor.getBlue(), Math.max(0, Math.min(1, backgroundOpacity)));
            css.append("-fx-background-color: ").append(ColorUtil.toCssRgba(c)).append(";");
        }
        if (borderVisible)
            css.append("-fx-border-color: ").append(ColorUtil.toCssHex(borderColor))
                    .append("; -fx-border-width: 1;");
        background.setStyle(css.toString());
    }

    public void setBorderVisible(boolean v) {
        borderVisible = v;
        applyStyle();
    }

    public boolean isBorderVisible() {
        return borderVisible;
    }

    public void setBorderColor(Color c) {
        borderColor = c;
        applyStyle();
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBackgroundVisible(boolean v) {
        backgroundVisible = v;
        applyStyle();
    }

    public boolean isBackgroundVisible() {
        return backgroundVisible;
    }

    public void setBackgroundColor(Color c) {
        backgroundColor = c;
        applyStyle();
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundOpacity(double o) {
        backgroundOpacity = o;
        applyStyle();
    }

    public double getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public RichTextArea getEditor() {
        return editor;
    }

    public double getBoxX() {
        return getLayoutX();
    }

    public double getBoxY() {
        return getLayoutY();
    }

    public double getBoxWidth() {
        return boxWidth;
    }

    public String encodeContent() {
        return CellCodec.encode(editor);
    }

    public void load(PageContent.FloatingTextBoxContent c) {
        boxWidth = Math.max(MIN_WIDTH, c.width);
        borderVisible = c.borderVisible;
        if (c.borderHex != null)
            borderColor = Color.web("#" + c.borderHex);
        backgroundVisible = c.backgroundVisible;
        if (c.backgroundHex != null)
            backgroundColor = Color.web("#" + c.backgroundHex);
        backgroundOpacity = c.backgroundOpacity;
        CellCodec.decode(c.cells, editor);
        applyStyle();
        requestLayout();
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    public void delete() {
        if (deleted)
            return;
        deleted = true;
        if (onDelete != null)
            onDelete.run();
    }

    public void setSnap(SnapGuides snap) {
        this.snap = snap;
    }
}
