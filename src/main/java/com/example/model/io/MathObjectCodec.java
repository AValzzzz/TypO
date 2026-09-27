package com.example.model.io;

import com.example.model.language.maths.MathObject;

public class MathObjectCodec {
    private static final String START = "\uE000MATH:";
    private static final String END = "\uE001";

    private MathObjectCodec() {
    }

    public static String encode(MathObject obj) {
        return START + obj.getType().name() + "|" + obj.getRaw() + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END)
                && text.length() >= START.length() + END.length();
    }

    public static MathObject decode(String token) {
        String body = token.substring(START.length(), token.length() - END.length());
        int sep = body.indexOf('|');
        String typeName = body.substring(0, sep);
        String raw = body.substring(sep + 1);
        return new MathObject(MathObject.Type.valueOf(typeName), raw);
    }

    public static String approximate(MathObject obj) {
        if (obj == null || obj.getType() == null) {
            return "";
        }
        String[] parts;
        switch (obj.getType()) {
            case FRACTION:
                parts = split(obj.getRaw(), ",", 2);
                return "(" + parts[0] + "/" + parts[1] + ")";
            case EXPONENT:
                parts = split(obj.getRaw(), ",", 2);
                return parts[0] + "^(" + parts[1] + ")";
            case SUBSCRIPT:
                parts = split(obj.getRaw(), ",", 2);
                return parts[0] + "_(" + parts[1] + ")";
            case SQRT:
                return "\u221A(" + obj.getRaw() + ")";
            case MATRIX:
                return "[" + obj.getRaw().replace(";", " ; ") + "]";
            case SUM: {
                parts = split(obj.getRaw(), "\\|", 3);
                return "\u03A3(" + parts[0] + "\u2192" + parts[1] + ") " + parts[2];
            }
            case INTEGRAL: {
                parts = split(obj.getRaw(), "\\|", 3);
                return "\u222B(" + parts[0] + "\u2192" + parts[1] + ") " + parts[2];
            }
            case PRODUCT: {
                parts = split(obj.getRaw(), "\\|", 3);
                return "\u03A0(" + parts[0] + "\u2192" + parts[1] + ") " + parts[2];
            }
            case LIMIT: {
                parts = split(obj.getRaw(), "\\|", 2);
                return "lim(" + parts[0] + ") " + parts[1];
            }
            default:
                return obj.getRaw();
        }
    }

    private static String[] split(String raw, String delimiterRegex, int expected) {
        String[] parts = raw.split(delimiterRegex, -1);
        if (parts.length >= expected)
            return parts;

        String[] padded = new String[expected];
        for (int i = 0; i < expected; i++)
            padded[i] = i < parts.length ? parts[i] : "";

        return padded;
    }
}
