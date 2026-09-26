package com.example.model.language;

public interface Command {
    boolean matches(String raw);

    CommandResult apply(String raw);
}
