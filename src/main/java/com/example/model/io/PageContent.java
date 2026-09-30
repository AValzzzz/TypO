package com.example.model.io;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledSegment;
import org.reactfx.util.Either;

import com.example.model.Page;
import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;
import com.example.model.language.shapes.ShapeType;
import com.example.model.settings.CodeTheme;
import com.example.view.ArrowOverlay;
import com.example.view.ImageOverlay;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;

public class PageContent {
    public final boolean landscape;
    public final List<ParagraphContent> paragraphs = new ArrayList<>();
    public final List<FloatingImageContent> images = new ArrayList<>();
    public final List<FloatingShapeContent> shapes = new ArrayList<>();
    public final List<FloatingArrowContent> arrows = new ArrayList<>();

    public PageContent(boolean landscape) {
        this.landscape = landscape;
    }

    public static PageContent capture(Page page) {
        boolean landscape = page.getPane().getWidth() > page.getPane().getHeight();
        PageContent content = new PageContent(landscape);
        RichTextArea editor = page.getEditor();

        for (Paragraph<CodeTheme, Either<String, MathObject>, TextStyle> paragraph : editor
                .getParagraphs()) {
            ParagraphContent pc = new ParagraphContent(paragraph.getParagraphStyle() != null);
            for (StyledSegment<Either<String, MathObject>, TextStyle> seg : paragraph.getStyledSegments()) {
                TextStyle style = seg.getStyle();
                seg.getSegment().unify(
                        text -> {
                            pc.runs.add(RunContent.text(text, style));
                            return null;
                        },
                        math -> {
                            pc.runs.add(RunContent.math(math, style));
                            return null;
                        });
            }
            content.paragraphs.add(pc);
        }

        for (ImageOverlay overlay : page.getImageOverlays()) {
            content.images.add(new FloatingImageContent(
                    overlay.getImageX(), overlay.getImageY(),
                    overlay.getImageWidth(), overlay.getImageHeight(),
                    overlay.getFormat(), overlay.getBase64(), overlay.getImageRotation()));
        }

        for (ShapeOverlay s : page.getShapeOverlays()) {
            content.shapes.add(new FloatingShapeContent(
                    s.getShapeType(), s.getShapeX(), s.getShapeY(), s.getShapeWidth(), s.getShapeHeight(),
                    ColorUtil.toHex(s.getFillColor()), s.getFillOpacity(),
                    ColorUtil.toHex(s.getStrokeColor()), s.getStrokeOpacity(), s.getStrokeWidth(),
                    s.getShapeRotation()));
        }

        for (ArrowOverlay a : page.getArrowOverlays()) {
            content.arrows.add(new FloatingArrowContent(
                    a.getStartX(), a.getStartY(), a.getEndX(), a.getEndY(),
                    a.getControlX(), a.getControlY(),
                    ColorUtil.toHex(a.getStrokeColor()), a.getStrokeOpacity(), a.getStrokeWidth()));
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

        private RunContent(String text, MathObject math, TextStyle style) {
            this.text = text;
            this.math = math;
            this.style = style;
        }

        public static RunContent text(String text, TextStyle style) {
            return new RunContent(text, null, style);
        }

        public static RunContent math(MathObject math, TextStyle style) {
            return new RunContent(null, math, style);
        }

        public boolean isMath() {
            return math != null;
        }
    }

    public static final class FloatingImageContent {
        public final double x, y, width, height;
        public final String format;
        public final String base64;
        public final double rotation;

        public FloatingImageContent(double x, double y, double width, double height, String format, String base64,
                double rotation) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.format = format;
            this.base64 = base64;
            this.rotation = rotation;
        }
    }

    public static final class FloatingShapeContent {
        public final ShapeType type;
        public final double x, y, width, height;
        public final String fillHex, strokeHex;
        public final double fillOpacity, strokeOpacity, strokeWidth, rotation;

        public FloatingShapeContent(ShapeType type, double x, double y, double width, double height,
                String fillHex, double fillOpacity, String strokeHex, double strokeOpacity, double strokeWidth,
                double rotation) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fillHex = fillHex;
            this.fillOpacity = fillOpacity;
            this.strokeHex = strokeHex;
            this.strokeOpacity = strokeOpacity;
            this.strokeWidth = strokeWidth;
            this.rotation = rotation;
        }
    }

    public static final class FloatingArrowContent {
        public final double startX, startY, endX, endY, controlX, controlY;
        public final String strokeHex;
        public final double strokeOpacity, strokeWidth;

        public FloatingArrowContent(double startX, double startY, double endX, double endY,
                double controlX, double controlY, String strokeHex, double strokeOpacity, double strokeWidth) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.controlX = controlX;
            this.controlY = controlY;
            this.strokeHex = strokeHex;
            this.strokeOpacity = strokeOpacity;
            this.strokeWidth = strokeWidth;
        }
    }
}
