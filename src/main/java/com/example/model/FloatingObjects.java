package com.example.model;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

import com.example.model.io.PageContent;
import com.example.model.io.PageContent.FloatingArrowContent;
import com.example.model.io.PageContent.FloatingImageContent;
import com.example.model.io.PageContent.FloatingShapeContent;
import com.example.model.io.PageContent.FloatingTableContent;
import com.example.model.io.PageContent.FloatingTextBoxContent;
import com.example.view.ArrowOverlay;
import com.example.view.ImageOverlay;
import com.example.view.Layerable;
import com.example.view.RichTextArea;
import com.example.view.ShapeOverlay;
import com.example.view.TableOverlay;
import com.example.view.TextBoxOverlay;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public final class FloatingObjects {
    private FloatingObjects() {
    }

    public static Object capture(Layerable layer) {
        return switch (layer) {
            case ImageOverlay o -> PageContent.capture(o);
            case ShapeOverlay o -> PageContent.capture(o);
            case ArrowOverlay o -> PageContent.capture(o);
            case TableOverlay o -> PageContent.capture(o);
            case TextBoxOverlay o -> PageContent.capture(o);
            default -> throw new IllegalArgumentException("Unknown floating object: " + layer);
        };
    }

    public static Layerable create(Page page, Object content, Consumer<RichTextArea> cellSetup) {
        switch (content) {
            case FloatingImageContent img -> {
                byte[] bytes = Base64.getDecoder().decode(img.base64);
                Image image = new Image(new ByteArrayInputStream(bytes));
                ImageOverlay o = page.addImageOverlay(image, img.x, img.y, img.width, img.height, img.format,
                        img.base64);
                o.setRotation(img.rotation);
                o.setImageOpacity(img.opacity);
                return o;
            }
            case FloatingShapeContent s -> {
                ShapeOverlay o = page.addShapeOverlay(s.type, s.x, s.y, s.width, s.height);
                o.setFillColor(Color.web("#" + s.fillHex));
                o.setFillOpacity(s.fillOpacity);
                o.setStrokeColor(Color.web("#" + s.strokeHex));
                o.setStrokeOpacity(s.strokeOpacity);
                o.setStrokeWidth(s.strokeWidth);
                o.setRotation(s.rotation);
                return o;
            }
            case FloatingArrowContent a -> {
                ArrowOverlay o = page.addArrowOverlay(a.startX, a.startY, a.endX, a.endY, a.controlX, a.controlY);
                o.setStrokeColor(Color.web("#" + a.strokeHex));
                o.setStrokeOpacity(a.strokeOpacity);
                o.setStrokeWidth(a.strokeWidth);
                return o;
            }
            case FloatingTableContent t -> {
                if (t.colWidths.length == 0 || t.rowHeights.length == 0)
                    return null;
                TableOverlay o = page.addTableOverlay(t.x, t.y, t.rowHeights.length, t.colWidths.length, cellSetup);
                o.load(t.colWidths, t.rowHeights, t.offX, t.offY, t.merges, t.cells);
                return o;
            }
            case FloatingTextBoxContent t -> {
                TextBoxOverlay o = page.addTextBoxOverlay(t.x, t.y, t.width, cellSetup);
                o.load(t);
                return o;
            }
            default -> throw new IllegalArgumentException("Unknown floating content: " + content);
        }
    }

    public static Object translate(Object content, double dx, double dy) {
        return switch (content) {
            case FloatingImageContent i -> {
                FloatingImageContent c = new FloatingImageContent(i.x + dx, i.y + dy, i.width, i.height, i.format,
                        i.base64, i.rotation);
                c.opacity = i.opacity;
                yield c;
            }
            case FloatingShapeContent s -> new FloatingShapeContent(s.type, s.x + dx, s.y + dy, s.width, s.height,
                    s.fillHex, s.fillOpacity, s.strokeHex, s.strokeOpacity, s.strokeWidth, s.rotation);
            case FloatingArrowContent a -> new FloatingArrowContent(a.startX + dx, a.startY + dy, a.endX + dx,
                    a.endY + dy, a.controlX + dx, a.controlY + dy, a.strokeHex, a.strokeOpacity, a.strokeWidth);
            case FloatingTableContent t -> new FloatingTableContent(t.x + dx, t.y + dy, t.colWidths, t.rowHeights,
                    t.offX, t.offY, t.merges, t.cells);
            case FloatingTextBoxContent t -> {
                FloatingTextBoxContent c = new FloatingTextBoxContent(t.x + dx, t.y + dy, t.width, t.borderVisible,
                        t.borderHex, t.backgroundVisible, t.backgroundHex, t.backgroundOpacity, t.cells);
                c.plainText = t.plainText;
                yield c;
            }
            default -> throw new IllegalArgumentException("Unknown floating content: " + content);
        };
    }

    public static String signature(Object content) {
        return switch (content) {
            case FloatingImageContent i -> join("image", i.x, i.y, i.width, i.height, i.rotation, i.opacity,
                    i.base64.length(), i.base64.hashCode());
            case FloatingShapeContent s -> join("shape", s.type, s.x, s.y, s.width, s.height, s.fillHex,
                    s.fillOpacity, s.strokeHex, s.strokeOpacity, s.strokeWidth, s.rotation);
            case FloatingArrowContent a -> join("arrow", a.startX, a.startY, a.endX, a.endY, a.controlX,
                    a.controlY, a.strokeHex, a.strokeOpacity, a.strokeWidth);
            case FloatingTableContent t -> join("table", t.x, t.y, Arrays.toString(t.colWidths),
                    Arrays.toString(t.rowHeights), Arrays.deepToString(t.offX), Arrays.deepToString(t.offY),
                    Arrays.deepToString(t.merges), Arrays.deepToString(t.cells));
            case FloatingTextBoxContent t -> join("textbox", t.x, t.y, t.width, t.borderVisible, t.borderHex,
                    t.backgroundVisible, t.backgroundHex, t.backgroundOpacity, t.cells);
            default -> throw new IllegalArgumentException("Unknown floating content: " + content);
        };
    }

    private static String join(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts)
            sb.append(p).append('|');
        return sb.toString();
    }

    public static void setSelected(Layerable layer, boolean selected) {
        switch (layer) {
            case ImageOverlay o -> o.setSelected(selected);
            case ShapeOverlay o -> o.setSelected(selected);
            case ArrowOverlay o -> o.setSelected(selected);
            case TextBoxOverlay o -> o.setSelected(selected);
            default -> {
            }
        }
    }
}
