package com.example.model.language.shapes;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class ArrowCommand implements Command {
    @Override
    public boolean matches(String raw) {
        return "arrow".equals(raw);
    }

    @Override
    public CommandResult apply(String raw) {
        return CommandResult.ofArrow();
    }
}