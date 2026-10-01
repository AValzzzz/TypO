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
import org.reactfx.util.Either;

import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.io.ColorUtil;
import com.example.model.language.maths.MathNodeFactory;
import com.example.model.language.maths.MathObject;
import com.example.model.language.maths.MathObjectSegmentOps;
import com.example.model.settings.CodeTheme;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
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
        return seg.getSegment().unify(str -> {
            TextExt text = new TextExt(str);
            text.setStyle(seg.getStyle().toCss());
            return text;
        }, mathObject -> buildMathNode(mathObject, seg.getStyle()));
    }

    private static Node buildMathNode(MathObject obj, TextStyle style) {
        if (obj == MathObject.EMPTY || obj.getType() == null)
            return new Label("");

        String raw = obj.getRaw();
        return switch (obj.getType()) {
            case EXPONENT -> {
                String[] p = raw.split(",", 2);
                yield MathNodeFactory.exponent(p[0], p[1], style);
            }
            case SUBSCRIPT -> {
                String[] p = raw.split(",", 2);
                yield MathNodeFactory.subscript(p[0], p[1], style);
            }
            case FRACTION -> {
                String[] p = raw.split(",", 2);
                yield MathNodeFactory.fraction(p[0], p[1], style);
            }
            case SQRT -> MathNodeFactory.sqrt(raw, style);
            case MATRIX -> MathNodeFactory.matrix(raw, style);
            case SUM, INTEGRAL, PRODUCT -> {
                String[] p = raw.split("\\|", -1);
                yield MathNodeFactory.bigOperator(obj.getType().symbol(), p[0], p[1], p[2], style);
            }
            case LIMIT -> {
                String[] p = raw.split("\\|", -1);
                yield MathNodeFactory.limit(p[0], p[1], style);
            }
            case IMAGE -> MathNodeFactory.image(raw, style);
        };
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
}