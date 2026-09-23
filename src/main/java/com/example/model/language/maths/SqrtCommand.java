package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;

import javafx.scene.Node;

public class SqrtCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^sqrt\\{(.+)}$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public Node render(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return MathNodeFactory.sqrt(m.group(1));
    }

    @Override
    public String renderPlaceholder(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return "\u221A(" + m.group(1) + ")";
    }
}
