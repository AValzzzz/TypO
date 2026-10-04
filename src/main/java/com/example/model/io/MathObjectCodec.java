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
}
