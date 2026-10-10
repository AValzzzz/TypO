package com.example.model.io;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledSegment;
import org.reactfx.util.Either;

import com.example.model.Page;
import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;
import com.example.model.language.shapes.ShapeType;
import com.example.model.settings.AppSettings;
import com.example.view.ArrowOverlay;
import com.example.view.ImageOverlay;
import com.example.view.Layerable;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;
import com.example.view.TableOverlay;
import com.example.view.TextBoxOverlay;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public class PageContent {
    public final boolean landscape;
    public double marginLeftCm = AppSettings.getInstance().getMarginLeft();
    public double marginTopCm = AppSettings.getInstance().getMarginTop();
    public double marginRightCm = AppSettings.getInstance().getMarginRight();
    public double marginBottomCm = AppSettings.getInstance().getMarginBottom();
    public boolean showPageNumbers = AppSettings.getInstance().isShowPageNumbers();

    public final List<ParagraphContent> paragraphs = new ArrayList<>();
    public final List<FloatingImageContent> images = new ArrayList<>();
    public final List<FloatingShapeContent> shapes = new ArrayList<>();
    public final List<FloatingArrowContent> arrows = new ArrayList<>();
    public final List<FloatingTableContent> tables = new ArrayList<>();
    public final List<FloatingTextBoxContent> textBoxes = new ArrayList<>();

    public PageContent(boolean landscape) {
        this.landscape = landscape;
    }

    public static PageContent capture(Page page) {
        boolean landscape = page.getPane().getWidth() > page.getPane().getHeight();
        PageContent content = new PageContent(landscape);
        RichTextArea editor = page.getEditor();

        for (Paragraph<ParagraphStyle, Either<String, MathObject>, TextStyle> paragraph : editor
                .getParagraphs()) {
            ParagraphStyle ps = ParagraphStyle.orDefault(paragraph.getParagraphStyle());
            ParagraphContent pc = new ParagraphContent(ps.codeBlock(), ps.alignment());
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

        for (ImageOverlay overlay : page.getImageOverlays())
            content.images.add(capture(overlay));
        for (ShapeOverlay s : page.getShapeOverlays())
            content.shapes.add(capture(s));
        for (ArrowOverlay a : page.getArrowOverlays())
            content.arrows.add(capture(a));
        for (TableOverlay t : page.getTableOverlays())
            content.tables.add(capture(t));
        for (TextBoxOverlay t : page.getTextBoxOverlays())
            content.textBoxes.add(capture(t));

        return content;
    }

    public static void populate(RichTextArea editor, List<ParagraphContent> paragraphs) {
        editor.clear();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphContent paragraph = paragraphs.get(i);
            for (RunContent run : paragraph.runs) {
                if (run.isMath())
                    editor.appendMathObject(run.math, run.style);
                else
                    editor.appendStyledText(run.text, run.style);
            }
            ParagraphStyle style = paragraph.codeBlock
                    ? new ParagraphStyle(AppSettings.getInstance().codeThemeProperty().get(), TextAlignment.LEFT)
                    : new ParagraphStyle(null, paragraph.alignment);
            editor.setParagraphStyle(editor.getParagraphs().size() - 1, style);
            if (i < paragraphs.size() - 1)
                editor.appendStyledText("\n", TextStyle.DEFAULT);
        }
    }

    public List<FloatingContent> floating() {
        List<FloatingContent> all = new ArrayList<>();
        all.addAll(images);
        all.addAll(shapes);
        all.addAll(arrows);
        all.addAll(tables);
        all.addAll(textBoxes);
        return all;
    }

    public static FloatingImageContent capture(ImageOverlay overlay) {
        FloatingImageContent fi = new FloatingImageContent(
                overlay.getImageX(), overlay.getImageY(),
                overlay.getImageWidth(), overlay.getImageHeight(),
                overlay.getFormat(), overlay.getBase64(), overlay.getImageRotation());
        fi.level = overlay.getLevel();
        fi.opacity = overlay.getImageOpacity();
        return fi;
    }

    public static FloatingShapeContent capture(ShapeOverlay s) {
        FloatingShapeContent fs = new FloatingShapeContent(
                s.getShapeType(), s.getShapeX(), s.getShapeY(), s.getShapeWidth(), s.getShapeHeight(),
                ColorUtil.toHex(s.getFillColor()), s.getFillOpacity(),
                ColorUtil.toHex(s.getStrokeColor()), s.getStrokeOpacity(), s.getStrokeWidth(),
                s.getShapeRotation());
        fs.level = s.getLevel();
        return fs;
    }

    public static FloatingArrowContent capture(ArrowOverlay a) {
        FloatingArrowContent fa = new FloatingArrowContent(
                a.getStartX(), a.getStartY(), a.getEndX(), a.getEndY(),
                a.getControlX(), a.getControlY(),
                ColorUtil.toHex(a.getStrokeColor()), a.getStrokeOpacity(), a.getStrokeWidth());
        fa.level = a.getLevel();
        return fa;
    }

    public static FloatingTableContent capture(TableOverlay t) {
        FloatingTableContent ft = new FloatingTableContent(t.getTableX(), t.getTableY(),
                t.getColumnWidths(), t.getRowHeights(),
                t.getOffsetsX(), t.getOffsetsY(), t.getMerges(), t.encodeCells());
        ft.level = t.getLevel();
        return ft;
    }

    public static FloatingTextBoxContent capture(TextBoxOverlay t) {
        FloatingTextBoxContent ft = new FloatingTextBoxContent(t.getBoxX(), t.getBoxY(), t.getBoxWidth(),
                t.isBorderVisible(), ColorUtil.toHex(t.getBorderColor()),
                t.isBackgroundVisible(), ColorUtil.toHex(t.getBackgroundColor()), t.getBackgroundOpacity(),
                t.encodeContent());
        ft.level = t.getLevel();
        ft.plainText = t.getEditor().getText();
        return ft;
    }

    public sealed interface FloatingContent permits FloatingImageContent, FloatingShapeContent,
            FloatingArrowContent, FloatingTableContent, FloatingTextBoxContent {
        Integer getLevel();

        FloatingContent translated(double dx, double dy);

        String signature();

        Layerable createOn(Page page, Consumer<RichTextArea> cellSetup);
    }

    private static String signature(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts)
            sb.append(p).append('|');
        return sb.toString();
    }

    public static final class ParagraphContent {
        public final boolean codeBlock;
        public TextAlignment alignment;
        public final List<RunContent> runs = new ArrayList<>();

        public ParagraphContent(boolean codeBlock) {
            this(codeBlock, TextAlignment.LEFT);
        }

        public ParagraphContent(boolean codeBlock, TextAlignment alignment) {
            this.codeBlock = codeBlock;
            this.alignment = alignment != null ? alignment : TextAlignment.LEFT;
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

    public static final class FloatingImageContent implements FloatingContent {
        public final double x, y, width, height;
        public final String format;
        public final String base64;
        public final double rotation;
        public Integer level;
        public double opacity = 1.0;

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

        public Integer getLevel() {
            return level;
        }

        @Override
        public FloatingImageContent translated(double dx, double dy) {
            FloatingImageContent c = new FloatingImageContent(x + dx, y + dy, width, height, format, base64, rotation);
            c.opacity = opacity;
            return c;
        }

        @Override
        public String signature() {
            return PageContent.signature("image", x, y, width, height, rotation, opacity, base64.length(),
                    base64.hashCode());
        }

        @Override
        public ImageOverlay createOn(Page page, Consumer<RichTextArea> cellSetup) {
            Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            ImageOverlay o = page.addImageOverlay(image, x, y, width, height, format, base64);
            o.setRotation(rotation);
            o.setImageOpacity(opacity);
            return o;
        }
    }

    public static final class FloatingShapeContent implements FloatingContent {
        public final ShapeType type;
        public final double x, y, width, height;
        public final String fillHex, strokeHex;
        public final double fillOpacity, strokeOpacity, strokeWidth, rotation;
        public Integer level;

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

        public Integer getLevel() {
            return level;
        }

        @Override
        public FloatingShapeContent translated(double dx, double dy) {
            return new FloatingShapeContent(type, x + dx, y + dy, width, height, fillHex, fillOpacity, strokeHex,
                    strokeOpacity, strokeWidth, rotation);
        }

        @Override
        public String signature() {
            return PageContent.signature("shape", type, x, y, width, height, fillHex, fillOpacity, strokeHex,
                    strokeOpacity, strokeWidth, rotation);
        }

        @Override
        public ShapeOverlay createOn(Page page, Consumer<RichTextArea> cellSetup) {
            ShapeOverlay o = page.addShapeOverlay(type, x, y, width, height);
            o.setFillColor(Color.web("#" + fillHex));
            o.setFillOpacity(fillOpacity);
            o.setStrokeColor(Color.web("#" + strokeHex));
            o.setStrokeOpacity(strokeOpacity);
            o.setStrokeWidth(strokeWidth);
            o.setRotation(rotation);
            return o;
        }
    }

    public static final class FloatingArrowContent implements FloatingContent {
        public final double startX, startY, endX, endY, controlX, controlY;
        public final String strokeHex;
        public final double strokeOpacity, strokeWidth;
        public Integer level;

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

        public Integer getLevel() {
            return level;
        }

        @Override
        public FloatingArrowContent translated(double dx, double dy) {
            return new FloatingArrowContent(startX + dx, startY + dy, endX + dx, endY + dy, controlX + dx,
                    controlY + dy, strokeHex, strokeOpacity, strokeWidth);
        }

        @Override
        public String signature() {
            return PageContent.signature("arrow", startX, startY, endX, endY, controlX, controlY, strokeHex,
                    strokeOpacity, strokeWidth);
        }

        @Override
        public ArrowOverlay createOn(Page page, Consumer<RichTextArea> cellSetup) {
            ArrowOverlay o = page.addArrowOverlay(startX, startY, endX, endY, controlX, controlY);
            o.setStrokeColor(Color.web("#" + strokeHex));
            o.setStrokeOpacity(strokeOpacity);
            o.setStrokeWidth(strokeWidth);
            return o;
        }
    }

    public static final class FloatingTableContent implements FloatingContent {
        public final double x, y;
        public final double[] colWidths, rowHeights;
        public final double[][] offX, offY;
        public final int[][] merges;
        public final String[][] cells;
        public Integer level;

        public FloatingTableContent(double x, double y, double[] colWidths, double[] rowHeights,
                double[][] offX, double[][] offY, int[][] merges, String[][] cells) {
            this.x = x;
            this.y = y;
            this.colWidths = colWidths;
            this.rowHeights = rowHeights;
            this.offX = offX;
            this.offY = offY;
            this.merges = merges;
            this.cells = cells;
        }

        public static FloatingTableContent empty() {
            return new FloatingTableContent(0, 0, new double[0], new double[0],
                    new double[0][0], new double[0][0], new int[0][0], new String[0][0]);
        }

        public Integer getLevel() {
            return level;
        }

        @Override
        public FloatingTableContent translated(double dx, double dy) {
            return new FloatingTableContent(x + dx, y + dy, colWidths, rowHeights, offX, offY, merges, cells);
        }

        @Override
        public String signature() {
            return PageContent.signature("table", x, y, Arrays.toString(colWidths), Arrays.toString(rowHeights),
                    Arrays.deepToString(offX), Arrays.deepToString(offY), Arrays.deepToString(merges),
                    Arrays.deepToString(cells));
        }

        @Override
        public TableOverlay createOn(Page page, Consumer<RichTextArea> cellSetup) {
            if (colWidths.length == 0 || rowHeights.length == 0)
                return null;
            TableOverlay o = page.addTableOverlay(x, y, rowHeights.length, colWidths.length, cellSetup);
            o.load(colWidths, rowHeights, offX, offY, merges, cells);
            return o;
        }
    }

    public static final class FloatingTextBoxContent implements FloatingContent {
        public final double x, y, width;
        public final boolean borderVisible, backgroundVisible;
        public final String borderHex, backgroundHex;
        public final double backgroundOpacity;
        public final String cells;
        public Integer level;
        public String plainText = "";

        public FloatingTextBoxContent(double x, double y, double width, boolean borderVisible, String borderHex,
                boolean backgroundVisible, String backgroundHex, double backgroundOpacity, String cells) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.borderVisible = borderVisible;
            this.borderHex = borderHex;
            this.backgroundVisible = backgroundVisible;
            this.backgroundHex = backgroundHex;
            this.backgroundOpacity = backgroundOpacity;
            this.cells = cells;
        }

        public Integer getLevel() {
            return level;
        }

        @Override
        public FloatingTextBoxContent translated(double dx, double dy) {
            FloatingTextBoxContent c = new FloatingTextBoxContent(x + dx, y + dy, width, borderVisible, borderHex,
                    backgroundVisible, backgroundHex, backgroundOpacity, cells);
            c.plainText = plainText;
            return c;
        }

        @Override
        public String signature() {
            return PageContent.signature("textbox", x, y, width, borderVisible, borderHex, backgroundVisible,
                    backgroundHex, backgroundOpacity, cells);
        }

        @Override
        public TextBoxOverlay createOn(Page page, Consumer<RichTextArea> cellSetup) {
            TextBoxOverlay o = page.addTextBoxOverlay(x, y, width, cellSetup);
            o.load(this);
            return o;
        }
    }
}
