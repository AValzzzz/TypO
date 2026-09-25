package com.example.controller;

import java.util.ArrayList;
import java.util.List;

import com.example.model.Page;
import com.example.model.actions.DeletePage;
import com.example.model.actions.FormatText;
import com.example.model.actions.Help;
import com.example.model.actions.NewPage;
import com.example.model.actions.Save;
import com.example.model.actions.SaveAs;
import com.example.model.actions.Settings;
import com.example.model.actions.ToggleOrientation;
import com.example.model.language.BackslashInputHandler;
import com.example.model.language.CommandRegistry;
import com.example.model.language.maths.MathCommands;
import com.example.view.RichTextArea;
import com.example.view.TextFormatMenu;

import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

    private boolean updatingScrollbars = false;

    private final List<Page> pages = new ArrayList<>();

    private ContextMenu activeMenu;

    private final CommandRegistry commandRegistry = new CommandRegistry();

    private static final double MIN_SCALE = 0.8;
    private static final double MAX_SCALE = 3.0;
    private static final double ZOOM_SENSITIVITY = 0.002;

    private static final double PAN_SENSITIVITY = 1.5;

    @FXML
    public void initialize() {
        MathCommands.registerAll(commandRegistry);
        Page firstPage = new Page(whitePane, textEditor);
        pages.add(firstPage);
        attachContextMenu(firstPage);
        new BackslashInputHandler(firstPage.getEditor(), commandRegistry);
        stackPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY();
                double zoomFactor = Math.exp(delta * ZOOM_SENSITIVITY);

                double newScaleX = pagesContainer.getScaleX() * zoomFactor;
                double newScaleY = pagesContainer.getScaleY() * zoomFactor;

                newScaleX = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleX));
                newScaleY = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScaleY));

                pagesContainer.setScaleX(newScaleX);
                pagesContainer.setScaleY(newScaleY);

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
    }

    private void attachContextMenu(Page page) {
        ContextMenu pageMenu = new ContextMenu();
        this.activeMenu = pageMenu;
        pageMenu.setAutoHide(true);

        MenuItem toggleOrientationItem = new MenuItem("Toggle Orientation (Portrait/Landscape)");
        toggleOrientationItem.setOnAction(e -> new ToggleOrientation(page).execute());

        MenuItem deletePageItem = new MenuItem("Delete Page");
        deletePageItem.setOnAction(e -> {
            new DeletePage(page, pagesContainer, pages).execute();
            clampTranslate();
        });

        pageMenu.getItems().addAll(toggleOrientationItem, deletePageItem);

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

    private void clampTranslate() {

        double viewportWidth = stackPane.getWidth();
        double viewportHeight = stackPane.getHeight();

        double contentWidth = pagesContainer.prefWidth(-1);
        double contentHeight = pagesContainer.prefHeight(-1);

        double scaledWidth = contentWidth * pagesContainer.getScaleX();
        double scaledHeight = contentHeight * pagesContainer.getScaleY();

        double maxTranslateX = Math.max(0, (scaledWidth - viewportWidth) / 2.0) + 45;
        double maxTranslateY = Math.max(0, (scaledHeight - viewportHeight) / 2.0) + 45;

        double clampedX = Math.max(-maxTranslateX, Math.min(maxTranslateX, pagesContainer.getTranslateX()));
        pagesContainer.setTranslateX(clampedX);

        double clampedY = Math.max(-maxTranslateY, Math.min(maxTranslateY, pagesContainer.getTranslateY()));
        pagesContainer.setTranslateY(clampedY);

        updateScrollBars(maxTranslateX, maxTranslateY, viewportWidth, viewportHeight, scaledWidth, scaledHeight);
    }

    private void updateScrollBars(double maxX, double maxY, double viewportW, double viewportH, double scaledW,
            double scaledH) {
        updatingScrollbars = true;

        hScrollBar.setMin(-maxX);
        hScrollBar.setMax(maxX);
        hScrollBar.setVisibleAmount(
                maxX > 0 ? Math.min(2 * maxX, viewportW * (2 * maxX) / Math.max(scaledW, 1)) : 2 * maxX);
        hScrollBar.setValue(-pagesContainer.getTranslateX());
        hScrollBar.setDisable(maxX <= 0);

        vScrollBar.setMin(-maxY);
        vScrollBar.setMax(maxY);
        vScrollBar.setVisibleAmount(
                maxY > 0 ? Math.min(2 * maxY, viewportH * (2 * maxY) / Math.max(scaledH, 1)) : 2 * maxY);
        vScrollBar.setValue(-pagesContainer.getTranslateY());
        vScrollBar.setDisable(maxY <= 0);

        updatingScrollbars = false;
    }

    @FXML
    private void handleSave() {
        new Save().execute();
    }

    @FXML
    private void handleSaveAs() {
        new SaveAs().execute();
    }

    @FXML
    private void handleNewPage() {
        NewPage action = new NewPage(pagesContainer);
        action.execute();
        Page created = action.getCreatedPage();
        pages.add(created);
        attachContextMenu(created);
        new BackslashInputHandler(created.getEditor(), commandRegistry);
        clampTranslate();
    }

    @FXML
    private void handleHelp() {
        new Help().execute();
    }

    @FXML
    private void handleSettings() {
        new Settings().execute();
    }
}
