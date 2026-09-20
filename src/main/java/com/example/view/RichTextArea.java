package com.example.view;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import com.example.model.TextStyle;

import javafx.scene.control.IndexRange;

public class RichTextArea extends StyledTextArea<Void,TextStyle> {
    public RichTextArea() {
        super(null, 
            (flow, paragraphStyle) -> {},
            TextStyle.DEFAULT,
            (text,style) -> text.setStyle(style.toCss()));
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

        StyleSpans<TextStyle> spans = getStyleSpans(sel.getStart(), sel.getEnd());
        StyleSpansBuilder<TextStyle> builder = new StyleSpansBuilder<>();

        for (StyleSpan<TextStyle> span : spans)
            builder.add(change.apply(span.getStyle()), span.getLength());

        setStyleSpans(sel.getStart(), builder.create());
    }
}
