package com.example.model.io;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledSegment;
import org.reactfx.util.Either;

import com.example.model.Page;
import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;
import com.example.view.RichTextArea;

public class PageContent {
    public final boolean landscape;
    public final List<ParagraphContent> paragraphs = new ArrayList<>();

    public PageContent(boolean landscape) {
        this.landscape = landscape;
    }

    public static PageContent capture(Page page) {
        boolean landscape = page.getPane().getWidth() > page.getPane().getHeight();
        PageContent content = new PageContent(landscape);
        RichTextArea editor = page.getEditor();

        for(Paragraph<Boolean, Either<String, MathObject>, TextStyle> paragraph : editor.getParagraphs()) {
            ParagraphContent pc = new ParagraphContent(Boolean.TRUE.equals(paragraph.getParagraphStyle()));
            for(StyledSegment<Either<String, MathObject>, TextStyle> seg : paragraph.getStyledSegments()) {
                TextStyle style = seg.getStyle();
                seg.getSegment().unify(
                    text-> {
                        pc.runs.add(RunContent.text(text,style));
                        return null;
                    },
                    math -> {
                        pc.runs.add(RunContent.math(math,style));
                        return null;
                    });
            }
            content.paragraphs.add(pc);
        }
        return content;
    }


    public static final class ParagraphContent {
        public final boolean codeBlock;
        public final List<RunContent> runs = new ArrayList<>();

        public ParagraphContent(boolean codeBlock) {
            this.codeBlock = codeBlock;
        }
    }


    public static final class RunContent {
        public final String text;
        public final MathObject math;
        public final TextStyle style;

        private RunContent (String text, MathObject math, TextStyle style) {
            this.text = text;
            this.math = math;
            this.style = style;
        }


        public static RunContent text (String text, TextStyle style) {
            return new RunContent(text, null, style);
        }


        public static RunContent math(MathObject math, TextStyle style) {
            return new RunContent(null, math, style);
        }

        public boolean isMath() {
            return math != null;
        }
    }
}
