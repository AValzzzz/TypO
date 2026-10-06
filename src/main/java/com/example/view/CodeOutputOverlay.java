package com.example.view;

import java.util.List;

import com.example.model.code.run.CodeRunner;
import com.example.model.i18n.I18n;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class CodeOutputOverlay extends VBox {
    private static final String BUTTON_STYLE = "-fx-background-color: transparent; -fx-text-fill: #d4d4d4; "
            + "-fx-cursor: hand; -fx-padding: 0 6 0 6;";

    private final Label title = new Label();
    private final Label status = new Label();
    private final Button toggle = new Button("▾");
    private final Button stop = new Button(I18n.t("code.stop"));
    private final Button close = new Button("✕");
    private final TextArea output = new TextArea();

    private boolean collapsed;
    private Runnable onStop, onClose;

    public CodeOutputOverlay() {
        getProperties().put("noExport", Boolean.TRUE);
        setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3c3c3c; -fx-border-width: 0 1 1 1;");
        setVisible(false);

        title.setStyle("-fx-text-fill: #d4d4d4; -fx-font-weight: bold; -fx-font-size: 11px;");
        status.setStyle("-fx-text-fill: #9a9a9a; -fx-font-size: 11px;");
        for (Button b : List.of(toggle, stop, close)) {
            b.setStyle(BUTTON_STYLE);
            b.setFocusTraversable(false);
        }
        stop.setVisible(false);
        stop.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, toggle, title, status, spacer, stop, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(3, 6, 3, 4));

        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(2);
        output.setPromptText("(aucune sortie)");
        output.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4; "
                + "-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 12px; "
                + "-fx-background-insets: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        getChildren().addAll(header, output);

        toggle.setOnAction(e -> setCollapsed(!collapsed));
        stop.setOnAction(e -> {
            if (onStop != null)
                onStop.run();
        });
        close.setOnAction(e -> {
            if (onClose != null)
                onClose.run();
        });
    }

    public void setOnStop(Runnable r) {
        this.onStop = r;
    }

    public void setOnClose(Runnable r) {
        this.onClose = r;
    }

    public void setCollapsed(boolean value) {
        collapsed = value;
        output.setVisible(!value);
        output.setManaged(!value);
        toggle.setText(value ? "▸" : "▾");
    }

    public void begin(String language) {
        title.setText(language);
        output.setPromptText(I18n.t("code.noOutput"));
        output.clear();
        output.setPrefRowCount(2);
        stop.setVisible(true);
        stop.setManaged(true);
    }

    public void append(String chunk) {
        output.appendText(chunk);
        long lines = output.getText().chars().filter(c -> c == '\n').count() + 1;
        output.setPrefRowCount((int) Math.max(2, Math.min(10, lines)));
    }

    public void finish(CodeRunner.Result result) {
        stop.setVisible(false);
        stop.setManaged(false);
        if (result.truncated())
            append("\n" + I18n.t("code.truncated"));
        status.setText(switch (result.status()) {
            case FINISHED ->
                result.exitCode() == 0 ? I18n.t("code.finished") : I18n.t("code.exitCode", result.exitCode());
            case TIMEOUT -> I18n.t("code.timeout", CodeRunner.TIMEOUT_SECONDS);
            case STOPPED -> I18n.t("code.stopped");
            case FAILED_TO_START -> I18n.t("code.failed");
        });
    }
}
