package com.example.model.io;

import static com.example.model.io.Ooxml.M;
import static com.example.model.io.Ooxml.attr;
import static com.example.model.io.Ooxml.elems;
import static com.example.model.io.Ooxml.esc;
import static com.example.model.io.Ooxml.kid;
import static com.example.model.io.Ooxml.kids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.w3c.dom.Element;

import com.example.model.TextStyle;
import com.example.model.io.PageContent.RunContent;
import com.example.model.language.maths.MathObject;
import com.example.model.language.maths.MathSyntax;
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

final class OmmlCodec {
    private static final String INTEGRAL = "\u222B";

    private OmmlCodec() {
    }

    static String write(MathObject o, String rpr) {
        if (o.getType() == MathObject.Type.IMAGE)
            return "";
        return expr(MathSyntax.fromObject(o), rpr);
    }

    private static String expr(Expr e, String rpr) {
        return switch (e) {
            case MathSyntax.Text t -> run(t.text(), rpr);
            case Seq seq -> {
                StringBuilder sb = new StringBuilder();
                for (Expr item : seq.items())
                    sb.append(expr(item, rpr));
                yield sb.toString();
            }
            case Group g -> "<m:d><m:dPr>" + ctrl(rpr) + "</m:dPr><m:e>" + expr(g.inner(), rpr) + "</m:e></m:d>";
            case Frac f -> "<m:f><m:fPr>" + ctrl(rpr) + "</m:fPr><m:num>" + expr(f.num(), rpr) + "</m:num><m:den>"
                    + expr(f.den(), rpr) + "</m:den></m:f>";
            case Sup sup -> "<m:sSup><m:sSupPr>" + ctrl(rpr) + "</m:sSupPr><m:e>" + expr(sup.base(), rpr)
                    + "</m:e><m:sup>" + expr(sup.exp(), rpr) + "</m:sup></m:sSup>";
            case Sub sub -> "<m:sSub><m:sSubPr>" + ctrl(rpr) + "</m:sSubPr><m:e>" + expr(sub.base(), rpr)
                    + "</m:e><m:sub>" + expr(sub.sub(), rpr) + "</m:sub></m:sSub>";
            case Sqrt r -> "<m:rad><m:radPr><m:degHide m:val=\"1\"/>" + ctrl(rpr) + "</m:radPr><m:deg/><m:e>"
                    + expr(r.inner(), rpr) + "</m:e></m:rad>";
            case Matrix m -> {
                StringBuilder sb = new StringBuilder("<m:m>");
                for (List<Expr> row : m.rows()) {
                    sb.append("<m:mr>");
                    for (Expr cell : row)
                        sb.append("<m:e>").append(expr(cell, rpr)).append("</m:e>");
                    sb.append("</m:mr>");
                }
                sb.append("</m:m>");
                yield "<m:d><m:dPr><m:begChr m:val=\"[\"/><m:endChr m:val=\"]\"/>" + ctrl(rpr)
                        + "</m:dPr><m:e>" + sb + "</m:e></m:d>";
            }
            case BigOp b -> {
                String chr = switch (b.type()) {
                    case SUM -> "\u2211";
                    case PRODUCT -> "\u220F";
                    default -> INTEGRAL;
                };
                String loc = b.type() == MathObject.Type.INTEGRAL ? "subSup" : "undOvr";
                yield "<m:nary><m:naryPr><m:chr m:val=\"" + chr + "\"/><m:limLoc m:val=\"" + loc + "\"/>"
                        + (MathSyntax.isEmpty(b.lower()) ? "<m:subHide m:val=\"1\"/>" : "")
                        + (MathSyntax.isEmpty(b.upper()) ? "<m:supHide m:val=\"1\"/>" : "")
                        + ctrl(rpr) + "</m:naryPr><m:sub>" + expr(b.lower(), rpr) + "</m:sub><m:sup>"
                        + expr(b.upper(), rpr) + "</m:sup><m:e>" + expr(b.body(), rpr) + "</m:e></m:nary>";
            }
            case Limit l -> {
                String lim = run("lim", rpr);
                String name = MathSyntax.isEmpty(l.condition()) ? lim
                        : "<m:limLow><m:limLowPr>" + ctrl(rpr) + "</m:limLowPr><m:e>" + lim + "</m:e><m:lim>"
                                + expr(l.condition(), rpr) + "</m:lim></m:limLow>";
                yield "<m:func><m:funcPr>" + ctrl(rpr) + "</m:funcPr><m:fName>" + name + "</m:fName><m:e>"
                        + expr(l.body(), rpr) + "</m:e></m:func>";
            }
        };
    }

    private static String run(String text, String rpr) {
        if (text == null || text.isEmpty())
            return "";
        return "<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr>" + (rpr.isEmpty() ? "" : "<w:rPr>" + rpr + "</w:rPr>")
                + "<m:t xml:space=\"preserve\">" + esc(text) + "</m:t></m:r>";
    }

    private static String ctrl(String rpr) {
        return "<m:ctrlPr>" + (rpr.isEmpty() ? "" : "<w:rPr>" + rpr + "</w:rPr>") + "</m:ctrlPr>";
    }

    static List<RunContent> read(Element oMath, Function<Element, TextStyle> styleOf) {
        List<RunContent> out = new ArrayList<>();
        for (Element e : elems(oMath)) {
            if (!M.equals(e.getNamespaceURI()))
                continue;
            String ln = e.getLocalName();
            if (ln.endsWith("Pr"))
                continue;
            TextStyle style = styleOf.apply(e);
            String flat = flatten(e);
            List<Element> parts = kids(e, M, "e");
            if ("d".equals(ln) && !flat.startsWith("matrix(") && parts.size() == 1) {
                String[] fence = fence(e);
                addText(out, fence[0], style);
                for (RunContent rc : read(parts.get(0), styleOf)) {
                    if (rc.isMath())
                        out.add(rc);
                    else
                        addText(out, rc.text, rc.style);
                }
                addText(out, fence[1], style);
                continue;
            }
            MathObject obj = switch (ln) {
                case "f", "sSup", "sSub", "rad", "d", "m", "nary", "func" -> MathSyntax.toObject(flat);
                default -> null;
            };
            if (obj != null)
                out.add(RunContent.math(obj, style));
            else
                addText(out, flat, style);
        }
        return out;
    }

    private static void addText(List<RunContent> out, String text, TextStyle style) {
        if (text == null || text.isEmpty())
            return;
        int n = out.size();
        if (n > 0 && !out.get(n - 1).isMath() && out.get(n - 1).style.equals(style))
            out.set(n - 1, RunContent.text(out.get(n - 1).text + text, style));
        else
            out.add(RunContent.text(text, style));
    }

    private static String arg(Element parent, String local) {
        return flatten(kid(parent, M, local));
    }






    private static String[] fence(Element d) {
        Element pr = kid(d, M, "dPr");
        Element b = pr == null ? null : kid(pr, M, "begChr");
        Element en = pr == null ? null : kid(pr, M, "endChr");
        return new String[] { b == null ? "(" : attr(b, M, "val"), en == null ? ")" : attr(en, M, "val") };
    }

    static String flatten(Element e) {
        if (e == null || !M.equals(e.getNamespaceURI()))
            return "";
        String ln = e.getLocalName();
        switch (ln) {
            case "t":
                return e.getTextContent();
            case "f":
                return "(" + arg(e, "num") + ")/(" + arg(e, "den") + ")";
            case "sSup":
                return arg(e, "e") + "^(" + arg(e, "sup") + ")";
            case "sSub":
                return arg(e, "e") + "_(" + arg(e, "sub") + ")";
            case "sSubSup":
                return arg(e, "e") + "_(" + arg(e, "sub") + ")^(" + arg(e, "sup") + ")";
            case "rad": {
                String deg = arg(e, "deg");
                return deg.isEmpty() ? "sqrt(" + arg(e, "e") + ")" : deg + "\u221A(" + arg(e, "e") + ")";
            }
            case "nary": {
                Element pr = kid(e, M, "naryPr");
                Element chr = pr == null ? null : kid(pr, M, "chr");
                String c = chr == null ? INTEGRAL : attr(chr, M, "val");
                String name = switch (c) {
                    case "\u2211", "\u03A3" -> "sum";
                    case "\u220F", "\u03A0" -> "prod";
                    case INTEGRAL -> "int";
                    default -> c;
                };
                return name + "_(" + arg(e, "sub") + ")^(" + arg(e, "sup") + ")(" + arg(e, "e") + ")";
            }
            case "d": {
                Element pr = kid(e, M, "dPr");
                Element sp = pr == null ? null : kid(pr, M, "sepChr");
                String[] fence = fence(e);
                String open = fence[0], close = fence[1];
                String sep = sp == null ? "|" : attr(sp, M, "val");
                List<Element> parts = kids(e, M, "e");
                if ("[".equals(open) && parts.size() == 1 && kid(parts.get(0), M, "m") != null)
                    return flatten(kid(parts.get(0), M, "m"));
                StringBuilder sb = new StringBuilder(open);
                boolean first = true;
                for (Element c : kids(e, M, "e")) {
                    if (!first)
                        sb.append(sep);
                    first = false;
                    sb.append(flatten(c));
                }
                return sb.append(close).toString();
            }
            case "m": {
                StringBuilder sb = new StringBuilder("matrix(");
                boolean firstRow = true;
                for (Element mr : kids(e, M, "mr")) {
                    if (!firstRow)
                        sb.append(';');
                    firstRow = false;
                    boolean first = true;
                    for (Element c : kids(mr, M, "e")) {
                        if (!first)
                            sb.append(',');
                        first = false;
                        sb.append(flatten(c));
                    }
                }
                return sb.append(')').toString();
            }
            case "func":
                return flatten(kid(e, M, "fName")) + "(" + arg(e, "e") + ")";
            case "limLow":
                return arg(e, "e") + "_(" + arg(e, "lim") + ")";
            case "limUpp":
                return arg(e, "e") + "^(" + arg(e, "lim") + ")";
            default: {
                StringBuilder sb = new StringBuilder();
                for (Element c : elems(e)) {
                    if (M.equals(c.getNamespaceURI()) && !c.getLocalName().endsWith("Pr"))
                        sb.append(flatten(c));
                }
                return sb.toString();
            }
        }
    }
}