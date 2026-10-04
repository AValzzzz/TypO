package com.example.view;

import java.util.Optional;
import java.util.function.Consumer;

import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import org.fxmisc.richtext.model.TwoDimensional.Position;
import org.reactfx.util.Either;

import com.example.model.Page;
import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.IndexRange;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

public class TextSelectionMover {
    private static final double HANDLE = 14;
    private static final double DRAG_THRESHOLD = 4;
    private static final double WIDTH_SLACK = 10;

    private final Page page;
    private final RichTextArea editor;
    private final Pane pane;
    private final Consumer<RichTextArea> cellSetup;
    private final Region handle = new Region();

    private boolean updateScheduled;
    private int pressStart, pressEnd;
    private Point2D pressPoint;

    public TextSelectionMover(Page page, Consumer<RichTextArea> cellSetup) {
        this.page = page;
        this.editor = page.getEditor();
        this.pane = page.getPane();
        this.cellSetup = cellSetup;

        handle.setStyle("-fx-background-color: #3399ff; -fx-border-color: white; -fx-border-width: 1;");
        handle.setCursor(Cursor.MOVE);
        handle.setManaged(false);
        handle.setVisible(false);
        pane.getChildren().add(handle);

        editor.selectionProperty().addListener((obs, o, n) -> scheduleUpdate());
        editor.richChanges().subscribe(c -> scheduleUpdate());

        handle.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY)
                return;
            IndexRange sel = editor.getSelection();
            pressStart = sel.getStart();
            pressEnd = sel.getEnd();
            pressPoint = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
            e.consume();
        });
        handle.setOnMouseDragged(e -> e.consume());
        handle.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY || pressPoint == null)
                return;
            Point2D p = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - pressPoint.getX();
            double dy = p.getY() - pressPoint.getY();
            pressPoint = null;
            if (Math.hypot(dx, dy) >= DRAG_THRESHOLD && pressEnd > pressStart)
                moveSelection(pressStart, pressEnd, dx, dy);
            e.consume();
        });
    }

    private void scheduleUpdate() {
        if (updateScheduled)
            return;
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            updateHandle();
        });
    }

    private void updateHandle() {
        IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) {
            handle.setVisible(false);
            return;
        }
        double[] b = rangeBounds(sel.getStart(), sel.getEnd());
        if (b == null) {
            handle.setVisible(false);
            return;
        }
        handle.resizeRelocate(b[0] - HANDLE, b[1] - HANDLE, HANDLE, HANDLE);
        handle.setVisible(true);
        handle.toFront();
    }

    private double[] rangeBounds(int start, int end) {
        Position a = editor.offsetToPosition(start, Bias.Forward);
        Position b = editor.offsetToPosition(end, Bias.Backward);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean found = false;

        for (int p = a.getMajor(); p <= b.getMajor(); p++) {
            int from = p == a.getMajor() ? a.getMinor() : 0;
            int to = p == b.getMajor() ? b.getMinor() : editor.getParagraphLength(p);
            if (to <= from)
                continue;
            int absFrom = editor.getAbsolutePosition(p, from);
            int absTo = editor.getAbsolutePosition(p, to);
            Optional<Bounds> bb = editor.getCharacterBoundsOnScreen(absFrom, absTo);
            if (bb.isEmpty())
                continue;
            Point2D tl = pane.screenToLocal(bb.get().getMinX(), bb.get().getMinY());
            Point2D br = pane.screenToLocal(bb.get().getMaxX(), bb.get().getMaxY());
            if (tl == null || br == null)
                continue;
            minX = Math.min(minX, tl.getX());
            minY = Math.min(minY, tl.getY());
            maxX = Math.max(maxX, br.getX());
            maxY = Math.max(maxY, br.getY());
            found = true;
        }
        return found ? new double[] { minX, minY, maxX - minX, maxY - minY } : null;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private int[] expandToWords(int start, int end) {
        String text = editor.getText();
        if (start > 0 && start < text.length() && isWordChar(text.charAt(start - 1))
                && isWordChar(text.charAt(start))) {
            while (start > 0 && isWordChar(text.charAt(start - 1)))
                start--;
        }
        if (end > 0 && end < text.length() && isWordChar(text.charAt(end - 1))
                && isWordChar(text.charAt(end))) {
            while (end < text.length() && isWordChar(text.charAt(end)))
                end++;
        }
        return new int[] { start, end };
    }

    private void moveSelection(int selStart, int selEnd, double dx, double dy) {
        int[] r = expandToWords(selStart, selEnd);
        double[] b = rangeBounds(r[0], r[1]);
        if (b == null)
            return;

        StyledDocument<ParagraphStyle, Either<String, MathObject>, TextStyle> doc = editor.subDocument(r[0], r[1]);
        String original = editor.getText(r[0], r[1]);

        double x = b[0] + dx - TextBoxOverlay.PAD_H;
        double y = b[1] + dy - TextBoxOverlay.PAD_V;
        double width = b[2] + 2 * TextBoxOverlay.PAD_H + WIDTH_SLACK;

        TextBoxOverlay box = page.addTextBoxOverlay(x, y, width, cellSetup);
        box.getEditor().replace(0, 0, doc);

        StringBuilder blank = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++)
            blank.append(original.charAt(i) == '\n' ? '\n' : ' ');
        editor.replaceText(r[0], r[1], blank.toString());
        editor.deselect();
        handle.setVisible(false);

        box.setSelected(true);
        box.requestFocus();
    }
}
