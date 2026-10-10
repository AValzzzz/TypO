package com.example.model.io.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import javafx.scene.text.Font;
import javafx.scene.text.Text;

final class PdfFonts {
    private static final Map<Font, Double> ASCENTS = new HashMap<>();

    private final PDDocument doc;
    private final Map<String, PDFont> embedded = new HashMap<>();

    PdfFonts(PDDocument doc) {
        this.doc = doc;
    }

    static double ascent(Font font) {
        return ASCENTS.computeIfAbsent(font, f -> {
            Text probe = new Text("Hg");
            probe.setFont(f);
            return probe.getBaselineOffset();
        });
    }

    PDFont resolve(Font f) {
        if (embedded.containsKey(f.getName()))
            return embedded.get(f.getName());
        PDFont font = null;
        for (String name : postScriptCandidates(f)) {
            FontMapping<TrueTypeFont> m = FontMappers.instance().getTrueTypeFont(name, null);
            if (m == null || m.isFallback() || !sameFamily(m.getFont(), f))
                continue;
            try {
                font = PDType0Font.load(doc, m.getFont(), true);
                break;
            } catch (IOException | RuntimeException ignored) {
            }
        }
        embedded.put(f.getName(), font);
        return font;
    }

    private static List<String> postScriptCandidates(Font f) {
        String family = f.getFamily().replace(" ", "");
        String style = f.getStyle().replace(" ", "");
        List<String> names = new ArrayList<>(List.of(family + "-" + style, f.getName().replace(" ", "")));
        if (style.equalsIgnoreCase("Regular"))
            names.add(family);
        return names;
    }

    private static boolean sameFamily(TrueTypeFont ttf, Font f) {
        try {
            NamingTable naming = ttf.getNaming();
            String family = naming == null ? null : naming.getFontFamily();
            return family != null && family.replace(" ", "").equalsIgnoreCase(f.getFamily().replace(" ", ""));
        } catch (IOException e) {
            return false;
        }
    }
}
