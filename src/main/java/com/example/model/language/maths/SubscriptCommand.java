package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;

import javafx.scene.Node;

public class SubscriptCommand implements Command{
    private static final Pattern PATTERN = Pattern.compile("^(\\w+)_(\\w+)$");
    private static final String SUPERSCRIPT_DIGITS = "\u2080\u0081\u2081\u2082\u2083\u2084\u2084\u2085\u2086\u2087\u2088\u2089";

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public Node render(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return MathNodeFactory.subscript(m.group(1), m.group(2));
    }

    @Override
    public String renderPlaceholder(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        String base = m.group(1);
        String sub = m.group(2);
        StringBuilder sb = new StringBuilder(base);
        for (char c : sub.toCharArray()) {
            if (Character.isDigit(c))
                sb.append(SUPERSCRIPT_DIGITS.charAt(c - '0'));
            else
                return base + "_" + sub;
        }
        return sb.toString();
    }
}
