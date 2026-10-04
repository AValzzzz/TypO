package com.example.model.io;

import static com.example.model.io.Ooxml.M;
import static com.example.model.io.Ooxml.attr;
import static com.example.model.io.Ooxml.elems;
import static com.example.model.io.Ooxml.esc;
import static com.example.model.io.Ooxml.kid;
import static com.example.model.io.Ooxml.kids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.w3c.dom.Element;

import com.example.model.TextStyle;
import com.example.model.io.PageContent.RunContent;
import com.example.model.language.maths.MathObject;

final class OmmlCodec {
    private static final String INTEGRAL = "\u222B";

    private OmmlCodec() {
    }

    static String write(MathObject o, String rpr) {
        String raw = o.getRaw() == null ? "" : o.getRaw();
        return switch (o.getType()) {
            case FRACTION -> {
                String[] p = split(raw, ",", 2);
                yield "<m:f><m:fPr>" + ctrl(rpr) + "</m:fPr><m:num>" + run(p[0], rpr) + "</m:num><m:den>"
                        + run(p[1], rpr) + "</m:den></m:f>";
            }
            case EXPONENT -> {
                String[] p = split(raw, ",", 2);
                yield "<m:sSup><m:sSupPr>" + ctrl(rpr) + "</m:sSupPr><m:e>" + run(p[0], rpr) + "</m:e><m:sup>"
                        + run(p[1], rpr) + "</m:sup></m:sSup>";
            }
            case SUBSCRIPT -> {
                String[] p = split(raw, ",", 2);
                yield "<m:sSub><m:sSubPr>" + ctrl(rpr) + "</m:sSubPr><m:e>" + run(p[0], rpr) + "</m:e><m:sub>"
                        + run(p[1], rpr) + "</m:sub></m:sSub>";
            }
            case SQRT -> "<m:rad><m:radPr><m:degHide m:val=\"1\"/>" + ctrl(rpr) + "</m:radPr><m:deg/><m:e>"
                    + run(raw, rpr) + "</m:e></m:rad>";
            case MATRIX -> {
                StringBuilder m = new StringBuilder("<m:m>");
                for (String row : raw.split(";", -1)) {
                    m.append("<m:mr>");
                    for (String cell : row.split(",", -1))
                        m.append("<m:e>").append(run(cell, rpr)).append("</m:e>");
                    m.append("</m:mr>");
                }
                m.append("</m:m>");
                yield "<m:d><m:dPr><m:begChr m:val=\"[\"/><m:endChr m:val=\"]\"/>" + ctrl(rpr)
                        + "</m:dPr><m:e>" + m + "</m:e></m:d>";
            }
            case SUM, INTEGRAL, PRODUCT -> {
                String[] p = split(raw, "\\|", 3);
                String chr = switch (o.getType()) {
                    case SUM -> "\u2211";
                    case PRODUCT -> "\u220F";
                    default -> INTEGRAL;
                };
                String loc = o.getType() == MathObject.Type.INTEGRAL ? "subSup" : "undOvr";
                yield "<m:nary><m:naryPr><m:chr m:val=\"" + chr + "\"/><m:limLoc m:val=\"" + loc + "\"/>"
                        + (p[0].isEmpty() ? "<m:subHide m:val=\"1\"/>" : "")
                        + (p[1].isEmpty() ? "<m:supHide m:val=\"1\"/>" : "")
                        + ctrl(rpr) + "</m:naryPr><m:sub>" + run(p[0], rpr) + "</m:sub><m:sup>" + run(p[1], rpr)
                        + "</m:sup><m:e>" + run(p[2], rpr) + "</m:e></m:nary>";
            }
            case LIMIT -> {
                String[] p = split(raw, "\\|", 2);
                String lim = run("lim", rpr);
                String name = p[0].isEmpty() ? lim
                        : "<m:limLow><m:limLowPr>" + ctrl(rpr) + "</m:limLowPr><m:e>" + lim + "</m:e><m:lim>"
                                + run(p[0], rpr) + "</m:lim></m:limLow>";
                yield "<m:func><m:funcPr>" + ctrl(rpr) + "</m:funcPr><m:fName>" + name + "</m:fName><m:e>"
                        + run(p[1], rpr) + "</m:e></m:func>";
            }
            case IMAGE -> "";
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

    private static String[] split(String raw, String regex, int n) {
        String[] p = raw.split(regex, n);
        if (p.length >= n)
            return p;
        String[] out = new String[n];
        Arrays.fill(out, "");
        System.arraycopy(p, 0, out, 0, p.length);
        return out;
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
            MathObject obj = switch (ln) {
                case "f" -> new MathObject(MathObject.Type.FRACTION, arg(e, "num") + "," + arg(e, "den"));
                case "sSup" -> new MathObject(MathObject.Type.EXPONENT, arg(e, "e") + "," + arg(e, "sup"));
                case "sSub" -> new MathObject(MathObject.Type.SUBSCRIPT, arg(e, "e") + "," + arg(e, "sub"));
                case "rad" -> radical(e);
                case "d" -> delimited(e);
                case "m" -> matrix(e);
                case "nary" -> nary(e);
                case "func" -> function(e);
                default -> null;
            };
            if (obj != null)
                out.add(RunContent.math(obj, style));
            else
                addText(out, flatten(e), style);
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

    private static MathObject radical(Element e) {
        if (!arg(e, "deg").isEmpty())
            return null;
        return new MathObject(MathObject.Type.SQRT, arg(e, "e"));
    }

    private static MathObject delimited(Element d) {
        List<Element> es = kids(d, M, "e");
        if (es.size() != 1)
            return null;
        List<Element> content = new ArrayList<>();
        for (Element c : elems(es.get(0)))
            if (!c.getLocalName().endsWith("Pr"))
                content.add(c);
        if (content.size() == 1 && M.equals(content.get(0).getNamespaceURI())
                && "m".equals(content.get(0).getLocalName()))
            return matrix(content.get(0));
        return null;
    }

    private static MathObject matrix(Element m) {
        StringBuilder sb = new StringBuilder();
        boolean firstRow = true;
        for (Element mr : kids(m, M, "mr")) {
            if (!firstRow)
                sb.append(';');
            firstRow = false;
            boolean first = true;
            for (Element cell : kids(mr, M, "e")) {
                if (!first)
                    sb.append(',');
                first = false;
                sb.append(flatten(cell));
            }
        }
        return new MathObject(MathObject.Type.MATRIX, sb.toString());
    }

    private static MathObject nary(Element e) {
        Element pr = kid(e, M, "naryPr");
        Element chr = pr == null ? null : kid(pr, M, "chr");
        String c = chr == null ? INTEGRAL : attr(chr, M, "val");
        MathObject.Type type;
        switch (c) {
            case "\u2211", "\u03A3" -> type = MathObject.Type.SUM;
            case "\u220F", "\u03A0" -> type = MathObject.Type.PRODUCT;
            case INTEGRAL -> type = MathObject.Type.INTEGRAL;
            default -> {
                return null;
            }
        }
        return new MathObject(type, arg(e, "sub") + "|" + arg(e, "sup") + "|" + arg(e, "e"));
    }

    private static MathObject function(Element e) {
        Element fn = kid(e, M, "fName");
        Element low = fn == null ? null : kid(fn, M, "limLow");
        String body = arg(e, "e");
        if (low != null) {
            if (arg(low, "e").trim().equalsIgnoreCase("lim"))
                return new MathObject(MathObject.Type.LIMIT, arg(low, "lim") + "|" + body);
            return null;
        }
        if (flatten(fn).trim().equalsIgnoreCase("lim"))
            return new MathObject(MathObject.Type.LIMIT, "|" + body);
        return null;
    }

    static String flatten(Element e) {
        if (e == null || !M.equals(e.getNamespaceURI()))
            return "";
        String ln = e.getLocalName();
        switch (ln) {
            case "t":
                return e.getTextContent();
            case "f":
                return "(" + arg(e, "num") + "/" + arg(e, "den") + ")";
            case "sSup":
                return arg(e, "e") + "^(" + arg(e, "sup") + ")";
            case "sSub":
                return arg(e, "e") + "_(" + arg(e, "sub") + ")";
            case "sSubSup":
                return arg(e, "e") + "_(" + arg(e, "sub") + ")^(" + arg(e, "sup") + ")";
            case "rad": {
                String deg = arg(e, "deg");
                return (deg.isEmpty() ? "" : deg) + "\u221A(" + arg(e, "e") + ")";
            }
            case "nary": {
                Element pr = kid(e, M, "naryPr");
                Element chr = pr == null ? null : kid(pr, M, "chr");
                String c = chr == null ? INTEGRAL : attr(chr, M, "val");
                return c + "_(" + arg(e, "sub") + ")^(" + arg(e, "sup") + ")" + arg(e, "e");
            }
            case "d": {
                Element pr = kid(e, M, "dPr");
                Element b = pr == null ? null : kid(pr, M, "begChr");
                Element en = pr == null ? null : kid(pr, M, "endChr");
                Element sp = pr == null ? null : kid(pr, M, "sepChr");
                String open = b == null ? "(" : attr(b, M, "val");
                String close = en == null ? ")" : attr(en, M, "val");
                String sep = sp == null ? "|" : attr(sp, M, "val");
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
                StringBuilder sb = new StringBuilder("[");
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
                return sb.append(']').toString();
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