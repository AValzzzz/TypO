package com.example.model.language.maths;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class MathExpressionCommand implements Command {
    @Override
    public boolean matches(String raw) {
        return MathSyntax.toObject(raw) != null;
    }

    @Override
    public CommandResult apply(String raw) {
        return CommandResult.ofMathObject(MathSyntax.toObject(raw));
    }
}
