package com.example.view;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.example.model.TextStyle;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

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
            new SeparatorMenuItem(),
            underlineItem(),
            highlightItem());
    }

    private Menu sizeMenu() {
        Menu menu = new Menu("Taille");
        Integer current = editor.commonValue(TextStyle::fontSize).orElse(null);
        //TODO : finir l'implémentation
        return menu;
    }

    private MenuItem colorItem() {
        //TODO : implémenter la méthode
        return new CustomMenuItem(null, false);
    }

    private MenuItem toggleItem(String label, Function<TextStyle, Boolean> getter, BiFunction<TextStyle, Boolean, TextStyle> setter) {
        //TODO : implémenter la méthode
        return new CheckMenuItem(label);
    }

    private MenuItem underlineItem() {
        //TODO : implémenter la méthode
        return new MenuItem(); // cf longpresscolorItem() ??
    }

    private MenuItem highlightItem() {
        //TODO : implémenter la méthode
        return new MenuItem(); //cf longpresscoloritem() ??
    }
}
