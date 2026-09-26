package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class ExponentCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile(
            "^(?!lim_|sum_|int_|prod_)([\\p{L}\\p{N}]+)\\^(?:\\((.+)\\)|([\\p{L}\\p{N}]+))$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        String base = m.group(1);
        String exponent = m.group(2) != null ? m.group(2) : m.group(3);
        return CommandResult.ofMathObject(new MathObject(MathObject.Type.EXPONENT, base + "," + exponent));
    }
}
