package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;

import javafx.scene.Node;

public class MatrixCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^matrix\\{(.+)}$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public Node render(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return MathNodeFactory.matrix(m.group(1));
    }

    @Override
    public String renderPlaceholder(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();

        String[] rows = m.group(1).split(";");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.length; i++) {
            sb.append(rows[i].trim());
            if (i<rows.length - 1) sb.append(" | ");
        }
        return sb.append("]").toString();
    }
}
