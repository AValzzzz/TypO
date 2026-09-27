package com.example.view;

import com.example.model.settings.AppSettings;
import com.example.model.settings.CodeTheme;

import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;

public class SettingsMenu extends ContextMenu {

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

        getItems().addAll(
            row("Couleur de fond", bgPicker),
            row("Couleur de sélection", selectionPicker),
            new SeparatorMenuItem(),
            row("Thème des blocs de code", themeChoice)
        );
    }

    private void keepOpenWhilePickerActive(ColorPicker picker) {
        picker.showingProperty().addListener((obs, wasShowing, isShowing) -> setAutoHide(!isShowing));
    }

    private CustomMenuItem row(String label, javafx.scene.Node control) {
        HBox box = new HBox(8, new Label(label), control);
        box.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(box, false);
    }
}