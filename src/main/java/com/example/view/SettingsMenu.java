package com.example.view;

import java.util.Arrays;
import java.util.Locale;

import com.example.model.settings.AppSettings;
import com.example.model.settings.CodeTheme;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class SettingsMenu extends ContextMenu {
    private static final int LEFT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3;

    private final TextField[] marginFields = new TextField[4];
    private final CheckBox linkBox = new CheckBox("Lier les quatre");

    public SettingsMenu() {
        setAutoHide(true);

        AppSettings settings = AppSettings.getInstance();

        ColorPicker bgPicker = new ColorPicker(settings.backgroundColorProperty().get());
        bgPicker.valueProperty().bindBidirectional(settings.backgroundColorProperty());
        keepOpenWhilePickerActive(bgPicker);

        ColorPicker selectionPicker = new ColorPicker(settings.selectionColorProperty().get());
        selectionPicker.valueProperty().bindBidirectional(settings.selectionColorProperty());
        keepOpenWhilePickerActive(selectionPicker);

        ChoiceBox<CodeTheme> themeChoice = new ChoiceBox<>();
        themeChoice.getItems().addAll(CodeTheme.values());
        themeChoice.setValue(settings.codeThemeProperty().get());
        themeChoice.valueProperty().addListener((obs, o, n) -> settings.codeThemeProperty().set(n));

        CheckBox pageNumberBox = new CheckBox("Numéroter les pages");
        pageNumberBox.setStyle("-fx-text-fill: BLACK;");
        pageNumberBox.selectedProperty().bindBidirectional(settings.showPageNumbersProperty());

        for (int i = 0; i < marginFields.length; i++)
            marginFields[i] = createMarginField(i);
        refreshMarginFields();

        linkBox.setStyle("-fx-text-fill: BLACK;");

        Menu menu = new Menu("Marges (cm)");
        menu.getItems().addAll(
                row("Gauche", marginFields[LEFT]),
                row("Haut", marginFields[TOP]),
                row("Droite", marginFields[RIGHT]),
                row("Bas", marginFields[BOTTOM]),
                new CustomMenuItem(linkBox, false));

        getItems().addAll(
                row("Couleur de fond", bgPicker),
                row("Couleur de sélection", selectionPicker),
                new SeparatorMenuItem(),
                row("Thème des blocs de code", themeChoice),
                new SeparatorMenuItem(),
                new CustomMenuItem(pageNumberBox, false),
                menu);
    }

    private TextField createMarginField(int index) {
        TextField field = new TextField();
        field.setPrefColumnCount(5);
        field.setPromptText("cm");
        field.setOnAction(e -> commitMargin(index));
        field.textProperty().addListener((obs, o, n) -> field.setStyle(""));
        return field;
    }

    private void commitMargin(int index) {
        TextField source = marginFields[index];
        double value;
        try {
            value = Double.parseDouble(source.getText().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            markInvalid(source);
            return;
        }

        AppSettings s = AppSettings.getInstance();
        double[] m = { s.getMarginLeft(), s.getMarginTop(), s.getMarginRight(), s.getMarginBottom() };
        if (linkBox.isSelected())
            Arrays.fill(m, value);
        else
            m[index] = value;

        if (!AppSettings.areMarginsValid(m[LEFT], m[TOP], m[RIGHT], m[BOTTOM])) {
            markInvalid(source);
            return;
        }

        s.setMarginsCm(m[LEFT], m[TOP], m[RIGHT], m[BOTTOM]);
        refreshMarginFields();
    }

    private void markInvalid(TextField field) {
        field.setStyle("-fx-border-color: red");
    }

    private void refreshMarginFields() {
        AppSettings s = AppSettings.getInstance();
        marginFields[LEFT].setText(format(s.getMarginLeft()));
        marginFields[TOP].setText(format(s.getMarginTop()));
        marginFields[RIGHT].setText(format(s.getMarginRight()));
        marginFields[BOTTOM].setText(format(s.getMarginBottom()));
    }

    private static String format(double cm) {
        return String.format(Locale.ROOT, "%.2f", cm);
    }

    private void keepOpenWhilePickerActive(ColorPicker picker) {
        picker.showingProperty().addListener((obs, wasShowing, isShowing) -> setAutoHide(!isShowing));
    }

    private CustomMenuItem row(String labelText, Node control) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: BLACK;");

        HBox box = new HBox(8, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(box, false);
    }
}