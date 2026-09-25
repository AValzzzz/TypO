package com.example.view;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.example.model.TextStyle;

import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class TextFormatMenu extends ContextMenu {
    private static final int[] SIZES = {8,10,12,14,18,24,36,48,72};

    private final RichTextArea editor;
    private final Consumer<UnaryOperator<TextStyle>> onChange;

    public TextFormatMenu(RichTextArea editor, Consumer<UnaryOperator<TextStyle>> onChange) {
        this.editor = editor;
        this.onChange = onChange;

        getItems().addAll(
            sizeMenu(),
            colorItem(),
            new SeparatorMenuItem(),
            toggleItem("Gras", TextStyle::bold, TextStyle::withBold),
            toggleItem("Italique", TextStyle::italic, TextStyle::withItalic),
            toggleItem("Barré", TextStyle::strikethrough, TextStyle::withStrikethrough),
            underlineToggle(),
            underlineStyleToggle(),
            underlineColorItem(),
            highlightToggle(),
            highlightColorItem());
        }

    private Menu sizeMenu() {
        Menu menu = new Menu("Taille");
        Integer current = editor.commonValue(TextStyle::fontSize).orElse(null);
        ToggleGroup group = new ToggleGroup();

        for (int size : SIZES) {
            RadioMenuItem item = new RadioMenuItem(String.valueOf(size));
            item.setToggleGroup(group);
            item.setSelected(current != null && current == size);
            item.setOnAction(e-> onChange.accept(s -> s.withFontSize(size)));
            menu.getItems().add(item); 
        }

        TextField field = new TextField(current != null ? String.valueOf(current):"");
        field.setPromptText("Taille");
        field.setPrefColumnCount(4);
        field.setOnAction(e-> {
            try {
                int size = Integer.parseInt(field.getText().trim());
                if(size < 1 || size > 400) {
                    throw new NumberFormatException();
                }
                onChange.accept(s-> s.withFontSize(size));
                hide();
            } catch(NumberFormatException ex) {
                field.setStyle("-fx-border-color: red");
            }
        });

        HBox custom = new HBox(6, new Label("Autre :"), field);
        custom.setAlignment(Pos.CENTER_LEFT);

        menu.getItems().addAll(new SeparatorMenuItem(), new CustomMenuItem(custom, false));
        return menu;
    }

    private MenuItem colorItem() {
        ColorPicker picker = new ColorPicker(editor.commonValue(TextStyle::textColor).orElse(Color.BLACK));
        picker.setOnAction(e-> {
            Color color = picker.getValue();
            onChange.accept(s->s.withTextColor(color));
            hide();
        });

        keepMenuOpenWhilePickerActive(picker);

        HBox row = new HBox(8, new Label("Couleur"), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        
        return new CustomMenuItem(row, false);
    }

    private void keepMenuOpenWhilePickerActive(ColorPicker picker) {
        picker.showingProperty().addListener((obs,wasShowing,isShowing)-> {
            if (isShowing) {
                setAutoHide(false);
            } else {
                setAutoHide(true);
            }
        });
    }

    private MenuItem underlineStyleToggle() {
        return toggleItem("Soulignement en pointillés", TextStyle::underlineDotted, TextStyle::withUnderlineDotted);
    }

    private MenuItem toggleItem(String label, Function<TextStyle, Boolean> getter, BiFunction<TextStyle, Boolean, TextStyle> setter) {
        CheckMenuItem item = new CheckMenuItem(label);
        item.setSelected(editor.commonValue(getter).orElse(false));
        item.setOnAction (e-> {
            boolean enabled = item.isSelected();
            onChange.accept(s->setter.apply(s, enabled));
        });
        return item;
    }

    private MenuItem underlineToggle() {
        return toggleItem("Souligné", TextStyle::underline, TextStyle::withUnderline);
    }

    private MenuItem underlineColorItem() {
        Color current = editor.commonValue(TextStyle::underlineColor).orElse(Color.BLACK);
        ColorPicker picker = new ColorPicker(current);
        picker.setOnAction(e-> {
            Color color = picker.getValue();
            onChange.accept(s -> s.withUnderline(true).withUnderlineColor(color));
            hide();
        });
        return colorRow("Couleur du soulignement", picker);
    }

    private MenuItem highlightToggle() {
        CheckMenuItem item = new CheckMenuItem("Surligné)");
        boolean active = editor.commonValue(TextStyle::highlight).isPresent();
        item.setSelected(active);
        item.setOnAction(e->onChange.accept(s->s.withHighlight(item.isSelected() ? Color.YELLOW : null)));
        return item;
    }

    private MenuItem highlightColorItem() {
        Color current = editor.commonValue(TextStyle::highlight).orElse(Color.YELLOW);
        ColorPicker picker = new ColorPicker(current);
            picker.setOnAction(e-> {
                Color color = picker.getValue();
                onChange.accept(s->s.withHighlight(color));
                hide();
            });
        return colorRow("Couleur du surlignage", picker);
    }

    private MenuItem colorRow(String label, ColorPicker picker) {
        HBox row = new HBox(8, new Label(label), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }

}
