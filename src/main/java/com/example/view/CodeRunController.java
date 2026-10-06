package com.example.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fxmisc.richtext.model.TwoDimensional.Bias;

import com.example.model.Page;
import com.example.model.code.run.CodeBlocks;
import com.example.model.code.run.CodeBlocks.Block;
import com.example.model.code.run.CodeRunner;
import com.example.model.i18n.I18n;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.Pane;

public class CodeRunController {
    private static boolean approved;

    public static void resetApproval() {
        approved = false;
    }

    private final class Entry {
        final CodeOutputOverlay overlay = new CodeOutputOverlay();
        int anchor;
        int generation;
        CodeRunner.Execution execution;

        void stop() {
            if (execution != null)
                execution.stop();
        }
    }

    private final RichTextArea editor;
    private final Pane pane;
    private final List<Entry> entries = new ArrayList<>();
    private boolean repositionScheduled;

    public CodeRunController(Page page) {
        this.editor = page.getEditor();
        this.pane = page.getPane();

        editor.plainTextChanges().subscribe(change -> {
            int pos = change.getPosition();
            int removed = change.getRemoved().length();
            int inserted = change.getInserted().length();
            for (Entry e : entries) {
                if (e.anchor >= pos + removed)
                    e.anchor += inserted - removed;
                else if (e.anchor > pos)
                    e.anchor = pos;
            }
            scheduleReposition();
        });
        pane.widthProperty().addListener((obs, o, n) -> scheduleReposition());
        pane.heightProperty().addListener((obs, o, n) -> scheduleReposition());
    }

    public List<MenuItem> contextItems(double screenX, double screenY) {
        Point2D p = editor.screenToLocal(screenX, screenY);
        if (p == null)
            return List.of();
        int offset = Math.min(editor.hit(p.getX(), p.getY()).getInsertionIndex(), editor.getLength());
        int paragraph = editor.offsetToPosition(offset, Bias.Forward).getMajor();
        Block block = CodeBlocks.at(editor, paragraph);
        if (block == null)
            return List.of();

        String name = CodeRunner.displayName(CodeBlocks.tag(editor, block));
        MenuItem item = new MenuItem(name != null
                ? I18n.t("code.run", name)
                : I18n.t("code.runUnsupported"));
        item.setDisable(name == null);
        item.setOnAction(e -> run(block));
        return List.of(new SeparatorMenuItem(), item);
    }

    private void run(Block block) {
        String tag = CodeBlocks.tag(editor, block);
        String name = CodeRunner.displayName(tag);
        if (name == null || !confirm())
            return;

        String code = CodeBlocks.code(editor, block);
        int anchor = editor.getAbsolutePosition(block.close(), 0);
        Entry entry = entries.stream().filter(e -> e.anchor == anchor).findFirst().orElseGet(() -> create(anchor));

        entry.stop();
        int generation = ++entry.generation;
        entry.overlay.begin(name);
        Motion.fadeSlideIn(entry.overlay, -10, 0);
        entry.overlay.setCollapsed(false);
        entry.overlay.toFront();
        scheduleReposition();

        entry.execution = CodeRunner.run(tag, code,
                chunk -> Platform.runLater(() -> {
                    if (entry.generation == generation)
                        entry.overlay.append(chunk);
                }),
                result -> Platform.runLater(() -> {
                    if (entry.generation == generation)
                        entry.overlay.finish(result);
                }));
    }

    private Entry create(int anchor) {
        Entry entry = new Entry();
        entry.anchor = anchor;
        entry.overlay.setOnStop(entry::stop);
        entry.overlay.setOnClose(() -> dispose(entry));
        entries.add(entry);
        pane.getChildren().add(entry.overlay);
        return entry;
    }

    private void dispose(Entry entry) {
        entry.generation++;
        entry.stop();
        pane.getChildren().remove(entry.overlay);
        entries.remove(entry);
    }

    private boolean confirm() {
        if (approved)
            return true;
        ButtonType run = new ButtonType(I18n.t("code.confirm.button"), ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.t("code.confirm.content"), run, ButtonType.CANCEL);
        alert.setTitle(I18n.t("code.confirm.title"));
        alert.setHeaderText(I18n.t("code.confirm.header"));
        if (pane.getScene() != null)
            alert.initOwner(pane.getScene().getWindow());
        approved = alert.showAndWait().filter(b -> b == run).isPresent();
        return approved;
    }

    private void scheduleReposition() {
        if (repositionScheduled || entries.isEmpty())
            return;
        repositionScheduled = true;
        Platform.runLater(() -> {
            repositionScheduled = false;
            reposition();
        });
    }

    private boolean isClosingFence(int anchor) {
        if (anchor < 0 || anchor > editor.getLength())
            return false;
        int paragraph = editor.offsetToPosition(anchor, Bias.Forward).getMajor();
        Block block = CodeBlocks.at(editor, paragraph);
        return block != null && block.close() == paragraph;
    }

    private void reposition() {
        Bounds editorBounds = pane.sceneToLocal(editor.localToScene(editor.getBoundsInLocal()));
        Insets in = editor.getInsets();
        double left = Math.max(0, editorBounds.getMinX() + in.getLeft());
        double right = Math.min(pane.getWidth(), editorBounds.getMaxX() - in.getRight());
        double width = Math.max(100, right - left);

        for (Entry e : new ArrayList<>(entries)) {
            if (!isClosingFence(e.anchor)) {
                dispose(e);
                continue;
            }
            int paragraph = editor.offsetToPosition(e.anchor, Bias.Forward).getMajor();
            Optional<Bounds> bounds = editor.getParagraphBoundsOnScreen(paragraph);
            if (bounds.isEmpty()) {
                e.overlay.setVisible(false);
                continue;
            }

            double bottomScreen = bounds.get().getMaxY();
            int fenceLength = editor.getParagraphLength(paragraph);
            if (fenceLength > 0) {
                Optional<Bounds> text = editor.getCharacterBoundsOnScreen(e.anchor, e.anchor + fenceLength);
                if (text.isPresent())
                    bottomScreen = Math.min(bottomScreen, text.get().getMaxY() + 2);
            }

            Point2D bottom = pane.screenToLocal(bounds.get().getMinX(), bottomScreen);
            if (bottom == null) {
                e.overlay.setVisible(false);
                continue;
            }

            double y = bottom.getY();
            double available = Math.max(40, pane.getHeight() - y);

            e.overlay.setMinWidth(width);
            e.overlay.setPrefWidth(width);
            e.overlay.setMaxWidth(width);
            e.overlay.setMaxHeight(available);
            e.overlay.setLayoutX(left);
            e.overlay.setLayoutY(y);
            e.overlay.setVisible(true);
        }
    }
}
