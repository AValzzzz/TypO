package com.example.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.util.Either;

import com.example.model.language.maths.MathObject;
import com.example.view.RichTextArea;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class LinkHandler {
    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}'\"";

    private final RichTextArea editor;
    private final Cursor normalCursor;
    private boolean updating = false;
    private boolean scheduled = false;

    public LinkHandler(RichTextArea editor) {
        this.editor = editor;
        this.normalCursor = editor.getCursor();

        editor.plainTextChanges().subscribe(change -> scheduleRescan());

        editor.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isShortcutDown()) {
                String link = linkAt(e);
                if (link != null) {
                    LinkOpener.open(link);
                    e.consume();
                }
            }
        });

        editor.addEventFilter(MouseEvent.MOUSE_MOVED, e -> editor.setCursor(
                e.isShortcutDown() && linkAt(e) != null ? Cursor.HAND : normalCursor));
        editor.addEventFilter(MouseEvent.MOUSE_EXITED, e -> editor.setCursor(normalCursor));

        scheduleRescan();
    }

    private String linkAt(MouseEvent e) {
        Point2D p = editor.screenToLocal(e.getScreenX(), e.getScreenY());
        if (p == null)
            return null;
        CharacterHit hit = editor.hit(p.getX(), p.getY());
        OptionalInt index = hit.getCharacterIndex();
        if (index.isEmpty())
            return null;
        return editor.getStyleOfChar(index.getAsInt()).link();
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

    private void rescan() {
        if (updating)
            return;
        updating = true;
        try {
            for (int i = 0; i < editor.getParagraphs().size(); i++)
                rescanParagraph(i);
        } finally {
            updating = false;
        }
    }

    private void rescanParagraph(int index) {
        Paragraph<ParagraphStyle, Either<String, MathObject>, TextStyle> paragraph = editor.getParagraph(index);
        int len = paragraph.length();
        if (len == 0)
            return;

        String[] wanted = new String[len];
        if (!ParagraphStyle.orDefault(paragraph.getParagraphStyle()).codeBlock()) {
            String text = paragraph.getText();
            Matcher m = URL.matcher(text);
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                while (end > start && TRAILING_PUNCTUATION.indexOf(text.charAt(end - 1)) >= 0)
                    end--;
                int scheme = text.indexOf("://", start) + 3;
                if (end <= scheme)
                    continue;
                Arrays.fill(wanted, start, end, text.substring(start, end));
            }
        }

        int paragraphStart = editor.getAbsolutePosition(index, 0);
        StyleSpans<TextStyle> spans = editor.getStyleSpans(paragraphStart, paragraphStart + len);
        StyleSpansBuilder<TextStyle> builder = new StyleSpansBuilder<>();
        boolean changed = false;
        int pos = 0;

        for (StyleSpan<TextStyle> span : spans) {
            int spanEnd = pos + span.getLength();
            int runStart = pos;
            while (runStart < spanEnd) {
                String link = wanted[runStart];
                int runEnd = runStart + 1;
                while (runEnd < spanEnd && Objects.equals(wanted[runEnd], link))
                    runEnd++;

                TextStyle old = span.getStyle();
                if (Objects.equals(old.link(), link)) {
                    builder.add(old, runEnd - runStart);
                } else {
                    builder.add(old.withLink(link), runEnd - runStart);
                    changed = true;
                }
                runStart = runEnd;
            }
            pos = spanEnd;
        }

        if (changed)
            editor.setStyleSpans(paragraphStart, builder.create());
    }
}