package com.example.view;

import javafx.scene.Cursor;
import javafx.scene.layout.Region;

final class Handles {
    static final double SIZE = 12;
    static final String BLUE = "#3399ff";
    static final String GREEN = "#33cc66";
    static final String SQUARE_STYLE = "-fx-background-color: " + BLUE
            + "; -fx-border-color: white; -fx-border-width: 1;";

    private Handles() {
    }

    static Region square(Cursor cursor) {
        Region r = new Region();
        r.setStyle(SQUARE_STYLE);
        r.setCursor(cursor);
        return r;
    }

    static Region sizedSquare(Cursor cursor) {
        Region r = square(cursor);
        r.setPrefSize(SIZE, SIZE);
        r.setVisible(false);
        return r;
    }

    static Region round(String color, Cursor cursor) {
        Region r = new Region();
        r.setPrefSize(SIZE, SIZE);
        r.setStyle("-fx-background-color: " + color + "; -fx-border-color: white; -fx-border-width: 1; "
                + "-fx-background-radius: 6; -fx-border-radius: 6;");
        r.setCursor(cursor);
        r.setVisible(false);
        return r;
    }
}
