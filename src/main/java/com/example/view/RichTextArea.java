package com.example.view;

import java.util.HashSet;
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

import com.example.model.TextStyle;
import com.example.model.language.maths.MathNodeFactory;
import com.example.model.language.maths.MathObject;
import com.example.model.language.maths.MathObjectSegmentOps;

import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;

public class RichTextArea extends GenericStyledArea<Void, Either<String, MathObject>, TextStyle> {
    private static final TextOps<Either<String, MathObject>, TextStyle> SEGMENT_OPS = SegmentOps.<TextStyle>styledTextOps()._or(new MathObjectSegmentOps(),(s1,s2) -> Optional.empty());
    
    public RichTextArea() {
        super(null,
                (paragraph, style) -> {},
                TextStyle.DEFAULT,
                SEGMENT_OPS,
                RichTextArea::createNode);
    }

    private static Node createNode (StyledSegment<Either<String, MathObject>, TextStyle> seg) {
        return seg.getSegment().unify(str -> {
            TextExt text = new TextExt(str);
            text.setStyle(seg.getStyle().toCss());
            return text;
        },
        RichTextArea::buildMathNode);
    }

    private static Node buildMathNode(MathObject obj) {
        if (obj == MathObject.EMPTY || obj.getType() == null) return new Label("");
        switch(obj.getType()) {
            case FRACTION: {
                String[] parts = obj.getRaw().split(",", 2);
                return MathNodeFactory.fraction(parts[0], parts[1]);
            }
            case SQRT:
                return MathNodeFactory.sqrt(obj.getRaw());
            case MATRIX:
                return MathNodeFactory.matrix(obj.getRaw());
            default:
                return new Label("?");
        }
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
        if (sel.getLength() == 0) return;
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
        replace(position, position, ReadOnlyStyledDocument.fromSegment(Either.right(obj), null, TextStyle.DEFAULT, SEGMENT_OPS));
    }
}
