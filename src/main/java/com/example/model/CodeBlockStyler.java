package com.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import com.example.view.RichTextArea;

import javafx.application.Platform;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;

public class CodeBlockStyler {
    private final RichTextArea editor;
    private boolean updating = false;

    private static final String CODE_CARET_CLASS = "code-caret-active";
    private static final String CARET_STYLESHEET = buildCaretStylesheet();

    public CodeBlockStyler(RichTextArea editor) {
        this.editor = editor;
        editor.getStylesheets().add(CARET_STYLESHEET);

        editor.addEventFilter(KeyEvent.KEY_TYPED, e -> Platform.runLater(this::rescan));
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> Platform.runLater(this::rescan));
        editor.caretPositionProperty().addListener((obs, o, n) -> Platform.runLater(this::rescan));
    }

    private static String buildCaretStylesheet() {
        String css = "." + CODE_CARET_CLASS + " .caret { -fx-stroke: white; }";
        String base64 = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        return "data:text/css;base64," + base64;
    }

    public void rescan() {
        if (updating)
            return;
        updating = true;
        try {
            int paragraphCount = editor.getParagraphs().size();
            int caretParagraph = editor.getCurrentParagraph();

            List<int[]> fencePairs = new ArrayList<>();
            Integer openIndex = null;
            for (int i = 0; i < paragraphCount; i++) {
                String line = editor.getParagraph(i).getText().trim();
                if (line.startsWith("```")) {
                    if (openIndex == null)
                        openIndex = i;
                    else {
                        fencePairs.add(new int[] { openIndex, i });
                        openIndex = null;
                    }
                }
            }

            boolean[] insideFence = new boolean[paragraphCount];
            for (int[] pair : fencePairs) {
                for (int i = pair[0]; i <= pair[1]; i++)
                    insideFence[i] = true;
            }

            for (int[] pair : fencePairs) {
                int open = pair[0];
                int close = pair[1];
                boolean caretInside = caretParagraph >= open && caretParagraph <= close;

                styleFenceLine(open, caretInside);
                styleFenceLine(close, caretInside);

                for (int i = open + 1; i < close; i++) {
                    styleBodyLine(i);
                }
            }

            for (int i = 0; i < paragraphCount; i++) {
                if (!insideFence[i]) {
                    clearLine(i);
                }
            }

            boolean caretInsideAnyFence = caretParagraph >= 0 && caretParagraph < paragraphCount
                    && insideFence[caretParagraph];
            updateCaretColor(caretInsideAnyFence);
        } finally {
            updating = false;
        }
    }

    private void updateCaretColor(boolean insideCode) {
        if (insideCode) {
            if (!editor.getStyleClass().contains(CODE_CARET_CLASS)) {
                editor.getStyleClass().add(CODE_CARET_CLASS);
            }
        } else {
            editor.getStyleClass().remove(CODE_CARET_CLASS);
        }
    }

    private void clearLine(int paragraph) {
        int len = editor.getParagraphLength(paragraph);
        if (len == 0) {
            editor.setParagraphStyle(paragraph, false);
            return;
        }
        int start = editor.position(paragraph, 0).toOffset();
        int end = editor.position(paragraph, len).toOffset();

        editor.applyStyle(start, end, s -> s.withCodeBlock(false).withFontSize(
                s.fontSize() != null && s.fontSize() == 1 ? 12 : s.fontSize()).withTextColor(
                        s.textColor() == Color.TRANSPARENT || s.textColor() == Color.WHITESMOKE ? null
                                : s.textColor()));
        editor.setParagraphStyle(paragraph, false);
    }

    private void styleFenceLine(int paragraph, boolean visible) {
        int len = editor.getParagraphLength(paragraph);
        int start = editor.position(paragraph, 0).toOffset();
        int end = editor.position(paragraph, len).toOffset();

        editor.applyStyle(start, end, s -> visible
                ? s.withCodeBlock(false).withFontSize(11).withTextColor(Color.WHITESMOKE)
                : s.withCodeBlock(false).withFontSize(1).withTextColor(Color.TRANSPARENT));

        editor.setParagraphStyle(paragraph, true);
    }

    private void styleBodyLine(int paragraph) {
        int len = editor.getParagraphLength(paragraph);
        int start = editor.position(paragraph, 0).toOffset();
        int end = editor.position(paragraph, len).toOffset();

        editor.applyStyle(start, end, s -> s.withCodeBlock(true));
        editor.setParagraphStyle(paragraph, true);
    }
}
