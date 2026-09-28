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
        String raw = obj.getRaw();
        return switch (obj.getType()) {
            case FRACTION -> {
                String[] p = split(raw, ",", 2);
                yield "(" + p[0] + "/" + p[1] + ")";
            }
            case EXPONENT -> {
                String[] p = split(raw, ",", 2);
                yield p[0] + "^(" + p[1] + ")";
            }
            case SUBSCRIPT -> {
                String[] p = split(raw, ",", 2);
                yield p[0] + "_(" + p[1] + ")";
            }
            case SQRT -> "\u221A(" + raw + ")";
            case MATRIX -> "[" + raw.replace(";", " ; ") + "]";
            case SUM, INTEGRAL, PRODUCT -> {
                String[] p = split(raw, "\\|", 3);
                yield obj.getType().symbol() + "(" + p[0] + "\u2192" + p[1] + ") " + p[2];
            }
            case LIMIT -> {
                String[] p = split(raw, "\\|", 2);
                yield "lim(" + p[0] + ") " + p[1];
            }
            case IMAGE -> "[image]";
        };
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
