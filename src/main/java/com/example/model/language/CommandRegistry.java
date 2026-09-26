package com.example.model.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandRegistry {
    private final List<Command> commands = new ArrayList<>();

    public void register (Command command) {
        commands.add(command);
    }

    public Optional<Command> find(String raw) {
        return commands.stream().filter(c -> c.matches(raw)).findFirst();
    }
}
