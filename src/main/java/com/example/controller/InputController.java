package com.example.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.model.CodeBlockStyler;
import com.example.model.LinkHandler;
import com.example.model.Page;
import com.example.model.actions.AlignParagraph;
import com.example.model.actions.DeletePage;
import com.example.model.actions.FormatText;
import com.example.model.actions.Help;
import com.example.model.actions.ImportImage;
import com.example.model.actions.InsertArrow;
import com.example.model.actions.InsertShape;
import com.example.model.actions.InsertTable;
import com.example.model.actions.NewPage;
import com.example.model.actions.OpenFile;
import com.example.model.actions.Save;
import com.example.model.actions.SaveAs;
import com.example.model.actions.Settings;
import com.example.model.actions.ToggleOrientation;
import com.example.model.i18n.I18n;
import com.example.model.io.DocumentSession;
import com.example.model.io.PageContent;
import com.example.model.io.PageContent.FloatingContent;
import com.example.model.language.BackslashInputHandler;
import com.example.model.language.CommandRegistry;
import com.example.model.language.maths.MathCommands;
import com.example.model.language.shapes.ArrowCommand;
import com.example.model.language.shapes.ShapeCommand;
import com.example.model.language.tables.TableCommand;
import com.example.model.settings.AppSettings;
import com.example.view.CodeOutputOverlay;
import com.example.view.CodeRunController;
import com.example.view.Icons;
import com.example.view.Layerable;
import com.example.view.Motion;
import com.example.view.Nodes;
import com.example.view.RichTextArea;
import com.example.view.SmoothCaret;
import com.example.view.SmoothViewport;
import com.example.view.TextFormatMenu;
import com.example.view.TextSelectionMover;
import com.example.view.Toast;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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

    private SmoothViewport viewport;

    private final List<Page> pages = new ArrayList<>();
    private ContextMenu activeMenu;
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final DocumentSession session = new DocumentSession();
    private final Set<Page> pendingReflow = new HashSet<>();
    private boolean loading = false;
    private FloatingShortcuts floatingShortcuts;

    @FXML
    public void initialize() {
        MathCommands.registerAll(commandRegistry);
        commandRegistry.register(new ShapeCommand());
        commandRegistry.register(new ArrowCommand());
        commandRegistry.register(new TableCommand());

        rootPane.setStyle("");
        rootPane.getStyleClass().add("app-root");

        viewport = new SmoothViewport(stackPane, pagesContainer, hScrollBar, vScrollBar);
        viewport.install();

        floatingShortcuts = new FloatingShortcuts(pages, this::setupCell);
        Page firstPage = new Page(whitePane, textEditor);
        setupPage(firstPage);

        AppSettings st = AppSettings.getInstance();
        for (DoubleProperty p : List.of(st.marginLeftProperty(), st.marginTopProperty(),
                st.marginRightProperty(), st.marginBottomProperty()))
            p.addListener((obs, o, n) -> applyMarginsToAllPages());

        stackPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (activeMenu != null && activeMenu.isShowing())
                activeMenu.hide();
        });

        KeyCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        rootPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                floatingShortcuts.install(scene);
                new FloatingPageDrag(pages, this::setupCell).install(scene);
                scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (saveShortcut.match(event)) {
                        handleSave();
                        event.consume();
                    }
                });
            }
        });

        setupToolbar();
        Toast.install(rootPane);
    }

    private void setupToolbar() {
        if (!(rootPane.lookup("#helpButton") instanceof Node help) || !(help.getParent() instanceof Pane bar))
            return;
        bar.getStyleClass().add("toolbar");

        Icons.decorate(rootPane, "#openButton", Icons.OPEN);
        Icons.decorate(rootPane, "#saveButton", Icons.SAVE);
        Icons.decorate(rootPane, "#saveAsButton", Icons.SAVE_AS);
        Icons.decorate(rootPane, "#newPageButton", Icons.NEW_PAGE);
        Icons.decorate(rootPane, "#helpButton", Icons.HELP);
        Icons.decorate(rootPane, "#settingsButton", Icons.SETTINGS);

        for (Node b : bar.getChildren())
            Motion.interactive(b);
        Motion.stagger(bar.getChildren(), 45);
    }

    private void attachContextMenu(Page page, CodeRunController runner) {
        ContextMenu pageMenu = new ContextMenu();
        pageMenu.setAutoHide(true);

        MenuItem toggleOrientationItem = new MenuItem(I18n.t("page.toggleOrientation"));
        toggleOrientationItem.setOnAction(e -> {
            new ToggleOrientation(page).execute();
            scheduleReflow(page);
        });

        MenuItem deletePageItem = new MenuItem(I18n.t("page.delete"));
        deletePageItem.setOnAction(e -> {
            new DeletePage(page, pagesContainer, pages).execute();
            for (int i = 0; i < pages.size(); i++)
                pages.get(i).setPageNumber(i + 1);
            clampTranslate();
        });

        MenuItem importImageItem = new MenuItem(I18n.t("page.importImage"));
        importImageItem.setOnAction(e -> {
            Window owner = page.getPane().getScene().getWindow();
            new ImportImage(page, owner).execute();
        });

        pageMenu.getItems().addAll(toggleOrientationItem, deletePageItem, importImageItem);

        List<MenuItem> runItems = new ArrayList<>();
        page.getPane().addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (isInsideOverlay(event.getTarget()))
                return;
            pageMenu.getItems().removeAll(runItems);
            runItems.clear();

            ContextMenu menu = page.hasSelection() ? createTextMenu(page) : pageMenu;
            runItems.addAll(runner.contextItems(event.getScreenX(), event.getScreenY()));
            menu.getItems().addAll(runItems);

            showMenu(menu, page, event);
            event.consume();
        });
    }

    private static boolean isInsideOverlay(Object target) {
        return Nodes.ancestor(target, Layerable.class) != null
                || Nodes.ancestor(target, CodeOutputOverlay.class) != null;
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

    private void clampTranslate() {
        viewport.refresh();
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
        loading = true;
        Motion.setQuiet(true);
        try {
            doLoadDocument(loadedPages, source);
        } finally {
            loading = false;
            Motion.setQuiet(false);
        }
        floatingShortcuts.reset();
        for (Page p : new ArrayList<>(pages))
            scheduleReflow(p);
    }

    private void doLoadDocument(List<PageContent> loadedPages, Path source) {
        CodeRunController.resetApproval();
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
            PageContent.populate(page.getEditor(), content.paragraphs);
            Map<Layerable, Integer> levels = new HashMap<>();

            for (FloatingContent item : content.floating()) {
                Layerable overlay = item.createOn(page, this::setupCell);
                if (overlay != null)
                    levels.put(overlay, item.getLevel() != null ? item.getLevel() : 0);
            }
            page.orderLayers(levels);

            if (content.landscape != (page.getPane().getWidth() > page.getPane().getHeight())) {
                new ToggleOrientation(page).execute();
            }
        }
        clampTranslate();
    }

    private void scheduleReflow(Page page) {
        if (loading || !pendingReflow.add(page))
            return;
        Platform.runLater(() -> {
            pendingReflow.remove(page);
            if (pages.contains(page))
                reflow(page);
        });
    }

    private void reflow(Page page) {
        RichTextArea editor = page.getEditor();
        int cut = editor.findOverflowOffset();
        if (cut <= 0)
            return;

        int index = pages.indexOf(page);
        Page next = index + 1 < pages.size() ? pages.get(index + 1) : createPageAfter(page);
        RichTextArea target = next.getEditor();

        int caret = editor.getCaretPosition();
        boolean followCaret = editor.isFocused() && caret >= cut;

        var tail = editor.removeTail(cut);
        target.prependDocument(tail);

        if (followCaret) {
            target.requestFocus();
            target.moveTo(Math.min(caret - cut, target.getLength()));
            Platform.runLater(() -> scrollCaretIntoView(target));
        }
    }

    private Page createPageAfter(Page previous) {
        Page created = createPage();
        if (previous.getPane().getPrefWidth() > previous.getPane().getPrefHeight())
            new ToggleOrientation(created).execute();
        return created;
    }

    private void scrollCaretIntoView(RichTextArea editor) {
        editor.getCaretBounds().ifPresent(caret -> {
            Bounds view = stackPane.localToScreen(stackPane.getBoundsInLocal());
            double margin = 60;
            double dy = 0;
            if (caret.getMaxY() > view.getMaxY() - margin)
                dy = -(caret.getMaxY() - (view.getMaxY() - margin));
            else if (caret.getMinY() < view.getMinY() + margin)
                dy = (view.getMinY() + margin) - caret.getMinY();
            if (dy != 0)
                viewport.scrollTo(pagesContainer.getTranslateX(), pagesContainer.getTranslateY() + dy);
        });
    }

    private Page createPage() {
        NewPage action = new NewPage(pagesContainer);
        action.execute();
        Page created = action.getCreatedPage();
        setupPage(created);
        return created;
    }

    private void applyMarginsToAllPages() {
        for (Page p : pages) {
            p.applyMargins();
            scheduleReflow(p);
        }
    }

    private void setupPage(Page page) {
        pages.add(page);
        for (int i = 0; i < pages.size(); i++)
            pages.get(i).setPageNumber(i + 1);
        page.applyMargins();
        attachContextMenu(page, new CodeRunController(page));
        new BackslashInputHandler(page.getEditor(), commandRegistry,
                type -> new InsertShape(page, type).execute(),
                () -> new InsertArrow(page).execute(),
                () -> new InsertTable(page, this::setupCell).execute());
        new CodeBlockStyler(page.getEditor());
        new LinkHandler(page.getEditor());
        new TextSelectionMover(page, this::setupCell);
        new SmoothCaret(page);
        floatingShortcuts.track(page);
        page.getEditor().richChanges().subscribe(c -> scheduleReflow(page));
    }

    private void setupCell(RichTextArea cell) {
        floatingShortcuts.trackText(cell);
        new BackslashInputHandler(cell, commandRegistry);
        new LinkHandler(cell);
    }

    @FXML
    private void handleNewPage() {
        createPage();
        clampTranslate();
    }

    @FXML
    private void handleHelp() {
        new Help(stackPane.getScene().getWindow()).execute();
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        Node source = (Node) event.getSource();
        new Settings(source).execute();
    }
}