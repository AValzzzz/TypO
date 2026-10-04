package com.example.model.io;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class Ooxml {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final String PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    static final String WPS = "http://schemas.microsoft.com/office/word/2010/wordprocessingShape";
    static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    static final double EMU_PER_PT = 12700.0;

    private Ooxml() {
    }

    static long emu(double pt) {
        return Math.round(pt * EMU_PER_PT);
    }

    static double fromEmu(long emu) {
        return emu / EMU_PER_PT;
    }

    static long twips(double pt) {
        return Math.round(pt * 20.0);
    }

    static long lng(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static double num(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    static boolean isTrue(String v) {
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    static double normAngle(double deg) {
        return ((deg % 360) + 360) % 360;
    }

    static String esc(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean ok = cp == 0x9 || cp == 0xA || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!ok)
                continue;
            switch (cp) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    static List<Element> elems(Element parent) {
        List<Element> out = new ArrayList<>();
        if (parent == null)
            return out;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e)
                out.add(e);
        return out;
    }

    static List<Element> kids(Element parent, String ns, String local) {
        List<Element> out = new ArrayList<>();
        for (Element e : elems(parent))
            if (local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI()))
                out.add(e);
        return out;
    }

    static Element kid(Element parent, String ns, String local) {
        if (parent == null)
            return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI()))
                return e;
        return null;
    }

    static Element desc(Element root, String ns, String local) {
        if (root == null)
            return null;
        NodeList nl = root.getElementsByTagNameNS(ns, local);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    static String attr(Element e, String ns, String local) {
        if (e == null)
            return "";
        String v = e.getAttributeNS(ns, local);
        return v == null ? "" : v;
    }

    static String wval(Element e) {
        return attr(e, W, "val");
    }
}