package com.example.model.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.example.model.TextStyle;
import static com.example.model.io.Ooxml.A;
import static com.example.model.io.Ooxml.MC;
import static com.example.model.io.Ooxml.R;
import static com.example.model.io.Ooxml.W;
import static com.example.model.io.Ooxml.WP;
import static com.example.model.io.Ooxml.WPS;
import static com.example.model.io.Ooxml.attr;
import static com.example.model.io.Ooxml.desc;
import static com.example.model.io.Ooxml.elems;
import static com.example.model.io.Ooxml.fromEmu;
import static com.example.model.io.Ooxml.isTrue;
import static com.example.model.io.Ooxml.kid;
import static com.example.model.io.Ooxml.kids;
import static com.example.model.io.Ooxml.lng;
import static com.example.model.io.Ooxml.normAngle;
import static com.example.model.io.Ooxml.num;
import static com.example.model.io.Ooxml.wval;
import com.example.model.io.PageContent.FloatingArrowContent;
import com.example.model.io.PageContent.FloatingImageContent;
import com.example.model.io.PageContent.FloatingShapeContent;
import com.example.model.io.PageContent.FloatingTableContent;
import com.example.model.io.PageContent.FloatingTextBoxContent;
import com.example.model.io.PageContent.ParagraphContent;
import com.example.model.io.PageContent.RunContent;
import com.example.model.language.maths.MathObject;
import com.example.model.language.shapes.ShapeType;

import javafx.scene.text.TextAlignment;

final class WordXmlReader {
    private static final String CODE_STYLE = "TypOCode";
    private static final double CONTENT_W = 555;
    private static final double LINE_K = 1.2;

    private interface Maker {
        Object make(double x, double y);
    }

    private record Pending(long key, String hFrom, String hAlign, double hOff, String vFrom, String vAlign,
            double vOff, double w, double h, double paraY, Maker mk) {
    }

    private static final class Draft {
        final List<ParagraphContent> paragraphs = new ArrayList<>();
        final List<Pending> objects = new ArrayList<>();
        double estY;

        boolean isEmpty() {
            return paragraphs.isEmpty() && objects.isEmpty();
        }
    }

    private static final class P {
        final String pStyle;
        final boolean box;
        final List<ParagraphContent> sink;
        final boolean code;
        final TextAlignment align;
        ParagraphContent para;
        boolean broke;

        P(String pStyle, boolean box, List<ParagraphContent> sink, TextAlignment align) {
            this.pStyle = pStyle;
            this.box = box;
            this.sink = sink;
            this.code = CODE_STYLE.equals(pStyle);
            this.align = code ? TextAlignment.LEFT : align;
            this.para = new ParagraphContent(code, this.align);
        }
    }

    private record TableDraft(double[] cols, double[] rows, int[][] merges, String[][] cells) {
    }

    private record Fill(String hex, double opacity, boolean none, boolean present) {
    }

    private final Map<String, byte[]> parts;
    private final String docPath;
    private WordPackage pkg;
    private WordStyles styles;
    private DocxSidecar sidecar = new DocxSidecar();
    private boolean pageNumbers;

    private final List<PageContent> out = new ArrayList<>();
    private List<Draft> sectionPages = new ArrayList<>();
    private Draft cur = new Draft();

    WordXmlReader(Map<String, byte[]> parts, String docPath) {
        this.parts = parts;
        this.docPath = docPath;
    }

    List<PageContent> read() throws Exception {
        pkg = new WordPackage(parts, docPath);
        styles = new WordStyles(pkg);
        sidecar = DocxSidecar.parse(parts);

        byte[] main = pkg.main();
        if (main == null)
            throw new IllegalArgumentException("word/document.xml introuvable");
        Document doc = Ooxml.parse(main);
        Element body = kid(doc.getDocumentElement(), W, "body");
        if (body != null)
            walkBody(body);
        flush(body == null ? null : kid(body, W, "sectPr"));

        if (out.isEmpty())
            out.add(new PageContent(false));
        for (PageContent pc : out)
            pc.showPageNumbers = pageNumbers;
        return out;
    }

    private void walkBody(Element container) {
        for (Element e : elems(container)) {
            if (!W.equals(e.getNamespaceURI()))
                continue;
            switch (e.getLocalName()) {
                case "p" -> readBodyParagraph(e);
                case "tbl" -> readBodyTable(e);
                case "sdt" -> {
                    Element c = kid(e, W, "sdtContent");
                    if (c != null)
                        walkBody(c);
                }
                default -> {
                }
            }
        }
    }

    private void newPage() {
        sectionPages.add(cur);
        cur = new Draft();
    }

    private void readBodyParagraph(Element p) {
        Element pPr = kid(p, W, "pPr");
        String pStyle = WordStyles.styleId(pPr, "pStyle");
        List<Element> chain = styles.pprChain(pPr, pStyle);

        if (WordStyles.isOn(WordStyles.find(chain, "pageBreakBefore")) && !cur.isEmpty())
            newPage();

        P ctx = new P(pStyle, false, null, WordStyles.alignment(chain));
        if (!ctx.code) {
            String prefix = styles.numberPrefix(chain);
            if (!prefix.isEmpty())
                ctx.para.runs.add(RunContent.text(prefix, TextStyle.DEFAULT));
        }
        walkInline(p, ctx, false);
        if (!(ctx.broke && ctx.para.runs.isEmpty()))
            finishParagraph(ctx);

        Element sect = pPr == null ? null : kid(pPr, W, "sectPr");
        if (sect != null) {
            if ("continuous".equals(wval(kid(sect, W, "type"))))
                noteFooter(sect);
            else
                flush(sect);
        }
    }

    private void finishParagraph(P ctx) {
        if (ctx.box) {
            ctx.sink.add(ctx.para);
        } else {
            cur.paragraphs.add(ctx.para);
            cur.estY += paragraphHeight(ctx.para);
        }
        ctx.para = new ParagraphContent(ctx.code, ctx.align);
    }

    private static double paragraphHeight(ParagraphContent para) {
        double size = WordStyles.DEF_SIZE;
        int chars = 0;
        for (RunContent r : para.runs) {
            if (r.style != null && r.style.fontSize() != null)
                size = Math.max(size, r.style.fontSize());
            chars += r.isMath() ? 4 : (r.text == null ? 0 : r.text.length());
        }
        int lines = Math.max(1, (int) Math.ceil(chars * size * 0.5 / CONTENT_W));
        return lines * size * LINE_K;
    }

    private void softBreak(P ctx) {
        finishParagraph(ctx);
    }

    private void pageBreak(P ctx) {
        if (ctx.box)
            return;
        if (!ctx.para.runs.isEmpty())
            finishParagraph(ctx);
        newPage();
        ctx.broke = true;
    }

    private void walkInline(Element parent, P ctx, boolean inLink) {
        for (Element e : elems(parent)) {
            String ns = e.getNamespaceURI();
            String ln = e.getLocalName();
            if (W.equals(ns)) {
                switch (ln) {
                    case "r" -> readRun(e, ctx, inLink);
                    case "hyperlink" -> walkInline(e, ctx, true);
                    case "ins", "moveTo", "smartTag", "customXml", "fldSimple", "dir", "bdo" ->
                        walkInline(e, ctx, inLink);
                    case "sdt" -> {
                        Element c = kid(e, W, "sdtContent");
                        if (c != null)
                            walkInline(c, ctx, inLink);
                    }
                    default -> {
                    }
                }
            } else if (Ooxml.M.equals(ns)) {
                if (ln.equals("oMath"))
                    readMath(e, ctx);
                else if (ln.equals("oMathPara"))
                    for (Element m : kids(e, Ooxml.M, "oMath"))
                        readMath(m, ctx);
            } else if (MC.equals(ns) && ln.equals("AlternateContent")) {
                Element choice = kid(e, MC, "Choice");
                if (choice != null)
                    walkInline(choice, ctx, inLink);
            }
        }
    }

    private void readMath(Element oMath, P ctx) {
        for (RunContent rc : OmmlCodec.read(oMath, styles::mathStyle))
            addRun(ctx, rc);
    }

    private void readRun(Element r, P ctx, boolean inLink) {
        Element rPr = kid(r, W, "rPr");
        String rStyle = WordStyles.styleId(rPr, "rStyle");
        boolean link = inLink || "Hyperlink".equals(rStyle);
        TextStyle style = styles.runStyle(rPr, rStyle, ctx.pStyle, link);
        StringBuilder sb = new StringBuilder();
        runChildren(r, sb, ctx, style);
        flushText(ctx, sb, style);
    }

    private void runChildren(Element holder, StringBuilder sb, P ctx, TextStyle style) {
        for (Element c : elems(holder)) {
            String ns = c.getNamespaceURI();
            String ln = c.getLocalName();
            if (MC.equals(ns) && ln.equals("AlternateContent")) {
                Element choice = kid(c, MC, "Choice");
                if (choice != null)
                    runChildren(choice, sb, ctx, style);
                continue;
            }
            if (!W.equals(ns))
                continue;
            switch (ln) {
                case "t" -> sb.append(c.getTextContent());
                case "tab" -> sb.append('\t');
                case "noBreakHyphen" -> sb.append('-');
                case "br" -> {
                    String type = attr(c, W, "type");
                    if (type.equals("page")) {
                        flushText(ctx, sb, style);
                        pageBreak(ctx);
                    } else if (!type.equals("column")) {
                        flushText(ctx, sb, style);
                        softBreak(ctx);
                    }
                }
                case "cr" -> {
                    flushText(ctx, sb, style);
                    softBreak(ctx);
                }
                case "drawing" -> {
                    flushText(ctx, sb, style);
                    readDrawing(c, ctx, style);
                }
                default -> {
                }
            }
        }
    }

    private void flushText(P ctx, StringBuilder sb, TextStyle style) {
        if (sb.length() == 0)
            return;
        TextStyle st = ctx.code ? TextStyle.DEFAULT.withFontSize(style.fontSize()) : style;
        addRun(ctx, RunContent.text(sb.toString(), st));
        sb.setLength(0);
    }

    private static void addRun(P ctx, RunContent rc) {
        List<RunContent> runs = ctx.para.runs;
        if (!rc.isMath() && !runs.isEmpty()) {
            RunContent last = runs.get(runs.size() - 1);
            if (!last.isMath() && Objects.equals(last.style, rc.style)) {
                runs.set(runs.size() - 1, RunContent.text(last.text + rc.text, rc.style));
                return;
            }
        }
        runs.add(rc);
    }

    private void readDrawing(Element d, P ctx, TextStyle style) {
        Element anchor = kid(d, WP, "anchor");
        Element inline = kid(d, WP, "inline");
        Element host = anchor != null ? anchor : inline;
        if (host == null)
            return;
        Element gd = desc(host, A, "graphicData");
        if (gd == null)
            return;
        Element ext = kid(host, WP, "extent");
        double w = ext == null ? 0 : fromEmu(lng(ext.getAttribute("cx")));
        double h = ext == null ? 0 : fromEmu(lng(ext.getAttribute("cy")));
        Element docPr = kid(host, WP, "docPr");
        String name = docPr == null ? "" : docPr.getAttribute("name");
        String uri = gd.getAttribute("uri");

        if (uri.endsWith("/picture"))
            readPicture(gd, anchor, ctx, style, w, h);
        else if (WPS.equals(uri) && anchor != null && !ctx.box)
            readShape(gd, anchor, w, h, name);
    }

    private void readPicture(Element gd, Element anchor, P ctx, TextStyle style, double w, double h) {
        Element blip = desc(gd, A, "blip");
        if (blip == null)
            return;
        String[] media = pkg.media(attr(blip, R, "embed"));
        if (media == null)
            return;
        final String fmt = media[0];
        final String b64 = media[1];
        if (anchor == null) {
            addRun(ctx, RunContent.math(new MathObject(MathObject.Type.IMAGE, fmt + "|" + b64), style));
            return;
        }
        if (ctx.box)
            return;
        Element xf = desc(gd, A, "xfrm");
        final double rot = xf == null ? 0 : normAngle(num(xf.getAttribute("rot"), 0) / 60000.0);
        Element alpha = desc(blip, A, "alphaModFix");
        final double op = alpha == null ? 1.0 : num(alpha.getAttribute("amt"), 100000) / 100000.0;
        pend(anchor, w, h, (x, y) -> {
            FloatingImageContent img = new FloatingImageContent(x, y, w, h, fmt, b64, rot);
            img.opacity = op;
            return img;
        });
    }

    private void readShape(Element gd, Element anchor, double w, double h, String name) {
        Element wsp = kid(gd, WPS, "wsp");
        if (wsp == null)
            return;
        Element spPr = kid(wsp, WPS, "spPr");
        if (spPr == null)
            return;
        Element xf = kid(spPr, A, "xfrm");
        final double rot = xf == null ? 0 : normAngle(num(xf.getAttribute("rot"), 0) / 60000.0);
        boolean flipH = xf != null && isTrue(xf.getAttribute("flipH"));
        boolean flipV = xf != null && isTrue(xf.getAttribute("flipV"));

        Element txbx = kid(wsp, WPS, "txbx");
        if (txbx != null) {
            readTextBoxOrTable(txbx, spPr, anchor, w, h, name);
            return;
        }

        boolean styled = kid(wsp, WPS, "style") != null;
        Element ln = kid(spPr, A, "ln");
        Element cust = kid(spPr, A, "custGeom");
        Element prst = kid(spPr, A, "prstGeom");
        String geom = prst == null ? "" : prst.getAttribute("prst");

        double[] rel = null;
        if (cust != null)
            rel = customPath(cust, w, h);
        else if (geom.equals("line") || geom.contains("Connector"))
            rel = new double[] { flipH ? w : 0, flipV ? h : 0, w / 2, h / 2, flipH ? 0 : w, flipV ? 0 : h };

        Fill lf = ln == null ? null : fillOf(ln);
        final double strokeW = ln == null ? (styled ? 1.0 : 0.0)
                : (ln.hasAttribute("w") ? fromEmu(lng(ln.getAttribute("w"))) : 0.75);
        final String strokeHex = lf != null && lf.hex() != null ? lf.hex() : (styled ? "2F528F" : "000000");
        final double strokeOp = lf != null && !lf.none() ? lf.opacity() : 1.0;

        if (rel != null) {
            Element tail = ln == null ? null : kid(ln, A, "tailEnd");
            Element head = ln == null ? null : kid(ln, A, "headEnd");
            boolean hasTail = tail != null && !"none".equals(tail.getAttribute("type"));
            boolean hasHead = head != null && !"none".equals(head.getAttribute("type"));
            final double[] r = rel.clone();
            if (hasHead && !hasTail) {
                double sx = r[0], sy = r[1];
                r[0] = r[4];
                r[1] = r[5];
                r[4] = sx;
                r[5] = sy;
            }
            final double sw = ln == null ? 0.75 : (strokeW > 0 ? strokeW : 0.75);
            pend(anchor, w, h, (x, y) -> new FloatingArrowContent(x + r[0], y + r[1], x + r[4], y + r[5],
                    x + r[2], y + r[3], strokeHex, strokeOp, Math.max(sw, 0.5)));
            return;
        }

        ShapeType type = switch (geom) {
            case "ellipse" -> ShapeType.CIRCLE;
            case "triangle", "rtTriangle" -> ShapeType.TRIANGLE;
            default -> ShapeType.SQUARE;
        };
        Fill f = fillOf(spPr);
        final String fillHex;
        final double fillOp;
        if (f.none()) {
            fillHex = "FFFFFF";
            fillOp = 0;
        } else if (f.hex() != null) {
            fillHex = f.hex();
            fillOp = f.opacity();
        } else if (f.present() || styled) {
            fillHex = "4472C4";
            fillOp = f.opacity();
        } else {
            fillHex = "FFFFFF";
            fillOp = 0;
        }
        final ShapeType st = type;
        pend(anchor, w, h, (x, y) -> new FloatingShapeContent(st, x, y, w, h, fillHex, fillOp, strokeHex, strokeOp,
                strokeW, rot));
    }

    private static double[] customPath(Element cust, double w, double h) {
        Element lst = kid(cust, A, "pathLst");
        Element path = lst == null ? null : kid(lst, A, "path");
        if (path == null)
            return null;
        double pw = num(path.getAttribute("w"), 0);
        double ph = num(path.getAttribute("h"), 0);
        double kx = pw > 0 ? w / pw : 1.0 / Ooxml.EMU_PER_PT;
        double ky = ph > 0 ? h / ph : 1.0 / Ooxml.EMU_PER_PT;

        double[] start = null;
        for (Element seg : elems(path)) {
            List<Element> pts = kids(seg, A, "pt");
            String ln = seg.getLocalName();
            if (ln.equals("moveTo") && !pts.isEmpty() && start == null) {
                start = pt(pts.get(0), kx, ky);
            } else if (start != null) {
                if (ln.equals("quadBezTo") && pts.size() >= 2) {
                    double[] c = pt(pts.get(0), kx, ky);
                    double[] e = pt(pts.get(1), kx, ky);
                    return new double[] { start[0], start[1], c[0], c[1], e[0], e[1] };
                }
                if (ln.equals("cubicBezTo") && pts.size() >= 3) {
                    double[] c1 = pt(pts.get(0), kx, ky);
                    double[] c2 = pt(pts.get(1), kx, ky);
                    double[] e = pt(pts.get(2), kx, ky);
                    return new double[] { start[0], start[1], (c1[0] + c2[0]) / 2, (c1[1] + c2[1]) / 2, e[0], e[1] };
                }
                if (ln.equals("lnTo") && !pts.isEmpty()) {
                    double[] e = pt(pts.get(0), kx, ky);
                    return new double[] { start[0], start[1], (start[0] + e[0]) / 2, (start[1] + e[1]) / 2, e[0],
                            e[1] };
                }
            }
        }
        return null;
    }

    private static double[] pt(Element e, double kx, double ky) {
        return new double[] { num(e.getAttribute("x"), 0) * kx, num(e.getAttribute("y"), 0) * ky };
    }

    private static Fill fillOf(Element parent) {
        if (kid(parent, A, "noFill") != null)
            return new Fill(null, 0, true, true);
        Element sf = kid(parent, A, "solidFill");
        if (sf == null)
            return new Fill(null, 1, false, false);
        List<Element> kids = elems(sf);
        Element clr = kid(sf, A, "srgbClr");
        String hex = null;
        if (clr != null) {
            String v = clr.getAttribute("val").toUpperCase(Locale.ROOT);
            if (v.matches("[0-9A-F]{6}"))
                hex = v;
        }
        Element base = clr != null ? clr : (kids.isEmpty() ? null : kids.get(0));
        double op = 1;
        if (base != null) {
            Element al = kid(base, A, "alpha");
            if (al != null)
                op = num(al.getAttribute("val"), 100000) / 100000.0;
        }
        return new Fill(hex, op, false, true);
    }

    private void pend(Element anchor, double w, double h, Maker mk) {
        Element ph = kid(anchor, WP, "positionH");
        Element pv = kid(anchor, WP, "positionV");
        long rh = lng(anchor.getAttribute("relativeHeight"));
        boolean behind = isTrue(anchor.getAttribute("behindDoc"));
        cur.objects.add(new Pending(rh - (behind ? 5_000_000_000L : 0),
                ph == null ? "page" : ph.getAttribute("relativeFrom"), textOf(ph, "align"),
                fromEmu(lng(textOf(ph, "posOffset"))),
                pv == null ? "page" : pv.getAttribute("relativeFrom"), textOf(pv, "align"),
                fromEmu(lng(textOf(pv, "posOffset"))),
                w, h, cur.estY, mk));
    }

    private static String textOf(Element parent, String local) {
        Element e = kid(parent, WP, local);
        return e == null ? "" : e.getTextContent().trim();
    }

    private void readTextBoxOrTable(Element txbx, Element spPr, Element anchor, double w, double h, String name) {
        Element tc = kid(txbx, W, "txbxContent");
        if (tc == null)
            return;
        Element tbl = kid(tc, W, "tbl");
        if (tbl != null) {
            TableDraft d = parseTable(tbl);
            if (d != null)
                pend(anchor, w, h, (x, y) -> makeTable(name, d, x, y));
            return;
        }

        List<ParagraphContent> paras = readBlockParagraphs(tc);
        final String cells = CellCodec.serialize(paras);
        final StringBuilder plain = new StringBuilder();
        for (ParagraphContent p : paras) {
            if (plain.length() > 0)
                plain.append('\n');
            for (RunContent r : p.runs)
                plain.append(r.isMath() ? "\uFFFC" : r.text);
        }

        Element ln = kid(spPr, A, "ln");
        Fill lf = ln == null ? null : fillOf(ln);
        final boolean borderVisible = lf != null && lf.present() && !lf.none();
        final String borderHex = lf != null && lf.hex() != null ? lf.hex() : "000000";
        Fill f = fillOf(spPr);
        final boolean bgVisible = f.present() && !f.none();
        final String bgHex = f.hex() != null ? f.hex() : "FFFFFF";
        final double bgOp = f.opacity();

        pend(anchor, w, h, (x, y) -> {
            FloatingTextBoxContent derived = new FloatingTextBoxContent(x, y, w, borderVisible, borderHex,
                    bgVisible, bgHex, bgOp, cells);
            derived.plainText = plain.toString();
            DocxSidecar.Entry e = sidecar.get(name);
            if (e != null && DocxSidecar.sameSignature(e.signature(), DocxSidecar.signature(derived))) {
                FloatingTextBoxContent exact = DocxSidecar.decodeTextBox(e.value());
                if (exact != null) {
                    exact.plainText = plain.toString();
                    return exact;
                }
            }
            return derived;
        });
    }

    private FloatingTableContent makeTable(String name, TableDraft d, double x, double y) {
        double[][] zx = new double[d.rows().length][d.cols().length];
        double[][] zy = new double[d.rows().length][d.cols().length];
        FloatingTableContent derived = new FloatingTableContent(x, y, d.cols(), d.rows(), zx, zy, d.merges(),
                d.cells());
        if (name != null && !name.isEmpty()) {
            DocxSidecar.Entry e = sidecar.get(name);
            if (e != null && DocxSidecar.sameSignature(e.signature(), DocxSidecar.signature(derived))) {
                FloatingTableContent exact = DocxSidecar.decodeTable(e.value());
                if (exact != null)
                    return exact;
            }
        }
        return derived;
    }

    private List<ParagraphContent> readBlockParagraphs(Element container) {
        List<ParagraphContent> sink = new ArrayList<>();
        for (Element e : elems(container)) {
            if (!W.equals(e.getNamespaceURI()) || !e.getLocalName().equals("p"))
                continue;
            Element pPr = kid(e, W, "pPr");
            String pStyle = WordStyles.styleId(pPr, "pStyle");
            List<Element> chain = styles.pprChain(pPr, pStyle);
            P ctx = new P(pStyle, true, sink, WordStyles.alignment(chain));
            if (!ctx.code) {
                String prefix = styles.numberPrefix(chain);
                if (!prefix.isEmpty())
                    ctx.para.runs.add(RunContent.text(prefix, TextStyle.DEFAULT));
            }
            walkInline(e, ctx, false);
            finishParagraph(ctx);
        }
        return sink;
    }

    private TableDraft parseTable(Element tbl) {
        record Tc(int col, int span, String vm, Element el) {
        }
        List<Element> trs = kids(tbl, W, "tr");
        int rows = trs.size();
        List<Double> grid = new ArrayList<>();
        Element tg = kid(tbl, W, "tblGrid");
        if (tg != null)
            for (Element gc : kids(tg, W, "gridCol"))
                grid.add(num(attr(gc, W, "w"), 2000) / 20.0);

        int cols = grid.size();
        List<List<Tc>> rowCells = new ArrayList<>();
        for (Element tr : trs) {
            List<Tc> row = new ArrayList<>();
            int c = 0;
            for (Element tc : kids(tr, W, "tc")) {
                Element pr = kid(tc, W, "tcPr");
                int span = Math.max(1, (int) num(wval(kid(pr, W, "gridSpan")), 1));
                String vm = null;
                Element v = kid(pr, W, "vMerge");
                if (v != null)
                    vm = wval(v).isEmpty() ? "continue" : wval(v);
                row.add(new Tc(c, span, vm, tc));
                c += span;
            }
            cols = Math.max(cols, c);
            rowCells.add(row);
        }
        if (rows == 0 || cols == 0)
            return null;

        double[] colW = new double[cols];
        for (int i = 0; i < cols; i++)
            colW[i] = i < grid.size() ? Math.max(30, grid.get(i)) : 100;
        double[] rowH = new double[rows];
        for (int r = 0; r < rows; r++) {
            Element trPr = kid(trs.get(r), W, "trPr");
            Element th = kid(trPr, W, "trHeight");
            rowH[r] = th == null ? 24 : Math.max(20, num(wval(th), 480) / 20.0);
        }

        String empty = CellCodec.serialize(List.of());
        String[][] cells = new String[rows][cols];
        for (String[] row : cells)
            java.util.Arrays.fill(row, empty);

        List<int[]> merges = new ArrayList<>();
        Map<Integer, int[]> open = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            for (Tc t : rowCells.get(r)) {
                if (t.col() >= cols)
                    continue;
                int c1 = Math.min(cols - 1, t.col() + t.span() - 1);
                if ("continue".equals(t.vm())) {
                    int[] m = open.get(t.col());
                    if (m != null)
                        m[2] = r;
                    continue;
                }
                cells[r][t.col()] = CellCodec.serialize(readBlockParagraphs(t.el()));
                if (t.span() > 1 || "restart".equals(t.vm())) {
                    int[] m = { r, t.col(), r, c1 };
                    merges.add(m);
                    if ("restart".equals(t.vm()))
                        open.put(t.col(), m);
                    else
                        open.remove(t.col());
                } else {
                    open.remove(t.col());
                }
            }
        }
        merges.removeIf(m -> m[0] == m[2] && m[1] == m[3]);
        return new TableDraft(colW, rowH, merges.toArray(new int[0][]), cells);
    }

    private void readBodyTable(Element tbl) {
        TableDraft d = parseTable(tbl);
        if (d == null)
            return;
        double tw = 0;
        double th = 0;
        for (double c : d.cols())
            tw += c;
        for (double r : d.rows())
            th += r;
        Element tblPr = kid(tbl, W, "tblPr");
        Element fp = kid(tblPr, W, "tblpPr");
        Maker mk = (x, y) -> makeTable("", d, x, y);

        if (fp != null) {
            String ha = attr(fp, W, "horzAnchor");
            String va = attr(fp, W, "vertAnchor");
            String hFrom = ha.equals("page") ? "page" : (ha.equals("margin") ? "margin" : "column");
            String vFrom = va.equals("page") ? "page" : (va.equals("margin") ? "margin" : "paragraph");
            cur.objects.add(new Pending(0, hFrom, specAlign(attr(fp, W, "tblpXSpec")),
                    num(attr(fp, W, "tblpX"), 0) / 20.0, vFrom, specAlign(attr(fp, W, "tblpYSpec")),
                    num(attr(fp, W, "tblpY"), 0) / 20.0, tw, th, cur.estY, mk));
            return;
        }

        cur.objects.add(new Pending(0, "margin", "", 0, "margin", "", cur.estY, tw, th, cur.estY, mk));
        double blank = WordStyles.DEF_SIZE * LINE_K;
        int k = (int) Math.ceil(th / blank);
        for (int i = 0; i < k; i++) {
            cur.paragraphs.add(new ParagraphContent(false));
            cur.estY += blank;
        }
    }

    private static String specAlign(String spec) {
        return switch (spec) {
            case "center" -> "center";
            case "right", "outside", "bottom" -> spec.equals("bottom") ? "bottom" : "right";
            case "left", "inside", "top" -> spec.equals("top") ? "top" : "left";
            default -> "";
        };
    }

    private void noteFooter(Element sect) {
        for (Element fr : kids(sect, W, "footerReference")) {
            if (!"default".equals(attr(fr, W, "type")))
                continue;
            byte[] b = pkg.related(attr(fr, R, "id"));
            if (b == null)
                continue;
            try {
                Element root = Ooxml.parse(b).getDocumentElement();
                var instr = root.getElementsByTagNameNS(W, "instrText");
                for (int i = 0; i < instr.getLength(); i++)
                    if (instr.item(i).getTextContent().trim().toUpperCase(Locale.ROOT).startsWith("PAGE"))
                        pageNumbers = true;
                var simple = root.getElementsByTagNameNS(W, "fldSimple");
                for (int i = 0; i < simple.getLength(); i++)
                    if (((Element) simple.item(i)).getAttributeNS(W, "instr").trim().toUpperCase(Locale.ROOT)
                            .startsWith("PAGE"))
                        pageNumbers = true;
            } catch (Exception ignored) {
            }
        }
    }

    private void flush(Element sect) {
        double pw = 595;
        double ph = 842;
        double[] m = { 72, 72, 72, 72 };
        boolean hasMargins = false;
        boolean landscape = false;
        if (sect != null) {
            noteFooter(sect);
            Element sz = kid(sect, W, "pgSz");
            if (sz != null) {
                double w = num(attr(sz, W, "w"), 0) / 20.0;
                double h = num(attr(sz, W, "h"), 0) / 20.0;
                if (w > 0 && h > 0) {
                    pw = w;
                    ph = h;
                }
                landscape = "landscape".equals(attr(sz, W, "orient")) || pw > ph;
                if (landscape && pw < ph) {
                    double t = pw;
                    pw = ph;
                    ph = t;
                }
            }
            Element pm = kid(sect, W, "pgMar");
            if (pm != null) {
                m[0] = Math.abs(num(attr(pm, W, "top"), 1440)) / 20.0;
                m[1] = Math.abs(num(attr(pm, W, "right"), 1440)) / 20.0;
                m[2] = Math.abs(num(attr(pm, W, "bottom"), 1440)) / 20.0;
                m[3] = Math.abs(num(attr(pm, W, "left"), 1440)) / 20.0;
                hasMargins = true;
            }
        }

        List<Draft> drafts = new ArrayList<>(sectionPages);
        drafts.add(cur);
        sectionPages = new ArrayList<>();
        cur = new Draft();

        for (Draft dr : drafts) {
            if (dr.isEmpty())
                continue;
            PageContent pc = new PageContent(landscape);
            if (hasMargins) {
                pc.marginTopCm = m[0] * 2.54 / 72.0;
                pc.marginRightCm = m[1] * 2.54 / 72.0;
                pc.marginBottomCm = m[2] * 2.54 / 72.0;
                pc.marginLeftCm = m[3] * 2.54 / 72.0;
            }
            pc.paragraphs.addAll(dr.paragraphs);

            List<Pending> sorted = new ArrayList<>(dr.objects);
            sorted.sort(Comparator.comparingLong(Pending::key));
            int level = 0;
            for (Pending p : sorted) {
                double x = resolve(p.hFrom(), p.hAlign(), p.hOff(), p.w(), pw, m[3], m[1], 0);
                double y = resolve(p.vFrom(), p.vAlign(), p.vOff(), p.h(), ph, m[0], m[2], p.paraY());
                Object o = p.mk().make(x, y);
                if (o instanceof FloatingImageContent i) {
                    i.level = level;
                    pc.images.add(i);
                } else if (o instanceof FloatingShapeContent s) {
                    s.level = level;
                    pc.shapes.add(s);
                } else if (o instanceof FloatingArrowContent a) {
                    a.level = level;
                    pc.arrows.add(a);
                } else if (o instanceof FloatingTableContent t) {
                    t.level = level;
                    pc.tables.add(t);
                } else if (o instanceof FloatingTextBoxContent b) {
                    b.level = level;
                    pc.textBoxes.add(b);
                } else {
                    continue;
                }
                level++;
            }
            out.add(pc);
        }
    }

    private static double resolve(String from, String align, double off, double size, double pageSize,
            double marginStart, double marginEnd, double paraY) {
        double a0;
        double a1;
        switch (from) {
            case "margin", "column", "leftMargin", "topMargin", "insideMargin", "character" -> {
                a0 = marginStart;
                a1 = pageSize - marginEnd;
            }
            case "paragraph", "line" -> {
                a0 = marginStart + paraY;
                a1 = a0;
            }
            case "rightMargin", "bottomMargin", "outsideMargin" -> {
                a0 = pageSize - marginEnd;
                a1 = pageSize;
            }
            default -> {
                a0 = 0;
                a1 = pageSize;
            }
        }
        return switch (align) {
            case "center" -> (a0 + a1) / 2 - size / 2;
            case "right", "bottom", "outside" -> a1 - size;
            case "left", "top", "inside" -> a0;
            default -> a0 + off;
        };
    }
}