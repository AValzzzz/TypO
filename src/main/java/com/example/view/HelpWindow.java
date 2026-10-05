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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class HelpWindow {
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
            Motion.popIn(instance.root);
        } catch (IOException e) {
            instance = null;
            new Alert(Alert.AlertType.ERROR, "Unable to load the help: " + e.getMessage()).showAndWait();
        }
    }

    private final Stage stage = new Stage();
    private final BorderPane root = new BorderPane();
    private final ScrollPane scroll = new ScrollPane();
    private final VBox content = new VBox(16);
    private final TextField searchField = new TextField();
    private final Button searchButton = new Button();
    private final Button backButton = new Button();
    private final ToggleButton enButton = new ToggleButton("EN");
    private final ToggleButton frButton = new ToggleButton("FR");
    private final Deque<Runnable> history = new ArrayDeque<>();

    private Runnable current;
    private HelpDemoPlayer player;
    private Runnable onYes, onNo, onSkip;
    private boolean closing;
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

        searchField.getStyleClass().add("search-field");
        searchButton.getStyleClass().add("btn-primary");
        backButton.getStyleClass().add("btn-ghost");
        Motion.interactive(searchButton);
        Motion.interactive(backButton);
        searchField.setOnAction(e -> search());
        searchButton.setOnAction(e -> search());
        backButton.setOnAction(e -> back());

        HBox pills = new HBox(enButton, frButton);
        pills.getStyleClass().add("lang-pills");

        HBox top = new HBox(8, searchField, searchButton, pills);
        top.getStyleClass().add("help-top");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        content.getStyleClass().add("help-content");
        scroll.setContent(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox bottom = new HBox(backButton);
        bottom.getStyleClass().add("help-bottom");

        root.getStyleClass().add("help-root");
        root.setTop(top);
        root.setCenter(scroll);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 660, 720);
        scene.setFill(Color.web("#faf4ed"));
        Theme.apply(scene);
        installKeys(scene);

        stage.setScene(scene);
        if (owner != null)
            stage.initOwner(owner);
        stage.setOnCloseRequest(e -> {
            e.consume();
            close();
        });
        stage.setOnHidden(e -> {
            stopPlayer();
            instance = null;
        });

        applyUiTexts();
        navigate(() -> renderNode(data.start(), data.ui("intro")));
    }

    private void close() {
        if (closing)
            return;
        closing = true;
        Motion.fadeOutThen(root, stage::hide);
    }

    private void installKeys(Scene scene) {
        KeyCombination ctrlF = KeyCombination.keyCombination("Shortcut+F");
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
                return;
            }
            if (ctrlF.match(e) || (e.getCode() == KeyCode.SLASH && !(scene.getFocusOwner() instanceof TextField))) {
                searchField.requestFocus();
                searchField.selectAll();
                e.consume();
                return;
            }
            if (scene.getFocusOwner() instanceof TextField)
                return;
            switch (e.getCode()) {
                case Y -> run(onYes);
                case N -> run(onNo);
                case S -> run(onSkip);
                case BACK_SPACE, LEFT -> back();
                default -> {
                    return;
                }
            }
            e.consume();
        });
    }

    private static void run(Runnable r) {
        if (r != null)
            r.run();
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
        backButton.setText("\u2190  " + data.ui("back"));
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
        onYes = onNo = onSkip = null;
        content.getChildren().clear();
        scroll.setVvalue(0);
    }

    private void renderNode(String id, String note) {
        resetContent();
        HelpNode n = data.node(id);
        if (n == null) {
            VBox card = card();
            card.getChildren().add(label(data.ui("missing") + " " + id, "help-body"));
            present(card);
            return;
        }
        if (n.isLeaf())
            renderLeaf(n);
        else
            renderQuestion(n, note);
    }

    private void renderQuestion(HelpNode n, String note) {
        VBox card = card();
        if (note != null && !note.isEmpty())
            card.getChildren().add(label(note, "help-note"));
        card.getChildren().add(label(data.question(n.id), "help-question"));

        Button yes = button(data.ui("yes"), "btn-primary");
        Button no = button(data.ui("no"), "btn-secondary");
        Button skip = button(data.ui("skip"), "btn-ghost");
        yes.setOnAction(e -> go(n.yes));
        no.setOnAction(e -> go(n.no));
        skip.setOnAction(e -> go(n.skip != null ? n.skip : n.no));
        onYes = yes::fire;
        onNo = no::fire;
        onSkip = skip::fire;

        for (Button b : List.of(yes, no))
            b.setMinWidth(96);
        HBox row = new HBox(10, yes, no, skip);
        row.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(row);
        present(card);
    }

    private void renderResults(String query) {
        resetContent();
        List<HelpSearch.Hit> hits = data.search(query);
        if (hits.isEmpty()) {
            renderNode(data.start(), data.ui("noMatch"));
            return;
        }
        content.getChildren().add(label(data.ui("resultsTitle"), "help-note"));
        for (HelpSearch.Hit hit : hits) {
            Button b = button(data.title(hit.id()) + "   \u2192", "result-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> go(hit.id()));
            content.getChildren().add(b);
        }
        Button guide = button(data.ui("guideMe"), "btn-ghost");
        guide.setOnAction(e -> go(data.start()));
        content.getChildren().add(guide);
        Motion.stagger(content.getChildren(), 28);
    }

    private void renderLeaf(HelpNode n) {
        VBox card = card();
        card.getChildren().add(label(data.title(n.id), "help-title"));
        for (String line : data.body(n.id))
            card.getChildren().add(bodyLine(line));

        if (n.demo != null && !n.demo.isEmpty()) {
            Button show = button(data.ui("showMe"), "btn-primary");
            show.setOnAction(e -> {
                if (player == null) {
                    player = new HelpDemoPlayer(n.demo, i -> data.caption(n.id, i), data.ui("replay"));
                    VBox demoCard = card();
                    demoCard.setAlignment(Pos.CENTER);
                    demoCard.getChildren().add(player);
                    content.getChildren().add(demoCard);
                    Motion.popIn(demoCard);
                    show.setDisable(true);
                }
                player.play();
            });
            HBox row = new HBox(show);
            row.setPadding(new Insets(10, 0, 0, 0));
            card.getChildren().add(row);
        }
        present(card);
    }

    private void present(VBox card) {
        content.getChildren().add(card);
        Motion.popIn(card);
        Motion.stagger(card.getChildren(), 24);
    }

    private static VBox card() {
        VBox card = new VBox(12);
        card.getStyleClass().add("help-card");
        return card;
    }

    private static Button button(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        Motion.interactive(b);
        return b;
    }

    private Label bodyLine(String line) {
        if (line.startsWith("> "))
            return label(line.substring(2), "help-code");
        if (line.startsWith("- "))
            return label("\u2022  " + line.substring(2), "help-bullet");
        return label(line, "help-body");
    }

    private static Label label(String text, String styleClass) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add(styleClass);
        return l;
    }
}