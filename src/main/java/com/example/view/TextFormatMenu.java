package com.example.view;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.example.model.ParagraphStyle;
import com.example.model.TextStyle;
import com.example.model.i18n.I18n;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class TextFormatMenu extends ContextMenu {
    private static final int[] SIZES = { 8, 10, 12, 14, 18, 24, 36, 48, 72 };

    private final RichTextArea editor;
    private final Consumer<UnaryOperator<TextStyle>> onChange;
    private final Consumer<TextAlignment> onAlign;

    public TextFormatMenu(RichTextArea editor, Consumer<UnaryOperator<TextStyle>> onChange,
            Consumer<TextAlignment> onAlign) {
        this.editor = editor;
        this.onChange = onChange;
        this.onAlign = onAlign;

        getItems().addAll(
                fontItem(),
                sizeMenu(),
                colorItem(),
                new SeparatorMenuItem(),
                toggleItem(I18n.t("fmt.bold"), TextStyle::bold, TextStyle::withBold),
                toggleItem(I18n.t("fmt.italic"), TextStyle::italic, TextStyle::withItalic),
                toggleItem(I18n.t("fmt.strike"), TextStyle::strikethrough, TextStyle::withStrikethrough),
                underlineToggle(),
                underlineStyleToggle(),
                underlineColorItem(),
                highlightToggle(),
                highlightColorItem(),
                new SeparatorMenuItem(),
                alignmentMenu());
    }

    private Menu sizeMenu() {
        Menu menu = new Menu(I18n.t("fmt.size"));
        Integer current = editor.commonValue(TextStyle::fontSize).orElse(null);
        ToggleGroup group = new ToggleGroup();

        for (int size : SIZES) {
            RadioMenuItem item = new RadioMenuItem(String.valueOf(size));
            item.setToggleGroup(group);
            item.setSelected(current != null && current == size);
            item.setOnAction(e -> onChange.accept(s -> s.withFontSize(size)));
            menu.getItems().add(item);
        }

        TextField field = new TextField(current != null ? String.valueOf(current) : "");
        field.setPromptText(I18n.t("fmt.sizePrompt"));
        field.setPrefColumnCount(4);
        field.setOnAction(e -> {
            try {
                int size = Integer.parseInt(field.getText().trim());
                if (size < 1 || size > 400) {
                    throw new NumberFormatException();
                }
                onChange.accept(s -> s.withFontSize(size));
                hide();
            } catch (NumberFormatException ex) {
                field.setStyle("-fx-border-color: red");
            }
        });

        HBox custom = new HBox(6, new Label(I18n.t("fmt.otherSize")), field);
        custom.setAlignment(Pos.CENTER_LEFT);

        menu.getItems().addAll(new SeparatorMenuItem(), new CustomMenuItem(custom, false));
        return menu;
    }

    private MenuItem colorItem() {
        ColorPicker picker = new ColorPicker(editor.commonValue(TextStyle::textColor).orElse(Color.BLACK));
        picker.setOnAction(e -> {
            Color color = picker.getValue();
            onChange.accept(s -> s.withTextColor(color));
            hide();
        });

        keepMenuOpenWhilePickerActive(picker);

        HBox row = new HBox(8, new Label(I18n.t("fmt.color")), picker);
        row.setAlignment(Pos.CENTER_LEFT);

        return new CustomMenuItem(row, false);
    }

    private void keepMenuOpenWhilePickerActive(ColorPicker picker) {
        picker.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                setAutoHide(false);
            } else {
                setAutoHide(true);
            }
        });
    }

    private MenuItem underlineStyleToggle() {
        return toggleItem(I18n.t("fmt.underlineDotted"), TextStyle::underlineDotted, TextStyle::withUnderlineDotted);
    }

    private MenuItem toggleItem(String label, Function<TextStyle, Boolean> getter,
            BiFunction<TextStyle, Boolean, TextStyle> setter) {
        CheckMenuItem item = new CheckMenuItem(label);
        item.setSelected(editor.commonValue(getter).orElse(false));
        item.setOnAction(e -> {
            boolean enabled = item.isSelected();
            onChange.accept(s -> setter.apply(s, enabled));
        });
        return item;
    }

    private MenuItem underlineToggle() {
        return toggleItem(I18n.t("fmt.underline"), TextStyle::underline, TextStyle::withUnderline);
    }

    private MenuItem underlineColorItem() {
        Color current = editor.commonValue(TextStyle::underlineColor).orElse(Color.BLACK);
        ColorPicker picker = new ColorPicker(current);
        picker.setOnAction(e -> {
            Color color = picker.getValue();
            onChange.accept(s -> s.withUnderline(true).withUnderlineColor(color));
            hide();
        });
        return colorRow(I18n.t("fmt.underlineColor"), picker);
    }

    private MenuItem highlightToggle() {
        CheckMenuItem item = new CheckMenuItem(I18n.t("fmt.highlight"));
        boolean active = editor.commonValue(TextStyle::highlight).isPresent();
        item.setSelected(active);
        item.setOnAction(e -> onChange.accept(s -> s.withHighlight(item.isSelected() ? Color.YELLOW : null)));
        return item;
    }

    private MenuItem highlightColorItem() {
        Color current = editor.commonValue(TextStyle::highlight).orElse(Color.YELLOW);
        ColorPicker picker = new ColorPicker(current);
        picker.setOnAction(e -> {
            Color color = picker.getValue();
            onChange.accept(s -> s.withHighlight(color));
            hide();
        });
        return colorRow(I18n.t("fmt.highlightColor"), picker);
    }

    private MenuItem colorRow(String label, ColorPicker picker) {
        HBox row = new HBox(8, new Label(label), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }

    private Menu alignmentMenu() {
        Menu menu = new Menu(I18n.t("fmt.align"));

        boolean hasEditableParagraph = editor.selectedParagraphStyles().stream().anyMatch(s -> !s.codeBlock());
        menu.setDisable(!hasEditableParagraph);

        TextAlignment current = editor.commonParagraphValue(ParagraphStyle::alignment).orElse(null);
        ToggleGroup group = new ToggleGroup();

        menu.getItems().addAll(
                alignmentItem(I18n.t("fmt.alignLeft"), TextAlignment.LEFT, current, group),
                alignmentItem(I18n.t("fmt.alignCenter"), TextAlignment.CENTER, current, group),
                alignmentItem(I18n.t("fmt.alignRight"), TextAlignment.RIGHT, current, group),
                alignmentItem(I18n.t("fmt.alignJustify"), TextAlignment.JUSTIFY, current, group));
        return menu;
    }

    private RadioMenuItem alignmentItem(String label, TextAlignment value, TextAlignment current, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(label);
        item.setToggleGroup(group);
        item.setSelected(value == current);
        item.setOnAction(e -> onAlign.accept(value));
        return item;
    }

    private MenuItem fontItem() {
        Optional<Optional<String>> common = editor.commonValue(s -> Optional.ofNullable(s.fontFamily()));
        String current = common.flatMap(o -> o).orElse(null);
        boolean mixed = common.isEmpty();
        String initialText = current == null ? "" : current;

        List<String> fonts = FontCatalog.available();
        FilteredList<String> filtered = new FilteredList<>(FXCollections.observableArrayList(fonts), f -> true);

        ComboBox<String> combo = new ComboBox<>(filtered);
        combo.setEditable(true);
        combo.setPrefWidth(210);
        combo.setVisibleRowCount(10);
        combo.setPromptText(I18n.t(mixed ? "fmt.fontMixed" : "fmt.fontDefault"));
        combo.getEditor().setText(initialText);

        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setFont(Font.getDefault());
                } else {
                    setText(item);
                    setFont(Font.font(item, 14));
                }
            }
        });

        combo.getEditor().textProperty().addListener((obs, old, text) -> {
            combo.getEditor().setStyle("");
            if (text != null && text.equals(combo.getValue()))
                return; 
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(f -> q.isEmpty() || f.toLowerCase(Locale.ROOT).contains(q));
            if (filtered.isEmpty())
                combo.hide();
            else if (!combo.isShowing() && combo.getEditor().isFocused())
                combo.show();
        });

        combo.setOnAction(e -> {
            String picked = combo.getValue();
            if (picked == null)
                picked = combo.getEditor().getText();
            picked = picked == null ? "" : picked.trim();

            if (picked.equals(initialText))
                return;

            if (picked.isEmpty()) {
                applyFont(null); 
                return;
            }
            String match = null;
            for (String f : fonts)
                if (f.equalsIgnoreCase(picked)) {
                    match = f;
                    break;
                }
            if (match == null && !filtered.isEmpty())
                match = filtered.get(0);
            if (match == null) {
                combo.getEditor().setStyle("-fx-border-color: red");
                return;
            }
            applyFont(match);
        });

        combo.showingProperty().addListener((obs, was, is) -> setAutoHide(!is));

        HBox row = new HBox(8, new Label(I18n.t("fmt.font")), combo);
        row.setAlignment(Pos.CENTER_LEFT);
        return new CustomMenuItem(row, false);
    }

    private void applyFont(String family) {
        onChange.accept(s -> s.codeBlock() ? s : s.withFontFamily(family));
        hide();
    }
}