package com.example.model.language.maths;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class MathNodeFactory {
    private MathNodeFactory() {
    }

    public static Node fraction(String numerator, String denominator) {
        Label num = new Label(numerator);
        Label den = new Label(denominator);

        num.getStyleClass().add("math-fraction-part");
        den.getStyleClass().add("math-fraction-part");

        Region bar = new Region();
        bar.setPrefHeight(1);
        bar.setStyle("-fx-background-color: black;");

        VBox box = new VBox(num, bar, den);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("math-fraction");
        return box;
    }

    public static Node exponent (String base, String exponent) {
        Label baseLabel = new Label(base);
        Label expLabel = new Label(exponent);
        expLabel.setStyle("-fx-font-size:10px;");
        expLabel.setTranslateY(-6);

        HBox box = new HBox(baseLabel, expLabel);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    public static Node subscript(String base, String sub) {
        Label baseLabel = new Label(base);
        Label subLabel = new Label(sub);

        subLabel.setStyle("-fx-font-size: 10px;");
        subLabel.setTranslateY(4);

        HBox box = new HBox(baseLabel, subLabel);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    public static Node sqrt(String content) {
        Label root = new Label("\u221A");
        Label inner = new Label(content);
        inner.setStyle("-fx-border-color: black; -fx-border-width:1 0 0 0;");

        HBox box = new HBox(root, inner);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    public static Node matrix(String content) {
        String[] rows = content.split(";");
        GridPane grid = new GridPane();

        grid.setHgap(10);
        grid.setVgap(4);

        for (int r = 0; r < rows.length; r++) {
            String[] cols = rows[r].split(",");
            for (int c = 0; c < cols.length; c++) {
                grid.add(new Label(cols[c].trim()), c, r);
            }
        }

        Label left = new Label("[");
        Label right = new Label("]");
        HBox box = new HBox(left, grid, right);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}