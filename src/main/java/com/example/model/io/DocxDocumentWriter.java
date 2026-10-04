package com.example.model.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STVerticalAlignRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;

import com.example.model.TextStyle;
import com.example.model.io.PageContent.ParagraphContent;
import com.example.model.io.PageContent.RunContent;
import com.example.model.language.maths.MathObject;
import com.example.model.settings.CodeTheme;

import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public final class DocxDocumentWriter {

    private static final long PAGE_LONG = 15840;
    private static final long PAGE_SHORT = 12240;
    private static final String CODE_BLOCK_BG = "1E1E1E";
    private static final String CODE_BLOCK_FG = "F6F6F6";
    private static final String CODE_FONT = "Consolas";

    public void write(List<PageContent> pages, Path target) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (int i = 0; i < pages.size(); i++) {
                writePage(doc, pages.get(i), i == 0, i == pages.size() - 1);
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                doc.write(out);
            }
        }
    }

    private void writePage(XWPFDocument doc, PageContent page, boolean firstPage, boolean lastPage) {
        for (ParagraphContent paragraph : page.paragraphs) {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignments.toPoi(paragraph.alignment));
            if (paragraph.codeBlock) {
                setParagraphShading(p, CODE_BLOCK_BG);
            }

            if (paragraph.runs.isEmpty()) {
                p.createRun();
            } else {
                for (RunContent run : paragraph.runs) {
                    if (run.isMath()) {
                        writeMathRun(p, run);
                    } else {
                        writeTextRun(p, run.text, run.style, paragraph.codeBlock);
                    }
                }
            }
        }

        for (PageContent.FloatingImageContent img : page.images) {
            writeFloatingImageParagraph(doc, img);
        }

        for (PageContent.FloatingShapeContent shape : page.shapes) {
            writeFloatingShapeParagraph(doc, shape);
        }

        for (PageContent.FloatingArrowContent arrow : page.arrows) {
            writeFloatingArrowParagraph(doc, arrow);
        }

        for (PageContent.FloatingTableContent table : page.tables) {
            writeFloatingTableParagraph(doc, table);
        }

        for (PageContent.FloatingTextBoxContent box : page.textBoxes) {
            writeFloatingTextBoxParagraph(doc, box);
        }

        CTSectPr sectPr;
        if (lastPage) {
            CTBody body = doc.getDocument().getBody();
            sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        } else {
            XWPFParagraph marker = doc.createParagraph();
            sectPr = marker.getCTP().addNewPPr().addNewSectPr();
            sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        }

        if (firstPage && page.showPageNumbers) {
            writePageNumberFooter(doc, sectPr);
        }

        CTPageSz pageSz = sectPr.addNewPgSz();
        if (page.landscape) {
            pageSz.setOrient(STPageOrientation.LANDSCAPE);
            pageSz.setW(PAGE_LONG);
            pageSz.setH(PAGE_SHORT);
        } else {
            pageSz.setW(PAGE_SHORT);
            pageSz.setH(PAGE_LONG);
        }
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setLeft(cmToTwips(page.marginLeftCm));
        mar.setRight(cmToTwips(page.marginRightCm));
        mar.setTop(cmToTwips(page.marginTopCm));
        mar.setBottom(cmToTwips(page.marginBottomCm));
        mar.setHeader(720L);
        mar.setFooter(page.showPageNumbers ? 300L : 720L);
        mar.setGutter(0L);
    }

    private void writeTextRun(XWPFParagraph p, String text, TextStyle style, boolean codeBlockParagraph) {
        XWPFRun run = (style != null && style.link() != null)
                ? p.createHyperlinkRun(style.link())
                : p.createRun();
        run.setText(text == null ? "" : text);
        applyStyle(run, style, codeBlockParagraph);
    }

    private void writeMathRun(XWPFParagraph p, RunContent run) {
        XWPFRun visible = p.createRun();
        visible.setText(MathObjectCodec.approximate(run.math));
        applyStyle(visible, run.style, false);

        addHiddenRun(p, MathObjectCodec.encode(run.math));
    }

    private void writeFloatingImageParagraph(XWPFDocument doc, PageContent.FloatingImageContent img) {
        XWPFParagraph p = doc.createParagraph();

        int pictureType = (img.format.equals("jpg") || img.format.equals("jpeg"))
                ? Document.PICTURE_TYPE_JPEG
                : Document.PICTURE_TYPE_PNG;

        byte[] bytes = Base64.getDecoder().decode(img.base64);
        XWPFRun visible = p.createRun();
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            int widthEmu = Units.pixelToEMU((int) img.width);
            int heightEmu = Units.pixelToEMU((int) img.height);
            visible.addPicture(is, pictureType, "image." + img.format, widthEmu, heightEmu);
        } catch (Exception e) {
            visible.setText("[image]");
        }

        addHiddenRun(p, FloatingImageCodec.encode(img.x, img.y, img.width, img.height, img.rotation,
                img.level, img.opacity));
        addHiddenRun(p, MathObjectCodec.encode(new MathObject(MathObject.Type.IMAGE, img.format + "|" + img.base64)));
    }

    private void writeFloatingShapeParagraph(XWPFDocument doc, PageContent.FloatingShapeContent shape) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun();
        addHiddenRun(p, FloatingShapeCodec.encode(shape));
    }

    private void writePageNumberFooter(XWPFDocument doc, CTSectPr sectPr) {
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(doc, sectPr);
        XWPFFooter footer = policy.createFooter(STHdrFtr.DEFAULT);

        XWPFParagraph p = footer.getParagraphs().isEmpty()
                ? footer.createParagraph()
                : footer.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);

        CTSimpleField field = p.getCTP().addNewFldSimple();
        field.setInstr("PAGE");
        field.addNewR().addNewT().setStringValue("1");

        addHiddenRun(p, PageNumberCodec.encode());
    }

    private void writeFloatingTableParagraph(XWPFDocument doc, PageContent.FloatingTableContent table) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun();
        addHiddenRun(p, FloatingTableCodec.encode(table));
    }

    private PageContent.FloatingTableContent tryReadFloatingTable(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (isHidden(run) && FloatingTableCodec.isToken(text)) {
                try {
                    return FloatingTableCodec.decode(text);
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private PageContent.FloatingShapeContent tryReadFloatingShape(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (isHidden(run) && FloatingShapeCodec.isToken(text))
                return FloatingShapeCodec.decode(text);
        }
        return null;
    }

    private void writeFloatingArrowParagraph(XWPFDocument doc, PageContent.FloatingArrowContent arrow) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun();
        addHiddenRun(p, FloatingArrowCodec.encode(arrow));
    }

    private void writeFloatingTextBoxParagraph(XWPFDocument doc, PageContent.FloatingTextBoxContent box) {
        XWPFParagraph p = doc.createParagraph();
        String visible = box.plainText == null ? "" : box.plainText.replace("\uFFFC", "").replace('\n', ' ');
        p.createRun().setText(visible);
        addHiddenRun(p, FloatingTextBoxCodec.encode(box));
    }

    private PageContent.FloatingTextBoxContent tryReadFloatingTextBox(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (isHidden(run) && FloatingTextBoxCodec.isToken(text)) {
                try {
                    return FloatingTextBoxCodec.decode(text);
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private PageContent.FloatingArrowContent tryReadFloatingArrow(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (isHidden(run) && FloatingArrowCodec.isToken(text))
                return FloatingArrowCodec.decode(text);
        }
        return null;
    }

    private void applyStyle(XWPFRun run, TextStyle style, boolean codeBlockParagraph) {
        if (style == null) {
            return;
        }
        run.setBold(style.bold());
        run.setItalic(style.italic());
        run.setStrikeThrough(style.strikethrough());

        if (style.underline()) {
            run.setUnderline(style.underlineDotted() ? UnderlinePatterns.DOTTED : UnderlinePatterns.SINGLE);
            String uColor = ColorUtil.toHex(style.underlineColor());
            if (uColor != null) {
                run.setUnderlineColor(uColor);
            }
        }

        if (style.highlight() != null) {
            setRunShading(run, ColorUtil.toHex(style.highlight()));
        }

        boolean mono = style.codeBlock() || codeBlockParagraph;
        if (mono) {
            run.setFontFamily(CODE_FONT);
        }

        if (style.textColor() != null) {
            Color exported = mono ? CodeTheme.toDarkPalette(style.textColor()) : style.textColor();
            run.setColor(ColorUtil.toHex(exported));
        } else if (mono) {
            run.setColor(CODE_BLOCK_FG);
        }

        if (style.fontSize() != null) {
            run.setFontSize(style.fontSize());
        }
        if (style.baselineShift() != null) {
            run.setSubscript(style.baselineShift() < 0 ? VerticalAlign.SUBSCRIPT : VerticalAlign.SUPERSCRIPT);
        }

        if (style.link() != null) {
            run.setColor("3588DB");
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
    }

    private void setParagraphShading(XWPFParagraph p, String hex) {
        CTShd shd = p.getCTP().isSetPPr()
                ? p.getCTP().getPPr().addNewShd()
                : p.getCTP().addNewPPr().addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setColor("auto");
        shd.setFill(hex);
    }

    private void setRunShading(XWPFRun run, String hex) {
        CTShd shd = rPr(run).addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setColor("auto");
        shd.setFill(hex);
    }

    private static CTRPr rPr(XWPFRun run) {
        return run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
    }

    private static void addHiddenRun(XWPFParagraph p, String text) {
        XWPFRun run = p.createRun();
        run.setText(text);
        CTRPr rpr = rPr(run);
        if (rpr.sizeOfVanishArray() == 0)
            rpr.addNewVanish();
        run.setFontSize(1);
    }

    private boolean isHidden(XWPFRun run) {
        return run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfVanishArray() > 0;
    }

    public List<PageContent> read(Path source) throws IOException {
        List<PageContent> pages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(source);
                XWPFDocument doc = new XWPFDocument(in)) {

            List<ParagraphContent> current = new ArrayList<>();
            List<PageContent.FloatingImageContent> currentImages = new ArrayList<>();
            List<PageContent.FloatingShapeContent> currentShapes = new ArrayList<>();
            List<PageContent.FloatingArrowContent> currentArrows = new ArrayList<>();
            List<PageContent.FloatingTableContent> currentTables = new ArrayList<>();
            List<PageContent.FloatingTextBoxContent> currentTextBoxes = new ArrayList<>();

            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                CTSectPr sectPr = (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr())
                        ? paragraph.getCTP().getPPr().getSectPr()
                        : null;

                PageContent.FloatingImageContent floatingImage = tryReadFloatingImage(paragraph);
                PageContent.FloatingShapeContent floatingShape = floatingImage == null
                        ? tryReadFloatingShape(paragraph)
                        : null;
                PageContent.FloatingArrowContent floatingArrow = (floatingImage == null && floatingShape == null)
                        ? tryReadFloatingArrow(paragraph)
                        : null;
                PageContent.FloatingTableContent floatingTable = (floatingImage == null && floatingShape == null
                        && floatingArrow == null) ? tryReadFloatingTable(paragraph) : null;
                PageContent.FloatingTextBoxContent floatingTextBox = (floatingImage == null && floatingShape == null
                        && floatingArrow == null && floatingTable == null) ? tryReadFloatingTextBox(paragraph) : null;

                boolean boundaryOnly = sectPr != null && paragraph.getRuns().isEmpty();

                if (floatingImage != null)
                    currentImages.add(floatingImage);
                else if (floatingShape != null)
                    currentShapes.add(floatingShape);
                else if (floatingArrow != null)
                    currentArrows.add(floatingArrow);
                else if (floatingTable != null)
                    currentTables.add(floatingTable);
                else if (!boundaryOnly)
                    current.add(readParagraph(paragraph));
                else if (floatingTextBox != null)
                    currentTextBoxes.add(floatingTextBox);

                if (sectPr != null) {
                    PageContent pc = finish(current, sectPr);
                    pc.images.addAll(currentImages);
                    pc.shapes.addAll(currentShapes);
                    pc.arrows.addAll(currentArrows);
                    pc.tables.addAll(currentTables);
                    pc.textBoxes.addAll(currentTextBoxes);
                    pages.add(pc);
                    current = new ArrayList<>();
                    currentImages = new ArrayList<>();
                    currentShapes = new ArrayList<>();
                    currentArrows = new ArrayList<>();
                    currentTables = new ArrayList<>();
                    currentTextBoxes = new ArrayList<>();
                }
            }

            CTBody body = doc.getDocument().getBody();
            CTSectPr bodySectPr = body.isSetSectPr() ? body.getSectPr() : null;
            if (!current.isEmpty() || !currentImages.isEmpty() || !currentShapes.isEmpty()
                    || !currentArrows.isEmpty() || !currentTables.isEmpty() || pages.isEmpty()) {
                PageContent pc = finish(current, bodySectPr);
                pc.images.addAll(currentImages);
                pc.shapes.addAll(currentShapes);
                pc.arrows.addAll(currentArrows);
                pc.tables.addAll(currentTables);
                pc.textBoxes.addAll(currentTextBoxes);
                pc.textBoxes.addAll(currentTextBoxes);
                pages.add(pc);
            }
            if (!pages.isEmpty() && readPageNumbersFlag(doc))
                pages.get(0).showPageNumbers = true;
        }

        return pages;
    }

    private PageContent.FloatingImageContent tryReadFloatingImage(XWPFParagraph paragraph) {
        String posToken = null;
        String dataToken = null;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (isHidden(run) && FloatingImageCodec.isToken(text))
                posToken = text;
            else if (isHidden(run) && MathObjectCodec.isToken(text))
                dataToken = text;
        }
        if (posToken == null || dataToken == null)
            return null;

        double[] pos = FloatingImageCodec.decode(posToken);
        MathObject obj = MathObjectCodec.decode(dataToken);
        if (obj.getType() != MathObject.Type.IMAGE)
            return null;

        String raw = obj.getRaw();
        int sep = raw.indexOf('|');
        PageContent.FloatingImageContent image = new PageContent.FloatingImageContent(pos[0], pos[1], pos[2], pos[3],
                raw.substring(0, sep), raw.substring(sep + 1), pos[4]);
        image.level = (int) pos[5];
        image.opacity = pos[6];
        return image;
    }

    private PageContent finish(List<ParagraphContent> paragraphs, CTSectPr sectPr) {
        boolean landscape = sectPr != null && sectPr.isSetPgSz()
                && sectPr.getPgSz().getOrient() == STPageOrientation.LANDSCAPE;
        PageContent content = new PageContent(landscape);
        if (sectPr != null && sectPr.isSetPgMar()) {
            CTPageMar m = sectPr.getPgMar();
            content.marginLeftCm = twipsToCm(m.getLeft(), content.marginLeftCm);
            content.marginRightCm = twipsToCm(m.getRight(), content.marginRightCm);
            content.marginTopCm = twipsToCm(m.getTop(), content.marginTopCm);
            content.marginBottomCm = twipsToCm(m.getBottom(), content.marginBottomCm);
        }
        content.paragraphs.addAll(paragraphs);
        return content;
    }

    private ParagraphContent readParagraph(XWPFParagraph paragraph) {
        boolean codeBlock = CODE_BLOCK_BG.equalsIgnoreCase(paragraphShadingHex(paragraph));
        ParagraphContent content = new ParagraphContent(codeBlock);
        content.alignment = codeBlock
                ? TextAlignment.LEFT
                : ParagraphAlignments.fromPoi(paragraph.getAlignment());

        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = 0; i < runs.size(); i++) {
            XWPFRun run = runs.get(i);
            XWPFRun next = i + 1 < runs.size() ? runs.get(i + 1) : null;

            if (next != null && isHidden(next) && MathObjectCodec.isToken(next.text())) {
                content.runs.add(RunContent.math(MathObjectCodec.decode(next.text()), readStyle(run, codeBlock)));
                i++;
                continue;
            }
            if (isHidden(run) && MathObjectCodec.isToken(run.text())) {
                content.runs.add(RunContent.math(MathObjectCodec.decode(run.text()), readStyle(run, codeBlock)));
                continue;
            }
            content.runs.add(RunContent.text(run.text() != null ? run.text() : "", readStyle(run, codeBlock)));
        }
        return content;
    }

    private TextStyle readStyle(XWPFRun run, boolean codeBlock) {
        TextStyle style = TextStyle.DEFAULT
                .withBold(run.isBold())
                .withItalic(run.isItalic())
                .withStrikethrough(run.isStrikeThrough())
                .withCodeBlock(codeBlock);

        boolean isLink = run instanceof XWPFHyperlinkRun;
        UnderlinePatterns underline = run.getUnderline();
        if (!isLink && underline != null && underline != UnderlinePatterns.NONE) {
            style = style.withUnderline(true).withUnderlineDotted(underline == UnderlinePatterns.DOTTED);
            String uColor = null;
            if (run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfUArray() > 0) {
                Object colorVal = run.getCTR().getRPr().getUArray(0).getColor();
                uColor = hexColorToString(colorVal);
            }
            style = style.withUnderlineColor(ColorUtil.fromHex(uColor));
        }

        String shadingHex = runShadingHex(run);
        if (shadingHex != null && !CODE_BLOCK_BG.equalsIgnoreCase(shadingHex))
            style = style.withHighlight(ColorUtil.fromHex(shadingHex));

        String color = run.getColor();
        if (!isLink && color != null && !(codeBlock && CODE_BLOCK_FG.equalsIgnoreCase(color)))
            style = style.withTextColor(ColorUtil.fromHex(color));

        Double fontSize = run.getFontSizeAsDouble();
        if (fontSize != null)
            style = style.withFontSize((int) Math.round(fontSize));

        STVerticalAlignRun.Enum align = run.getVerticalAlignment();
        if (align == STVerticalAlignRun.SUPERSCRIPT)
            style = style.withBaselineShift(4.0);
        else if (align == STVerticalAlignRun.SUBSCRIPT)
            style = style.withBaselineShift(-4.0);

        return style;
    }

    private boolean readPageNumbersFlag(XWPFDocument doc) {
        for (XWPFFooter footer : doc.getFooterList())
            for (XWPFParagraph p : footer.getParagraphs())
                for (XWPFRun run : p.getRuns())
                    if (isHidden(run) && PageNumberCodec.isToken(run.text()))
                        return true;
        return false;
    }

    private String hexColorToString(Object val) {
        if (val == null)
            return null;
        if (val instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }
        return val.toString();
    }

    private String paragraphShadingHex(XWPFParagraph p) {
        if (p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetShd())
            return hexColorToString(p.getCTP().getPPr().getShd().getFill());
        return null;
    }

    private String runShadingHex(XWPFRun run) {
        if (run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfShdArray() > 0)
            return hexColorToString(run.getCTR().getRPr().getShdArray(0).getFill());
        return null;
    }

    private static long cmToTwips(double cm) {
        return Math.round(cm * 1440.0 / 2.54);
    }

    private static double twipsToCm(Object twips, double fallback) {
        if (twips == null)
            return fallback;
        try {
            return Double.parseDouble(twips.toString()) * 2.54 / 1440.0;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}