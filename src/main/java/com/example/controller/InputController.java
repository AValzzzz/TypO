package com.example.controller;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.example.model.CodeBlockStyler;
import com.example.model.Page;
import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.actions.AlignParagraph;
import com.example.model.actions.DeletePage;
import com.example.model.actions.FormatText;
import com.example.model.actions.Help;
import com.example.model.actions.ImportImage;
import com.example.model.actions.InsertArrow;
import com.example.model.actions.InsertShape;
import com.example.model.actions.NewPage;
import com.example.model.actions.OpenFile;
import com.example.model.actions.Save;
import com.example.model.actions.SaveAs;
import com.example.model.actions.Settings;
import com.example.model.actions.ToggleOrientation;
import com.example.model.io.ColorUtil;
import com.example.model.io.DocumentSession;
import com.example.model.io.PageContent;
import com.example.model.language.BackslashInputHandler;
import com.example.model.language.CommandRegistry;
import com.example.model.language.maths.MathCommands;
import com.example.model.language.shapes.ArrowCommand;
import com.example.model.language.shapes.ShapeCommand;
import com.example.model.settings.AppSettings;
import com.example.view.ArrowOverlay;
import com.example.view.ImageOverlay;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;
import com.example.view.TextFormatMenu;

import javafx.beans.property.DoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.Image;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;

public class InputController {
    @FXML
    private StackPane stackPane;

    @FXML
    private VBox pagesContainer;

    @FXML
    private Pane whitePane;

    @FXML
    private RichTextArea textEditor;

    @FXML
    private ScrollBar hScrollBar;

    @FXML
    private ScrollBar vScrollBar;

    @FXML
    private AnchorPane rootPane;

    private boolean updatingScrollbars = false;

    private final List<Page> pages = new ArrayList<>();
    private ContextMenu activeMenu;
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final DocumentSession session = new DocumentSession();

    private static final double MIN_SCALE = 0.8;
    private static final double MAX_SCALE = 3.0;
    private static final double ZOOM_SENSITIVITY = 0.002;

    private static final double PAN_SENSITIVITY = 1.5;

    @FXML
    public void initialize() {
        MathCommands.registerAll(commandRegistry);
        commandRegistry.register(new ShapeCommand());
        commandRegistry.register(new ArrowCommand());
        Page firstPage = new Page(whitePane, textEditor);
        setupPage(firstPage);
        AppSettings.getInstance().backgroundColorProperty().addListener((obs, o, n) -> applyBackgroundColor(n));
        applyBackgroundColor(AppSettings.getInstance().backgroundColorProperty().get());

        AppSettings.getInstance().selectionColorProperty().addListener((obs, o, n) -> applySelectionColorToAllPages());
        applySelectionColorToAllPages();
        AppSettings st = AppSettings.getInstance();
        for (DoubleProperty p : List.of(st.marginLeftProperty(), st.marginTopProperty(),
                st.marginRightProperty(), st.marginBottomProperty()))
            p.addListener((obs, o, n) -> applyMarginsToAllPages());

        stackPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double zoomFactor = Math.exp(event.getDeltaY() * ZOOM_SENSITIVITY);
                double scale = pagesContainer.getScaleX() * zoomFactor;
                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

                pagesContainer.setScaleX(scale);
                pagesContainer.setScaleY(scale);
            } else if (event.isShiftDown()) {
                double deltaX = event.getDeltaX();
                pagesContainer.setTranslateX(pagesContainer.getTranslateX() + deltaX * PAN_SENSITIVITY);
            } else {
                double deltaY = event.getDeltaY();
                pagesContainer.setTranslateY(pagesContainer.getTranslateY() + deltaY * PAN_SENSITIVITY);
            }
            clampTranslate();
            event.consume();
        });

        hScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingScrollbars)
                return;
            pagesContainer.setTranslateX(-newV.doubleValue());
            clampTranslate();
        });

        vScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingScrollbars)
                return;
            pagesContainer.setTranslateY(-newV.doubleValue());
            clampTranslate();
        });

        stackPane.widthProperty().addListener((obs, o, n) -> clampTranslate());
        stackPane.heightProperty().addListener((obs, o, n) -> clampTranslate());

        stackPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (activeMenu != null && activeMenu.isShowing())
                activeMenu.hide();
        });

        KeyCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        rootPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (saveShortcut.match(event)) {
                        handleSave();
                        event.consume();
                    }
                });
            }
        });

    }

    private void attachContextMenu(Page page) {
        ContextMenu pageMenu = new ContextMenu();
        pageMenu.setAutoHide(true);

        MenuItem toggleOrientationItem = new MenuItem("Toggle Orientation (Portrait/Landscape)");
        toggleOrientationItem.setOnAction(e -> new ToggleOrientation(page).execute());

        MenuItem deletePageItem = new MenuItem("Delete Page");
        deletePageItem.setOnAction(e -> {
            new DeletePage(page, pagesContainer, pages).execute();
            for (int i = 0; i < pages.size(); i++)
                pages.get(i).setPageNumber(i + 1);
            clampTranslate();
        });

        MenuItem importImageItem = new MenuItem("Importer une image");
        importImageItem.setOnAction(e -> {
            Window owner = page.getPane().getScene().getWindow();
            new ImportImage(page, owner).execute();
        });

        pageMenu.getItems().addAll(toggleOrientationItem, deletePageItem, importImageItem);

        page.getPane().addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (isInsideShape(event.getTarget()))
                return;
            ContextMenu menu = page.hasSelection() ? createTextMenu(page) : pageMenu;
            showMenu(menu, page, event);
            event.consume();
        });
    }

    private static boolean isInsideShape(Object target) {
        Node n = target instanceof Node node ? node : null;
        while (n != null) {
            if (n instanceof ShapeOverlay || n instanceof ArrowOverlay)
                return true;
            n = n.getParent();
        }
        return false;
    }

    private ContextMenu createTextMenu(Page page) {
        return new TextFormatMenu(page.getEditor(),
                change -> new FormatText(page, change).execute(),
                alignment -> new AlignParagraph(page, alignment).execute());
    }

    private void showMenu(ContextMenu menu, Page page, ContextMenuEvent event) {
        if (activeMenu != null && activeMenu.isShowing())
            activeMenu.hide();

        activeMenu = menu;
        menu.show(page.getPane(), event.getScreenX(), event.getScreenY());
    }

    private static final double PAN_MARGIN = 45;

    private static double maxTranslate(double scaled, double viewport) {
        return Math.max(0, (scaled - viewport) / 2.0) + PAN_MARGIN;
    }

    private static double clamp(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }

    private void clampTranslate() {
        double viewportWidth = stackPane.getWidth();
        double viewportHeight = stackPane.getHeight();

        double scaledWidth = pagesContainer.prefWidth(-1) * pagesContainer.getScaleX();
        double scaledHeight = pagesContainer.prefHeight(-1) * pagesContainer.getScaleY();

        double maxX = maxTranslate(scaledWidth, viewportWidth);
        double maxY = maxTranslate(scaledHeight, viewportHeight);

        pagesContainer.setTranslateX(clamp(pagesContainer.getTranslateX(), maxX));
        pagesContainer.setTranslateY(clamp(pagesContainer.getTranslateY(), maxY));

        updatingScrollbars = true;
        try {
            configureScrollBar(hScrollBar, maxX, viewportWidth, scaledWidth, pagesContainer.getTranslateX());
            configureScrollBar(vScrollBar, maxY, viewportHeight, scaledHeight, pagesContainer.getTranslateY());
        } finally {
            updatingScrollbars = false;
        }
    }

    private void configureScrollBar(ScrollBar bar, double max, double viewport, double scaled, double translate) {
        bar.setMin(-max);
        bar.setMax(max);
        bar.setVisibleAmount(max > 0
                ? Math.min(2 * max, viewport * (2 * max) / Math.max(scaled, 1))
                : 2 * max);
        bar.setValue(-translate);
        bar.setDisable(max <= 0);
    }

    @FXML
    private void handleSave() {
        new Save(pages, stackPane.getScene().getWindow(), session).execute();
    }

    @FXML
    private void handleSaveAs() {
        new SaveAs(pages, stackPane.getScene().getWindow(), session).execute();
    }

    @FXML
    private void handleOpenFile() {
        new OpenFile(stackPane.getScene().getWindow(), this::loadDocument).execute();
    }

    private void loadDocument(List<PageContent> loadedPages, Path source) {
        session.setCurrentFile(source);
        pagesContainer.getChildren().clear();
        pages.clear();

        if (!loadedPages.isEmpty()) {
            PageContent first = loadedPages.get(0);
            AppSettings.getInstance().setShowPageNumbers(first.showPageNumbers);
            if (AppSettings.areMarginsValid(first.marginLeftCm, first.marginTopCm,
                    first.marginRightCm, first.marginBottomCm)) {
                AppSettings.getInstance().setMarginsCm(first.marginLeftCm, first.marginTopCm,
                        first.marginRightCm, first.marginBottomCm);
            }
        }

        for (PageContent content : loadedPages) {
            Page page = createPage();
            populate(page, content);
            for (PageContent.FloatingImageContent img : content.images) {
                byte[] bytes = Base64.getDecoder().decode(img.base64);
                Image image = new Image(new ByteArrayInputStream(bytes));
                ImageOverlay overlay = page.addImageOverlay(image, img.x, img.y, img.width, img.height, img.format,
                        img.base64);
                overlay.setRotation(img.rotation);
            }
            for (PageContent.FloatingShapeContent s : content.shapes) {
                ShapeOverlay o = page.addShapeOverlay(s.type, s.x, s.y, s.width, s.height);
                o.setFillColor(Color.web("#" + s.fillHex));
                o.setFillOpacity(s.fillOpacity);
                o.setStrokeColor(Color.web("#" + s.strokeHex));
                o.setStrokeOpacity(s.strokeOpacity);
                o.setStrokeWidth(s.strokeWidth);
                o.setRotation(s.rotation);
            }

            for (PageContent.FloatingArrowContent a : content.arrows) {
                ArrowOverlay o = page.addArrowOverlay(a.startX, a.startY, a.endX, a.endY, a.controlX,
                        a.controlY);
                o.setStrokeColor(Color.web("#" + a.strokeHex));
                o.setStrokeOpacity(a.strokeOpacity);
                o.setStrokeWidth(a.strokeWidth);
            }
            if (content.landscape != (page.getPane().getWidth() > page.getPane().getHeight())) {
                new ToggleOrientation(page).execute();
            }
        }
        clampTranslate();
    }

    private void populate(Page page, PageContent content) {
        RichTextArea editor = page.getEditor();
        editor.clear();

        for (int i = 0; i < content.paragraphs.size(); i++) {
            PageContent.ParagraphContent paragraph = content.paragraphs.get(i);
            for (PageContent.RunContent run : paragraph.runs) {
                if (run.isMath()) {
                    editor.appendMathObject(run.math, run.style);
                } else {
                    editor.appendStyledText(run.text, run.style);
                }
            }
            ParagraphStyle style = paragraph.codeBlock
                    ? new ParagraphStyle(AppSettings.getInstance().codeThemeProperty().get(), TextAlignment.LEFT)
                    : new ParagraphStyle(null, paragraph.alignment);
            editor.setParagraphStyle(editor.getParagraphs().size() - 1, style);
            if (i < content.paragraphs.size() - 1) {
                editor.appendStyledText("\n", TextStyle.DEFAULT);
            }
        }
    }

    private Page createPage() {
        NewPage action = new NewPage(pagesContainer);
        action.execute();
        Page created = action.getCreatedPage();
        setupPage(created);
        return created;
    }

    private void applyBackgroundColor(Color color) {
        rootPane.setStyle("-fx-background-color: " + ColorUtil.toCssHex(color) + ";");
    }

    private void applySelectionColorToAllPages() {
        for (Page p : pages)
            applySelectionColor(p.getEditor());
    }

    private void applyMarginsToAllPages() {
        for (Page p : pages)
            p.applyMargins();
    }

    private void applySelectionColor(RichTextArea editor) {
        Color color = AppSettings.getInstance().selectionColorProperty().get();
        String css = ".styled-text-area .selection { -fx-fill: " + ColorUtil.toCssRgba(color) + "; }";
        String base64 = Base64.getEncoder()
                .encodeToString(css.getBytes(StandardCharsets.UTF_8));
        editor.getStylesheets().add("data:text/css;base64," + base64);
    }

    private void setupPage(Page page) {
        pages.add(page);
        for (int i = 0; i < pages.size(); i++)
            pages.get(i).setPageNumber(i + 1);
        page.applyMargins();
        attachContextMenu(page);
        new BackslashInputHandler(page.getEditor(), commandRegistry,
                type -> new InsertShape(page, type).execute(),
                () -> new InsertArrow(page).execute());
        new CodeBlockStyler(page.getEditor());
    }

    @FXML
    private void handleNewPage() {
        createPage();
        clampTranslate();
    }

    @FXML
    private void handleHelp() {
        new Help().execute();
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        Node source = (Node) event.getSource();
        new Settings(source).execute();
    }
}