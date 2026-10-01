package com.example.model;

import java.util.Locale;

import javafx.application.HostServices;

public class LinkOpener {
    private static HostServices hostServices;

    private LinkOpener() {}

    public static void init (HostServices services) {
        hostServices = services;
    }

    public static void open (String url) {
        if (hostServices == null || url == null) 
            return;

        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://"))
            hostServices.showDocument(url);
    }
}
