package com.example.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.example.model.TextStyle;
import com.example.model.i18n.I18n;
import com.example.model.io.CellCodec;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

public class TableOverlay extends Pane implements Layerable {
    public static final int MAX_ROWS = 20;
    public static final int MAX_COLS = 20;

    private static final double DEFAULT_COL_WIDTH = 100;
    private static final double DEFAULT_ROW_HEIGHT = 30;
    private static final double MIN_COL_WIDTH = 30;
    private static final double MIN_ROW_HEIGHT = 20;
    private static final double HIT = 6;
    private static final double SNAP = 6;
    private static final double PAD_V = 2;
    private static final double PAD_H = 4;
    private static final double HANDLE = 14;
    private static final String DIVIDER_HIGHLIGHT = "-fx-background-color: rgba(51,153,255,0.7);";

    private static ContextMenu openMenu;

    private static final class Drag {
        boolean vertical, ctrl;
        double start, startBase, minBase;
        int b;
        int[] idx;
        double[] startOff;
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        double[] snaps = new double[0];
    }

    private final Consumer<RichTextArea> cellSetup;
    private final List<List<RichTextArea>> cells = new ArrayList<>();
    private final List<List<Region>> rightDividers = new ArrayList<>();
    private final List<List<Region>> bottomDividers = new ArrayList<>();
    private final List<Double> colWidths = new ArrayList<>();
    private final List<Double> rowHeights = new ArrayList<>();

    private double[][] offX = new double[0][0];
    private double[][] offY = new double[0][0];

    private final List<int[]> merges = new ArrayList<>();

    private final Path grid = new Path();
    private final Path selectionShade = new Path();
    private final Region moveHandle = new Region();
    private SnapGuides snap;

    private double[] xs = new double[] { 0 };
    private double[] ys = new double[] { 0 };

    private int[] cellSel;
    private RichTextArea pressCell;
    private boolean selecting;
    private Drag drag;

    private boolean handleSuppressed = false;
    private boolean layoutScheduled = false;
    private double pressParentX, pressParentY, pressLayoutX, pressLayoutY;

    private Runnable onDelete;

    public TableOverlay(int rows, int cols, double x, double y, Consumer<RichTextArea> cellSetup) {
        this.cellSetup = cellSetup;

        grid.setStroke(Color.BLACK);
        grid.setStrokeWidth(1);
        grid.setFill(null);
        grid.setMouseTransparent(true);

        selectionShade.getStyleClass().add("table-selection");
        selectionShade.setStroke(null);
        selectionShade.setMouseTransparent(true);

        moveHandle.setStyle("-fx-background-color: #3399ff; -fx-border-color: white; -fx-border-width: 1;");
        moveHandle.setCursor(Cursor.MOVE);
        moveHandle.setVisible(false);
        installMoveHandlers();

        for (int c = 0; c < cols; c++)
            colWidths.add(DEFAULT_COL_WIDTH);
        offX = new double[rows][cols];
        offY = new double[rows][cols];
        for (int r = 0; r < rows; r++)
            addRow(DEFAULT_ROW_HEIGHT);

        setLayoutX(x);
        setLayoutY(y);

        hoverProperty().addListener((obs, o, n) -> updateHandle());
        focusWithinProperty().addListener((obs, o, n) -> {
            updateHandle();
            if (!n && (openMenu == null || !openMenu.isShowing()))
                clearSelection();
        });

        setFocusTraversable(true);
        focusedProperty().addListener((obs, o, n) -> grid.setStroke(n ? Color.web("#3399ff") : Color.BLACK));

        setOnKeyPressed(e -> {
            if (e.getTarget() == this
                    && (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)) {
                delete();
                e.consume();
            }
        });

        setOnContextMenuRequested(e -> {
            showMenu(tableMenu(null), e);
            e.consume();
        });

        rebuildChildren();
    }

    private void addRow(double height) {
        List<RichTextArea> row = new ArrayList<>();
        List<Region> rights = new ArrayList<>();
        List<Region> bottoms = new ArrayList<>();
        for (int c = 0; c < colWidths.size(); c++) {
            row.add(createCell());
            rights.add(newDivider(true));
            bottoms.add(newDivider(false));
        }
        cells.add(row);
        rightDividers.add(rights);
        bottomDividers.add(bottoms);
        rowHeights.add(height);
    }

    private RichTextArea createCell() {
        RichTextArea cell = new RichTextArea();
        cell.setWrapText(true);
        cell.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        cell.setPadding(new Insets(PAD_V, PAD_H, PAD_V, PAD_H));
        cell.totalHeightEstimateProperty().addListener((obs, o, n) -> scheduleLayout());

        cell.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            boolean multi = cellSel != null && countUnits(cellSel) > 1;
            ContextMenu menu = (!multi && cell.hasSelection())
                    ? new TextFormatMenu(cell,
                            change -> cell.updateSelectionStyle(change),
                            align -> cell.updateSelectionParagraphStyle(s -> s.withAlignment(align)))
                    : tableMenu(cell);
            showMenu(menu, e);
            e.consume();
        });

        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY)
                return;
            pressCell = cell;
            selecting = false;
            clearSelection();
        });
        cell.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || pressCell != cell)
                return;
            computeMetrics();
            int[] from = positionOf(cell);
            if (from == null)
                return;
            int[] fa = anchorOf(from[0], from[1]);
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            int[] to = cellAt(p.getX(), p.getY());
            if (to == null) {
                if (selecting)
                    e.consume();
                return;
            }
            if (!selecting && to[0] == fa[0] && to[1] == fa[1])
                return;
            selecting = true;
            cell.deselect();
            cellSel = expand(new int[] { Math.min(fa[0], to[0]), Math.min(fa[1], to[1]),
                    Math.max(fa[0], to[0]), Math.max(fa[1], to[1]) });
            requestLayout();
            e.consume();
        });
        cell.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (pressCell == cell)
                pressCell = null;
        });

        cellSetup.accept(cell);
        return cell;
    }

    public void resizeTable(int newRows, int newCols) {
        final int nr = Math.max(1, Math.min(MAX_ROWS, newRows));
        final int nc = Math.max(1, Math.min(MAX_COLS, newCols));
        final int oldRows = rowHeights.size();
        final int oldCols = colWidths.size();

        merges.removeIf(m -> m[2] >= nr || m[3] >= nc);
        cellSel = null;

        while (colWidths.size() > nc) {
            colWidths.remove(colWidths.size() - 1);
            for (int r = 0; r < cells.size(); r++) {
                cells.get(r).remove(cells.get(r).size() - 1);
                rightDividers.get(r).remove(rightDividers.get(r).size() - 1);
                bottomDividers.get(r).remove(bottomDividers.get(r).size() - 1);
            }
        }
        while (colWidths.size() < nc) {
            colWidths.add(MIN_COL_WIDTH);
            for (int r = 0; r < cells.size(); r++) {
                cells.get(r).add(createCell());
                rightDividers.get(r).add(newDivider(true));
                bottomDividers.get(r).add(newDivider(false));
            }
        }
        while (rowHeights.size() > nr) {
            int last = rowHeights.size() - 1;
            rowHeights.remove(last);
            cells.remove(last);
            rightDividers.remove(last);
            bottomDividers.remove(last);
        }
        while (rowHeights.size() < nr)
            addRow(MIN_ROW_HEIGHT);

        double[][] nx = new double[nr][nc];
        double[][] ny = new double[nr][nc];
        for (int r = 0; r < nr; r++) {
            for (int c = 0; c < nc; c++) {
                if (r < oldRows && c < oldCols) {
                    nx[r][c] = offX[r][c];
                    ny[r][c] = offY[r][c];
                } else {
                    nx[r][c] = r < oldRows ? offX[r][oldCols - 1] : 0;
                    ny[r][c] = c < oldCols ? offY[oldRows - 1][c] : 0;
                }
            }
        }
        offX = nx;
        offY = ny;

        rebuildChildren();
    }

    private void rebuildChildren() {
        List<Node> nodes = new ArrayList<>();
        for (List<RichTextArea> row : cells)
            nodes.addAll(row);
        nodes.add(grid);
        nodes.add(selectionShade);
        for (int r = 0; r < cells.size(); r++) {
            for (int c = 0; c < cells.get(r).size(); c++) {
                Region rd = rightDividers.get(r).get(c);
                Region bd = bottomDividers.get(r).get(c);
                rd.getProperties().put("rc", new int[] { r, c });
                bd.getProperties().put("rc", new int[] { r, c });
                nodes.add(rd);
                nodes.add(bd);
            }
        }
        nodes.add(moveHandle);
        getChildren().setAll(nodes);
        requestLayout();
    }

    private int[] mergeContaining(int r, int c) {
        for (int[] m : merges)
            if (r >= m[0] && r <= m[2] && c >= m[1] && c <= m[3])
                return m;
        return null;
    }

    private boolean isHidden(int r, int c) {
        int[] m = mergeContaining(r, c);
        return m != null && (m[0] != r || m[1] != c);
    }

    private int[] anchorOf(int r, int c) {
        int[] m = mergeContaining(r, c);
        return m == null ? new int[] { r, c } : new int[] { m[0], m[1] };
    }

    private int[] positionOf(RichTextArea cell) {
        for (int r = 0; r < cells.size(); r++) {
            int c = cells.get(r).indexOf(cell);
            if (c >= 0)
                return new int[] { r, c };
        }
        return null;
    }

    private double[] rectOf(int r, int c) {
        int r1 = r, c1 = c, r2 = r, c2 = c;
        int[] m = mergeContaining(r, c);
        if (m != null) {
            r1 = m[0];
            c1 = m[1];
            r2 = m[2];
            c2 = m[3];
        }
        double left = c1 == 0 ? 0 : xs[c1] + offX[r1][c1 - 1];
        double right = xs[c2 + 1] + offX[r1][c2];
        double top = r1 == 0 ? 0 : ys[r1] + offY[r1 - 1][c1];
        double bottom = ys[r2 + 1] + offY[r2][c1];
        return new double[] { left, top, right, bottom };
    }

    private int[] cellAt(double x, double y) {
        for (int r = 0; r < cells.size(); r++) {
            for (int c = 0; c < cells.get(r).size(); c++) {
                if (isHidden(r, c))
                    continue;
                double[] rc = rectOf(r, c);
                if (x >= rc[0] && x <= rc[2] && y >= rc[1] && y <= rc[3])
                    return new int[] { r, c };
            }
        }
        return null;
    }

    private int[] expand(int[] rect) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int[] m : merges) {
                boolean intersects = m[0] <= rect[2] && m[2] >= rect[0] && m[1] <= rect[3] && m[3] >= rect[1];
                if (!intersects)
                    continue;
                if (m[0] < rect[0]) {
                    rect[0] = m[0];
                    changed = true;
                }
                if (m[1] < rect[1]) {
                    rect[1] = m[1];
                    changed = true;
                }
                if (m[2] > rect[2]) {
                    rect[2] = m[2];
                    changed = true;
                }
                if (m[3] > rect[3]) {
                    rect[3] = m[3];
                    changed = true;
                }
            }
        }
        return rect;
    }

    private int countUnits(int[] rect) {
        int n = 0;
        for (int r = rect[0]; r <= rect[2] && r < cells.size(); r++)
            for (int c = rect[1]; c <= rect[3] && c < colWidths.size(); c++)
                if (!isHidden(r, c))
                    n++;
        return n;
    }

    private void clearSelection() {
        if (cellSel != null) {
            cellSel = null;
            requestLayout();
        }
    }

    private void mergeCells(int[] rect) {
        final int r1 = rect[0], c1 = rect[1], r2 = rect[2], c2 = rect[3];
        if (r2 >= rowHeights.size() || c2 >= colWidths.size())
            return;

        RichTextArea anchor = cells.get(r1).get(c1);
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                if ((r == r1 && c == c1) || isHidden(r, c))
                    continue;
                RichTextArea other = cells.get(r).get(c);
                if (other.getLength() > 0) {
                    if (anchor.getLength() > 0)
                        anchor.appendStyledText("\n", TextStyle.DEFAULT);
                    anchor.replace(anchor.getLength(), anchor.getLength(), other.getDocument());
                }
                other.clear();
            }
        }

        merges.removeIf(m -> m[0] >= r1 && m[1] >= c1 && m[2] <= r2 && m[3] <= c2);

        for (int r = r1 + 1; r <= r2; r++) {
            offX[r][c2] = offX[r1][c2];
            if (c1 > 0)
                offX[r][c1 - 1] = offX[r1][c1 - 1];
        }
        for (int c = c1 + 1; c <= c2; c++) {
            offY[r2][c] = offY[r2][c1];
            if (r1 > 0)
                offY[r1 - 1][c] = offY[r1 - 1][c1];
        }

        merges.add(new int[] { r1, c1, r2, c2 });
        cellSel = null;
        requestLayout();
        anchor.requestFocus();
    }

    private void unmerge(int[] merge) {
        merges.remove(merge);
        requestLayout();
    }

    private ContextMenu tableMenu(RichTextArea ctxCell) {
        List<MenuItem> extra = new ArrayList<>();
        if (cellSel != null && countUnits(cellSel) > 1) {
            int[] snapshot = cellSel.clone();
            MenuItem merge = new MenuItem(I18n.t("table.merge"));
            merge.setOnAction(e -> mergeCells(snapshot));
            extra.add(merge);
        }
        if (ctxCell != null) {
            int[] pos = positionOf(ctxCell);
            int[] m = pos == null ? null : mergeContaining(pos[0], pos[1]);
            if (m != null) {
                MenuItem split = new MenuItem(I18n.t("table.split"));
                split.setOnAction(e -> unmerge(m));
                extra.add(split);
            }
        }
        if (!extra.isEmpty())
            extra.add(new SeparatorMenuItem());
        extra.addAll(LayerMenu.items(this));

        return new TableFormatMenu(this, extra);
    }

    private double need(RichTextArea cell) {
        Double est = cell.totalHeightEstimateProperty().getValue();
        return (est != null && !est.isNaN()) ? est + 2 * PAD_V : 0;
    }

    private double[] effectiveRowHeights() {
        int rows = rowHeights.size();
        double[] h = new double[rows];
        for (int r = 0; r < rows; r++)
            h[r] = Math.max(MIN_ROW_HEIGHT, rowHeights.get(r));

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cells.get(r).size(); c++) {
                if (isHidden(r, c))
                    continue;
                int[] m = mergeContaining(r, c);
                int last = m == null ? r : m[2];
                if (last != r)
                    continue;
                h[r] = Math.max(h[r], need(cells.get(r).get(c)));
            }
        }
        for (int[] m : merges) {
            if (m[2] <= m[0] || m[2] >= rows)
                continue;
            double sum = 0;
            for (int r = m[0]; r <= m[2]; r++)
                sum += h[r];
            double n = need(cells.get(m[0]).get(m[1]));
            if (n > sum)
                h[m[2]] += n - sum;
        }
        return h;
    }

    private void computeMetrics() {
        double[] rh = effectiveRowHeights();
        xs = new double[colWidths.size() + 1];
        ys = new double[rh.length + 1];
        for (int c = 0; c < colWidths.size(); c++)
            xs[c + 1] = xs[c] + colWidths.get(c);
        for (int r = 0; r < rh.length; r++)
            ys[r + 1] = ys[r] + rh[r];
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
        return colWidths.stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    protected double computePrefHeight(double width) {
        double sum = 0;
        for (double h : effectiveRowHeights())
            sum += h;
        return sum;
    }

    @Override
    protected void layoutChildren() {
        computeMetrics();
        int rows = rowHeights.size();
        int cols = colWidths.size();

        List<PathElement> lines = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RichTextArea cell = cells.get(r).get(c);
                Region rd = rightDividers.get(r).get(c);
                Region bd = bottomDividers.get(r).get(c);
                if (isHidden(r, c)) {
                    cell.setVisible(false);
                    rd.setVisible(false);
                    bd.setVisible(false);
                    continue;
                }
                cell.setVisible(true);
                rd.setVisible(true);
                bd.setVisible(true);

                double[] rc = rectOf(r, c);
                double w = rc[2] - rc[0];
                double h = rc[3] - rc[1];
                cell.resizeRelocate(rc[0], rc[1], w, h);
                addRect(lines, rc, false);
                rd.resizeRelocate(rc[2] - HIT / 2, rc[1], HIT, h);
                bd.resizeRelocate(rc[0], rc[3] - HIT / 2, w, HIT);
            }
        }
        grid.getElements().setAll(lines);

        List<PathElement> shade = new ArrayList<>();
        if (cellSel != null && cellSel[2] < rows && cellSel[3] < cols) {
            for (int r = cellSel[0]; r <= cellSel[2]; r++)
                for (int c = cellSel[1]; c <= cellSel[3]; c++)
                    if (!isHidden(r, c))
                        addRect(shade, rectOf(r, c), true);
        }
        selectionShade.getElements().setAll(shade);

        moveHandle.resizeRelocate(-HANDLE, -HANDLE, HANDLE, HANDLE);
    }

    private static void addRect(List<PathElement> out, double[] rc, boolean close) {
        out.add(new MoveTo(rc[0], rc[1]));
        out.add(new LineTo(rc[2], rc[1]));
        out.add(new LineTo(rc[2], rc[3]));
        out.add(new LineTo(rc[0], rc[3]));
        out.add(close ? new ClosePath() : new LineTo(rc[0], rc[1]));
    }

    private Region newDivider(boolean vertical) {
        Region d = new Region();
        d.setCursor(vertical ? Cursor.H_RESIZE : Cursor.V_RESIZE);

        d.setOnMouseEntered(e -> highlight(d, e.isControlDown()));
        d.setOnMouseMoved(e -> highlight(d, e.isControlDown()));
        d.setOnMouseExited(e -> {
            if (drag == null)
                highlight(d, false);
        });
        d.setOnMousePressed(e -> startDrag(d, vertical, e));
        d.setOnMouseDragged(this::doDrag);
        d.setOnMouseReleased(e -> {
            if (drag != null) {
                drag = null;
                highlight(d, false);
                e.consume();
            }
        });
        return d;
    }

    private static void highlight(Region d, boolean on) {
        d.setStyle(on ? DIVIDER_HIGHLIGHT : "");
    }

    private void startDrag(Region d, boolean vertical, MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY)
            return;
        computeMetrics();
        int[] rc = (int[]) d.getProperties().get("rc");
        int r1 = rc[0], c1 = rc[1], r2 = r1, c2 = c1;
        int[] m = mergeContaining(r1, c1);
        if (m != null) {
            r2 = m[2];
            c2 = m[3];
        }

        Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
        Drag dr = new Drag();
        dr.vertical = vertical;
        dr.ctrl = e.isControlDown();
        dr.start = vertical ? p.getX() : p.getY();
        dr.b = vertical ? c2 : r2;

        if (!dr.ctrl) {
            if (vertical) {
                dr.startBase = colWidths.get(dr.b);
                dr.minBase = minColBase(dr.b);
            } else {
                dr.startBase = effectiveRowHeights()[dr.b];
                dr.minBase = minRowBase(dr.b);
            }
        } else if (vertical) {
            dr.idx = affectedRows(r1, r2, dr.b).stream().mapToInt(Integer::intValue).toArray();
            dr.startOff = new double[dr.idx.length];
            for (int k = 0; k < dr.idx.length; k++) {
                int i = dr.idx[k];
                dr.startOff[k] = offX[i][dr.b];
                double[] cur = rectOf(i, dr.b);
                dr.lo = Math.max(dr.lo, cur[0] + MIN_COL_WIDTH - cur[2]);
                if (dr.b + 1 < colWidths.size()) {
                    double[] next = rectOf(i, dr.b + 1);
                    dr.hi = Math.min(dr.hi, next[2] - MIN_COL_WIDTH - cur[2]);
                }
            }
            dr.snaps = verticalSnaps(dr);
        } else {
            dr.idx = affectedCols(c1, c2, dr.b).stream().mapToInt(Integer::intValue).toArray();
            dr.startOff = new double[dr.idx.length];
            for (int k = 0; k < dr.idx.length; k++) {
                int j = dr.idx[k];
                dr.startOff[k] = offY[dr.b][j];
                double[] cur = rectOf(dr.b, j);
                dr.lo = Math.max(dr.lo, cur[1] + MIN_ROW_HEIGHT - cur[3]);
                if (dr.b + 1 < rowHeights.size()) {
                    double[] next = rectOf(dr.b + 1, j);
                    dr.hi = Math.min(dr.hi, next[3] - MIN_ROW_HEIGHT - cur[3]);
                }
            }
            dr.snaps = horizontalSnaps(dr);
        }

        drag = dr;
        if (dr.ctrl)
            highlight(d, true);
        e.consume();
    }

    private void doDrag(MouseEvent e) {
        if (drag == null)
            return;
        Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
        double delta = (drag.vertical ? p.getX() : p.getY()) - drag.start;

        if (!drag.ctrl) {
            double v = Math.max(drag.minBase, drag.startBase + delta);
            if (snap != null) {
                if (drag.vertical) {
                    double total = computePrefWidth(-1) - colWidths.get(drag.b) + v;
                    double[] s = snap.resize(this, total, 0, true, false, e.isAltDown());
                    v = Math.max(drag.minBase, v + (s[0] - total));
                } else {
                    double[] eff = effectiveRowHeights();
                    double total = -eff[drag.b] + v;
                    for (double h : eff)
                        total += h;
                    double[] s = snap.resize(this, 0, total, false, true, e.isAltDown());
                    v = Math.max(drag.minBase, v + (s[1] - total));
                }
            }
            if (drag.vertical)
                colWidths.set(drag.b, v);
            else
                rowHeights.set(drag.b, v);
        } else {
            double d = drag.lo > drag.hi ? 0 : Math.max(drag.lo, Math.min(drag.hi, delta));
            if (!e.isAltDown())
                d = snap(d);
            for (int k = 0; k < drag.idx.length; k++) {
                if (drag.vertical)
                    offX[drag.idx[k]][drag.b] = drag.startOff[k] + d;
                else
                    offY[drag.b][drag.idx[k]] = drag.startOff[k] + d;
            }
        }
        requestLayout();
        e.consume();
    }

    private List<Integer> affectedRows(int r1, int r2, int b) {
        Set<Integer> rows = new TreeSet<>();
        for (int i = r1; i <= r2; i++)
            rows.add(i);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i : new ArrayList<>(rows)) {
                for (int col = b; col <= b + 1; col++) {
                    if (col >= colWidths.size())
                        continue;
                    int[] m = mergeContaining(i, col);
                    if (m == null)
                        continue;
                    for (int k = m[0]; k <= m[2]; k++)
                        if (rows.add(k))
                            changed = true;
                }
            }
        }
        return new ArrayList<>(rows);
    }

    private List<Integer> affectedCols(int c1, int c2, int b) {
        Set<Integer> cols = new TreeSet<>();
        for (int j = c1; j <= c2; j++)
            cols.add(j);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int j : new ArrayList<>(cols)) {
                for (int row = b; row <= b + 1; row++) {
                    if (row >= rowHeights.size())
                        continue;
                    int[] m = mergeContaining(row, j);
                    if (m == null)
                        continue;
                    for (int k = m[1]; k <= m[3]; k++)
                        if (cols.add(k))
                            changed = true;
                }
            }
        }
        return new ArrayList<>(cols);
    }

    private double minColBase(int b) {
        double min = MIN_COL_WIDTH;
        for (int r = 0; r < rowHeights.size(); r++) {
            if (mergeContaining(r, b) != null)
                continue;
            double prev = b > 0 ? offX[r][b - 1] : 0;
            min = Math.max(min, MIN_COL_WIDTH - (offX[r][b] - prev));
        }
        return min;
    }

    private double minRowBase(int b) {
        double min = MIN_ROW_HEIGHT;
        for (int c = 0; c < colWidths.size(); c++) {
            if (mergeContaining(b, c) != null)
                continue;
            double prev = b > 0 ? offY[b - 1][c] : 0;
            min = Math.max(min, MIN_ROW_HEIGHT - (offY[b][c] - prev));
        }
        return min;
    }

    private void installMoveHandlers() {
        moveHandle.setOnMousePressed(e -> {
            requestFocus();
            Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            pressParentX = p.getX();
            pressParentY = p.getY();
            pressLayoutX = getLayoutX();
            pressLayoutY = getLayoutY();
            e.consume();
        });
        moveHandle.setOnMouseDragged(e -> {
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
            e.consume();
        });
    }

    private void showMenu(ContextMenu menu, ContextMenuEvent e) {
        if (openMenu != null && openMenu.isShowing())
            openMenu.hide();
        openMenu = menu;
        menu.setOnHidden(ev -> {
            if (!isFocusWithin())
                clearSelection();
        });
        menu.show(this, e.getScreenX(), e.getScreenY());
    }

    private void updateHandle() {
        boolean show = !handleSuppressed && (isHover() || isFocusWithin());
        Motion.fadeVisible(moveHandle, show, handleSuppressed);
    }

    public void setHandleSuppressed(boolean suppressed) {
        this.handleSuppressed = suppressed;
        updateHandle();
    }

    public void focusFirstCell() {
        cells.get(0).get(0).requestFocus();
    }

    public int getRowCount() {
        return rowHeights.size();
    }

    public int getColumnCount() {
        return colWidths.size();
    }

    public double getTableX() {
        return getLayoutX();
    }

    public double getTableY() {
        return getLayoutY();
    }

    public double[] getColumnWidths() {
        return colWidths.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public double[] getRowHeights() {
        return effectiveRowHeights();
    }

    public double[][] getOffsetsX() {
        double[][] out = new double[offX.length][];
        for (int r = 0; r < out.length; r++)
            out[r] = offX[r].clone();
        return out;
    }

    public double[][] getOffsetsY() {
        double[][] out = new double[offY.length][];
        for (int r = 0; r < out.length; r++)
            out[r] = offY[r].clone();
        return out;
    }

    public int[][] getMerges() {
        int[][] out = new int[merges.size()][];
        for (int i = 0; i < out.length; i++)
            out[i] = merges.get(i).clone();
        return out;
    }

    public String[][] encodeCells() {
        String[][] out = new String[cells.size()][];
        for (int r = 0; r < out.length; r++) {
            out[r] = new String[cells.get(r).size()];
            for (int c = 0; c < out[r].length; c++)
                out[r][c] = CellCodec.encode(cells.get(r).get(c));
        }
        return out;
    }

    public void load(double[] widths, double[] heights, double[][] ox, double[][] oy, int[][] mergeData,
            String[][] encoded) {
        for (int c = 0; c < colWidths.size() && c < widths.length; c++)
            colWidths.set(c, Math.max(MIN_COL_WIDTH, widths[c]));
        for (int r = 0; r < rowHeights.size() && r < heights.length; r++)
            rowHeights.set(r, Math.max(MIN_ROW_HEIGHT, heights[r]));

        for (int r = 0; r < offX.length; r++) {
            for (int c = 0; c < offX[r].length; c++) {
                if (r < ox.length && c < ox[r].length)
                    offX[r][c] = ox[r][c];
                if (r < oy.length && c < oy[r].length)
                    offY[r][c] = oy[r][c];
            }
        }

        merges.clear();
        for (int[] m : mergeData) {
            if (m.length == 4 && m[0] >= 0 && m[1] >= 0 && m[0] <= m[2] && m[1] <= m[3]
                    && m[2] < rowHeights.size() && m[3] < colWidths.size())
                merges.add(m.clone());
        }

        for (int r = 0; r < cells.size() && r < encoded.length; r++)
            for (int c = 0; c < cells.get(r).size() && c < encoded[r].length; c++)
                CellCodec.decode(encoded[r][c], cells.get(r).get(c));
        requestLayout();
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    public void delete() {
        if (onDelete != null)
            onDelete.run();
    }

    private double[] verticalSnaps(Drag dr) {
        int first = dr.idx[0], last = dr.idx[dr.idx.length - 1];
        List<Double> targets = new ArrayList<>();
        for (int i : new int[] { first - 1, last + 1 }) {
            if (i < 0 || i >= rowHeights.size())
                continue;
            int[] m = mergeContaining(i, dr.b);
            if (m != null && m[3] != dr.b)
                continue;
            targets.add(rectOf(i, dr.b)[2]);
        }
        double[] out = new double[targets.size() * dr.idx.length];
        int n = 0;
        for (double t : targets)
            for (int row : dr.idx)
                out[n++] = t - rectOf(row, dr.b)[2];
        return out;
    }

    private double[] horizontalSnaps(Drag dr) {
        int first = dr.idx[0], last = dr.idx[dr.idx.length - 1];
        List<Double> targets = new ArrayList<>();
        for (int j : new int[] { first - 1, last + 1 }) {
            if (j < 0 || j >= colWidths.size())
                continue;
            int[] m = mergeContaining(dr.b, j);
            if (m != null && m[2] != dr.b)
                continue;
            targets.add(rectOf(dr.b, j)[3]);
        }
        double[] out = new double[targets.size() * dr.idx.length];
        int n = 0;
        for (double t : targets)
            for (int col : dr.idx)
                out[n++] = t - rectOf(dr.b, col)[3];
        return out;
    }

    private double snap(double d) {
        if (drag.lo > drag.hi)
            return d;
        double best = d;
        double bestDist = SNAP;
        for (double s : drag.snaps) {
            if (s < drag.lo || s > drag.hi)
                continue;
            double dist = Math.abs(s - d);
            if (dist <= bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    public void setSnap(SnapGuides snap) {
        this.snap = snap;
    }
}