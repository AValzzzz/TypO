package com.example.model.language.shapes;

import java.util.Map;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class ShapeCommand implements Command {
    private static final Map<String, ShapeType> SHAPES = Map.of(
        "circle", ShapeType.CIRCLE,
        "square", ShapeType.SQUARE,
        "triangle", ShapeType.TRIANGLE
    );

    @Override
    public boolean matches(String raw) {
        return SHAPES.containsKey(raw);
    }

    @Override
    public CommandResult apply(String raw) {
        return CommandResult.ofShape(SHAPES.get(raw));
    }
}
