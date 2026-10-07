package com.example.model.language.maths;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.example.model.TextStyle;
import com.example.model.language.maths.MathSyntax.BigOp;
import com.example.model.language.maths.MathSyntax.Expr;
import com.example.model.language.maths.MathSyntax.Frac;
import com.example.model.language.maths.MathSyntax.Group;
import com.example.model.language.maths.MathSyntax.Limit;
import com.example.model.language.maths.MathSyntax.Matrix;
import com.example.model.language.maths.MathSyntax.Seq;
import com.example.model.language.maths.MathSyntax.Sqrt;
import com.example.model.language.maths.MathSyntax.Sub;
import com.example.model.language.maths.MathSyntax.Sup;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

public class MathNodeFactory {
    private static final double SCRIPT_SCALE = 0.7;
    private static final double LIMIT_SCALE = 0.6;
    private static final double BIG_SYMBOL_SCALE = 1.8;
    private static final double AXIS = 0.28;

    private MathNodeFactory() {
    }

    public static Node render(MathObject obj, TextStyle style) {
        if (obj == null || obj == MathObject.EMPTY || obj.getType() == null)
            return new Label("");
        if (obj.getType() == MathObject.Type.IMAGE)
            return image(obj.getRaw(), style);
        return render(MathSyntax.fromObject(obj), style);
    }

    public static Node render(Expr expr, TextStyle style) {
        TextStyle s = mathStyle(style);
        return switch (expr) {
            case MathSyntax.Text t -> text(t.text(), s);
            case Seq seq -> {
                List<Node> nodes = new ArrayList<>();
                for (Expr e : seq.items())
                    nodes.add(render(e, s));
                yield new Row(nodes);
            }
            case Group g -> g.inner() instanceof MathSyntax.Text t
                    ? text("(" + t.text() + ")", s)
                    : new Fenced(render(g.inner(), s), false, s);
            case Frac f -> new Fraction(render(f.num(), s), render(f.den(), s), s);
            case Sup sup -> new Script(render(sup.base(), s), render(sup.exp(), scaled(s, SCRIPT_SCALE)), true, s);
            case Sub sub -> new Script(render(sub.base(), s), render(sub.sub(), scaled(s, SCRIPT_SCALE)), false, s);
            case Sqrt r -> new Radical(render(r.inner(), s), s);
            case Matrix m -> {
                List<List<Node>> rows = new ArrayList<>();
                for (List<Expr> row : m.rows()) {
                    List<Node> cells = new ArrayList<>();
                    for (Expr cell : row)
                        cells.add(render(cell, s));
                    rows.add(cells);
                }
                yield new Fenced(new MatrixGrid(rows, s), true, s);
            }
            case BigOp b -> new Operator(b.type().symbol(), optional(b.lower(), scaled(s, LIMIT_SCALE)),
                    optional(b.upper(), scaled(s, LIMIT_SCALE)), render(b.body(), s), s);
            case Limit l -> new LimitBox(optional(l.condition(), scaled(s, LIMIT_SCALE)), render(l.body(), s), s);
        };
    }

    public static Node fraction(String numerator, String denominator, TextStyle style) {
        return render(new Frac(MathSyntax.parseArgument(numerator), MathSyntax.parseArgument(denominator)), style);
    }

    public static Node exponent(String base, String exponent, TextStyle style) {
        return render(new Sup(MathSyntax.parse(base), MathSyntax.parseArgument(exponent)), style);
    }

    public static Node subscript(String base, String sub, TextStyle style) {
        return render(new Sub(MathSyntax.parse(base), MathSyntax.parseArgument(sub)), style);
    }

    public static Node sqrt(String content, TextStyle style) {
        return render(new Sqrt(MathSyntax.parseArgument(content)), style);
    }

    public static Node matrix(String content, TextStyle style) {
        return render(new MathObject(MathObject.Type.MATRIX, content), style);
    }

    public static Node bigOperator(String symbol, String lower, String upper, String value, TextStyle style) {
        MathObject.Type type = MathObject.Type.SUM;
        for (MathObject.Type t : MathObject.Type.values())
            if (symbol.equals(t.symbol()))
                type = t;
        return render(new BigOp(type, MathSyntax.parseArgument(lower), MathSyntax.parseArgument(upper),
                MathSyntax.parseArgument(value)), style);
    }

    public static Node limit(String condition, String value, TextStyle style) {
        return render(new Limit(MathSyntax.parseArgument(condition), MathSyntax.parseArgument(value)), style);
    }

    public static Node image(String raw, TextStyle style) {
        try {
            int sep = raw.indexOf('|');
            String base64 = raw.substring(sep + 1);
            byte[] bytes = Base64.getDecoder().decode(base64);

            Image img = new Image(new ByteArrayInputStream(bytes));
            ImageView view = new ImageView(img);
            view.setPreserveRatio(true);

            double maxWidth = 400;
            if (img.getWidth() > maxWidth) {
                view.setFitWidth(maxWidth);
            }

            return view;
        } catch (Exception e) {
            return new Label("[image]");
        }
    }

    private static TextStyle mathStyle(TextStyle style) {
        TextStyle s = style != null ? style : TextStyle.DEFAULT;
        return s.withFontFamily(null).withUnderline(false).withHighlight(null).withLink(null)
                .withBaselineShift(null).withCodeTheme(null);
    }

    private static double size(TextStyle s) {
        return s.fontSize() != null ? s.fontSize() : 12;
    }

    private static TextStyle scaled(TextStyle s, double factor) {
        return s.withFontSize((int) Math.max(6, Math.round(size(s) * factor)));
    }

    private static Color ink(TextStyle s) {
        return s.textColor() != null ? s.textColor() : Color.BLACK;
    }

    private static Node text(String str, TextStyle s) {
        Text t = new Text(str);
        t.setStyle(s.toCss());
        return t;
    }

    private static Node optional(Expr e, TextStyle s) {
        return MathSyntax.isEmpty(e) ? null : render(e, s);
    }

    private static double w(Node n) {
        return n == null ? 0 : n.prefWidth(-1);
    }

    private static double h(Node n) {
        return n == null ? 0 : n.prefHeight(-1);
    }

    private static double baseline(Node n) {
        if (n == null)
            return 0;
        double b = n.getBaselineOffset();
        return b == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? h(n) : b;
    }

    private abstract static class Box extends Region {
        protected double width, height, base;

        Box(Node... kids) {
            for (Node k : kids)
                if (k != null)
                    getChildren().add(k);
            setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
            setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        }

        protected abstract void arrange(boolean place);

        @Override
        protected double computePrefWidth(double h) {
            arrange(false);
            return width;
        }

        @Override
        protected double computePrefHeight(double w) {
            arrange(false);
            return height;
        }

        @Override
        public double getBaselineOffset() {
            arrange(false);
            return base;
        }

        @Override
        protected void layoutChildren() {
            arrange(true);
        }

        static void place(Node n, double x, double y) {
            if (n != null) {
                n.autosize();
                n.relocate(x, y);
            }
        }
    }

    private static final class Row extends Box {
        private final List<Node> items;

        Row(List<Node> items) {
            super(items.toArray(Node[]::new));
            this.items = items;
        }

        @Override
        protected void arrange(boolean place) {
            double asc = 0, desc = 0, x = 0;
            for (Node n : items) {
                asc = Math.max(asc, baseline(n));
                desc = Math.max(desc, h(n) - baseline(n));
            }
            for (Node n : items) {
                if (place)
                    place(n, x, asc - baseline(n));
                x += w(n) + 1;
            }
            width = Math.max(0, x - 1);
            height = asc + desc;
            base = asc;
        }
    }

    private static final class Fraction extends Box {
        private final Node num, den;
        private final Rectangle bar = new Rectangle();
        private final double size;

        Fraction(Node num, Node den, TextStyle s) {
            super(num, den);
            getChildren().add(bar);
            bar.setFill(ink(s));
            this.num = num;
            this.den = den;
            this.size = size(s);
        }

        @Override
        protected void arrange(boolean place) {
            double pad = Math.max(2, size * 0.15), gap = Math.max(1.5, size * 0.12);
            double thickness = Math.max(1, size / 14);
            width = Math.max(w(num), w(den)) + 2 * pad;
            double barY = h(num) + gap;
            height = barY + thickness + gap + h(den);
            base = barY + thickness / 2 + size * AXIS;
            if (place) {
                place(num, (width - w(num)) / 2, 0);
                bar.setX(0);
                bar.setY(barY);
                bar.setWidth(width);
                bar.setHeight(thickness);
                place(den, (width - w(den)) / 2, barY + thickness + gap);
            }
        }
    }

    private static final class Script extends Box {
        private final Node baseNode, script;
        private final boolean sup;
        private final double size;

        Script(Node baseNode, Node script, boolean sup, TextStyle s) {
            super(baseNode, script);
            this.baseNode = baseNode;
            this.script = script;
            this.sup = sup;
            this.size = size(s);
        }

        @Override
        protected void arrange(boolean place) {
            double bb = baseline(baseNode), bh = h(baseNode);
            double sb = baseline(script), sh = h(script);
            double asc, desc, scriptY;
            if (sup) {
                double shift = Math.max(Math.max(size * 0.42, sh - sb + size * 0.12), bb - sb * 0.9);
                asc = Math.max(bb, shift + sb);
                desc = Math.max(bh - bb, sh - sb - shift);
                scriptY = asc - shift - sb;
            } else {
                double shift = Math.max(size * 0.22, sb - size * 0.6);
                asc = Math.max(bb, sb - shift);
                desc = Math.max(bh - bb, shift + sh - sb);
                scriptY = asc + shift - sb;
            }
            width = w(baseNode) + 1 + w(script);
            height = asc + desc;
            base = asc;
            if (place) {
                place(baseNode, 0, asc - bb);
                place(script, w(baseNode) + 1, scriptY);
            }
        }
    }

    private static final class Radical extends Box {
        private final Node content;
        private final Path sign = new Path();
        private final double size;

        Radical(Node content, TextStyle s) {
            super(content);
            getChildren().add(sign);
            this.content = content;
            this.size = size(s);
            sign.setStroke(ink(s));
            sign.setStrokeWidth(Math.max(1, size / 12));
            sign.setFill(null);
        }

        @Override
        protected void arrange(boolean place) {
            double t = sign.getStrokeWidth(), pad = Math.max(1.5, size * 0.12);
            double hook = Math.max(6, size * 0.55);
            width = hook + 2 + w(content) + 2;
            height = t + pad + h(content) + 1;
            base = t + pad + baseline(content);
            if (place) {
                double top = t / 2;
                sign.getElements().setAll(
                        new MoveTo(0, height * 0.62),
                        new LineTo(hook * 0.25, height * 0.55),
                        new LineTo(hook * 0.6, height - t / 2),
                        new LineTo(hook, top),
                        new LineTo(width, top));
                place(content, hook + 2, t + pad);
            }
        }
    }

    private static final class Fenced extends Box {
        private final Node content;
        private final Path left = new Path(), right = new Path();
        private final boolean square;
        private final double size;

        Fenced(Node content, boolean square, TextStyle s) {
            super(content);
            getChildren().addAll(left, right);
            this.content = content;
            this.square = square;
            this.size = size(s);
            for (Path p : List.of(left, right)) {
                p.setStroke(ink(s));
                p.setStrokeWidth(Math.max(1, size / 12));
                p.setFill(null);
            }
        }

        @Override
        protected void arrange(boolean place) {
            double pad = 1;
            double ph = h(content) + 2 * pad;
            double pw = square ? Math.max(4, size * 0.3) : Math.max(4, Math.min(ph * 0.18, size * 0.6));
            width = pw + 2 + w(content) + 2 + pw;
            height = ph;
            base = pad + baseline(content);
            if (place) {
                double r = width;
                if (square) {
                    left.getElements().setAll(new MoveTo(pw, 0), new LineTo(1, 0), new LineTo(1, ph),
                            new LineTo(pw, ph));
                    right.getElements().setAll(new MoveTo(r - pw, 0), new LineTo(r - 1, 0), new LineTo(r - 1, ph),
                            new LineTo(r - pw, ph));
                } else {
                    left.getElements().setAll(new MoveTo(pw, 0), new QuadCurveTo(-pw * 0.3, ph / 2, pw, ph));
                    right.getElements().setAll(new MoveTo(r - pw, 0),
                            new QuadCurveTo(r + pw * 0.3, ph / 2, r - pw, ph));
                }
                place(content, pw + 2, pad);
            }
        }
    }

    private static final class MatrixGrid extends Box {
        private final List<List<Node>> rows;
        private final double size;

        MatrixGrid(List<List<Node>> rows, TextStyle s) {
            super(rows.stream().flatMap(List::stream).toArray(Node[]::new));
            this.rows = rows;
            this.size = size(s);
        }

        @Override
        protected void arrange(boolean place) {
            int cols = rows.stream().mapToInt(List::size).max().orElse(0);
            double hgap = size * 0.8, vgap = size * 0.3;
            double[] colW = new double[cols];
            double[] asc = new double[rows.size()], desc = new double[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                List<Node> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    Node n = row.get(c);
                    colW[c] = Math.max(colW[c], w(n));
                    asc[r] = Math.max(asc[r], baseline(n));
                    desc[r] = Math.max(desc[r], h(n) - baseline(n));
                }
            }
            width = 0;
            for (double cw : colW)
                width += cw;
            width += hgap * Math.max(0, cols - 1);
            double y = 0;
            for (int r = 0; r < rows.size(); r++) {
                if (place) {
                    double x = 0;
                    List<Node> row = rows.get(r);
                    for (int c = 0; c < cols; c++) {
                        if (c < row.size()) {
                            Node n = row.get(c);
                            place(n, x + (colW[c] - w(n)) / 2, y + asc[r] - baseline(n));
                        }
                        x += colW[c] + hgap;
                    }
                }
                y += asc[r] + desc[r] + (r < rows.size() - 1 ? vgap : 0);
            }
            height = y;
            base = height / 2 + size * AXIS;
        }
    }

    private static final class Operator extends Box {
        private final Node upper, symbol, lower, body;
        private final double size;

        Operator(String sym, Node lower, Node upper, Node body, TextStyle s) {
            this(upper, text(sym, scaled(s, BIG_SYMBOL_SCALE)), lower, body, s);
        }

        private Operator(Node upper, Node symbol, Node lower, Node body, TextStyle s) {
            super(upper, symbol, lower, body);
            this.upper = upper;
            this.symbol = symbol;
            this.lower = lower;
            this.body = body;
            this.size = size(s);
        }

        @Override
        protected void arrange(boolean place) {
            double stackW = Math.max(w(symbol), Math.max(w(upper), w(lower)));
            double symTop = h(upper) * 0.85;
            double symBottom = symTop + h(symbol);
            double stackBase = symTop + h(symbol) / 2 + size * AXIS;
            double top = Math.max(0, baseline(body) - stackBase);
            base = top + stackBase;
            double stackH = symBottom + h(lower) * 0.85;
            height = Math.max(top + stackH, base + h(body) - baseline(body));
            double gap = size * 0.25;
            width = stackW + gap + w(body);
            if (place) {
                place(upper, (stackW - w(upper)) / 2, top);
                place(symbol, (stackW - w(symbol)) / 2, top + symTop);
                place(lower, (stackW - w(lower)) / 2, top + symBottom - h(lower) * 0.15);
                place(body, stackW + gap, base - baseline(body));
            }
        }
    }

    private static final class LimitBox extends Box {
        private final Node lim, condition, body;
        private final double size;

        LimitBox(Node condition, Node body, TextStyle s) {
            this(text("lim", s), condition, body, s);
        }

        private LimitBox(Node lim, Node condition, Node body, TextStyle s) {
            super(lim, condition, body);
            this.lim = lim;
            this.condition = condition;
            this.body = body;
            this.size = size(s);
        }

        @Override
        protected void arrange(boolean place) {
            double stackW = Math.max(w(lim), w(condition));
            double asc = Math.max(baseline(lim), baseline(body));
            double limTop = asc - baseline(lim);
            double condTop = limTop + h(lim) * 0.9;
            double gap = size * 0.25;
            width = stackW + gap + w(body);
            base = asc;
            height = Math.max(condTop + h(condition), asc + h(body) - baseline(body));
            if (place) {
                place(lim, (stackW - w(lim)) / 2, limTop);
                place(condition, (stackW - w(condition)) / 2, condTop);
                place(body, stackW + gap, asc - baseline(body));
            }
        }
    }
}
