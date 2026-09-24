package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class ExponentCommand implements Command {
    private static final Pattern PATTERN = Pattern.compile("^([\\p{L}\\p{N}]+)\\^(?:\\((.+)\\)|([\\p{L}\\p{N}]+))$");

    @Override
    public boolean matches(String raw) {
        return PATTERN.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher m = PATTERN.matcher(raw);
        m.matches();
        String base = m.group(1);
        String exponent = m.group(2) != null ? m.group(2): m.group(3);
        String text = base + exponent;

        return new CommandResult(text, base.length(), text.length(), s -> s.withFontSize(9).withBaselineShift(-6.0), null);
    }
}
