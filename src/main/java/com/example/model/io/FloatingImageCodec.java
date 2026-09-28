package com.example.model.io;

public final class FloatingImageCodec {
    private static final String START = "\uE010POS:";
    private static final String END = "\uE011";

    private FloatingImageCodec() {}

    public static String encode(double x, double y, double width, double height) {
        return START + x + "," + y + "," + width + "," + height + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static double[] decode(String token) {
        String body = token.substring(START.length(), token.length() - END.length());
        String[] parts = body.split(",");
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) result[i] = Double.parseDouble(parts[i]);
        return result;
    }
}