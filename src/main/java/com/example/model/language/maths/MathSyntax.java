package com.example.model.language.maths;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the typed math syntax ({@code x^2}, {@code a/b}, {@code sqrt(x)}, {@code sum_(i=1)^(n)(i)}, ...),
 * where every argument may itself contain math: {@code x^(1/2)}, {@code (a/b)^2}, {@code sqrt(x^2/y_1)}.
 *
 * <p>
 * Parentheses used as argument delimiters ({@code x^(...)}, {@code sqrt(...)}, around a numerator or a
 * denominator) are not displayed; parentheses around the base of an exponent or a subscript, or inside plain
 * text, are kept.
 *
 * <p>
 * {@link MathObject} keeps its historical flat format (e.g. {@code FRACTION "num,den"}); each piece of it is the
 * source of a sub-expression, which {@link #fromObject} parses back.
 */
public final class MathSyntax {
    private MathSyntax() {
    }

    public sealed interface Expr {
    }

    public record Text(String text) implements Expr {
    }

    public record Seq(List<Expr> items) implements Expr {
    }

    /** Parentheses that are displayed around their content. */
    public record Group(Expr inner) implements Expr {
    }

    public record Frac(Expr num, Expr den) implements Expr {
    }

    public record Sup(Expr base, Expr exp) implements Expr {
    }

    public record Sub(Expr base, Expr sub) implements Expr {
    }

    public record Sqrt(Expr inner) implements Expr {
    }

    public record Matrix(List<List<Expr>> rows) implements Expr {
    }

    public record BigOp(MathObject.Type type, Expr lower, Expr upper, Expr body) implements Expr {
    }

    public record Limit(Expr condition, Expr body) implements Expr {
    }

    public static final Expr EMPTY = new Seq(List.of());

    // ---- parsing --------------------------------------------------------------------------------------------

    public static Expr parse(String src) {
        Parser p = new Parser(src == null ? "" : src);
        List<Expr> items = new ArrayList<>();
        while (!p.done()) {
            // A stray closing parenthesis at the top level is plain text.
            if (p.peek() == ')') {
                items.add(new Text(")"));
                p.pos++;
                continue;
            }
            items.addAll(((Seq) p.seq()).items());
        }
        return simplify(items);
    }

    /** Parses an argument: one pair of parentheses around the whole of it is a delimiter, not displayed. */
    public static Expr parseArgument(String src) {
        return unwrap(parse(src));
    }

    private static Expr unwrap(Expr e) {
        return e instanceof Group g ? g.inner() : e;
    }

    private static Expr simplify(List<Expr> items) {
        List<Expr> merged = new ArrayList<>();
        for (Expr e : items) {
            if (e instanceof Seq s) {
                merged.addAll(s.items());
                continue;
            }
            int n = merged.size();
            if (e instanceof Text t && n > 0 && merged.get(n - 1) instanceof Text prev)
                merged.set(n - 1, new Text(prev.text() + t.text()));
            else
                merged.add(e);
        }
        return merged.size() == 1 ? merged.get(0) : new Seq(List.copyOf(merged));
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean done() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        boolean at(char c) {
            return !done() && peek() == c;
        }

        /** seq := frac* (until ')' or the end) */
        Expr seq() {
            List<Expr> items = new ArrayList<>();
            while (!done() && peek() != ')')
                items.add(frac());
            return new Seq(items);
        }

        /** frac := script ('/' script)* */
        Expr frac() {
            Expr left = script();
            while (at('/') && pos + 1 < s.length() && s.charAt(pos + 1) != ')') {
                pos++;
                left = new Frac(unwrap(left), unwrap(script()));
            }
            return left;
        }

        /** script := primary (('^' | '_') argument)* */
        Expr script() {
            Expr base = primary();
            while ((at('^') || at('_')) && pos + 1 < s.length() && s.charAt(pos + 1) != ')') {
                boolean sup = peek() == '^';
                pos++;
                Expr arg = argument();
                base = sup ? new Sup(base, arg) : new Sub(base, arg);
            }
            return base;
        }

        /** argument := '(' seq ')' | [+-]? word | any single character */
        Expr argument() {
            if (at('('))
                return unwrap(group());
            int start = pos;
            if (at('+') || at('-'))
                pos++;
            int wordStart = pos;
            while (!done() && isWordChar(peek()))
                pos++;
            if (pos == wordStart && pos == start)
                pos++;
            return new Text(s.substring(start, pos));
        }

        Expr primary() {
            if (at('('))
                return group();
            if (isWordChar(peek())) {
                int start = pos;
                while (!done() && isWordChar(peek()))
                    pos++;
                String word = s.substring(start, pos);
                Expr special = keyword(word, start);
                if (special != null)
                    return special;
                // A function call such as sin(x) is a single operand: sin(x)/x, f(x)^2.
                if (at('('))
                    return new Seq(List.of(new Text(word), group()));
                return new Text(word);
            }
            return new Text(String.valueOf(s.charAt(pos++)));
        }

        /** '(' seq ')' — a missing ')' closes at the end of the input. */
        Expr group() {
            pos++;
            Expr inner = simplify(((Seq) seq()).items());
            if (at(')'))
                pos++;
            return new Group(inner);
        }

        private Expr keyword(String word, int start) {
            MathObject.Type bigOp = switch (word) {
                case "sum" -> MathObject.Type.SUM;
                case "int" -> MathObject.Type.INTEGRAL;
                case "prod" -> MathObject.Type.PRODUCT;
                default -> null;
            };
            switch (word) {
                case "sqrt":
                    if (at('('))
                        return new Sqrt(unwrap(group()));
                    break;
                case "matrix":
                    if (at('('))
                        return matrix();
                    break;
                case "lim": {
                    int save = pos;
                    Expr cond = EMPTY;
                    if (at('_')) {
                        pos++;
                        cond = limitCondition(argument());
                    }
                    if (at('('))
                        return new Limit(cond, unwrap(group()));
                    pos = save;
                    break;
                }
                default:
                    if (bigOp != null) {
                        int save = pos;
                        Expr lower = EMPTY, upper = EMPTY;
                        if (at('_')) {
                            pos++;
                            lower = argument();
                        }
                        if (at('^')) {
                            pos++;
                            upper = argument();
                        }
                        if (at('('))
                            return new BigOp(bigOp, lower, upper, unwrap(group()));
                        pos = save;
                    }
            }
            return null;
        }

        private Expr matrix() {
            int open = pos;
            int close = matchingParen(s, open);
            String body = s.substring(open + 1, close < 0 ? s.length() : close);
            pos = close < 0 ? s.length() : close + 1;
            List<List<Expr>> rows = new ArrayList<>();
            for (String row : splitTopLevel(body, ';', 0)) {
                List<Expr> cells = new ArrayList<>();
                for (String cell : splitTopLevel(row, ',', 0))
                    cells.add(parseArgument(cell.trim()));
                rows.add(cells);
            }
            return new Matrix(rows);
        }
    }

    /** {@code x->0} becomes {@code x→0}, {@code infinity} becomes {@code ∞}, as the original command did. */
    private static Expr limitCondition(Expr cond) {
        String src = source(cond).replace("->", "→").replace("infinity", "∞");
        return parse(src);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.';
    }

    private static int matchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')' && --depth == 0)
                return i;
        }
        return -1;
    }

    /** Splits on {@code sep} outside parentheses; {@code limit > 0} caps the number of pieces. */
    public static List<String> splitTopLevel(String s, char sep, int limit) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth = Math.max(0, depth - 1);
            else if (c == sep && depth == 0 && (limit <= 0 || out.size() < limit - 1)) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static List<String> pieces(String raw, char sep, int n) {
        List<String> p = splitTopLevel(raw == null ? "" : raw, sep, n);
        while (p.size() < n)
            p.add("");
        return p;
    }

    // ---- MathObject <-> Expr ----------------------------------------------------------------------------------

    /** The expression a math object stands for, or null for an image. */
    public static Expr fromObject(MathObject o) {
        if (o == null || o.getType() == null)
            return EMPTY;
        String raw = o.getRaw() == null ? "" : o.getRaw();
        return switch (o.getType()) {
            case FRACTION -> {
                List<String> p = pieces(raw, ',', 2);
                yield new Frac(parseArgument(p.get(0)), parseArgument(p.get(1)));
            }
            case EXPONENT -> {
                List<String> p = pieces(raw, ',', 2);
                yield new Sup(parse(p.get(0)), parseArgument(p.get(1)));
            }
            case SUBSCRIPT -> {
                List<String> p = pieces(raw, ',', 2);
                yield new Sub(parse(p.get(0)), parseArgument(p.get(1)));
            }
            case SQRT -> new Sqrt(parseArgument(raw));
            case MATRIX -> {
                List<List<Expr>> rows = new ArrayList<>();
                for (String row : splitTopLevel(raw, ';', 0)) {
                    List<Expr> cells = new ArrayList<>();
                    for (String cell : splitTopLevel(row, ',', 0))
                        cells.add(parseArgument(cell.trim()));
                    rows.add(cells);
                }
                yield new Matrix(rows);
            }
            case SUM, INTEGRAL, PRODUCT -> {
                List<String> p = pieces(raw, '|', 3);
                yield new BigOp(o.getType(), parseArgument(p.get(0)), parseArgument(p.get(1)),
                        parseArgument(p.get(2)));
            }
            case LIMIT -> {
                List<String> p = pieces(raw, '|', 2);
                yield new Limit(parseArgument(p.get(0)), parseArgument(p.get(1)));
            }
            case IMAGE -> null;
        };
    }

    /** The math object for a whole expression, or null if it is only text (nothing to convert). */
    public static MathObject toObject(Expr e) {
        while (e instanceof Group g)
            e = g.inner();
        return switch (e) {
            case Frac f -> new MathObject(MathObject.Type.FRACTION, piece(f.num(), ",") + "," + piece(f.den(), ","));
            case Sup s -> new MathObject(MathObject.Type.EXPONENT, basePiece(s.base()) + "," + piece(s.exp(), ""));
            case Sub s -> new MathObject(MathObject.Type.SUBSCRIPT, basePiece(s.base()) + "," + piece(s.sub(), ""));
            case Sqrt r -> new MathObject(MathObject.Type.SQRT, piece(r.inner(), ""));
            case Matrix m -> new MathObject(MathObject.Type.MATRIX, matrixSource(m));
            case BigOp b -> new MathObject(b.type(),
                    piece(b.lower(), "|") + "|" + piece(b.upper(), "|") + "|" + piece(b.body(), "|"));
            case Limit l -> new MathObject(MathObject.Type.LIMIT, piece(l.condition(), "|") + "|" + piece(l.body(), "|"));
            default -> null;
        };
    }

    /** Converts typed math source, or returns null when it contains no math construct. */
    public static MathObject toObject(String src) {
        return toObject(parse(src));
    }

    /** Source of a raw piece, in parentheses if it contains one of the separators of its object. */
    private static String piece(Expr e, String separators) {
        String src = source(e);
        if (e instanceof Group)
            return "(" + src + ")";
        for (char c : separators.toCharArray())
            if (splitTopLevel(src, c, 0).size() > 1)
                return "(" + src + ")";
        return src;
    }

    private static String basePiece(Expr base) {
        String src = source(base);
        return splitTopLevel(src, ',', 0).size() > 1 ? "(" + src + ")" : src;
    }

    private static String matrixSource(Matrix m) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.rows().size(); r++) {
            if (r > 0)
                sb.append(';');
            List<Expr> row = m.rows().get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0)
                    sb.append(',');
                sb.append(piece(row.get(c), ",;"));
            }
        }
        return sb.toString();
    }

    /** Typed syntax for an expression; {@code parse(source(e))} gives {@code e} back. */
    public static String source(Expr e) {
        return switch (e) {
            case Text t -> t.text();
            case Seq s -> {
                StringBuilder sb = new StringBuilder();
                for (Expr i : s.items())
                    sb.append(source(i));
                yield sb.toString();
            }
            case Group g -> "(" + source(g.inner()) + ")";
            case Frac f -> operand(f.num()) + "/" + operand(f.den());
            case Sup s -> scriptBase(s.base()) + "^" + script(s.exp());
            case Sub s -> scriptBase(s.base()) + "_" + script(s.sub());
            case Sqrt r -> "sqrt(" + source(r.inner()) + ")";
            case Matrix m -> "matrix(" + matrixSource(m) + ")";
            case BigOp b -> {
                String name = switch (b.type()) {
                    case SUM -> "sum";
                    case PRODUCT -> "prod";
                    default -> "int";
                };
                boolean bounds = !isEmpty(b.lower()) || !isEmpty(b.upper());
                yield name + (bounds ? "_(" + source(b.lower()) + ")^(" + source(b.upper()) + ")" : "")
                        + "(" + source(b.body()) + ")";
            }
            case Limit l -> "lim" + (isEmpty(l.condition()) ? "" : "_(" + source(l.condition()) + ")")
                    + "(" + source(l.body()) + ")";
        };
    }

    public static boolean isEmpty(Expr e) {
        return e instanceof Seq s && s.items().isEmpty() || e instanceof Text t && t.text().isEmpty();
    }

    private static boolean isWord(Expr e) {
        if (!(e instanceof Text t) || t.text().isEmpty())
            return false;
        for (char c : t.text().toCharArray())
            if (!isWordChar(c))
                return false;
        return true;
    }

    /** {@code f(x)}: a word directly followed by parentheses. */
    private static boolean isCall(Expr e) {
        return e instanceof Seq s && s.items().size() == 2 && isWord(s.items().get(0))
                && s.items().get(1) instanceof Group;
    }

    private static String operand(Expr e) {
        boolean bare = isWord(e) || isCall(e) || e instanceof Sup || e instanceof Sub || e instanceof Sqrt || e instanceof Matrix
                || e instanceof Group;
        return bare ? source(e) : "(" + source(e) + ")";
    }

    private static String scriptBase(Expr e) {
        boolean bare = isWord(e) || isCall(e) || e instanceof Group || e instanceof Sup || e instanceof Sub || e instanceof Sqrt
                || e instanceof Matrix;
        return bare ? source(e) : "(" + source(e) + ")";
    }

    private static String script(Expr e) {
        return isWord(e) ? source(e) : "(" + source(e) + ")";
    }
}
