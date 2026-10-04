package com.example.model.io;

import static com.example.model.io.Ooxml.A;
import static com.example.model.io.Ooxml.M;
import static com.example.model.io.Ooxml.PIC;
import static com.example.model.io.Ooxml.R;
import static com.example.model.io.Ooxml.W;
import static com.example.model.io.Ooxml.WP;
import static com.example.model.io.Ooxml.WPS;
import static com.example.model.io.Ooxml.emu;
import static com.example.model.io.Ooxml.esc;
import static com.example.model.io.Ooxml.twips;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.example.model.TextStyle;
import com.example.model.io.PageContent.FloatingArrowContent;
import com.example.model.io.PageContent.FloatingImageContent;
import com.example.model.io.PageContent.FloatingShapeContent;
import com.example.model.io.PageContent.FloatingTableContent;
import com.example.model.io.PageContent.FloatingTextBoxContent;
import com.example.model.io.PageContent.ParagraphContent;
import com.example.model.io.PageContent.RunContent;
import com.example.model.language.maths.MathObject;

final class WordXmlWriter {
    static final String FOOTER_REL = "rId2";
    private static final long Z_BASE = 251658240L;
    private static final String CODE_STYLE = "TypOCode";
    private static final String FONT_CODE = "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/>";
    private static final String FONT_MATH = "<w:rFonts w:ascii=\"Cambria Math\" w:hAnsi=\"Cambria Math\"/>";

    private final Map<String, byte[]> media = new LinkedHashMap<>();
    private final Set<String> mediaExt = new TreeSet<>();
    private final StringBuilder rels = new StringBuilder();
    private final Map<String, String> linkRels = new HashMap<>();
    private final DocxSidecar sidecar = new DocxSidecar();
    private int relSeq = 100;
    private int docPrSeq = 1;
    private int mediaSeq = 0;

    Map<String, byte[]> media() {
        return media;
    }

    Set<String> mediaExtensions() {
        return mediaExt;
    }

    String relationshipsXml() {
        return rels.toString();
    }

    DocxSidecar sidecar() {
        return sidecar;
    }

    String documentXml(List<PageContent> pages) {
        StringBuilder d = new StringBuilder(64 * 1024);
        d.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        d.append("<w:document xmlns:w=\"").append(W).append("\" xmlns:r=\"").append(R)
                .append("\" xmlns:m=\"").append(M).append("\" xmlns:wp=\"").append(WP)
                .append("\" xmlns:a=\"").append(A).append("\" xmlns:pic=\"").append(PIC)
                .append("\" xmlns:wps=\"").append(WPS).append("\"><w:body>");
        List<PageContent> src = pages.isEmpty() ? List.of(new PageContent(false)) : pages;
        for (int i = 0; i < src.size(); i++)
            appendPage(d, src.get(i), i == src.size() - 1);
        d.append("</w:body></w:document>");
        return d.toString();
    }

    private void appendPage(StringBuilder d, PageContent page, boolean lastPage) {
        List<ParagraphContent> paras = page.paragraphs.isEmpty()
                ? List.of(new ParagraphContent(false))
                : page.paragraphs;
        String objects = drawings(page);
        String sect = sectPr(page);
        for (int i = 0; i < paras.size(); i++) {
            boolean last = i == paras.size() - 1;
            appendParagraph(d, paras.get(i), i == 0 ? objects : "", last && !lastPage ? sect : null);
        }
        if (lastPage)
            d.append(sect);
    }

    private static String sectPr(PageContent p) {
        long wTw = p.landscape ? 16840 : 11900;
        long hTw = p.landscape ? 11900 : 16840;
        StringBuilder s = new StringBuilder("<w:sectPr>");
        if (p.showPageNumbers)
            s.append("<w:footerReference w:type=\"default\" r:id=\"").append(FOOTER_REL).append("\"/>");
        s.append("<w:type w:val=\"nextPage\"/><w:pgSz w:w=\"").append(wTw).append("\" w:h=\"").append(hTw)
                .append('"');
        if (p.landscape)
            s.append(" w:orient=\"landscape\"");
        s.append("/><w:pgMar w:top=\"").append(cmTw(p.marginTopCm)).append("\" w:right=\"")
                .append(cmTw(p.marginRightCm)).append("\" w:bottom=\"").append(cmTw(p.marginBottomCm))
                .append("\" w:left=\"").append(cmTw(p.marginLeftCm))
                .append("\" w:header=\"720\" w:footer=\"300\" w:gutter=\"0\"/></w:sectPr>");
        return s.toString();
    }

    private static long cmTw(double cm) {
        return Math.round(cm / 2.54 * 1440.0);
    }

    private String paragraphsXml(List<ParagraphContent> paras) {
        if (paras.isEmpty())
            return "<w:p/>";
        StringBuilder sb = new StringBuilder();
        for (ParagraphContent p : paras)
            appendParagraph(sb, p, "", null);
        return sb.toString();
    }

    private void appendParagraph(StringBuilder d, ParagraphContent p, String prefixRuns, String sect) {
        StringBuilder ppr = new StringBuilder();
        if (p.codeBlock)
            ppr.append("<w:pStyle w:val=\"").append(CODE_STYLE).append("\"/>");
        String jc = switch (p.alignment) {
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "both";
            default -> null;
        };
        if (jc != null && !p.codeBlock)
            ppr.append("<w:jc w:val=\"").append(jc).append("\"/>");
        if (sect != null)
            ppr.append(sect);

        d.append("<w:p>");
        if (ppr.length() > 0)
            d.append("<w:pPr>").append(ppr).append("</w:pPr>");
        d.append(prefixRuns);
        for (RunContent run : p.runs)
            appendRun(d, run, p.codeBlock);
        d.append("</w:p>");
    }

    private void appendRun(StringBuilder d, RunContent run, boolean codeParagraph) {
        TextStyle s = run.style;
        if (run.isMath()) {
            MathObject m = run.math;
            if (m.getType() == null)
                return;
            if (m.getType() == MathObject.Type.IMAGE) {
                d.append(inlineImage(m.getRaw()));
                return;
            }
            StringBuilder rpr = new StringBuilder(FONT_MATH);
            appendProps(rpr, s, false);
            d.append("<m:oMath>").append(OmmlCodec.write(m, rpr.toString())).append("</m:oMath>");
            return;
        }

        String t = run.text;
        if (t == null || t.isEmpty())
            return;
        boolean link = s != null && s.link() != null && !codeParagraph;
        String rpr = rPr(s, codeParagraph, link);
        String r = "<w:r>" + (rpr.isEmpty() ? "" : "<w:rPr>" + rpr + "</w:rPr>") + textXml(t) + "</w:r>";
        if (link)
            d.append("<w:hyperlink r:id=\"").append(externalRel(s.link())).append("\">").append(r)
                    .append("</w:hyperlink>");
        else
            d.append(r);
    }

    private static String rPr(TextStyle s, boolean codeParagraph, boolean link) {
        boolean mono = codeParagraph || (s != null && s.codeBlock());
        StringBuilder x = new StringBuilder();
        if (link)
            x.append("<w:rStyle w:val=\"Hyperlink\"/>");
        if (mono)
            x.append(FONT_CODE);
        appendProps(x, s, mono);
        return x.toString();
    }

    private static void appendProps(StringBuilder x, TextStyle s, boolean mono) {
        if (s == null)
            return;
        if (s.bold())
            x.append("<w:b/>");
        if (s.italic())
            x.append("<w:i/>");
        if (s.strikethrough())
            x.append("<w:strike/>");
        if (s.textColor() != null && !mono)
            x.append("<w:color w:val=\"").append(ColorUtil.toHex(s.textColor())).append("\"/>");
        if (s.baselineShift() != null) {
            long pos = Math.round(-s.baselineShift() * 2);
            if (pos != 0)
                x.append("<w:position w:val=\"").append(pos).append("\"/>");
        }
        if (s.fontSize() != null) {
            int sz = s.fontSize();
            if (mono && sz <= 1)
                sz = 12;
            x.append("<w:sz w:val=\"").append(sz * 2).append("\"/><w:szCs w:val=\"").append(sz * 2).append("\"/>");
        }
        if (s.underline()) {
            x.append("<w:u w:val=\"").append(s.underlineDotted() ? "dotted" : "single").append('"');
            if (s.underlineColor() != null)
                x.append(" w:color=\"").append(ColorUtil.toHex(s.underlineColor())).append('"');
            x.append("/>");
        }
        if (s.highlight() != null && !mono)
            x.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(ColorUtil.toHex(s.highlight()))
                    .append("\"/>");
    }

    private static String textXml(String t) {
        StringBuilder out = new StringBuilder();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\t' || c == '\n') {
                flushText(out, cur);
                out.append(c == '\t' ? "<w:tab/>" : "<w:br/>");
            } else {
                cur.append(c);
            }
        }
        flushText(out, cur);
        return out.toString();
    }

    private static void flushText(StringBuilder out, StringBuilder cur) {
        if (cur.length() == 0)
            return;
        out.append("<w:t xml:space=\"preserve\">").append(esc(cur.toString())).append("</w:t>");
        cur.setLength(0);
    }

    private String externalRel(String url) {
        return linkRels.computeIfAbsent(url, u -> {
            String id = "rId" + (relSeq++);
            rels.append("<Relationship Id=\"").append(id).append("\" Type=\"").append(R)
                    .append("/hyperlink\" Target=\"").append(esc(u)).append("\" TargetMode=\"External\"/>");
            return id;
        });
    }

    private String addMedia(String format, byte[] bytes) {
        String ext = switch (format == null ? "" : format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "jpg";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "png";
        };
        String name = "image" + (++mediaSeq) + "." + ext;
        media.put("word/media/" + name, bytes);
        mediaExt.add(ext);
        String id = "rId" + (relSeq++);
        rels.append("<Relationship Id=\"").append(id).append("\" Type=\"").append(R)
                .append("/image\" Target=\"media/").append(name).append("\"/>");
        return id;
    }

    private String inlineImage(String raw) {
        if (raw == null)
            return "";
        int sep = raw.indexOf('|');
        if (sep < 0)
            return "";
        try {
            byte[] bytes = Base64.getDecoder().decode(raw.substring(sep + 1));
            String rid = addMedia(raw.substring(0, sep), bytes);
            double w = 100;
            double h = 100;
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi != null) {
                w = bi.getWidth();
                h = bi.getHeight();
            }
            if (w > 400) {
                h = h * 400 / w;
                w = 400;
            }
            int id = docPrSeq++;
            return "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\""
                    + emu(w) + "\" cy=\"" + emu(h)
                    + "\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:docPr id=\"" + id
                    + "\" name=\"typo-inl-" + id + "\"/><wp:cNvGraphicFramePr/>"
                    + pictureGraphic(rid, w, h, 0, 1.0) + "</wp:inline></w:drawing></w:r>";
        } catch (Exception e) {
            return "";
        }
    }

    private static String rotAttr(double deg) {
        long rot = Math.round(Ooxml.normAngle(deg) * 60000.0);
        return rot == 0 ? "" : " rot=\"" + rot + "\"";
    }

    private static String xfrm(double rotation, double w, double h) {
        return "<a:xfrm" + rotAttr(rotation) + "><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + emu(w) + "\" cy=\""
                + emu(h) + "\"/></a:xfrm>";
    }

    private static String pictureGraphic(String rid, double w, double h, double rotation, double opacity) {
        String alpha = opacity < 0.999 ? "<a:alphaModFix amt=\"" + Math.round(opacity * 100000) + "\"/>" : "";
        return "<a:graphic><a:graphicData uri=\"" + PIC + "\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" "
                + "name=\"image\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"" + rid + "\">"
                + alpha + "</a:blip><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr>"
                + xfrm(rotation, w, h) + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
                + "</a:graphicData></a:graphic>";
    }

    private static int lvl(Integer level) {
        return level == null ? 0 : Math.max(0, level);
    }

    private String drawings(PageContent page) {
        record Item(int level, String xml) {
        }
        List<Item> items = new ArrayList<>();
        for (FloatingImageContent i : page.images)
            items.add(new Item(lvl(i.level), floatingImage(i)));
        for (FloatingShapeContent s : page.shapes)
            items.add(new Item(lvl(s.level), floatingShape(s)));
        for (FloatingArrowContent a : page.arrows)
            items.add(new Item(lvl(a.level), floatingArrow(a)));
        for (FloatingTableContent t : page.tables)
            items.add(new Item(lvl(t.level), floatingTable(t)));
        for (FloatingTextBoxContent b : page.textBoxes)
            items.add(new Item(lvl(b.level), floatingTextBox(b)));
        items.sort(Comparator.comparingInt(Item::level));
        StringBuilder sb = new StringBuilder();
        for (Item it : items)
            sb.append(it.xml());
        return sb.toString();
    }

    private static String anchor(String name, int id, double x, double y, double w, double h, int level,
            String graphic) {
        return "<w:r><w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" "
                + "relativeHeight=\"" + (Z_BASE + level * 8L)
                + "\" behindDoc=\"0\" locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\">"
                + "<wp:simplePos x=\"0\" y=\"0\"/>"
                + "<wp:positionH relativeFrom=\"page\"><wp:posOffset>" + emu(x) + "</wp:posOffset></wp:positionH>"
                + "<wp:positionV relativeFrom=\"page\"><wp:posOffset>" + emu(y) + "</wp:posOffset></wp:positionV>"
                + "<wp:extent cx=\"" + emu(w) + "\" cy=\"" + emu(h) + "\"/>"
                + "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:wrapNone/>"
                + "<wp:docPr id=\"" + id + "\" name=\"" + esc(name) + "\"/><wp:cNvGraphicFramePr/>"
                + graphic + "</wp:anchor></w:drawing></w:r>";
    }

    private static String solid(String hex, double opacity) {
        String h = hex == null ? "000000" : hex;
        String alpha = opacity < 0.999 ? "<a:alpha val=\"" + Math.round(opacity * 100000) + "\"/>" : "";
        return "<a:solidFill><a:srgbClr val=\"" + h + "\">" + alpha + "</a:srgbClr></a:solidFill>";
    }

    private static String line(String hex, double opacity, double width, String ends) {
        if (width <= 0)
            return "<a:ln><a:noFill/></a:ln>";
        return "<a:ln w=\"" + emu(width) + "\">" + solid(hex, opacity) + (ends == null ? "" : ends) + "</a:ln>";
    }

    private String floatingImage(FloatingImageContent img) {
        try {
            byte[] bytes = Base64.getDecoder().decode(img.base64);
            String rid = addMedia(img.format, bytes);
            int id = docPrSeq++;
            return anchor("typo-img-" + id, id, img.x, img.y, img.width, img.height, lvl(img.level),
                    pictureGraphic(rid, img.width, img.height, img.rotation, img.opacity));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String floatingShape(FloatingShapeContent s) {
        String prst = switch (s.type) {
            case CIRCLE -> "ellipse";
            case SQUARE -> "rect";
            case TRIANGLE -> "triangle";
        };
        int id = docPrSeq++;
        String g = "<a:graphic><a:graphicData uri=\"" + WPS + "\"><wps:wsp><wps:cNvSpPr/><wps:spPr>"
                + xfrm(s.rotation, s.width, s.height) + "<a:prstGeom prst=\"" + prst + "\"><a:avLst/></a:prstGeom>"
                + solid(s.fillHex == null ? "FFFFFF" : s.fillHex, s.fillOpacity)
                + line(s.strokeHex, s.strokeOpacity, s.strokeWidth, null)
                + "</wps:spPr><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic>";
        return anchor("typo-shp-" + id, id, s.x, s.y, s.width, s.height, lvl(s.level), g);
    }

    private String floatingArrow(FloatingArrowContent a) {
        double minX = Math.min(a.startX, Math.min(a.controlX, a.endX));
        double maxX = Math.max(a.startX, Math.max(a.controlX, a.endX));
        double minY = Math.min(a.startY, Math.min(a.controlY, a.endY));
        double maxY = Math.max(a.startY, Math.max(a.controlY, a.endY));
        double w = Math.max(maxX - minX, 1);
        double h = Math.max(maxY - minY, 1);
        int id = docPrSeq++;
        String geom = "<a:custGeom><a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/><a:rect l=\"0\" t=\"0\" r=\"r\" b=\"b\"/>"
                + "<a:pathLst><a:path w=\"" + emu(w) + "\" h=\"" + emu(h) + "\" fill=\"none\"><a:moveTo>"
                + pt(a.startX - minX, a.startY - minY) + "</a:moveTo><a:quadBezTo>"
                + pt(a.controlX - minX, a.controlY - minY) + pt(a.endX - minX, a.endY - minY)
                + "</a:quadBezTo></a:path></a:pathLst></a:custGeom>";
        String g = "<a:graphic><a:graphicData uri=\"" + WPS + "\"><wps:wsp><wps:cNvSpPr/><wps:spPr>"
                + "<a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + emu(w) + "\" cy=\"" + emu(h) + "\"/></a:xfrm>"
                + geom + "<a:noFill/>"
                + line(a.strokeHex, a.strokeOpacity, Math.max(a.strokeWidth, 0.5),
                        "<a:tailEnd type=\"triangle\" w=\"med\" len=\"med\"/>")
                + "</wps:spPr><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic>";
        return anchor("typo-arw-" + id, id, minX, minY, w, h, lvl(a.level), g);
    }

    private static String pt(double x, double y) {
        return "<a:pt x=\"" + emu(x) + "\" y=\"" + emu(y) + "\"/>";
    }

    private String floatingTextBox(FloatingTextBoxContent b) {
        List<ParagraphContent> paras = CellCodec.parse(b.cells);
        double h = estimateHeight(paras, b.width);
        int id = docPrSeq++;
        String name = "typo-box-" + id;
        String fill = b.backgroundVisible
                ? solid(b.backgroundHex == null ? "FFFFFF" : b.backgroundHex, b.backgroundOpacity)
                : "<a:noFill/>";
        String ln = b.borderVisible
                ? line(b.borderHex == null ? "000000" : b.borderHex, 1.0, 1.0, null)
                : "<a:ln><a:noFill/></a:ln>";
        String g = "<a:graphic><a:graphicData uri=\"" + WPS + "\"><wps:wsp><wps:cNvSpPr txBox=\"1\"/><wps:spPr>"
                + xfrm(0, b.width, h) + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" + fill + ln
                + "</wps:spPr><wps:txbx><w:txbxContent>" + paragraphsXml(paras)
                + "</w:txbxContent></wps:txbx><wps:bodyPr rot=\"0\" vert=\"horz\" wrap=\"square\" lIns=\"50800\" "
                + "tIns=\"25400\" rIns=\"50800\" bIns=\"25400\" anchor=\"t\" anchorCtr=\"0\"><a:spAutoFit/>"
                + "</wps:bodyPr></wps:wsp></a:graphicData></a:graphic>";
        sidecar.put(name, DocxSidecar.signature(b), DocxSidecar.encode(b));
        return anchor(name, id, b.x, b.y, b.width, h, lvl(b.level), g);
    }

    private String floatingTable(FloatingTableContent t) {
        if (t.colWidths.length == 0 || t.rowHeights.length == 0)
            return "";
        double w = sum(t.colWidths);
        double h = sum(t.rowHeights);
        int id = docPrSeq++;
        String name = "typo-tbl-" + id;
        String g = "<a:graphic><a:graphicData uri=\"" + WPS + "\"><wps:wsp><wps:cNvSpPr txBox=\"1\"/><wps:spPr>"
                + xfrm(0, w, h + 2) + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/>"
                + "<a:ln><a:noFill/></a:ln></wps:spPr><wps:txbx><w:txbxContent>" + tableXml(t)
                + "<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/>"
                + "<w:rPr><w:sz w:val=\"2\"/></w:rPr></w:pPr></w:p></w:txbxContent></wps:txbx>"
                + "<wps:bodyPr rot=\"0\" vert=\"horz\" wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" "
                + "anchor=\"t\" anchorCtr=\"0\"><a:noAutofit/></wps:bodyPr></wps:wsp></a:graphicData></a:graphic>";
        sidecar.put(name, DocxSidecar.signature(t), DocxSidecar.encode(t));
        return anchor(name, id, t.x, t.y, w, h + 2, lvl(t.level), g);
    }

    private String tableXml(FloatingTableContent t) {
        int rows = t.rowHeights.length;
        int cols = t.colWidths.length;
        StringBuilder x = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"")
                .append(twips(sum(t.colWidths))).append("\" w:type=\"dxa\"/><w:tblBorders>");
        for (String b : new String[] { "top", "left", "bottom", "right", "insideH", "insideV" })
            x.append("<w:").append(b).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>");
        x.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar>")
                .append("<w:top w:w=\"40\" w:type=\"dxa\"/><w:left w:w=\"80\" w:type=\"dxa\"/>")
                .append("<w:bottom w:w=\"40\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/>")
                .append("</w:tblCellMar><w:tblLook w:val=\"0000\"/></w:tblPr><w:tblGrid>");
        for (double cw : t.colWidths)
            x.append("<w:gridCol w:w=\"").append(twips(cw)).append("\"/>");
        x.append("</w:tblGrid>");

        for (int r = 0; r < rows; r++) {
            x.append("<w:tr><w:trPr><w:trHeight w:val=\"").append(twips(t.rowHeights[r]))
                    .append("\" w:hRule=\"atLeast\"/></w:trPr>");
            int c = 0;
            while (c < cols) {
                int[] m = mergeAt(t.merges, r, c);
                if (m != null && m[1] != c)
                    m = null;
                int c2 = m == null ? c : Math.min(cols - 1, m[3]);
                double cw = 0;
                for (int k = c; k <= c2; k++)
                    cw += t.colWidths[k];
                boolean continued = m != null && r > m[0];
                x.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(twips(cw)).append("\" w:type=\"dxa\"/>");
                if (c2 > c)
                    x.append("<w:gridSpan w:val=\"").append(c2 - c + 1).append("\"/>");
                if (m != null && m[2] > m[0])
                    x.append(continued ? "<w:vMerge/>" : "<w:vMerge w:val=\"restart\"/>");
                x.append("</w:tcPr>");
                if (continued) {
                    x.append("<w:p/>");
                } else {
                    String enc = r < t.cells.length && c < t.cells[r].length ? t.cells[r][c] : "";
                    x.append(paragraphsXml(CellCodec.parse(enc)));
                }
                x.append("</w:tc>");
                c = c2 + 1;
            }
            x.append("</w:tr>");
        }
        return x.append("</w:tbl>").toString();
    }

    private static int[] mergeAt(int[][] merges, int r, int c) {
        for (int[] m : merges)
            if (m.length == 4 && r >= m[0] && r <= m[2] && c >= m[1] && c <= m[3])
                return m;
        return null;
    }

    private static double sum(double[] v) {
        double s = 0;
        for (double d : v)
            s += d;
        return s;
    }

    private static double estimateHeight(List<ParagraphContent> paras, double width) {
        if (paras.isEmpty())
            return 20;
        double usable = Math.max(20, width - 8);
        double h = 4;
        for (ParagraphContent p : paras) {
            double size = 12;
            int chars = 0;
            for (RunContent r : p.runs) {
                if (r.style != null && r.style.fontSize() != null)
                    size = Math.max(size, r.style.fontSize());
                chars += r.isMath() ? 4 : (r.text == null ? 0 : r.text.length());
            }
            int lines = Math.max(1, (int) Math.ceil(chars * size * 0.5 / usable));
            h += lines * size * 1.2;
        }
        return Math.max(20, h);
    }
}