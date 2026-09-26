package com.example.model.language.maths;

import java.util.Locale;

import com.example.model.TextStyle;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

public class MathNodeFactory {
    private MathNodeFactory() {}

    private static Label styledLabel(String text, TextStyle style) {
        Label label = new Label(text);
        if (style != null) {
            label.setStyle(style.toCss());
            if (style.textColor() != null) 
                label.setStyle(label.getStyle() + "-fx-text-fill: " + toRgb(style.textColor()));
        } 
        return label;
    }

    private static Color barColor(TextStyle style) {
        return (style != null && style.textColor() != null) ? style.textColor() : Color.BLACK;
    }

    public static Node fraction(String numerator, String denominator, TextStyle style) {
        Label num = styledLabel(numerator, style);
        Label den = styledLabel(denominator, style);

        num.getStyleClass().add("math-fraction-part");
        den.getStyleClass().add("math-fraction-part");

        Region bar = new Region();
        bar.setPrefHeight(1);
        bar.setStyle("-fx-background-color: " + toRgb(barColor(style)) + ";");

        VBox box = new VBox(num, bar, den);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("math-fraction");
        return box;
    }

    public static Node exponent (String base, String exponent, TextStyle style) {
        int baseSize = (style != null && style.fontSize() != null) ? style.fontSize() : 12;
        double expSize = Math.max(6, baseSize * 0.65);

        Label baseLabel = styledLabel(base, style);
        Label expLabel = styledLabel(exponent, style);
        expLabel.setStyle("-fx-font-size: " + expSize + "px;");
        expLabel.setTranslateY(-baseSize * 0.35);

        HBox box = new HBox(baseLabel, expLabel);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    public static Node subscript(String base, String sub, TextStyle style) {
        int baseSize = (style != null && style.fontSize() != null) ? style.fontSize() : 12;
        double expSize = Math.max(6, baseSize * 0.65);

        Label baseLabel = styledLabel(base, style);
        Label subLabel = styledLabel(sub, style);

        subLabel.setStyle("-fx-font-size: "+expSize+"px;");
        subLabel.setTranslateY(baseSize * 0.5);

        HBox box = new HBox(baseLabel, subLabel);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    public static Node sqrt(String content, TextStyle style) {
        Label inner = styledLabel(content, style);
        inner.setStyle(inner.getStyle() + "-fx-padding: 2 2 2 2;");

        Path radical = new Path();
        radical.setStroke(barColor(style));
        radical.setStrokeWidth(1.5);
        radical.setFill(null);

        Region bar = new Region();
        bar.setStyle("-fx-background-color: " + toRgb(barColor(style)) + ";");
        bar.setPrefHeight(1.5);
        bar.setMaxHeight(1.5);
        bar.prefWidthProperty().bind(inner.widthProperty());

        VBox rightSide = new VBox(0,bar, inner);
        
        HBox box = new HBox(0, radical, rightSide);
        box.setAlignment(Pos.BOTTOM_LEFT);

        Runnable rebuild = () -> {
            double h = inner.getHeight() > 0 ? inner.getHeight() : inner.prefHeight(-1);

            double hookWidth = 6;
            radical.getElements().setAll(
                new MoveTo(0,h*0.6),
                new LineTo(hookWidth * 0.5, h),
                new LineTo(hookWidth,0)
            );
        };

        inner.heightProperty().addListener((obs,o,n) -> rebuild.run());
        Platform.runLater(rebuild);
        box.setTranslateY(9);
        return box;
    }

    public static Node matrix(String content, TextStyle style) {
        String[] rows = content.split(";");
        GridPane grid = new GridPane();

        grid.setHgap(10);
        grid.setVgap(4);

        for (int r = 0; r < rows.length; r++) {
            String[] cols = rows[r].split(",");
            for (int c = 0; c < cols.length; c++) {
                grid.add(styledLabel(cols[c].trim(), style), c, r);
            }
        }

        Node left = bracket(true, barColor(style));
        Node right = bracket (false, barColor(style));

        HBox box = new HBox(4, left, grid, right);
        box.setAlignment(Pos.CENTER);

        grid.heightProperty().addListener((obs, oldH, newH) -> {
            resizeBracket(left, newH.doubleValue());
            resizeBracket(right, newH.doubleValue());
        });

        return box;
    }

    private static Node bracket(boolean isLeft, Color color) {
        Path path = new Path();
        path.setStroke(color);
        path.setStrokeWidth(1.5);
        double tick = 5;
        double x0 = isLeft ? tick : 0;
        double x1 = isLeft ? 0 : tick;

        MoveTo start = new MoveTo(x0,0);
        LineTo topCorner = new LineTo(x1,0);
        LineTo bottomCorner = new LineTo(x1, 20);
        LineTo end = new LineTo(x0, 20);
    
        path.getElements().addAll(start, topCorner, bottomCorner, end);
        path.getProperties().put("bottomCorner", bottomCorner);
        path.getProperties().put("end", end);
        return path;
    }

    private static void resizeBracket(Node bracketNode, double height) {
        Path path = (Path) bracketNode;
        ((LineTo) path.getProperties().get("bottomCorner")).setY(height);
        ((LineTo) path.getProperties().get("end")).setY(height);
    }

    private static String toRgb(Color c) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
            Math.round(c.getRed() * 255),
            Math.round(c.getGreen() * 255),
            Math.round(c.getBlue() * 255),
        c.getOpacity());
    }

    private static Label bigSymbolLabel(String symbol, TextStyle style) {
        int baseSize = (style != null && style.fontSize() != null) ? style.fontSize() : 12;
        double sizePx = baseSize*2.2;
        Label label = styledLabel(symbol, style);
        label.setStyle(label.getStyle() + "-fx-font-size: " + sizePx + "px;");
        return label;
    }

    private static Label smallLabel(String text, TextStyle style) {
        if (text == null || text.isEmpty()) return null;
        int baseSize = (style != null && style.fontSize() != null) ? style.fontSize() : 12;
        double sizePx = Math.max(6, baseSize*0.55);
        Label label = styledLabel(text, style);
        label.setStyle(label.getStyle() + "-fx-font-size: "+ sizePx +"px;");
        return label;
    }

    public static Node bigOperator(String symbol, String lower, String upper, String value, TextStyle style) {
        VBox stack = new VBox(-2);
        stack.setAlignment(Pos.CENTER);

        boolean hasBounds = (upper != null && !upper.isEmpty()) || (lower != null && !lower.isEmpty());

        Label upperLabel = smallLabel(upper, style);
        if (upperLabel != null) stack.getChildren().add(upperLabel);

        stack.getChildren().add(bigSymbolLabel(symbol,style));

        Label lowerLabel = smallLabel(lower, style);
        if(lowerLabel != null) stack.getChildren().add(lowerLabel);

        Label valueLabel = styledLabel(value, style);

        HBox box = new HBox(4,stack,valueLabel);
        box.setAlignment(Pos.CENTER);
        
        int baseSize = (style!= null && style.fontSize() != null)?style.fontSize():12;

        box.setTranslateY(baseSize);
        if (hasBounds) box.setTranslateY(baseSize*1.5);
        return box;
    }

    public static Node limit (String condition, String value, TextStyle style) {
        VBox stack = new VBox(-2);
        stack.setAlignment(Pos.CENTER);

        stack.getChildren().add(styledLabel("lim", style));

        Label conditionLabel = smallLabel(condition, style);
        if (conditionLabel != null) stack.getChildren().add(conditionLabel);


        Label valueLabel = styledLabel(value, style);
        int baseSize = (style!= null && style.fontSize() != null)?style.fontSize():12;
        valueLabel.setTranslateY(-baseSize*0.15);

        HBox box = new HBox(4, stack, valueLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}