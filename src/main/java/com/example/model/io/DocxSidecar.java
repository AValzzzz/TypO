package com.example.model.io;

import static com.example.model.io.Ooxml.esc;
import static com.example.model.io.Ooxml.kid;
import static com.example.model.io.Ooxml.kids;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.example.model.io.PageContent.FloatingTableContent;
import com.example.model.io.PageContent.FloatingTextBoxContent;

final class DocxSidecar {
    static final String NS = "urn:typo:docx-sidecar:1";
    private static final double TOL = 0.06;

    record Entry(String signature, String value) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    void put(String id, String signature, String value) {
        entries.put(id, new Entry(signature, value));
    }

    Entry get(String id) {
        return entries.get(id);
    }

    byte[] toXml() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<typo:data xmlns:typo=\"").append(NS).append("\">");
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            sb.append("<typo:obj id=\"").append(esc(e.getKey())).append("\"><typo:sig>")
                    .append(esc(e.getValue().signature())).append("</typo:sig><typo:val>")
                    .append(esc(e.getValue().value())).append("</typo:val></typo:obj>");
        }
        sb.append("</typo:data>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static DocxSidecar parse(Map<String, byte[]> parts) {
        DocxSidecar sc = new DocxSidecar();
        for (Map.Entry<String, byte[]> p : parts.entrySet()) {
            String n = p.getKey();
            if (!n.startsWith("customXml/") || !n.endsWith(".xml") || n.contains("_rels/"))
                continue;
            try {
                Document d = Ooxml.parse(p.getValue());
                Element root = d.getDocumentElement();
                if (!NS.equals(root.getNamespaceURI()))
                    continue;
                for (Element o : kids(root, NS, "obj")) {
                    Element s = kid(o, NS, "sig");
                    Element v = kid(o, NS, "val");
                    if (s != null && v != null)
                        sc.entries.put(o.getAttribute("id"), new Entry(s.getTextContent(), v.getTextContent()));
                }
            } catch (Exception ignored) {
            }
        }
        return sc;
    }

    static String signature(FloatingTableContent t) {
        List<String> l = new ArrayList<>();
        l.add("tbl");
        l.add(String.valueOf(t.x));
        l.add(String.valueOf(t.y));
        l.add(String.valueOf(t.colWidths.length));
        for (double c : t.colWidths)
            l.add(String.valueOf(c));
        l.add(String.valueOf(t.rowHeights.length));
        for (double r : t.rowHeights)
            l.add(String.valueOf(r));
        int[][] merges = Arrays.stream(t.merges).map(int[]::clone)
                .sorted(Comparator.<int[]>comparingInt(m -> m[0]).thenComparingInt(m -> m[1]))
                .toArray(int[][]::new);
        l.add(String.valueOf(merges.length));
        for (int[] m : merges)
            for (int v : m)
                l.add(String.valueOf(v));
        for (int r = 0; r < t.rowHeights.length; r++)
            for (int c = 0; c < t.colWidths.length; c++) {
                String cell = r < t.cells.length && c < t.cells[r].length ? t.cells[r][c] : "";
                l.add(CellCodec.normalize(cell));
            }
        return String.join("\n", l);
    }

    static String signature(FloatingTextBoxContent t) {
        List<String> l = new ArrayList<>();
        l.add("box");
        l.add(String.valueOf(t.x));
        l.add(String.valueOf(t.y));
        l.add(String.valueOf(t.width));
        l.add(t.borderVisible ? "1" : "0");
        l.add(t.borderVisible ? "#" + t.borderHex : "#-");
        l.add(t.backgroundVisible ? "1" : "0");
        l.add(t.backgroundVisible ? "#" + t.backgroundHex : "#-");
        l.add(String.valueOf(t.backgroundVisible ? t.backgroundOpacity : 0.0));
        l.add(CellCodec.normalize(t.cells));
        return String.join("\n", l);
    }

    static boolean sameSignature(String a, String b) {
        if (a == null || b == null)
            return false;
        String[] x = a.split("\n", -1);
        String[] y = b.split("\n", -1);
        if (x.length != y.length)
            return false;
        for (int i = 0; i < x.length; i++) {
            if (x[i].equals(y[i]))
                continue;
            Double dx = parse(x[i]);
            Double dy = parse(y[i]);
            if (dx == null || dy == null || !(Math.abs(dx - dy) <= TOL))
                return false;
        }
        return true;
    }

    private static Double parse(String s) {
        if (s.isEmpty() || s.startsWith("#"))
            return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String encode(FloatingTableContent t) {
        List<String> l = new ArrayList<>();
        l.add(String.valueOf(t.x));
        l.add(String.valueOf(t.y));
        l.add(join(t.colWidths));
        l.add(join(t.rowHeights));
        l.add(joinMatrix(t.offX));
        l.add(joinMatrix(t.offY));
        l.add(joinMerges(t.merges));
        for (String[] row : t.cells)
            for (String c : row)
                l.add(b64(c));
        return String.join("\n", l);
    }

    static FloatingTableContent decodeTable(String s) {
        try {
            String[] l = s.split("\n", -1);
            double[] cols = doubles(l[2]);
            double[] rows = doubles(l[3]);
            String[][] cells = new String[rows.length][cols.length];
            int k = 7;
            for (int r = 0; r < rows.length; r++)
                for (int c = 0; c < cols.length; c++)
                    cells[r][c] = k < l.length ? unb64(l[k++]) : "";
            return new FloatingTableContent(Double.parseDouble(l[0]), Double.parseDouble(l[1]), cols, rows,
                    matrix(l[4], rows.length, cols.length), matrix(l[5], rows.length, cols.length),
                    merges(l[6]), cells);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String encode(FloatingTextBoxContent t) {
        return String.join("\n",
                String.valueOf(t.x), String.valueOf(t.y), String.valueOf(t.width),
                t.borderVisible ? "1" : "0", t.borderHex == null ? "-" : t.borderHex,
                t.backgroundVisible ? "1" : "0", t.backgroundHex == null ? "-" : t.backgroundHex,
                String.valueOf(t.backgroundOpacity), b64(t.cells));
    }

    static FloatingTextBoxContent decodeTextBox(String s) {
        try {
            String[] l = s.split("\n", -1);
            return new FloatingTextBoxContent(Double.parseDouble(l[0]), Double.parseDouble(l[1]),
                    Double.parseDouble(l[2]), l[3].equals("1"), l[4].equals("-") ? null : l[4],
                    l[5].equals("1"), l[6].equals("-") ? null : l[6], Double.parseDouble(l[7]), unb64(l[8]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String join(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    private static String joinMatrix(double[][] m) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.length; r++) {
            if (r > 0)
                sb.append(';');
            sb.append(join(m[r]));
        }
        return sb.toString();
    }

    private static String joinMerges(int[][] merges) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < merges.length; i++) {
            if (i > 0)
                sb.append(';');
            sb.append(merges[i][0]).append(',').append(merges[i][1]).append(',')
                    .append(merges[i][2]).append(',').append(merges[i][3]);
        }
        return sb.toString();
    }

    private static double[] doubles(String s) {
        if (s.isEmpty())
            return new double[0];
        String[] p = s.split(",");
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++)
            out[i] = Double.parseDouble(p[i]);
        return out;
    }

    private static double[][] matrix(String s, int rows, int cols) {
        double[][] m = new double[rows][cols];
        String[] rs = s.split(";", -1);
        for (int r = 0; r < rs.length && r < rows; r++) {
            String[] vs = rs[r].split(",", -1);
            for (int c = 0; c < vs.length && c < cols; c++)
                if (!vs[c].isEmpty())
                    m[r][c] = Double.parseDouble(vs[c]);
        }
        return m;
    }

    private static int[][] merges(String s) {
        if (s.isEmpty())
            return new int[0][];
        String[] ms = s.split(";");
        int[][] out = new int[ms.length][4];
        for (int i = 0; i < ms.length; i++) {
            String[] v = ms[i].split(",");
            for (int k = 0; k < 4; k++)
                out[i][k] = Integer.parseInt(v[k]);
        }
        return out;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}