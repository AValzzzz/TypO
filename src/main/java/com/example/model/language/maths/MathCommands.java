package com.example.model.language.maths;

import com.example.model.language.CommandRegistry;

public class MathCommands {
    private MathCommands() {}

    public static void registerAll(CommandRegistry registry) {
        registry.register(new MathExpressionCommand());
        registry.register(new SymbolCommand());
    }
}
