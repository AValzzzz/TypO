package com.example.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import com.example.model.help.HelpStep;
import com.example.model.language.maths.MathNodeFactory;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class HelpDemoPlayer extends VBox {
    private static final double W = 380, H = 230;
    private static final String BLUE = "#3399ff", GREEN = "#33cc66";
    private static final long CHAR_MS = 90;

    private final List<HelpStep> steps;
    private final IntFunction<String> captions;

    private final StackPane objectLayer = new StackPane();
    private final Pane overlay = new Pane();
    private final Text typed = new Text();
    private final Label caption = new Label();
    private final Label keyBadge = new Label();
    private final Polygon cursor = new Polygon(0, 0, 0, 16, 4, 12, 7, 18, 9, 17, 6, 11, 11, 11);
    private final List<Animation> running = new ArrayList<>();

    private int generation;
    private String typedValue = "";
    private Node object;
    private VBox menuBox;

    private Text objectText;
    private TextFlow objectFlow;
    private final Set<String> textStyles = new LinkedHashSet<>();
    private boolean textSelected, textFixedWidth;
    private int textSize = 22;
    private String textAlign = "left";

    public HelpDemoPlayer(List<HelpStep> steps, IntFunction<String> captions, String replayLabel) {
        super(8);
        this.steps = steps;
        this.captions = captions;

        objectLayer.setPrefSize(W, H);
        objectLayer.setMinSize(W, H);
        objectLayer.setMaxSize(W, H);
        objectLayer.setAlignment(Pos.CENTER);
        objectLayer.setMouseTransparent(true);

        typed.setFont(Font.font("Monospaced", 18));
        typed.setX(16);
        typed.setY(30);

        overlay.setPrefSize(W, H);
        overlay.setMouseTransparent(true);
        cursor.setFill(Color.BLACK);
        cursor.setStroke(Color.WHITE);
        keyBadge.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-padding: 3 8 3 8; "
                + "-fx-background-radius: 4; -fx-font-size: 12px; -fx-font-family: 'Monospaced';");
        keyBadge.relocate(10, H - 32);

        Pane page = new Pane(objectLayer, typed, overlay);
        page.setPrefSize(W, H);
        page.setMinSize(W, H);
        page.setMaxSize(W, H);
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #dfdad9; "
                + "-fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(87,82,121,0.15), 14, 0.05, 0, 4);");
        Rectangle clip = new Rectangle(W, H);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        page.setClip(clip);

        caption.setWrapText(true);
        caption.setPrefWidth(W);
        caption.setMinHeight(44);
        caption.getStyleClass().add("help-caption");

        Button replay = new Button("\u21BB  " + replayLabel);
        replay.getStyleClass().add("btn-ghost");
        Motion.interactive(replay);
        replay.setOnAction(e -> play());

        setPadding(new Insets(6, 0, 6, 0));
        getChildren().addAll(page, caption, replay);
        reset();
    }

    public void play() {
        stop();
        reset();
        run(0, ++generation);
    }

    public void stop() {
        generation++;
        for (Animation a : new ArrayList<>(running))
            a.stop();
        running.clear();
    }

    private void reset() {
        typedValue = "";
        showTyped();
        objectLayer.getChildren().clear();
        objectLayer.setScaleX(1);
        objectLayer.setScaleY(1);
        objectLayer.setTranslateX(0);
        objectLayer.setTranslateY(0);
        object = null;
        objectText = null;
        objectFlow = null;
        textStyles.clear();
        menuBox = null;
        overlay.getChildren().setAll(keyBadge, cursor);
        keyBadge.setVisible(false);
        cursor.setVisible(false);
        cursor.setTranslateX(0);
        cursor.setTranslateY(0);
        caption.setText("");
    }

    private void run(int i, int gen) {
        if (gen != generation || i >= steps.size())
            return;
        HelpStep s = steps.get(i);
        long duration = exec(s, gen);
        if (s.par || duration <= 0)
            run(i + 1, gen);
        else
            after(duration, gen, () -> run(i + 1, gen));
    }

    private void after(long ms, int gen, Runnable next) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> {
            running.remove(p);
            if (gen == generation)
                next.run();
        });
        running.add(p);
        p.play();
    }

    private void track(Animation a) {
        running.add(a);
        a.setOnFinished(e -> running.remove(a));
        a.play();
    }

    private long exec(HelpStep s, int gen) {
        String t = s.type == null ? "" : s.type;
        switch (t) {
            case "type":
                return type(s.text, gen);
            case "clear":
                typedValue = "";
                showTyped();
                return 0;
            case "show":
                show(s);
                return s.ms > 0 ? s.ms : 800;
            case "caption":
                caption.setText(captions.apply(s.cap));
                return s.ms > 0 ? s.ms : 1200;
            case "menu":
                menu(s);
                return s.ms > 0 ? s.ms : 1000;
            case "closeMenu":
                removeMenu();
                return 0;
            case "key":
                key(s.keys);
                return s.ms > 0 ? s.ms : 900;
            case "cursor":
                return cursor(s);
            case "anim":
                return anim(s);
            case "style":
                style(s);
                return s.ms > 0 ? s.ms : 700;
            case "transform":
                return transform(s);
            case "pause":
                return s.ms > 0 ? s.ms : 600;
            default:
                return 0;
        }
    }

    private void showTyped() {
        typed.setText(typedValue + "|");
    }

    private long type(String text, int gen) {
        if (text == null || text.isEmpty())
            return 0;
        final String base = typedValue;
        Timeline t = new Timeline();
        for (int k = 1; k <= text.length(); k++) {
            final int len = k;
            t.getKeyFrames().add(new KeyFrame(Duration.millis((double) CHAR_MS * k), e -> {
                if (gen != generation)
                    return;
                typedValue = base + text.substring(0, len);
                showTyped();
            }));
        }
        track(t);
        return CHAR_MS * text.length() + 300;
    }

    private void key(String keys) {
        boolean on = keys != null && !keys.isEmpty();
        keyBadge.setText(on ? keys : "");
        keyBadge.setVisible(on);
    }

    private void menu(HelpStep s) {
        removeMenu();
        if (s.caps == null)
            return;
        VBox m = new VBox();
        m.setStyle("-fx-background-color: white; -fx-border-color: #999; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 2, 2);");
        m.setLayoutX(s.x != null ? s.x : 200);
        m.setLayoutY(s.y != null ? s.y : 60);
        for (int k = 0; k < s.caps.length; k++) {
            Label l = new Label(captions.apply(s.caps[k]));
            l.setMaxWidth(Double.MAX_VALUE);
            l.setPadding(new Insets(3, 14, 3, 10));
            l.setStyle("-fx-font-size: 12px;" + (k == s.hl
                    ? "-fx-background-color: #3399ff; -fx-text-fill: white;"
                    : "-fx-text-fill: #222;"));
            m.getChildren().add(l);
        }
        menuBox = m;
        overlay.getChildren().add(m);
    }

    private void removeMenu() {
        if (menuBox != null)
            overlay.getChildren().remove(menuBox);
        menuBox = null;
    }

    private long cursor(HelpStep s) {
        double x = s.x != null ? s.x : 0;
        double y = s.y != null ? s.y : 0;
        if (!cursor.isVisible()) {
            cursor.setTranslateX(x);
            cursor.setTranslateY(y);
            cursor.setVisible(true);
            return 150;
        }
        long ms = s.ms > 0 ? s.ms : 700;
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), cursor);
        tt.setToX(x);
        tt.setToY(y);
        track(tt);
        return ms;
    }

    private long anim(HelpStep s) {
        if (object == null)
            return 0;
        long ms = s.ms > 0 ? s.ms : 800;
        ParallelTransition pt = new ParallelTransition();
        if (s.dx != null || s.dy != null) {
            TranslateTransition t = new TranslateTransition(Duration.millis(ms), object);
            t.setByX(s.dx != null ? s.dx : 0);
            t.setByY(s.dy != null ? s.dy : 0);
            pt.getChildren().add(t);
        }
        if (s.scale != null) {
            ScaleTransition t = new ScaleTransition(Duration.millis(ms), object);
            t.setToX(s.scale);
            t.setToY(s.scale);
            pt.getChildren().add(t);
        }
        if (s.angle != null) {
            RotateTransition t = new RotateTransition(Duration.millis(ms), object);
            t.setByAngle(s.angle);
            pt.getChildren().add(t);
        }
        track(pt);
        return ms;
    }

    private long transform(HelpStep s) {
        long ms = s.ms > 0 ? s.ms : 800;
        ParallelTransition pt = new ParallelTransition();
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), objectLayer);
        st.setToX(s.scale != null ? s.scale : 1);
        st.setToY(s.scale != null ? s.scale : 1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), objectLayer);
        tt.setToX(s.dx != null ? s.dx : 0);
        tt.setToY(s.dy != null ? s.dy : 0);
        pt.getChildren().addAll(st, tt);
        track(pt);
        return ms;
    }

    private void style(HelpStep s) {
        if (objectText == null)
            return;
        if (s.add != null)
            textStyles.addAll(Arrays.asList(s.add));
        if (s.deselect)
            textSelected = false;
        if (s.align != null)
            textAlign = s.align;
        refreshText();
    }

    private void show(HelpStep s) {
        typedValue = "";
        typed.setText("");
        objectLayer.getChildren().clear();
        objectText = null;
        objectFlow = null;
        textStyles.clear();

        Node n = build(s);
        object = n;
        if (n == null)
            return;
        if (n instanceof Region r)
            r.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        objectLayer.getChildren().add(n);

        FadeTransition ft = new FadeTransition(Duration.millis(250), n);
        ft.setFromValue(0);
        ft.setToValue(1);
        track(ft);
    }

    private Node build(HelpStep s) {
        String k = s.kind == null ? "" : s.kind;
        return switch (k) {
            case "table" -> table(s);
            case "circle", "square", "triangle" -> shape(k, s);
            case "arrow" -> arrow(s);
            case "math" -> math(s);
            case "text" -> textObject(s);
            case "textbox" -> textBox(s);
            case "code" -> code(s);
            case "image" -> image(s);
            case "page" -> pages(s);
            case "layers" -> layers(s);
            case "guides" -> guides(s);
            default -> null;
        };
    }

    private static Pane box(double w, double h) {
        Pane p = new Pane();
        p.setPrefSize(w, h);
        p.setMinSize(w, h);
        p.setMaxSize(w, h);
        return p;
    }

    private static Rectangle handle(double x, double y, double size, String color) {
        Rectangle r = new Rectangle(x, y, size, size);
        r.setFill(Color.web(color));
        r.setStroke(Color.WHITE);
        return r;
    }

    private static Circle dot(double x, double y, String color) {
        Circle c = new Circle(x, y, 6, Color.web(color));
        c.setStroke(Color.WHITE);
        return c;
    }

    private static void addResizeRotate(Pane p, double w, double h) {
        Line l = new Line(w / 2, 0, w / 2, -30);
        l.setStroke(Color.web(GREEN));
        p.getChildren().addAll(l, handle(w - 6, h - 6, 12, BLUE), dot(w / 2, -30, GREEN));
    }

    private static boolean has(String[] arr, String v) {
        if (arr == null)
            return false;
        for (String a : arr)
            if (v.equals(a))
                return true;
        return false;
    }

    private static String arg(String[] a, int i) {
        return a != null && i < a.length ? a[i] : "";
    }

    private Node table(HelpStep s) {
        int rows = Math.max(1, s.rows), cols = Math.max(1, s.cols);
        double rh = 30;
        double[] x = new double[cols + 1];
        for (int c = 0; c < cols; c++)
            x[c + 1] = x[c] + ((s.w != null && c < s.w.length) ? s.w[c] : 70);

        Pane p = box(x[cols], rows * rh);
        int[] m = (s.merge != null && s.merge.length == 4) ? s.merge : null;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int r2 = r, c2 = c;
                if (m != null && r >= m[0] && r <= m[2] && c >= m[1] && c <= m[3]) {
                    if (r != m[0] || c != m[1])
                        continue;
                    r2 = m[2];
                    c2 = m[3];
                }
                Rectangle cell = new Rectangle(x[c], r * rh, x[c2 + 1] - x[c], (r2 - r + 1) * rh);
                cell.setFill(Color.WHITE);
                cell.setStroke(Color.BLACK);
                p.getChildren().add(cell);
            }
        }
        if (s.shade != null && s.shade.length == 4) {
            Rectangle shade = new Rectangle(x[s.shade[1]], s.shade[0] * rh,
                    x[s.shade[3] + 1] - x[s.shade[1]], (s.shade[2] - s.shade[0] + 1) * rh);
            shade.setFill(Color.rgb(51, 153, 255, 0.3));
            p.getChildren().add(shade);
        }
        if (s.handles)
            p.getChildren().add(handle(-12, -12, 12, BLUE));
        return p;
    }

    private Node shape(String kind, HelpStep s) {
        Shape sh = switch (kind) {
            case "circle" -> new Ellipse(40, 40, 40, 40);
            case "square" -> new Rectangle(0, 0, 80, 80);
            default -> new Polygon(40, 0, 80, 80, 0, 80);
        };
        sh.setFill(Color.rgb(100, 149, 237, 0.4));
        sh.setStroke(Color.BLACK);
        sh.setStrokeWidth(2);
        Pane p = box(80, 80);
        p.getChildren().add(sh);
        if (s.handles)
            addResizeRotate(p, 80, 80);
        return p;
    }

    private Node arrow(HelpStep s) {
        double cx = s.curve ? 20 : 70, cy = s.curve ? -20 : 35;
        Pane p = box(140, 70);
        QuadCurve c = new QuadCurve(0, 70, cx, cy, 140, 0);
        c.setFill(null);
        c.setStroke(Color.BLACK);
        c.setStrokeWidth(2.5);

        double dx = 140 - cx, dy = -cy;
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double ux = dx / len, uy = dy / len, px = -uy, py = ux;
        double bx = 140 - ux * 14, by = -uy * 14;
        Polygon head = new Polygon(140, 0, bx + px * 5, by + py * 5, bx - px * 5, by - py * 5);
        head.setFill(Color.BLACK);

        p.getChildren().addAll(c, head);
        if (s.handles)
            p.getChildren().addAll(dot(0, 70, BLUE), dot(140, 0, BLUE), dot(cx, cy, GREEN));
        return p;
    }

    private Node math(HelpStep s) {
        String[] a = s.args;
        Node n = switch (s.fn == null ? "" : s.fn) {
            case "fraction" -> MathNodeFactory.fraction(arg(a, 0), arg(a, 1), null);
            case "exponent" -> MathNodeFactory.exponent(arg(a, 0), arg(a, 1), null);
            case "subscript" -> MathNodeFactory.subscript(arg(a, 0), arg(a, 1), null);
            case "sqrt" -> MathNodeFactory.sqrt(arg(a, 0), null);
            case "matrix" -> MathNodeFactory.matrix(arg(a, 0), null);
            case "sum" -> MathNodeFactory.bigOperator("\u03A3", arg(a, 0), arg(a, 1), arg(a, 2), null);
            case "integral" -> MathNodeFactory.bigOperator("\u222B", arg(a, 0), arg(a, 1), arg(a, 2), null);
            case "product" -> MathNodeFactory.bigOperator("\u03A0", arg(a, 0), arg(a, 1), arg(a, 2), null);
            case "limit" -> MathNodeFactory.limit(arg(a, 0), arg(a, 1), null);
            default -> new Label("");
        };
        n.setScaleX(1.8);
        n.setScaleY(1.8);
        return n;
    }

    private Node textObject(HelpStep s) {
        Text t = new Text(s.text == null ? "" : s.text);
        TextFlow f = new TextFlow(t);
        objectText = t;
        objectFlow = f;
        textSize = s.size > 0 ? s.size : 22;
        textSelected = s.selected;
        textFixedWidth = s.align != null;
        textAlign = s.align == null ? "left" : s.align;
        if (s.styles != null)
            textStyles.addAll(Arrays.asList(s.styles));
        if (textFixedWidth) {
            f.setPrefWidth(300);
            f.setMinWidth(300);
            f.setMaxWidth(300);
        }
        refreshText();
        return f;
    }

    private void refreshText() {
        if (objectText == null || objectFlow == null)
            return;
        StringBuilder css = new StringBuilder("-fx-font-size: " + textSize + "px;");
        if (textStyles.contains("bold"))
            css.append("-fx-font-weight: bold;");
        if (textStyles.contains("italic"))
            css.append("-fx-font-style: italic;");
        if (textStyles.contains("strike"))
            css.append("-fx-strikethrough: true;");
        if (textStyles.contains("underline"))
            css.append("-fx-underline: true;");
        if (textStyles.contains("link"))
            css.append("-fx-fill: #0563C1; -fx-underline: true;");
        objectText.setStyle(css.toString());

        StringBuilder flow = new StringBuilder();
        if (textFixedWidth)
            flow.append("-fx-border-color: #ddd; -fx-border-style: dashed;");
        if (textStyles.contains("highlight"))
            flow.append("-fx-background-color: yellow;");
        else if (textSelected)
            flow.append("-fx-background-color: rgba(51,153,255,0.4);");
        objectFlow.setStyle(flow.toString());
        objectFlow.setTextAlignment(switch (textAlign) {
            case "center" -> TextAlignment.CENTER;
            case "right" -> TextAlignment.RIGHT;
            case "justify" -> TextAlignment.JUSTIFY;
            default -> TextAlignment.LEFT;
        });
    }

    private Node textBox(HelpStep s) {
        Pane p = box(160, 34);
        Rectangle bg = new Rectangle(0, 0, 160, 34);
        bg.setFill(has(s.styles, "fill") ? Color.web("#fff3b0") : Color.TRANSPARENT);
        bg.setStroke(has(s.styles, "border") ? Color.BLACK : Color.TRANSPARENT);
        Text t = new Text(10, 23, s.text == null ? "" : s.text);
        t.setFont(Font.font(16));
        p.getChildren().addAll(bg, t);
        if (s.handles) {
            Rectangle outline = new Rectangle(0, 0, 160, 34);
            outline.setFill(null);
            outline.setStroke(Color.web(BLUE));
            outline.getStrokeDashArray().setAll(4.0, 3.0);
            Rectangle widthHandle = new Rectangle(155, 11, 10, 12);
            widthHandle.setFill(Color.web(BLUE));
            widthHandle.setStroke(Color.WHITE);
            p.getChildren().addAll(outline, handle(-14, -14, 14, BLUE), widthHandle);
        }
        return p;
    }

    private Node code(HelpStep s) {
        VBox root = new VBox(0);
        root.setPrefWidth(300);
        root.setMinWidth(300);

        VBox block = new VBox(2);
        block.setPadding(new Insets(6, 10, 6, 10));
        block.setStyle("-fx-background-color: #1e1e1e;");
        if (s.lines != null) {
            for (String line : s.lines) {
                Text t = new Text(line.isEmpty() ? " " : line);
                t.setFont(Font.font("Monospaced", 13));
                t.setFill(Color.web(line.startsWith("```") ? "#8a8a8a" : "#d4d4d4"));
                block.getChildren().add(t);
            }
        }
        root.getChildren().add(block);

        if (s.output != null) {
            VBox out = new VBox(2);
            out.setPadding(new Insets(4, 10, 6, 10));
            out.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3c3c3c; -fx-border-width: 1 0 0 0;");
            Text head = new Text("\u25BE");
            head.setFill(Color.web("#9a9a9a"));
            out.getChildren().add(head);
            for (String line : s.output) {
                Text t = new Text(line);
                t.setFont(Font.font("Monospaced", 12));
                t.setFill(Color.web("#d4d4d4"));
                out.getChildren().add(t);
            }
            root.getChildren().add(out);
        }
        return root;
    }

    private Node image(HelpStep s) {
        Pane p = box(120, 80);
        Rectangle r = new Rectangle(0, 0, 120, 80);
        r.setFill(Color.web("#cfd8dc"));
        r.setStroke(Color.web("#607d8b"));
        Polygon mountains = new Polygon(10, 70, 45, 30, 70, 55, 90, 40, 110, 70);
        mountains.setFill(Color.web("#78909c"));
        Circle sun = new Circle(95, 22, 8, Color.web("#ffd54f"));
        p.getChildren().addAll(r, mountains, sun);
        if (s.handles)
            addResizeRotate(p, 120, 80);
        return p;
    }

    private Node pages(HelpStep s) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        double w = s.landscape ? 127 : 90, h = s.landscape ? 90 : 127;
        for (int i = 0; i < Math.max(1, s.count); i++) {
            Pane pg = box(w, h);
            pg.setStyle("-fx-background-color: white; -fx-border-color: #888; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 1, 1);");
            for (int k = 0; k < 6; k++) {
                Line l = new Line(10, 14 + k * 14, w - 10, 14 + k * 14);
                l.setStroke(Color.web("#bbb"));
                pg.getChildren().add(l);
            }
            row.getChildren().add(pg);
        }
        return row;
    }

    private Node layers(HelpStep s) {
        Pane p = box(110, 80);
        Rectangle sq = new Rectangle(0, 0, 70, 70);
        sq.setFill(Color.rgb(100, 149, 237, 0.85));
        sq.setStroke(Color.BLACK);
        Circle ci = new Circle(70, 42, 36);
        ci.setFill(Color.rgb(255, 152, 0, 0.85));
        ci.setStroke(Color.BLACK);
        if ("square".equals(s.front))
            p.getChildren().addAll(ci, sq);
        else
            p.getChildren().addAll(sq, ci);
        return p;
    }

    private Node guides(HelpStep s) {
        Pane p = box(230, 70);
        Rectangle a = new Rectangle(0, 0, 60, 60);
        a.setFill(Color.rgb(100, 149, 237, 0.5));
        a.setStroke(Color.BLACK);
        Rectangle b = new Rectangle(160, s.selected ? 0 : 22, 50, 50);
        b.setFill(Color.rgb(100, 149, 237, 0.5));
        b.setStroke(Color.BLACK);
        p.getChildren().addAll(a, b);
        if (s.selected) {
            Line g = new Line(-30, 0, 260, 0);
            g.setStroke(Color.web("#FF2D75"));
            p.getChildren().add(g);
        }
        return p;
    }
}