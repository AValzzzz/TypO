package com.example;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Files the OS asks TypO to open ("Open with TypO"). Linux and Windows pass them as
 * command-line arguments; macOS delivers them as an event, also while TypO is running.
 * Requests arriving before the UI is ready are kept until a handler is set.
 */
public final class OpenRequests {
    private static final List<Path> pending = new ArrayList<>();
    private static Consumer<Path> handler;

    private OpenRequests() {
    }

    static void collect(String[] args) {
        for (String arg : args)
            toDocx(arg).ifPresent(OpenRequests::submit);
        installMacHandler();
    }

    private static void installMacHandler() {
        // only macOS uses this; avoids starting AWT for nothing elsewhere
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                || !Desktop.isDesktopSupported())
            return;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE))
            return;
        desktop.setOpenFileHandler(event -> {
            for (File file : event.getFiles())
                toDocx(file.getPath()).ifPresent(OpenRequests::submit);
        });
    }

    private static Optional<Path> toDocx(String arg) {
        try {
            Path path = Path.of(arg).toAbsolutePath();
            if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
                return Optional.of(path);
        } catch (InvalidPathException e) {
            // not a path (e.g. a JVM/launcher flag)
        }
        return Optional.empty();
    }

    private static synchronized void submit(Path path) {
        if (handler != null) {
            Consumer<Path> current = handler;
            Platform.runLater(() -> current.accept(path));
        } else
            pending.add(path);
    }

    static synchronized void setHandler(Consumer<Path> handler) {
        OpenRequests.handler = handler;
        // single window: only the last requested file can be shown
        if (!pending.isEmpty())
            handler.accept(pending.get(pending.size() - 1));
        pending.clear();
    }
}
