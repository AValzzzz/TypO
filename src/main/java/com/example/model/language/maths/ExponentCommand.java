package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;

import javafx.scene.Node;

public class ExponentCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^(\\w+)\\^(\\w+)$");
    private static final String SUPERSCRIPT_DIGITS = "\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079";

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public Node render(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return MathNodeFactory.exponent(m.group(1), m.group(2));
    }

    @Override
    public String renderPlaceholder(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        String base = m.group(1);
        String exponent = m.group(2);
        StringBuilder sb = new StringBuilder(base);
        for (char c : exponent.toCharArray()) {
            if (Character.isDigit(c))
                sb.append(SUPERSCRIPT_DIGITS.charAt(c - '0'));
            else
                return base + "^" + exponent;
        }
        return sb.toString();
    }
}
