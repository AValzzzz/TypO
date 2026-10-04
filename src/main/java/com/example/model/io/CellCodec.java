package com.example.model.io;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledSegment;
import org.reactfx.util.Either;

import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;
import com.example.view.RichTextArea;

import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public final class CellCodec {
    private static final String NULL = "-";

    private CellCodec() {
    }

    public static String encode(RichTextArea cell) {
        StringBuilder sb = new StringBuilder();
        boolean firstParagraph = true;
        for (Paragraph<ParagraphStyle, Either<String, MathObject>, TextStyle> p : cell.getParagraphs()) {
            if (!firstParagraph)
                sb.append('!');
            firstParagraph = false;
            sb.append(alignChar(ParagraphStyle.orDefault(p.getParagraphStyle()).alignment())).append('#');

            boolean firstRun = true;
            for (StyledSegment<Either<String, MathObject>, TextStyle> seg : p.getStyledSegments()) {
                if (!firstRun)
                    sb.append('&');
                firstRun = false;
                String style = encodeStyle(seg.getStyle());
                seg.getSegment().unify(
                        text -> sb.append("T:").append(style).append(':').append(b64(text)),
                        math -> sb.append("M:").append(style).append(':')
                                .append(b64(MathObjectCodec.encode(math))));
            }
        }
        return sb.toString();
    }

    public static void decode(String encoded, RichTextArea cell) {
        cell.clear();
        if (encoded == null || encoded.isEmpty())
            return;
        try {
            String[] paragraphs = encoded.split("!", -1);
            for (int i = 0; i < paragraphs.length; i++) {
                String p = paragraphs[i];
                int hash = p.indexOf('#');
                TextAlignment align = hash > 0 ? alignFrom(p.charAt(0)) : TextAlignment.LEFT;
                String runsPart = hash >= 0 ? p.substring(hash + 1) : p;

                for (String run : runsPart.split("&")) {
                    if (run.isEmpty())
                        continue;
                    String[] parts = run.split(":", 3);
                    TextStyle style = decodeStyle(parts[1]);
                    String payload = parts.length > 2 ? unb64(parts[2]) : "";
                    if (parts[0].equals("M")) {
                        if (MathObjectCodec.isToken(payload))
                            cell.appendMathObject(MathObjectCodec.decode(payload), style);
                    } else if (!payload.isEmpty()) {
                        cell.appendStyledText(payload, style);
                    }
                }
                cell.setParagraphStyle(cell.getParagraphs().size() - 1, new ParagraphStyle(null, align));
                if (i < paragraphs.length - 1)
                    cell.appendStyledText("\n", TextStyle.DEFAULT);
            }
        } catch (RuntimeException e) {
            cell.clear();
        }
    }

    private static String encodeStyle(TextStyle s) {
        return String.join(",",
                flag(s.bold()), flag(s.italic()), flag(s.strikethrough()), flag(s.underline()),
                color(s.underlineColor()), flag(s.underlineDotted()), color(s.highlight()), color(s.textColor()),
                s.fontSize() == null ? NULL : String.valueOf(s.fontSize()),
                s.baselineShift() == null ? NULL : String.valueOf(s.baselineShift()));
    }

    private static TextStyle decodeStyle(String encoded) {
        String[] f = encoded.split(",", -1);
        return TextStyle.DEFAULT
                .withBold(f[0].equals("1"))
                .withItalic(f[1].equals("1"))
                .withStrikethrough(f[2].equals("1"))
                .withUnderline(f[3].equals("1"))
                .withUnderlineColor(parseColor(f[4]))
                .withUnderlineDotted(f[5].equals("1"))
                .withHighlight(parseColor(f[6]))
                .withTextColor(parseColor(f[7]))
                .withFontSize(f[8].equals(NULL) ? null : Integer.valueOf(f[8]))
                .withBaselineShift(f[9].equals(NULL) ? null : Double.valueOf(f[9]));
    }

    private static String flag(boolean b) {
        return b ? "1" : "0";
    }

    private static String color(Color c) {
        return c == null ? NULL : ColorUtil.toHex(c);
    }

    private static Color parseColor(String s) {
        return NULL.equals(s) ? null : ColorUtil.fromHex(s);
    }

    private static char alignChar(TextAlignment a) {
        return switch (a) {
            case CENTER -> 'C';
            case RIGHT -> 'R';
            case JUSTIFY -> 'J';
            default -> 'L';
        };
    }

    private static TextAlignment alignFrom(char c) {
        return switch (c) {
            case 'C' -> TextAlignment.CENTER;
            case 'R' -> TextAlignment.RIGHT;
            case 'J' -> TextAlignment.JUSTIFY;
            default -> TextAlignment.LEFT;
        };
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}