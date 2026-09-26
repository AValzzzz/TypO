package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class FractionCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^(\\w+)/(\\w+)$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        String rawArgs = m.group(1) + "," + m.group(2);
        return CommandResult.ofMathObject(new MathObject(MathObject.Type.FRACTION, rawArgs));
    }
}
