package com.example.model.language.maths;

import com.example.model.language.CommandRegistry;

public class MathCommands {
    private MathCommands() {}

    public static void registerAll(CommandRegistry registry) {
        registry.register(new FractionCommand());
        registry.register(new ExponentCommand());
        registry.register(new SubscriptCommand());
        registry.register(new SqrtCommand());
        registry.register(new MatrixCommand());
    }
}
