package com.example.model.io;

import com.example.model.io.PageContent.FloatingArrowContent;

public final class FloatingArrowCodec {
    private static final String START = "\uE014ARROW:";
    private static final String END = "\uE015";

    private FloatingArrowCodec() {}

    public static String encode(FloatingArrowContent a) {
        return START + a.startX + "," + a.startY + "," + a.endX + "," + a.endY + ","
                + a.controlX + "," + a.controlY + "," + a.strokeHex + "," + a.strokeOpacity + ","
                + a.strokeWidth + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static FloatingArrowContent decode(String token) {
        String[] p = token.substring(START.length(), token.length() - END.length()).split(",");
        return new FloatingArrowContent(
                Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                p[6], Double.parseDouble(p[7]), Double.parseDouble(p[8]));
    }
}