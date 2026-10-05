package com.example.view;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.example.model.help.HelpData;
import com.example.model.help.HelpNode;
import com.example.model.help.HelpSearch;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class HelpWindow {
    private static final String QUESTION_STYLE = "-fx-font-size: 17px; -fx-font-weight: bold;";
    private static final String TITLE_STYLE = "-fx-font-size: 20px; -fx-font-weight: bold;";
    private static final String NOTE_STYLE = "-fx-text-fill: #666; -fx-font-style: italic;";
    private static final String CODE_STYLE = "-fx-font-family: 'Monospaced'; -fx-background-color: #f0f0f0; "
            + "-fx-padding: 4 8 4 8;";

    private static HelpWindow instance;

    public static void open(Window owner) {
        if (instance != null) {
            instance.stage.toFront();
            instance.stage.requestFocus();
            return;
        }
        try {
            instance = new HelpWindow(owner);
            instance.stage.show();
        } catch (IOException e) {
            instance = null;
            new Alert(Alert.AlertType.ERROR, "Unable to load the help: " + e.getMessage()).showAndWait();
        }
    }

    private final Stage stage = new Stage();
    private final ScrollPane scroll = new ScrollPane();
    private final VBox content = new VBox(12);
    private final TextField searchField = new TextField();
    private final Button searchButton = new Button();
    private final Button backButton = new Button();
    private final ToggleButton enButton = new ToggleButton("EN");
    private final ToggleButton frButton = new ToggleButton("FR");
    private final Deque<Runnable> history = new ArrayDeque<>();

    private Runnable current;
    private HelpDemoPlayer player;
    private String lang;
    private HelpData data;

    private HelpWindow(Window owner) throws IOException {
        lang = Locale.getDefault().getLanguage().equals("fr") ? "fr" : "en";
        data = HelpData.load(lang);

        ToggleGroup group = new ToggleGroup();
        enButton.setToggleGroup(group);
        frButton.setToggleGroup(group);
        (lang.equals("fr") ? frButton : enButton).setSelected(true);
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                old.setSelected(true);
                return;
            }
            setLanguage(now == frButton ? "fr" : "en");
        });

        searchField.setOnAction(e -> search());
        searchButton.setOnAction(e -> search());
        backButton.setOnAction(e -> back());

        HBox top = new HBox(8, searchField, searchButton, enButton, frButton);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        content.setPadding(new Insets(16));
        scroll.setContent(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox bottom = new HBox(backButton);
        bottom.setPadding(new Insets(8, 10, 8, 10));

        BorderPane root = new BorderPane(scroll);
        root.setTop(top);
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 600, 680));
        if (owner != null)
            stage.initOwner(owner);
        stage.setOnHidden(e -> {
            stopPlayer();
            instance = null;
        });

        applyUiTexts();
        navigate(() -> renderNode(data.start(), data.ui("intro")));
    }

    private void setLanguage(String newLang) {
        try {
            data = HelpData.load(newLang);
            lang = newLang;
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Unable to load the help: " + e.getMessage()).showAndWait();
            (lang.equals("fr") ? frButton : enButton).setSelected(true);
            return;
        }
        applyUiTexts();
        if (current != null)
            current.run();
    }

    private void applyUiTexts() {
        stage.setTitle(data.ui("windowTitle"));
        searchField.setPromptText(data.ui("searchPrompt"));
        searchButton.setText(data.ui("search"));
        backButton.setText(data.ui("back"));
    }

    private void navigate(Runnable view) {
        if (current != null)
            history.push(current);
        current = view;
        view.run();
        updateBack();
    }

    private void back() {
        if (history.isEmpty())
            return;
        current = history.pop();
        current.run();
        updateBack();
    }

    private void updateBack() {
        backButton.setDisable(history.isEmpty());
    }

    private void go(String id) {
        if (id != null)
            navigate(() -> renderNode(id, null));
    }

    private void search() {
        String q = searchField.getText().trim();
        if (q.isEmpty())
            return;
        navigate(() -> renderResults(q));
    }

    private void stopPlayer() {
        if (player != null) {
            player.stop();
            player = null;
        }
    }

    private void resetContent() {
        stopPlayer();
        content.getChildren().clear();
        scroll.setVvalue(0);
    }

    private void renderNode(String id, String note) {
        resetContent();
        HelpNode n = data.node(id);
        if (n == null) {
            content.getChildren().add(label(data.ui("missing") + " " + id, null));
            return;
        }
        if (n.isLeaf())
            renderLeaf(n);
        else
            renderQuestion(n, note);
    }

    private void renderQuestion(HelpNode n, String note) {
        if (note != null && !note.isEmpty())
            content.getChildren().add(label(note, NOTE_STYLE));
        content.getChildren().add(label(data.question(n.id), QUESTION_STYLE));

        Button yes = new Button(data.ui("yes"));
        Button no = new Button(data.ui("no"));
        Button skip = new Button(data.ui("skip"));
        for (Button b : List.of(yes, no, skip))
            b.setMinWidth(90);
        yes.setOnAction(e -> go(n.yes));
        no.setOnAction(e -> go(n.no));
        skip.setOnAction(e -> go(n.skip != null ? n.skip : n.no));
        content.getChildren().add(new HBox(10, yes, no, skip));
    }

    private void renderResults(String query) {
        resetContent();
        List<HelpSearch.Hit> hits = data.search(query);
        if (hits.isEmpty()) {
            renderNode(data.start(), data.ui("noMatch"));
            return;
        }
        content.getChildren().add(label(data.ui("resultsTitle"), QUESTION_STYLE));
        for (HelpSearch.Hit hit : hits) {
            Button b = new Button(data.title(hit.id()));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setOnAction(e -> go(hit.id()));
            content.getChildren().add(b);
        }
        Button guide = new Button(data.ui("guideMe"));
        guide.setOnAction(e -> go(data.start()));
        content.getChildren().add(guide);
    }

    private void renderLeaf(HelpNode n) {
        content.getChildren().add(label(data.title(n.id), TITLE_STYLE));
        for (String line : data.body(n.id))
            content.getChildren().add(bodyLine(line));

        if (n.demo != null && !n.demo.isEmpty()) {
            Button show = new Button(data.ui("showMe"));
            show.setOnAction(e -> {
                if (player == null) {
                    player = new HelpDemoPlayer(n.demo, i -> data.caption(n.id, i), data.ui("replay"));
                    content.getChildren().add(player);
                    show.setDisable(true);
                }
                player.play();
            });
            content.getChildren().add(show);
        }
    }

    private Label bodyLine(String line) {
        if (line.startsWith("> "))
            return label(line.substring(2), CODE_STYLE);
        if (line.startsWith("- "))
            return label("\u2022  " + line.substring(2), "-fx-padding: 0 0 0 8;");
        return label(line, null);
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        if (style != null)
            l.setStyle(style);
        return l;
    }
}