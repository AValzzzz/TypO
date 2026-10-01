package com.example.model.io;

public final class PageNumberCodec {
    private static final String TOKEN = "\uE000PAGENUMBERS\uE001";

    private PageNumberCodec() {
    }

    public static String encode() {
        return TOKEN;
    }

    public static boolean isToken(String text) {
        return TOKEN.equals(text);
    }
}