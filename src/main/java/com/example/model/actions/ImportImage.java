package com.example.model.actions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import com.example.model.Page;

import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class ImportImage implements AppAction {
    private final Page page;
    private final Window owner;

    public ImportImage(Page page, Window owner) {
        this.page = page;
        this.owner = owner;
    }

    @Override
    public void execute() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer une image");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("PNG", "*.png"),
            new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String format = extensionOf(file.getName());
            String base64 = Base64.getEncoder().encodeToString(bytes);

            Image image = new Image(new ByteArrayInputStream(bytes));
            double width = Math.min(image.getWidth(), 250);
            double height = width / (image.getWidth() / image.getHeight());

            double startX = (page.getPane().getWidth() - width) / 2;
            double startY = (page.getPane().getHeight() - height) / 2;

            page.addImageOverlay(image, startX, startY, width, height, format, base64);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "png";
        return ext.equals("jpeg") ? "jpg" : ext;
    }
}