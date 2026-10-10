package com.example.controller;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.model.FloatingObjects;
import com.example.model.Page;
import com.example.model.io.PageContent.FloatingImageContent;
import com.example.model.io.PageContent.FloatingTextBoxContent;
import com.example.view.Layerable;
import com.example.view.RichTextArea;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

final class FloatingShortcuts {
    private static final int MAX_HISTORY = 100;
    private static final double PASTE_OFFSET = 20;
    private static final String MIME = "application/x-typo-floating";
    private static final DataFormat FORMAT = DataFormat.lookupMimeType(MIME) != null
            ? DataFormat.lookupMimeType(MIME)
            : new DataFormat(MIME);

    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO_ALT = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
            KeyCombination.SHIFT_DOWN);
    private static final KeyCombination COPY = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination PASTE = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

    private record State(List<Object> contents, List<String> signatures) {
    }

    private record Change(Page page, State before, State after) {
    }

    private record Entry(List<Change> changes, long time) {
        Entry at(long newTime) {
            return new Entry(changes, newTime);
        }
    }

    private final List<Page> pages;
    private final Consumer<RichTextArea> cellSetup;
    private final Map<Page, State> baseline = new HashMap<>();
    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();
    private boolean pending;
    private boolean restoring;
    private long lastTextEdit;

    private Object copied;
    private Page copiedFrom;
    private String copyToken;
    private int pasteCount;

    FloatingShortcuts(List<Page> pages, Consumer<RichTextArea> cellSetup) {
        this.pages = pages;
        this.cellSetup = cellSetup;
    }

    void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> onKey(scene, e));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> begin());
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> changed());
        scene.focusOwnerProperty().addListener((obs, o, n) -> begin());
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next())
                if (c.wasRemoved() && c.getRemoved().stream().anyMatch(w -> w instanceof ContextMenu))
                    changed();
        });
    }

    void track(Page page) {
        page.setOnLayersChanged(this::changed);
        trackText(page.getEditor());
        baseline.put(page, capture(page));
    }

    void trackText(RichTextArea area) {
        area.plainTextChanges().subscribe(c -> {
            if (!restoring)
                lastTextEdit = System.nanoTime();
        });
    }

    void reset() {
        undo.clear();
        redo.clear();
        pending = false;
        lastTextEdit = 0;
        baseline.clear();
        for (Page p : pages)
            baseline.put(p, capture(p));
    }

    private void begin() {
        if (restoring)
            return;
        if (pending) {
            flush();
            return;
        }
        baseline.clear();
        for (Page p : pages)
            baseline.put(p, capture(p));
    }

    private void changed() {
        if (restoring || pending)
            return;
        pending = true;
        Platform.runLater(this::flush);
    }

    private void flush() {
        if (!pending)
            return;
        pending = false;
        baseline.keySet().retainAll(pages);
        List<Change> changes = new ArrayList<>();
        for (Page p : pages) {
            State now = capture(p);
            State before = baseline.put(p, now);
            if (before != null && !before.signatures().equals(now.signatures()))
                changes.add(new Change(p, before, now));
        }
        if (changes.isEmpty())
            return;
        undo.push(new Entry(changes, System.nanoTime()));
        if (undo.size() > MAX_HISTORY)
            undo.removeLast();
        redo.clear();
    }

    private boolean undo() {
        flush();
        Entry e = pop(undo);
        if (e == null)
            return false;
        for (Change c : e.changes())
            if (pages.contains(c.page()))
                restore(c.page(), c.before());
        redo.push(e.at(System.nanoTime()));
        return true;
    }

    private boolean redo() {
        flush();
        Entry e = pop(redo);
        if (e == null)
            return false;
        for (Change c : e.changes())
            if (pages.contains(c.page()))
                restore(c.page(), c.after());
        undo.push(e.at(System.nanoTime()));
        return true;
    }

    private Entry pop(Deque<Entry> stack) {
        while (!stack.isEmpty()) {
            Entry e = stack.pop();
            if (e.changes().stream().anyMatch(c -> pages.contains(c.page())))
                return e;
        }
        return null;
    }

    private boolean newerThanText(Deque<Entry> stack) {
        flush();
        Entry top = stack.peek();
        return top != null && top.time() > lastTextEdit;
    }

    private static State capture(Page page) {
        List<Object> contents = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
        for (Layerable l : page.getLayers()) {
            Object c = FloatingObjects.capture(l);
            contents.add(c);
            signatures.add(FloatingObjects.signature(c));
        }
        return new State(contents, signatures);
    }

    private void restore(Page page, State target) {
        restoring = true;
        try {
            Map<String, Deque<Layerable>> existing = new HashMap<>();
            for (Layerable l : page.getLayers())
                existing.computeIfAbsent(FloatingObjects.signature(FloatingObjects.capture(l)),
                        k -> new ArrayDeque<>()).add(l);

            List<Layerable> order = new ArrayList<>();
            List<Layerable> recreated = new ArrayList<>();
            for (int i = 0; i < target.contents().size(); i++) {
                Deque<Layerable> same = existing.get(target.signatures().get(i));
                Layerable l = same != null ? same.poll() : null;
                if (l == null && (l = FloatingObjects.create(page, target.contents().get(i), cellSetup)) != null)
                    recreated.add(l);
                if (l != null)
                    order.add(l);
            }
            for (Deque<Layerable> leftovers : existing.values())
                for (Layerable l : leftovers)
                    page.remove(l);
            page.setLayerOrder(order);
            baseline.put(page, capture(page));

            if (!recreated.isEmpty()) {
                for (Layerable l : page.getLayers())
                    FloatingObjects.setSelected(l, recreated.contains(l));
                recreated.get(recreated.size() - 1).node().requestFocus();
            }
        } finally {
            restoring = false;
        }
    }

    private void copy(Layerable layer) {
        flush();
        copied = FloatingObjects.capture(layer);
        copiedFrom = pageOf(layer.node());
        copyToken = UUID.randomUUID().toString();
        pasteCount = 0;

        ClipboardContent content = new ClipboardContent();
        content.put(FORMAT, copyToken);
        if (copied instanceof FloatingTextBoxContent box && !box.plainText.isEmpty())
            content.putString(box.plainText);
        if (copied instanceof FloatingImageContent img)
            content.putImage(new Image(new ByteArrayInputStream(Base64.getDecoder().decode(img.base64))));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private boolean canPaste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        return copied != null && clipboard.hasContent(FORMAT) && copyToken.equals(clipboard.getContent(FORMAT));
    }

    private void paste(Page target) {
        flush();
        pasteCount++;
        Object content = FloatingObjects.translate(copied, PASTE_OFFSET * pasteCount, PASTE_OFFSET * pasteCount);

        Layerable pasted;
        restoring = true;
        try {
            pasted = FloatingObjects.create(target, content, cellSetup);
        } finally {
            restoring = false;
        }
        if (pasted == null)
            return;
        changed();
        for (Layerable l : target.getLayers())
            FloatingObjects.setSelected(l, l == pasted);
        pasted.node().requestFocus();
    }

    private void onKey(Scene scene, KeyEvent e) {
        Node focus = scene.getFocusOwner();
        boolean inText = textOwner(focus);

        if (UNDO.match(e)) {
            if ((!inText || newerThanText(undo)) && undo())
                e.consume();
        } else if (REDO.match(e) || REDO_ALT.match(e)) {
            if ((!inText || newerThanText(redo)) && redo())
                e.consume();
        } else if (COPY.match(e)) {
            Layerable layer = inText ? null : layerOf(focus);
            if (layer != null) {
                copy(layer);
                e.consume();
            }
        } else if (PASTE.match(e)) {
            if (canPaste() && !pages.isEmpty()) {
                paste(pageOf(focus));
                e.consume();
            }
        }
    }

    private static boolean textOwner(Node focus) {
        for (Node n = focus; n != null; n = n.getParent())
            if (n instanceof RichTextArea || n instanceof TextInputControl)
                return true;
        return false;
    }

    private static Layerable layerOf(Node focus) {
        for (Node n = focus; n != null; n = n.getParent())
            if (n instanceof Layerable l)
                return l;
        return null;
    }

    private Page pageOf(Node focus) {
        for (Node n = focus; n != null; n = n.getParent())
            for (Page p : pages)
                if (p.getPane() == n)
                    return p;
        return pages.contains(copiedFrom) ? copiedFrom : pages.get(0);
    }
}
