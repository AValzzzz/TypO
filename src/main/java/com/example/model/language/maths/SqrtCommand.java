package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class SqrtCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^sqrt\\((.+)\\)$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        return CommandResult.ofMathObject(new MathObject(MathObject.Type.SQRT, m.group(1)));
    }
}
