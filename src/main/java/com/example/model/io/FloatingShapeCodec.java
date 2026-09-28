package com.example.model.io;

import com.example.model.io.PageContent.FloatingShapeContent;
import com.example.model.language.shapes.ShapeType;

public final class FloatingShapeCodec {
    private static final String START = "\uE012SHAPE:";
    private static final String END = "\uE013";

    private FloatingShapeCodec() {
    }

    public static String encode(FloatingShapeContent s) {
        return START + s.type.name() + "," + s.x + "," + s.y + "," + s.width + "," + s.height + "," + s.fillHex + ","
                + s.fillOpacity + "," + s.strokeHex + "," + s.strokeOpacity + "," + s.strokeWidth + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static FloatingShapeContent decode(String token) {
        String[] p = token.substring(START.length(), token.length() - END.length()).split(",");
        return new FloatingShapeContent(ShapeType.valueOf(p[0]),
                Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Double.parseDouble(p[4]),
                p[5], Double.parseDouble(p[6]),
                p[7], Double.parseDouble(p[8]),
                Double.parseDouble(p[9]));
    }
}
