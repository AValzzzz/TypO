package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class LimitCommand implements Command {
    private static final Pattern BOUNDED = Pattern.compile("^lim_\\((\\w+)->(\\w+)\\)\\((.+)\\)$");
    private static final Pattern SIMPLE = Pattern.compile("^lim\\((.+)\\)$");

    @Override
    public boolean matches(String raw) {
        return BOUNDED.matcher(raw).matches() || SIMPLE.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher b = BOUNDED.matcher(raw);
        if (b.matches()) {
            String var = b.group(1);
            String target = b.group(2);

            if (target.equalsIgnoreCase("infinity")) target = "\u221E";

            String condition = var + "\u2192" + target;
            String rawData = condition + "|" + b.group(3);
            return CommandResult.ofMathObject(new MathObject(MathObject.Type.LIMIT, rawData));
        }
        Matcher s = SIMPLE.matcher(raw);
        s.matches();
        String rawData = "|" + s.group(1);
        return CommandResult.ofMathObject(new MathObject(MathObject.Type.LIMIT, rawData));
    }
}
