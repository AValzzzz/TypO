package com.example.model.io;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class WordPackage {
    private final Map<String, byte[]> parts;
    private final String docDir;
    private final String docName;
    private final String docPath;
    private final Map<String, String[]> rels = new HashMap<>();

    WordPackage(Map<String, byte[]> parts, String docPath) throws Exception {
        this.parts = parts;
        this.docPath = docPath;
        int i = docPath.lastIndexOf('/');
        this.docDir = i < 0 ? "" : docPath.substring(0, i + 1);
        this.docName = docPath.substring(i + 1);
        loadRels();
    }

    Map<String, byte[]> parts() {
        return parts;
    }

    byte[] main() {
        return parts.get(docPath);
    }

    byte[] related(String rid) {
        String[] r = rels.get(rid);
        return r == null ? null : parts.get(resolve(r[0]));
    }

    private void loadRels() throws Exception {
        byte[] b = parts.get(docDir + "_rels/" + docName + ".rels");
        if (b == null)
            return;
        Document d = Ooxml.parse(b);
        var nl = d.getDocumentElement().getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            rels.put(e.getAttribute("Id"), new String[] { e.getAttribute("Target"), e.getAttribute("Type") });
        }
    }

    private String resolve(String target) {
        String p = target.startsWith("/") ? target.substring(1) : docDir + target;
        Deque<String> stack = new ArrayDeque<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals("."))
                continue;
            if (seg.equals("..")) {
                if (!stack.isEmpty())
                    stack.removeLast();
            } else {
                stack.addLast(seg);
            }
        }
        return String.join("/", stack);
    }

    byte[] partByType(String typeSuffix, String fallback) {
        for (String[] r : rels.values())
            if (r[1].endsWith(typeSuffix))
                return parts.get(resolve(r[0]));
        return parts.get(fallback);
    }

    String[] media(String rid) {
        String[] r = rels.get(rid);
        if (r == null)
            return null;
        byte[] bytes = parts.get(resolve(r[0]));
        if (bytes == null || bytes.length < 4)
            return null;
        String fmt = null;
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P')
            fmt = "png";
        else if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8)
            fmt = "jpg";
        else if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F')
            fmt = "gif";
        else if (bytes[0] == 'B' && bytes[1] == 'M')
            fmt = "bmp";
        if (fmt == null)
            return null;
        return new String[] { fmt, Base64.getEncoder().encodeToString(bytes) };
    }
}
