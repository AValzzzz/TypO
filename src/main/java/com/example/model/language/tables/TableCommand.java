package com.example.model.language.tables;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class TableCommand implements Command {
    @Override
    public boolean matches(String raw) {
        return "tab".equals(raw);
    }

    @Override
    public CommandResult apply(String raw) {
        return CommandResult.ofTable();
    }
}