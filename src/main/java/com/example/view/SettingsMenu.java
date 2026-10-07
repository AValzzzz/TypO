package com.example.view;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import com.example.model.i18n.AppLanguage;
import com.example.model.i18n.I18n;
import com.example.model.settings.AppSettings;
import com.example.model.settings.AppTheme;
import com.example.model.settings.CodeTheme;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

public class SettingsMenu extends ContextMenu {
    private static final int LEFT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3;

    private final TextField[] marginFields = new TextField[4];
    private final CheckBox linkBox = new CheckBox(I18n.t("settings.linkMargins"));
    private ChangeListener<AppTheme> themeSync;

    public SettingsMenu() {
        setAutoHide(true);

        AppSettings settings = AppSettings.getInstance();

        ChoiceBox<AppTheme> appThemeChoice = new ChoiceBox<>(Theme.themes());
        appThemeChoice.setValue(Theme.current());
        appThemeChoice.valueProperty().addListener((obs, o, n) -> Theme.select(n));
        Theme.currentProperty().addListener(new WeakChangeListener<>(themeSync = (obs, o, n) -> {
            if (appThemeChoice.getValue() != n)
                appThemeChoice.setValue(n);
        }));

        Button importTheme = new Button(I18n.t("settings.importTheme"));
        importTheme.setOnAction(e -> importTheme(importTheme));

        ChoiceBox<CodeTheme> themeChoice = new ChoiceBox<>();
        themeChoice.getItems().addAll(CodeTheme.values());
        themeChoice.setValue(settings.codeThemeProperty().get());
        themeChoice.valueProperty().addListener((obs, o, n) -> settings.codeThemeProperty().set(n));

        CheckBox pageNumberBox = new CheckBox(I18n.t("settings.pageNumbers"));
        pageNumberBox.selectedProperty().bindBidirectional(settings.showPageNumbersProperty());

        CheckBox reduceBox = new CheckBox(I18n.t("settings.reduceMotion"));
        reduceBox.setSelected(Motion.isReduced());
        reduceBox.selectedProperty().addListener((o, was, is) -> Motion.setReduced(is));
        getItems().add(new CustomMenuItem(reduceBox, false));

        CheckBox caretBox = new CheckBox(I18n.t("settings.smoothCaret"));
        caretBox.selectedProperty().bindBidirectional(Motion.smoothCaretProperty());
        getItems().add(new CustomMenuItem(caretBox, false));

        for (int i = 0; i < marginFields.length; i++)
            marginFields[i] = createMarginField(i);
        refreshMarginFields();

        Menu menu = new Menu(I18n.t("settings.margins"));
        menu.getItems().addAll(
                row(I18n.t("settings.marginLeft"), marginFields[LEFT]),
                row(I18n.t("settings.marginTop"), marginFields[TOP]),
                row(I18n.t("settings.marginRight"), marginFields[RIGHT]),
                row(I18n.t("settings.marginBottom"), marginFields[BOTTOM]),
                new CustomMenuItem(linkBox, false));

        ChoiceBox<AppLanguage> languageChoice = new ChoiceBox<>();
        languageChoice.getItems().addAll(AppLanguage.values());
        languageChoice.setValue(I18n.selected());
        languageChoice.valueProperty().addListener((obs, o, n) -> {
            if (n != null)
                I18n.setSelected(n);
        });

        Label restartNote = new Label(I18n.t("settings.restartNote"));
        restartNote.getStyleClass().add("settings-note");
        restartNote.visibleProperty().bind(languageChoice.valueProperty().isNotEqualTo(I18n.active()));
        restartNote.managedProperty().bind(restartNote.visibleProperty());

        getItems().addAll(
                row(I18n.t("settings.appTheme"), appThemeChoice, importTheme),
                new SeparatorMenuItem(),
                row(I18n.t("settings.codeTheme"), themeChoice),
                new SeparatorMenuItem(),
                new CustomMenuItem(pageNumberBox, false),
                menu,
                new SeparatorMenuItem(),
                row(I18n.t("settings.language"), languageChoice),
                new CustomMenuItem(restartNote, false));
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

    private void importTheme(Node anchor) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("settings.importTheme.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("settings.importTheme.filter"),
                "*.json"));
        File file = chooser.showOpenDialog(anchor.getScene().getWindow());
        if (file == null)
            return;
        try {
            AppTheme theme = Theme.importTheme(file.toPath());
            Toast.success(I18n.t("settings.importTheme.done", theme.getName()));
        } catch (IOException | IllegalArgumentException ex) {
            Toast.error(I18n.t("settings.importTheme.error", ex.getMessage()));
        }
    }

    private CustomMenuItem row(String labelText, Node... controls) {
        HBox box = new HBox(8, new Label(labelText));
        box.getChildren().addAll(controls);
        box.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(box, false);
    }
}