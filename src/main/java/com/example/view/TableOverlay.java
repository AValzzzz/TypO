package com.example.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.example.model.TextStyle;
import com.example.model.i18n.I18n;
import com.example.model.io.CellCodec;
import com.example.model.io.ColorUtil;
import com.example.model.io.PageContent;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
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
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class TableOverlay extends Pane implements Layerable {
    public static final int MAX_ROWS = 20;
    public static final int MAX_COLS = 20;

    private static final double DEFAULT_COL_WIDTH = 100;
    private static final double DEFAULT_ROW_HEIGHT = 30;
    private static final double MIN_COL_WIDTH = 30;
    private static final double MIN_ROW_HEIGHT = 20;
    private static final double HIT = 6;
    private static final double SNAP_PX = 6;
    private static final double EQUAL_SNAP_PX = 12;
    private static final double GUIDE_EXTEND = 8;
    private static final double EQUAL_BAR = 6;
    private static final Color GUIDE_COLOR = Color.web("#FF2D75");
    private static final Color EQUAL_GUIDE_COLOR = Color.web("#22C55E");
    private static final String FILL_KEY = "cellFill";
    private static final Text MEASURE = new Text();
    private static final double PAD_V = 2;
    private static final double PAD_H = 4;
    private static final double HANDLE = 14;
    private static final String DIVIDER_HIGHLIGHT = "-fx-background-color: rgba(51,153,255,0.7);";

    private static final MenuSlot MENU = new MenuSlot();

    private enum SnapKind {
        EDGE, FIT, EQUAL
    }

    // value is in drag space: the base size (plain drag) or the offset delta (Ctrl drag).
    // EDGE/FIT draw a line at `line`; EQUAL draws bars over `equal` ({-1, k} = whole column/row k, else a cell).
    private record SnapTarget(SnapKind kind, double value, double line, List<int[]> equal) {
    }

    private record CellBox(int r1, int c1, int r2, int c2, double[] rect) {
        int lo(boolean v) {
            return v ? c1 : r1;
        }

        int hi(boolean v) {
            return v ? c2 : r2;
        }

        int crossLo(boolean v) {
            return v ? r1 : c1;
        }

        double start(boolean v) {
            return v ? rect[0] : rect[1];
        }

        double end(boolean v) {
            return v ? rect[2] : rect[3];
        }

        double size(boolean v) {
            return end(v) - start(v);
        }
    }

    private static final class Drag {
        boolean vertical, ctrl, lead;
        double start, startBase, minBase, startLayout;
        int b;
        int[] idx;
        double[] startOff;
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        List<SnapTarget> targets = new ArrayList<>();
        SnapTarget active;
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
    private final Path edgeGuide = new Path();
    private final Path equalGuide = new Path();
    private final Region moveHandle = Handles.square(Cursor.MOVE);
    private final Region leftEdge = newDivider(true);
    private final Region topEdge = newDivider(false);
    private final MoveDrag move = new MoveDrag(this, () -> this.snap);
    private SnapGuides snap;

    private double[] xs = new double[] { 0 };
    private double[] ys = new double[] { 0 };

    private int[] cellSel;
    private RichTextArea pressCell;
    private boolean selecting;
    private Drag drag;

    private boolean handleSuppressed = false;
    private final CoalescedTask relayout = new CoalescedTask(this::requestLayout);

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

        for (Path guide : List.of(edgeGuide, equalGuide)) {
            guide.setFill(null);
            guide.setMouseTransparent(true);
            guide.getProperties().put("noExport", Boolean.TRUE);
        }
        edgeGuide.setStroke(GUIDE_COLOR);
        equalGuide.setStroke(EQUAL_GUIDE_COLOR);

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
            if (!n && !MENU.isShowing())
                clearSelection();
        });

        setFocusTraversable(true);
        focusedProperty().addListener((obs, o, n) -> grid.setStroke(n ? Color.web("#3399ff") : Color.BLACK));

        Overlays.deleteOnKey(this, e -> e.getTarget() == this, this::delete);

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
        setFill(cell, null);
        cell.setPadding(new Insets(PAD_V, PAD_H, PAD_V, PAD_H));
        cell.totalHeightEstimateProperty().addListener((obs, o, n) -> relayout.schedule());

        cell.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            boolean multi = cellSel != null && countUnits(cellSel) > 1;
            ContextMenu menu;
            if (!multi && cell.hasSelection()) {
                menu = new TextFormatMenu(cell,
                        change -> cell.updateSelectionStyle(change),
                        align -> cell.updateSelectionParagraphStyle(s -> s.withAlignment(align)));
                int[] unit = unitOf(cell);
                if (unit != null) {
                    menu.getItems().add(new SeparatorMenuItem());
                    menu.getItems().addAll(fillItems(menu, unit));
                }
            } else {
                menu = tableMenu(cell);
            }
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
        final double newColWidth = median(getColumnWidths());
        final double newRowHeight = median(effectiveRowHeights());

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
            colWidths.add(newColWidth);
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
            addRow(newRowHeight);

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

        if (nc > oldCols)
            fitToPage(true);
        if (nr > oldRows)
            fitToPage(false);

        rebuildChildren();
    }

    private static double median(double[] values) {
        double[] v = values.clone();
        Arrays.sort(v);
        int n = v.length;
        return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
    }

    // Scales columns (or rows) down proportionally, with their offsets, when the table runs past the
    // page's right (or bottom) edge. Rows can't go below their text height, so they may still overflow.
    private void fitToPage(boolean horizontal) {
        if (!(getParent() instanceof Region page))
            return;
        double total = horizontal ? computePrefWidth(-1) : computePrefHeight(-1);
        double available = (horizontal ? page.getWidth() : page.getHeight())
                - (horizontal ? getLayoutX() : getLayoutY());
        if (available <= 0 || total <= available)
            return;
        double k = available / total;
        List<Double> sizes = horizontal ? colWidths : rowHeights;
        double[] current = horizontal ? getColumnWidths() : effectiveRowHeights();
        double min = horizontal ? MIN_COL_WIDTH : MIN_ROW_HEIGHT;
        for (int i = 0; i < sizes.size(); i++)
            sizes.set(i, Math.max(min, current[i] * k));
        double[][] off = horizontal ? offX : offY;
        for (double[] row : off)
            for (int i = 0; i < row.length; i++)
                row[i] *= k;
    }

    private void rebuildChildren() {
        List<Node> nodes = new ArrayList<>();
        for (List<RichTextArea> row : cells)
            nodes.addAll(row);
        nodes.add(grid);
        nodes.add(selectionShade);
        nodes.add(edgeGuide);
        nodes.add(equalGuide);
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
        leftEdge.getProperties().put("lead", Boolean.TRUE);
        topEdge.getProperties().put("lead", Boolean.TRUE);
        nodes.add(leftEdge);
        nodes.add(topEdge);
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
        boolean multi = cellSel != null && countUnits(cellSel) > 1;
        int[] fillRect = multi ? cellSel.clone() : (ctxCell == null ? null : unitOf(ctxCell));
        if (multi) {
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
        extra.addAll(FormatMenu.layerItems(this));

        TableFormatMenu menu = new TableFormatMenu(this, extra);
        if (fillRect != null) {
            List<MenuItem> fill = new ArrayList<>(fillItems(menu, fillRect));
            fill.add(new SeparatorMenuItem());
            menu.getItems().addAll(0, fill);
        }
        return menu;
    }

    private int[] unitOf(RichTextArea cell) {
        int[] pos = positionOf(cell);
        if (pos == null)
            return null;
        int[] m = mergeContaining(pos[0], pos[1]);
        return m != null ? m.clone() : new int[] { pos[0], pos[1], pos[0], pos[1] };
    }

    private List<MenuItem> fillItems(ContextMenu menu, int[] rect) {
        Color current = fillOf(cells.get(rect[0]).get(rect[1]));
        ColorPicker picker = new ColorPicker(current != null ? current : Color.WHITE);
        picker.setOnAction(e -> fillCells(rect, ColorUtil.opaque(picker.getValue())));
        picker.showingProperty().addListener((obs, was, is) -> menu.setAutoHide(!is));

        boolean anyFill = false;
        for (int r = rect[0]; r <= rect[2]; r++)
            for (int c = rect[1]; c <= rect[3]; c++)
                anyFill |= fillOf(cells.get(r).get(c)) != null;
        MenuItem none = new MenuItem(I18n.t("table.noFill"));
        none.setDisable(!anyFill);
        none.setOnAction(e -> fillCells(rect, null));

        return List.of(FormatMenu.row(I18n.t("table.fill"), picker), none);
    }

    private void fillCells(int[] rect, Color color) {
        for (int r = rect[0]; r <= rect[2] && r < cells.size(); r++)
            for (int c = rect[1]; c <= rect[3] && c < colWidths.size(); c++)
                setFill(cells.get(r).get(c), color);
    }

    private static Color fillOf(RichTextArea cell) {
        return (Color) cell.getProperties().get(FILL_KEY);
    }

    private static void setFill(RichTextArea cell, Color color) {
        if (color == null)
            cell.getProperties().remove(FILL_KEY);
        else
            cell.getProperties().put(FILL_KEY, color);
        cell.setStyle("-fx-background-color: " + (color == null ? "transparent" : ColorUtil.toCssHex(color))
                + "; -fx-font-size: 14px;");
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
        updateGuides();

        leftEdge.resizeRelocate(-HIT / 2, 0, HIT, ys[rows]);
        topEdge.resizeRelocate(0, -HIT / 2, xs[cols], HIT);
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
                requestLayout();
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
        boolean lead = d.getProperties().containsKey("lead");
        int[] rc = lead ? new int[] { 0, 0 } : (int[]) d.getProperties().get("rc");
        int r1 = rc[0], c1 = rc[1], r2 = r1, c2 = c1;
        int[] m = mergeContaining(r1, c1);
        if (m != null) {
            r2 = m[2];
            c2 = m[3];
        }

        Drag dr = new Drag();
        dr.vertical = vertical;
        dr.lead = lead;
        dr.ctrl = e.isControlDown() && !lead;
        dr.start = dragPos(dr, e);
        dr.b = lead ? 0 : vertical ? c2 : r2;
        dr.startLayout = vertical ? getLayoutX() : getLayoutY();

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
        }
        dr.targets = snapTargets(dr);

        drag = dr;
        if (dr.ctrl)
            highlight(d, true);
        e.consume();
    }

    private void doDrag(MouseEvent e) {
        if (drag == null)
            return;
        double delta = dragPos(drag, e) - drag.start;

        if (drag.lead) {
            // Left/top edge: the first column/row grows towards the outside while the opposite edge stays put.
            double v = applySnap(Math.max(drag.minBase, drag.startBase - delta), e.isAltDown());
            if (snap != null)
                snap.clear();
            if (drag.vertical) {
                colWidths.set(0, v);
                setLayoutX(drag.startLayout + drag.startBase - v);
            } else {
                rowHeights.set(0, v);
                setLayoutY(drag.startLayout + drag.startBase - effectiveRowHeights()[0]);
            }
        } else if (!drag.ctrl) {
            double v = applySnap(Math.max(drag.minBase, drag.startBase + delta), e.isAltDown());
            if (snap != null && drag.active != null) {
                snap.clear();
            } else if (snap != null) {
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
            double d = applySnap(drag.lo > drag.hi ? 0 : Math.max(drag.lo, Math.min(drag.hi, delta)),
                    e.isAltDown());
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

    // Edge drags move the table itself, so they are measured in the parent's coordinates.
    private double dragPos(Drag dr, MouseEvent e) {
        Point2D p = dr.lead && getParent() != null ? getParent().sceneToLocal(e.getSceneX(), e.getSceneY())
                : sceneToLocal(e.getSceneX(), e.getSceneY());
        return dr.vertical ? p.getX() : p.getY();
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
            move.begin(e);
            e.consume();
        });
        moveHandle.setOnMouseDragged(e -> {
            move.update(e);
            e.consume();
        });
    }

    private void showMenu(ContextMenu menu, ContextMenuEvent e) {
        menu.setOnHidden(ev -> {
            if (!isFocusWithin())
                clearSelection();
        });
        MENU.show(menu, this, e);
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

    public String[][] getCellFills() {
        String[][] out = new String[cells.size()][];
        for (int r = 0; r < out.length; r++) {
            out[r] = new String[cells.get(r).size()];
            for (int c = 0; c < out[r].length; c++)
                out[r][c] = ColorUtil.toHex(fillOf(cells.get(r).get(c)));
        }
        return out;
    }

    public void load(double[] widths, double[] heights, double[][] ox, double[][] oy, int[][] mergeData,
            String[][] encoded, String[][] fills) {
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
        for (int r = 0; r < cells.size() && r < fills.length; r++)
            for (int c = 0; c < cells.get(r).size() && c < fills[r].length; c++)
                setFill(cells.get(r).get(c), ColorUtil.fromHex(fills[r][c]));
        requestLayout();
    }

    @Override
    public PageContent.FloatingTableContent capture() {
        return PageContent.capture(this);
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    public void delete() {
        if (onDelete != null)
            onDelete.run();
    }

    private List<CellBox> boxes() {
        List<CellBox> out = new ArrayList<>();
        for (int r = 0; r < cells.size(); r++) {
            for (int c = 0; c < cells.get(r).size(); c++) {
                if (isHidden(r, c))
                    continue;
                int[] m = mergeContaining(r, c);
                out.add(m == null ? new CellBox(r, c, r, c, rectOf(r, c))
                        : new CellBox(m[0], m[1], m[2], m[3], rectOf(r, c)));
            }
        }
        return out;
    }

    private List<SnapTarget> snapTargets(Drag dr) {
        boolean v = dr.vertical;
        Set<Integer> movingLines = new TreeSet<>();
        if (dr.ctrl)
            for (int i : dr.idx)
                movingLines.add(i);

        List<CellBox> own = new ArrayList<>();
        List<CellBox> neigh = new ArrayList<>();
        List<CellBox> others = new ArrayList<>();
        List<Double> fixed = new ArrayList<>();
        for (CellBox x : boxes()) {
            boolean inLine = !dr.ctrl || movingLines.contains(x.crossLo(v));
            boolean isOwn = inLine && x.hi(v) == dr.b;
            boolean isNeigh = dr.ctrl && inLine && x.lo(v) == dr.b + 1;
            if (isOwn)
                own.add(x);
            else if (isNeigh)
                neigh.add(x);
            else
                others.add(x);
            if (dr.ctrl) {
                if (!isOwn)
                    fixed.add(x.end(v));
                if (!isNeigh)
                    fixed.add(x.start(v));
            } else {
                if (x.hi(v) < dr.b)
                    fixed.add(x.end(v));
                if (x.lo(v) <= dr.b)
                    fixed.add(x.start(v));
            }
        }

        double base = dr.ctrl ? 0 : dr.startBase;
        List<SnapTarget> out = new ArrayList<>();
        if (dr.lead) {
            if (snap != null)
                for (double page : snap.edges(this, v)) {
                    double f = page - dr.startLayout;
                    out.add(new SnapTarget(SnapKind.EDGE, base - f, f, null));
                }
        } else {
            for (CellBox o : own)
                for (double f : fixed)
                    out.add(new SnapTarget(SnapKind.EDGE, base + f - o.end(v), f, null));
        }

        CellBox widest = null;
        double fitDelta = 0;
        for (CellBox o : own) {
            double fit = fitSize(o, v);
            if (fit > 0 && (widest == null || fit - o.size(v) > fitDelta)) {
                widest = o;
                fitDelta = fit - o.size(v);
            }
        }
        if (widest != null)
            out.add(new SnapTarget(SnapKind.FIT, base + fitDelta,
                    dr.lead ? -fitDelta : widest.end(v) + fitDelta, null));

        if (!dr.ctrl) {
            double[] sizes = v ? getColumnWidths() : effectiveRowHeights();
            for (int k = 0; k < sizes.length; k++) {
                if (k == dr.b)
                    continue;
                List<int[]> eq = new ArrayList<>();
                eq.add(new int[] { -1, dr.b });
                for (int j = 0; j < sizes.length; j++)
                    if (j != dr.b && Math.abs(sizes[j] - sizes[k]) < 0.5)
                        eq.add(new int[] { -1, j });
                out.add(new SnapTarget(SnapKind.EQUAL, sizes[k], 0, eq));
            }
        } else {
            Map<Long, List<CellBox>> bySize = new HashMap<>();
            for (CellBox x : others)
                bySize.computeIfAbsent(Math.round(x.size(v) * 2), k -> new ArrayList<>()).add(x);
            for (List<CellBox> same : bySize.values()) {
                double size = same.get(0).size(v);
                List<int[]> marks = new ArrayList<>();
                for (CellBox x : same)
                    marks.add(new int[] { x.r1(), x.c1() });
                for (CellBox o : own)
                    out.add(new SnapTarget(SnapKind.EQUAL, size - o.size(v), 0,
                            withMark(marks, o)));
                for (CellBox n : neigh)
                    out.add(new SnapTarget(SnapKind.EQUAL, n.size(v) - size, 0,
                            withMark(marks, n)));
            }
        }
        return out;
    }

    private static List<int[]> withMark(List<int[]> marks, CellBox x) {
        List<int[]> out = new ArrayList<>(marks);
        out.add(new int[] { x.r1(), x.c1() });
        return out;
    }

    private double fitSize(CellBox x, boolean vertical) {
        RichTextArea cell = cells.get(x.r1()).get(x.c1());
        if (cell.getLength() == 0)
            return 0;
        if (!vertical)
            return need(cell);
        double w = naturalWidth(cell);
        return w > 0 ? Math.ceil(w) + 2 * PAD_H + 2 : 0;
    }

    // Width of the longest paragraph laid out on a single line, measured from the rendered text nodes.
    private static double naturalWidth(Node n) {
        if (n instanceof TextFlow flow) {
            double w = 0;
            for (Node child : flow.getChildrenUnmodifiable()) {
                if (child instanceof Text t) {
                    MEASURE.setFont(t.getFont());
                    MEASURE.setText(t.getText());
                    w += MEASURE.getLayoutBounds().getWidth();
                } else if (child.isManaged() && !(child instanceof Shape)) {
                    w += child.prefWidth(-1);
                }
            }
            return w;
        }
        double max = 0;
        if (n instanceof Parent p)
            for (Node child : p.getChildrenUnmodifiable())
                max = Math.max(max, naturalWidth(child));
        return max;
    }

    // Equal-size targets win within a wider radius; otherwise the closest edge/fit target is used.
    private double applySnap(double raw, boolean disabled) {
        drag.active = null;
        if (disabled)
            return raw;
        double scale = viewScale();
        SnapTarget best = closest(raw, true, EQUAL_SNAP_PX / scale);
        if (best == null)
            best = closest(raw, false, SNAP_PX / scale);
        drag.active = best;
        return best == null ? raw : best.value();
    }

    private SnapTarget closest(double raw, boolean equal, double threshold) {
        SnapTarget best = null;
        double bestDist = threshold;
        for (SnapTarget t : drag.targets) {
            if ((t.kind() == SnapKind.EQUAL) != equal || !reachable(t.value()))
                continue;
            double dist = Math.abs(t.value() - raw);
            if (dist <= bestDist) {
                bestDist = dist;
                best = t;
            }
        }
        return best;
    }

    private boolean reachable(double value) {
        if (!drag.ctrl)
            return value >= drag.minBase;
        return drag.lo <= drag.hi && value >= drag.lo && value <= drag.hi;
    }

    private double viewScale() {
        Point2D a = localToScene(0, 0);
        Point2D b = localToScene(1, 0);
        double s = a.distance(b);
        return s > 0.0001 ? s : 1;
    }

    private void updateGuides() {
        List<PathElement> edge = new ArrayList<>();
        List<PathElement> equal = new ArrayList<>();
        SnapTarget t = drag == null ? null : drag.active;
        if (t != null) {
            double w = 1 / viewScale();
            edgeGuide.setStrokeWidth(w);
            equalGuide.setStrokeWidth(2 * w);
            boolean v = drag.vertical;
            if (t.kind() != SnapKind.EQUAL) {
                // Lead-edge lines are recorded in the table's frame at drag start, which has moved since.
                double at = t.line() - (drag.lead ? (v ? getLayoutX() : getLayoutY()) - drag.startLayout : 0);
                if (v)
                    line(edge, at, -GUIDE_EXTEND, at, ys[ys.length - 1] + GUIDE_EXTEND);
                else
                    line(edge, -GUIDE_EXTEND, at, xs[xs.length - 1] + GUIDE_EXTEND, at);
            } else {
                double[] lines = v ? xs : ys;
                for (int[] u : t.equal()) {
                    if (u[0] < 0) {
                        bar(equal, v, lines[u[1]], lines[u[1] + 1], -EQUAL_BAR);
                    } else {
                        double[] rc = rectOf(u[0], u[1]);
                        bar(equal, v, v ? rc[0] : rc[1], v ? rc[2] : rc[3], (v ? rc[1] : rc[0]) + EQUAL_BAR);
                    }
                }
            }
        }
        edgeGuide.getElements().setAll(edge);
        equalGuide.getElements().setAll(equal);
    }

    // A dimension bar with end ticks from a to b along the drag axis, placed at `at` across it.
    private static void bar(List<PathElement> out, boolean vertical, double a, double b, double at) {
        double tick = EQUAL_BAR / 2;
        a += 2;
        b -= 2;
        if (vertical) {
            line(out, a, at, b, at);
            line(out, a, at - tick, a, at + tick);
            line(out, b, at - tick, b, at + tick);
        } else {
            line(out, at, a, at, b);
            line(out, at - tick, a, at + tick, a);
            line(out, at - tick, b, at + tick, b);
        }
    }

    private static void line(List<PathElement> out, double x1, double y1, double x2, double y2) {
        out.add(new MoveTo(x1, y1));
        out.add(new LineTo(x2, y2));
    }

    public void setSnap(SnapGuides snap) {
        this.snap = snap;
    }
}