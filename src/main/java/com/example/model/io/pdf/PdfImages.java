package com.example.model.io.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.example.view.ImageOverlay;
import com.example.view.Nodes;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

final class PdfImages {
    private final PDDocument doc;
    private final Map<Image, PDImageXObject> cache = new IdentityHashMap<>();

    PdfImages(PDDocument doc) {
        this.doc = doc;
    }

    PDImageXObject of(ImageView iv) throws IOException {
        Image image = iv.getImage();
        Rectangle2D vp = iv.getViewport();
        if (vp == null && cache.containsKey(image))
            return cache.get(image);
        PDImageXObject x = vp == null ? originalJpeg(iv) : null;
        if (x == null)
            x = lossless(image, vp);
        if (x != null && vp == null)
            cache.put(image, x);
        return x;
    }

    private PDImageXObject originalJpeg(ImageView iv) {
        ImageOverlay o = Nodes.ancestor(iv, ImageOverlay.class);
        if (o == null || o.getBase64() == null || o.getFormat() == null
                || !o.getFormat().toLowerCase().matches("jpe?g"))
            return null;
        try {
            return JPEGFactory.createFromByteArray(doc, Base64.getDecoder().decode(o.getBase64()));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private PDImageXObject lossless(Image image, Rectangle2D vp) throws IOException {
        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
        if (buffered == null)
            return null;
        if (vp != null) {
            int x = clamp(vp.getMinX(), buffered.getWidth()), y = clamp(vp.getMinY(), buffered.getHeight());
            buffered = buffered.getSubimage(x, y,
                    Math.max(1, clamp(vp.getWidth(), buffered.getWidth() - x)),
                    Math.max(1, clamp(vp.getHeight(), buffered.getHeight() - y)));
        }
        return LosslessFactory.createFromImage(doc, buffered);
    }

    private static int clamp(double v, int max) {
        return (int) Math.max(0, Math.min(max, Math.round(v)));
    }
}
