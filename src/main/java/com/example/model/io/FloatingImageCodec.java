package com.example.model.io;

public final class FloatingImageCodec {
    private static final String START = "\uE010POS:";
    private static final String END = "\uE011";

    private FloatingImageCodec() {
    }

    public static String encode(double x, double y, double width, double height, double rotation, int level,
            double opacity) {
        return START + x + "," + y + "," + width + "," + height + "," + rotation + "," + level + ","
                + opacity + END;
    }

    public static boolean isToken(String text) {
        return text != null && text.startsWith(START) && text.endsWith(END);
    }

    public static double[] decode(String token) {
        String body = token.substring(START.length(), token.length() - END.length());
        String[] parts = body.split(",");
        double[] result = new double[7];
        for (int i = 0; i < 4; i++)
            result[i] = Double.parseDouble(parts[i]);
        result[4] = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.0;
        result[5] = parts.length > 5 ? Double.parseDouble(parts[5]) : 0.0;
        result[6] = parts.length > 6 ? Double.parseDouble(parts[6]) : 1.0;
        return result;
    }
}