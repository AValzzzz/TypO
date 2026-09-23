package com.example.model.language;

import javafx.scene.Node;

public interface Command {
    boolean matches(String raw);

    Node render(String raw);

    String renderPlaceholder(String raw);
}
