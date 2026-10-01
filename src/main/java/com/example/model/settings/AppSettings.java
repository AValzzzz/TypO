package com.example.model.settings;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

public class AppSettings {
    private static final AppSettings INSTANCE = new AppSettings();

    public static AppSettings getInstance() {
        return INSTANCE;
    }

    public static final double PX_PER_CM = 72.0 / 2.54;
    public static final double MAX_MARGIN_PAIR_CM = 19.0;
    private static final double DEFAULT_MARGIN_CM = 20.0 / PX_PER_CM;

    private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.rgb(211, 211, 211)); // current
                                                                                                                // #D3D3D3
    private final ObjectProperty<Color> selectionColor = new SimpleObjectProperty<>(Color.rgb(51, 153, 255, 0.4)); // default
                                                                                                                   // JavaFX-ish
                                                                                                                   // blue
    private final ObjectProperty<CodeTheme> codeTheme = new SimpleObjectProperty<>(CodeTheme.DARK);

    private final DoubleProperty marginLeft = new SimpleDoubleProperty(DEFAULT_MARGIN_CM);
    private final DoubleProperty marginTop = new SimpleDoubleProperty(DEFAULT_MARGIN_CM);
    private final DoubleProperty marginRight = new SimpleDoubleProperty(DEFAULT_MARGIN_CM);
    private final DoubleProperty marginBottom = new SimpleDoubleProperty(DEFAULT_MARGIN_CM);

    private AppSettings() {
    }

    public ObjectProperty<Color> backgroundColorProperty() { return backgroundColor; }
    public ObjectProperty<Color> selectionColorProperty() { return selectionColor; }
    public ObjectProperty<CodeTheme> codeThemeProperty() { return codeTheme; }

    public DoubleProperty marginLeftProperty() { return marginLeft; }
    public DoubleProperty marginTopProperty() { return marginTop; }
    public DoubleProperty marginRightProperty() { return marginRight; }
    public DoubleProperty marginBottomProperty() { return marginBottom; }

    public double getMarginLeft() { return marginLeft.get(); }
    public double getMarginTop() { return marginTop.get(); }
    public double getMarginRight() { return marginRight.get(); }
    public double getMarginBottom() { return marginBottom.get(); }


    public void setMarginsCm(double left, double top, double right, double bottom) {
        marginLeft.set(left);
        marginTop.set(top);
        marginRight.set(right);
        marginBottom.set(bottom);
    }

    public static boolean areMarginsValid(double left, double top, double right, double bottom) {
        return left >= 0 && top >= 0 && right >= 0 && bottom >= 0
                && left + right <= MAX_MARGIN_PAIR_CM
                && top + bottom <= MAX_MARGIN_PAIR_CM;
    }

    public static double cmToPx(double cm) {
        return cm * PX_PER_CM;
    }
}
