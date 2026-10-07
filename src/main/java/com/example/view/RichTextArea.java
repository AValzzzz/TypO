package com.example.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.StyledDocument;
import org.reactfx.util.Either;

import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.io.ColorUtil;
import com.example.model.language.maths.MathNodeFactory;
import com.example.model.language.maths.MathObject;
import com.example.model.language.maths.MathObjectSegmentOps;
import com.example.model.settings.CodeTheme;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class RichTextArea extends GenericStyledArea<ParagraphStyle, Either<String, MathObject>, TextStyle> {
    private static final TextOps<Either<String, MathObject>, TextStyle> SEGMENT_OPS = SegmentOps
            .<TextStyle>styledTextOps()._or(new MathObjectSegmentOps(), (s1, s2) -> Optional.empty());

    public RichTextArea() {
        super(ParagraphStyle.DEFAULT,
                RichTextArea::applyParagraphStyle,
                TextStyle.DEFAULT,
                SEGMENT_OPS,
                RichTextArea::createNode);
    }

    private static void applyParagraphStyle(TextFlow flow, ParagraphStyle style) {
        ParagraphStyle s = ParagraphStyle.orDefault(style);
        CodeTheme theme = s.codeTheme();
        if (theme != null) {
            flow.setStyle("-fx-background-color: " + ColorUtil.toCssRgba(theme.getBackground()) + ";");
            flow.setPadding(new Insets(2, 8, 2, 8));
            flow.setMaxWidth(Double.MAX_VALUE);
            flow.setTextAlignment(TextAlignment.LEFT);
        } else {
            flow.setStyle("");
            flow.setPadding(Insets.EMPTY);
            flow.setTextAlignment(s.alignment());
        }
    }

    private static Node createNode(StyledSegment<Either<String, MathObject>, TextStyle> seg) {
        Node node = seg.getSegment().unify(str -> {
            TextExt text = new TextExt(str);
            text.setStyle(seg.getStyle().toCss());
            return text;
        }, mathObject -> MathNodeFactory.render(mathObject, seg.getStyle()));
        TightTextShapes.track(node);
        return node;
    }

    public boolean hasSelection() {
        return getSelection().getLength() > 0;
    }

    public <T> Optional<T> commonValue(Function<TextStyle, T> property) {
        IndexRange sel = getSelection();
        Set<T> values = new HashSet<>();
        for (StyleSpan<TextStyle> span : getStyleSpans(sel.getStart(), sel.getEnd()))
            values.add(property.apply(span.getStyle()));

        return values.size() == 1 ? Optional.ofNullable(values.iterator().next()) : Optional.empty();
    }

    public void updateSelectionStyle(UnaryOperator<TextStyle> change) {
        IndexRange sel = getSelection();
        if (sel.getLength() == 0)
            return;
        applyStyle(sel.getStart(), sel.getEnd(), change);
    }

    public void applyStyle(int start, int end, UnaryOperator<TextStyle> change) {
        if (end <= start)
            return;

        StyleSpans<TextStyle> spans = getStyleSpans(start, end);
        StyleSpansBuilder<TextStyle> builder = new StyleSpansBuilder<>();

        for (StyleSpan<TextStyle> span : spans) {
            builder.add(change.apply(span.getStyle()), span.getLength());
        }

        setStyleSpans(start, builder.create());
    }

    public void insertMathObject(int position, MathObject obj) {
        insertMathObject(position, obj, TextStyle.DEFAULT);
    }

    public void insertMathObject(int position, MathObject obj, TextStyle style) {
        replace(position, position,
                ReadOnlyStyledDocument.fromSegment(Either.right(obj), null, style, SEGMENT_OPS));
    }

    public void appendStyledText(String text, TextStyle style) {
        int end = getLength();
        replace(end, end, ReadOnlyStyledDocument.fromString(text, null, style, SEGMENT_OPS));
    }

    public void appendMathObject(MathObject obj, TextStyle style) {
        insertMathObject(getLength(), obj, style);
    }

    private int[] selectedParagraphRange() {
        IndexRange sel = getSelection();
        int first = offsetToPosition(sel.getStart(), Bias.Forward).getMajor();
        int last = offsetToPosition(sel.getEnd(), Bias.Backward).getMajor();
        return new int[] { first, Math.max(first, last) };
    }

    public List<ParagraphStyle> selectedParagraphStyles() {
        int[] r = selectedParagraphRange();
        List<ParagraphStyle> styles = new ArrayList<>();
        for (int i = r[0]; i <= r[1]; i++)
            styles.add(ParagraphStyle.orDefault(getParagraph(i).getParagraphStyle()));
        return styles;
    }

    public <T> Optional<T> commonParagraphValue(Function<ParagraphStyle, T> property) {
        Set<T> values = new HashSet<>();
        for (ParagraphStyle s : selectedParagraphStyles())
            if (!s.codeBlock())
                values.add(property.apply(s));
        return values.size() == 1 ? Optional.ofNullable(values.iterator().next()) : Optional.empty();
    }

    public void updateSelectionParagraphStyle(UnaryOperator<ParagraphStyle> change) {
        int[] r = selectedParagraphRange();
        for (int i = r[0]; i <= r[1]; i++) {
            ParagraphStyle current = ParagraphStyle.orDefault(getParagraph(i).getParagraphStyle());
            if (current.codeBlock())
                continue;
            ParagraphStyle next = change.apply(current);
            if (!next.equals(current))
                setParagraphStyle(i, next);
        }
    }

    public void setParagraphCodeTheme(int paragraph, CodeTheme theme) {
        ParagraphStyle current = ParagraphStyle.orDefault(getParagraph(paragraph).getParagraphStyle());
        ParagraphStyle next = theme != null
                ? new ParagraphStyle(theme, TextAlignment.LEFT)
                : current.withCodeTheme(null);
        if (!next.equals(current))
            setParagraphStyle(paragraph, next);
    }

    public int findOverflowOffset() {
        if (getScene() == null || getParagraphs().isEmpty())
            return -1;

        Insets in = getInsets();
        double viewport = getHeight() - in.getTop() - in.getBottom();
        Double estimate = totalHeightEstimateProperty().getValue();
        if (estimate != null && estimate < viewport - 30)
            return -1;

        scrollYToPixel(0);
        applyCss();
        layout();

        Point2D bottom = localToScreen(0, getHeight() - in.getBottom());
        if (bottom == null)
            return -1;
        double limit = bottom.getY() + 0.5;

        int count = getParagraphs().size();
        int k = -1;
        Bounds kBounds = null;
        for (int i = 0; i < count; i++) {
            Optional<Bounds> b = getParagraphBoundsOnScreen(i);
            if (b.isEmpty() || b.get().getMaxY() > limit) {
                k = i;
                kBounds = b.orElse(null);
                break;
            }
        }
        if (k < 0 || (k == 0 && kBounds == null))
            return -1;

        int start = position(k, 0).toOffset();
        int len = getParagraphLength(k);

        if (kBounds != null && kBounds.getMinY() < limit && len > 1) {
            int lo = 0, hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                Optional<Bounds> cb = getCharacterBoundsOnScreen(start + mid, start + mid + 1);
                if (cb.isPresent() && cb.get().getMaxY() <= limit)
                    lo = mid + 1;
                else
                    hi = mid;
            }
            if (lo > 0 && lo < len)
                return start + lo;
        }
        return start > 0 ? start : -1;
    }

    public StyledDocument<ParagraphStyle, Either<String, MathObject>, TextStyle> removeTail(int cut) {
        int end = getLength();
        StyledDocument<ParagraphStyle, Either<String, MathObject>, TextStyle> tail = subDocument(cut, end);
        boolean atParagraphStart = offsetToPosition(cut, Bias.Forward).getMinor() == 0;
        deleteText(atParagraphStart && cut > 0 ? cut - 1 : cut, end);
        return tail;
    }

    public void prependDocument(StyledDocument<ParagraphStyle, Either<String, MathObject>, TextStyle> doc) {
        boolean wasEmpty = getLength() == 0;
        ParagraphStyle firstOld = ParagraphStyle.orDefault(getParagraph(0).getParagraphStyle());
        int movedLength = doc.length();
        int movedParagraphs = doc.getParagraphs().size();

        replace(0, 0, doc);
        if (!wasEmpty) {
            insertText(movedLength, "\n");
            setParagraphStyle(movedParagraphs, firstOld);
        }
        for (int i = 0; i < movedParagraphs; i++)
            setParagraphStyle(i, ParagraphStyle.orDefault(doc.getParagraphs().get(i).getParagraphStyle()));
    }
}