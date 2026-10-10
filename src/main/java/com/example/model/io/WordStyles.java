package com.example.model.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Element;

import com.example.model.TextStyle;
import static com.example.model.io.Ooxml.W;
import static com.example.model.io.Ooxml.attr;
import static com.example.model.io.Ooxml.desc;
import static com.example.model.io.Ooxml.isTrue;
import static com.example.model.io.Ooxml.kid;
import static com.example.model.io.Ooxml.kids;
import static com.example.model.io.Ooxml.num;
import static com.example.model.io.Ooxml.wval;

import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

final class WordStyles {
    static final double DEF_SIZE = 12;

    private final WordPackage pkg;
    private final Map<String, Element> styles = new HashMap<>();
    private String defaultParaStyle;
    private Element defRpr;
    private Element defPpr;
    private final Map<String, String> numAbstract = new HashMap<>();
    private final Map<String, Map<Integer, String>> absFmt = new HashMap<>();
    private final Map<String, int[]> counters = new HashMap<>();

    WordStyles(WordPackage pkg) {
        this.pkg = pkg;
        loadStyles();
        loadNumbering();
    }

    private void loadStyles() {
        byte[] b = pkg.partByType("/styles", "word/styles.xml");
        if (b == null)
            return;
        try {
            Element root = Ooxml.parse(b).getDocumentElement();
            Element dd = kid(root, W, "docDefaults");
            if (dd != null) {
                Element rd = kid(dd, W, "rPrDefault");
                Element pd = kid(dd, W, "pPrDefault");
                defRpr = rd == null ? null : kid(rd, W, "rPr");
                defPpr = pd == null ? null : kid(pd, W, "pPr");
            }
            for (Element st : kids(root, W, "style")) {
                String id = attr(st, W, "styleId");
                styles.put(id, st);
                if ("paragraph".equals(attr(st, W, "type")) && isTrue(attr(st, W, "default")))
                    defaultParaStyle = id;
            }
        } catch (Exception ignored) {
        }
    }

    private void loadNumbering() {
        byte[] b = pkg.partByType("/numbering", "word/numbering.xml");
        if (b == null)
            return;
        try {
            Element root = Ooxml.parse(b).getDocumentElement();
            for (Element an : kids(root, W, "abstractNum")) {
                Map<Integer, String> lv = new HashMap<>();
                for (Element l : kids(an, W, "lvl"))
                    lv.put((int) num(attr(l, W, "ilvl"), 0), wval(kid(l, W, "numFmt")));
                absFmt.put(attr(an, W, "abstractNumId"), lv);
            }
            for (Element n : kids(root, W, "num"))
                numAbstract.put(attr(n, W, "numId"), wval(kid(n, W, "abstractNumId")));
        } catch (Exception ignored) {
        }
    }

    private String effStyle(String id) {
        return id != null ? id : defaultParaStyle;
    }

    private void addStyleChain(List<Element> outList, String id, boolean rpr) {
        int guard = 0;
        while (id != null && guard++ < 20) {
            Element st = styles.get(id);
            if (st == null)
                break;
            Element pr = kid(st, W, rpr ? "rPr" : "pPr");
            if (pr != null)
                outList.add(pr);
            String based = wval(kid(st, W, "basedOn"));
            id = based.isEmpty() ? null : based;
        }
    }

    List<Element> pprChain(Element pPr, String pStyle) {
        List<Element> l = new ArrayList<>();
        if (pPr != null)
            l.add(pPr);
        addStyleChain(l, effStyle(pStyle), false);
        if (defPpr != null)
            l.add(defPpr);
        return l;
    }

    static Element find(List<Element> chain, String local) {
        for (Element e : chain) {
            Element k = kid(e, W, local);
            if (k != null)
                return k;
        }
        return null;
    }

    static boolean isOn(Element toggle) {
        if (toggle == null)
            return false;
        String v = wval(toggle);
        return !(v.equals("0") || v.equals("false") || v.equals("off"));
    }

    static String styleId(Element pr, String local) {
        String v = wval(kid(pr, W, local));
        return v.isEmpty() ? null : v;
    }

    private static Color hexColor(String v) {
        if (v == null || !v.matches("[0-9A-Fa-f]{6}"))
            return null;
        return ColorUtil.fromHex(v.toUpperCase(Locale.ROOT));
    }

    private static Color highlightColor(String name) {
        return switch (name) {
            case "yellow" -> Color.YELLOW;
            case "green" -> Color.LIME;
            case "cyan" -> Color.CYAN;
            case "magenta" -> Color.MAGENTA;
            case "blue" -> Color.BLUE;
            case "red" -> Color.RED;
            case "darkBlue" -> Color.DARKBLUE;
            case "darkCyan" -> Color.DARKCYAN;
            case "darkGreen" -> Color.DARKGREEN;
            case "darkMagenta" -> Color.DARKMAGENTA;
            case "darkRed" -> Color.DARKRED;
            case "darkYellow" -> Color.web("#808000");
            case "darkGray" -> Color.DARKGRAY;
            case "lightGray" -> Color.LIGHTGRAY;
            case "black" -> Color.BLACK;
            case "white" -> Color.WHITE;
            default -> null;
        };
    }

    TextStyle runStyle(Element rPr, String rStyle, String pStyle, boolean link) {
        List<Element> chain = new ArrayList<>();
        if (rPr != null)
            chain.add(rPr);
        addStyleChain(chain, rStyle, true);
        addStyleChain(chain, effStyle(pStyle), true);
        if (defRpr != null)
            chain.add(defRpr);
        List<Element> direct = rPr == null ? List.of() : List.of(rPr);
        return styleFrom(chain, link ? direct : chain);
    }

    TextStyle mathStyle(Element container) {
        List<Element> chain = new ArrayList<>();
        Element rpr = desc(container, W, "rPr");
        if (rpr != null)
            chain.add(rpr);
        if (defRpr != null)
            chain.add(defRpr);
        return styleFrom(chain, chain).withFontFamily(null);
    }

    private TextStyle styleFrom(List<Element> chain, List<Element> colorChain) {
        TextStyle s = TextStyle.DEFAULT
                .withBold(isOn(find(chain, "b")))
                .withItalic(isOn(find(chain, "i")))
                .withStrikethrough(isOn(find(chain, "strike")) || isOn(find(chain, "dstrike")));

        Element u = find(colorChain, "u");
        if (u != null) {
            String v = wval(u);
            if (!v.isEmpty() && !v.equals("none"))
                s = s.withUnderline(true).withUnderlineDotted(v.startsWith("dot"))
                        .withUnderlineColor(hexColor(attr(u, W, "color")));
        }

        Element shd = find(chain, "shd");
        Color hl = shd == null ? null : hexColor(attr(shd, W, "fill"));
        if (hl == null)
            hl = highlightColor(wval(find(chain, "highlight")));
        if (hl != null)
            s = s.withHighlight(hl);

        Element col = find(colorChain, "color");
        Color c = col == null ? null : hexColor(wval(col));
        if (c != null)
            s = s.withTextColor(c);

        Element sz = find(chain, "sz");
        double pts = sz == null ? DEF_SIZE : num(wval(sz), DEF_SIZE * 2) / 2.0;
        s = s.withFontSize(Math.max(1, (int) Math.round(pts)));

        double shift = 0;
        Element pos = find(chain, "position");
        if (pos != null) {
            long p = (long) num(wval(pos), 0);
            if (p != 0)
                shift = -p / 2.0;
        }
        if (shift == 0) {
            String va = wval(find(chain, "vertAlign"));
            if (va.equals("superscript"))
                shift = -4.0;
            else if (va.equals("subscript"))
                shift = 4.0;
        }
        if (shift != 0)
            s = s.withBaselineShift(shift);

        String font = fontOf(chain);
        if (font != null)
            s = s.withFontFamily(font);

        return s;
    }

    static TextAlignment alignment(List<Element> pchain) {
        return switch (wval(find(pchain, "jc"))) {
            case "center" -> TextAlignment.CENTER;
            case "right", "end" -> TextAlignment.RIGHT;
            case "both", "distribute" -> TextAlignment.JUSTIFY;
            default -> TextAlignment.LEFT;
        };
    }

    String numberPrefix(List<Element> pchain) {
        Element np = find(pchain, "numPr");
        if (np == null)
            return "";
        String numId = wval(kid(np, W, "numId"));
        if (numId.isEmpty() || numId.equals("0"))
            return "";
        int lvl = Math.max(0, Math.min(9, (int) num(wval(kid(np, W, "ilvl")), 0)));
        String abs = numAbstract.get(numId);
        String fmt = abs == null ? "bullet" : absFmt.getOrDefault(abs, Map.of()).getOrDefault(lvl, "decimal");
        int[] cnt = counters.computeIfAbsent(numId, k -> new int[10]);
        cnt[lvl]++;
        for (int i = lvl + 1; i < 10; i++)
            cnt[i] = 0;
        String indent = "    ".repeat(lvl);
        return indent + switch (fmt) {
            case "none" -> "";
            case "bullet" -> "\u2022 ";
            case "lowerLetter" -> letters(cnt[lvl]).toLowerCase(Locale.ROOT) + ". ";
            case "upperLetter" -> letters(cnt[lvl]) + ". ";
            case "lowerRoman" -> roman(cnt[lvl]).toLowerCase(Locale.ROOT) + ". ";
            case "upperRoman" -> roman(cnt[lvl]) + ". ";
            default -> cnt[lvl] + ". ";
        };
    }

    private static String letters(int n) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }

    private static String roman(int n) {
        int[] v = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] s = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++)
            while (n >= v[i]) {
                sb.append(s[i]);
                n -= v[i];
            }
        return sb.toString();
    }

    private String fontOf(List<Element> chain) {
        for (Element pr : chain) {
            if (pr == defRpr)
                continue;
            Element f = kid(pr, W, "rFonts");
            if (f == null)
                continue;
            if (!attr(f, W, "asciiTheme").isEmpty() || !attr(f, W, "hAnsiTheme").isEmpty())
                return null;
            String name = attr(f, W, "ascii");
            if (name.isEmpty())
                name = attr(f, W, "hAnsi");
            if (!name.isEmpty())
                return name;
        }
        return null;
    }
}
