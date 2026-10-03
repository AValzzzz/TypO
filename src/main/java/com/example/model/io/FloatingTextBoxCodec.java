package com.example.model.io;

import com.example.model.io.PageContent.FloatingTextBoxContent;

public final class FloatingTextBoxCodec {
    private static final String START = "\uE040TEXTBOX:";
    private static final String END = "\uE041";

    private FloatingTextBoxCodec() {
    }

    public static String encode(FloatingTextBoxContent t) {
        return START + t.x + "," + t.y + "," + t.width + "," + (t.level == null ? 0 : t.level) + ","
                + (t.borderVisible ? "1" : "0") + "," + t.borderHex + ","
                + (t.backgroundVisible ? "1" : "0") + "," + t.backgroundHex + "," + t.backgroundOpacity
                + "|" + t.cells + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static FloatingTextBoxContent decode(String token) {
        String body = token.substring(START.length(), token.length() - END.length());
        int sep = body.indexOf('|');
        String[] p = body.substring(0, sep).split(",");
        String cells = body.substring(sep + 1);
        FloatingTextBoxContent t = new FloatingTextBoxContent(
                Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                p[4].equals("1"), p[5], p[6].equals("1"), p[7], Double.parseDouble(p[8]), cells);
        t.level = Integer.parseInt(p[3]);
        return t;
    }
}