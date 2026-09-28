package com.example.controller;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.example.model.CodeBlockStyler;
import com.example.model.Page;
import com.example.model.TextStyle;
import com.example.model.actions.DeletePage;
import com.example.model.actions.FormatText;
import com.example.model.actions.Help;
import com.example.model.actions.ImportImage;
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
import com.example.model.settings.AppSettings;
import com.example.view.RichTextArea;
import com.example.view.TextFormatMenu;

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
        Page firstPage = new Page(whitePane, textEditor);
        setupPage(firstPage);
        AppSettings.getInstance().backgroundColorProperty().addListener((obs, o, n) -> applyBackgroundColor(n));
        applyBackgroundColor(AppSettings.getInstance().backgroundColorProperty().get());

        AppSettings.getInstance().selectionColorProperty().addListener((obs, o, n) -> applySelectionColorToAllPages());
        applySelectionColorToAllPages();
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
            clampTranslate();
        });

        MenuItem importImageItem = new MenuItem("Importer une image");
        importImageItem.setOnAction(e -> {
            Window owner = page.getPane().getScene().getWindow();
            new ImportImage(page, owner).execute();
        });

        pageMenu.getItems().addAll(toggleOrientationItem, deletePageItem, importImageItem);

        page.getPane().addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            ContextMenu menu = page.hasSelection() ? createTextMenu(page) : pageMenu;
            showMenu(menu, page, event);
            event.consume();
        });
    }

    private ContextMenu createTextMenu(Page page) {
        return new TextFormatMenu(page.getEditor(), change -> new FormatText(page, change).execute());
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

        for (PageContent content : loadedPages) {
            Page page = createPage();
            populate(page, content);
            for (PageContent.FloatingImageContent img : content.images) {
                byte[] bytes = Base64.getDecoder().decode(img.base64);
                Image image = new Image(new ByteArrayInputStream(bytes));
                page.addImageOverlay(image, img.x, img.y, img.width, img.height, img.format, img.base64);
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
            editor.setParagraphStyle(
                    editor.getParagraphs().size() - 1,
                    paragraph.codeBlock ? AppSettings.getInstance().codeThemeProperty().get() : null);
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

    private void applySelectionColor(RichTextArea editor) {
        Color color = AppSettings.getInstance().selectionColorProperty().get();
        String css = ".styled-text-area .selection { -fx-fill: " + ColorUtil.toCssRgba(color) + "; }";
        String base64 = Base64.getEncoder()
                .encodeToString(css.getBytes(StandardCharsets.UTF_8));
        editor.getStylesheets().add("data:text/css;base64," + base64);
    }

    private void setupPage(Page page) {
        pages.add(page);
        attachContextMenu(page);
        new BackslashInputHandler(page.getEditor(), commandRegistry);
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