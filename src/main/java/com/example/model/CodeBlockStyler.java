package com.example.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import com.example.model.settings.AppSettings;
import com.example.model.settings.CodeTheme;
import com.example.model.syntax.Language;
import com.example.model.syntax.Languages;
import com.example.model.syntax.SyntaxHighlighter;
import com.example.model.syntax.TokenType;
import com.example.view.RichTextArea;

import javafx.application.Platform;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;

public class CodeBlockStyler {
    private static final List<CodeBlockStyler> ACTIVE = new ArrayList<>();
    private static final String CODE_CARET_CLASS = "code-caret-active";

    static {
        AppSettings.getInstance().codeThemeProperty().addListener((obs, o, n) -> {
            for (CodeBlockStyler s : ACTIVE)
                s.scheduleRescan();
        });
    }

    private final RichTextArea editor;
    private boolean updating = false;
    private boolean scheduled = false;
    private String injectedStylesheet;

    public CodeBlockStyler(RichTextArea editor) {
        this.editor = editor;
        ACTIVE.add(this);

        editor.addEventFilter(KeyEvent.KEY_TYPED, e -> scheduleRescan());
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> scheduleRescan());
        editor.caretPositionProperty().addListener((obs, o, n) -> scheduleRescan());
        editor.plainTextChanges().subscribe(c -> scheduleRescan());

        refreshCaretStylesheet();
        AppSettings.getInstance().codeThemeProperty().addListener((obs, o, n) -> refreshCaretStylesheet());
    }

    private void scheduleRescan() {
        if (scheduled)
            return;
        scheduled = true;
        Platform.runLater(() -> {
            scheduled = false;
            rescan();
        });
    }

    private void refreshCaretStylesheet() {
        if (injectedStylesheet != null)
            editor.getStylesheets().remove(injectedStylesheet);
        CodeTheme theme = AppSettings.getInstance().codeThemeProperty().get();
        String hex = String.format("#%02X%02X%02X",
                (int) Math.round(theme.getCaretColor().getRed() * 255),
                (int) Math.round(theme.getCaretColor().getGreen() * 255),
                (int) Math.round(theme.getCaretColor().getBlue() * 255));
        String css = "." + CODE_CARET_CLASS + " .caret { -fx-stroke: " + hex + "; }";
        String base64 = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        injectedStylesheet = "data:text/css;base64," + base64;
        editor.getStylesheets().add(injectedStylesheet);
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
                styleBody(open, close);
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
            if (!editor.getStyleClass().contains(CODE_CARET_CLASS))
                editor.getStyleClass().add(CODE_CARET_CLASS);
        } else {
            editor.getStyleClass().remove(CODE_CARET_CLASS);
        }
    }

    private void clearLine(int paragraph) {
        int len = editor.getParagraphLength(paragraph);
        if (len == 0) {
            editor.setParagraphCodeTheme(paragraph, null);
            return;
        }
        int start = editor.position(paragraph, 0).toOffset();
        int end = editor.position(paragraph, len).toOffset();

        editor.applyStyle(start, end, s -> {
            Color color = s.textColor();
            boolean wasCode = s.codeTheme() != null;
            if (wasCode || color == Color.TRANSPARENT || color == Color.WHITESMOKE)
                color = null;
            Integer size = s.fontSize() != null && s.fontSize() == 1 ? Integer.valueOf(12) : s.fontSize();
            return s.withCodeTheme(null).withFontSize(size).withTextColor(color);
        });
        editor.setParagraphCodeTheme(paragraph, null);
    }

    private void styleFenceLine(int paragraph, boolean visible) {
        int len = editor.getParagraphLength(paragraph);
        int start = editor.position(paragraph, 0).toOffset();
        int end = editor.position(paragraph, len).toOffset();

        CodeTheme theme = AppSettings.getInstance().codeThemeProperty().get();
        Color markerColor = theme.getTextColor().deriveColor(0, 1, 1, 0.6);

        editor.applyStyle(start, end, s -> visible
                ? s.withCodeTheme(null).withFontSize(11).withTextColor(markerColor)
                : s.withCodeTheme(null).withFontSize(1).withTextColor(Color.TRANSPARENT));

        editor.setParagraphCodeTheme(paragraph, theme);
    }

    private Language languageOf(int fenceParagraph) {
        String line = editor.getParagraph(fenceParagraph).getText().trim();
        String tag = line.length() > 3 ? line.substring(3).trim() : "";
        int space = 0;
        while (space < tag.length() && !Character.isWhitespace(tag.charAt(space)))
            space++;
        return Languages.forTag(tag.substring(0, space));
    }

    private void styleBody(int open, int close) {
        int first = open + 1;
        int last = close - 1;
        if (first > last)
            return;

        CodeTheme theme = AppSettings.getInstance().codeThemeProperty().get();
        Language language = languageOf(open);

        int[] offsets = new int[last - first + 1];
        StringBuilder code = new StringBuilder();
        for (int p = first; p <= last; p++) {
            offsets[p - first] = code.length();
            code.append(editor.getParagraph(p).getText());
            if (p < last)
                code.append('\n');
        }

        TokenType[] tokens = SyntaxHighlighter.highlight(code.toString(), language);
        for (int p = first; p <= last; p++)
            styleBodyLine(p, tokens, offsets[p - first], theme);
    }

    private void styleBodyLine(int paragraph, TokenType[] tokens, int offset, CodeTheme theme) {
        int len = editor.getParagraphLength(paragraph);
        if (len > 0) {
            int start = editor.position(paragraph, 0).toOffset();
            StyleSpans<TextStyle> spans = editor.getStyleSpans(start, start + len);
            StyleSpansBuilder<TextStyle> builder = new StyleSpansBuilder<>();
            boolean changed = false;
            int pos = 0;

            for (StyleSpan<TextStyle> span : spans) {
                int spanEnd = pos + span.getLength();
                int runStart = pos;
                while (runStart < spanEnd) {
                    Color color = colorFor(tokens[offset + runStart], theme);
                    int runEnd = runStart + 1;
                    while (runEnd < spanEnd && Objects.equals(colorFor(tokens[offset + runEnd], theme), color))
                        runEnd++;

                    TextStyle old = span.getStyle();
                    TextStyle next = old.withCodeTheme(theme).withTextColor(color);
                    if (!next.equals(old))
                        changed = true;
                    builder.add(next, runEnd - runStart);
                    runStart = runEnd;
                }
                pos = spanEnd;
            }

            if (changed)
                editor.setStyleSpans(start, builder.create());
        }
        editor.setParagraphCodeTheme(paragraph, theme);
    }

    private static Color colorFor(TokenType type, CodeTheme theme) {
        return type == null ? null : theme.tokenColor(type);
    }
}