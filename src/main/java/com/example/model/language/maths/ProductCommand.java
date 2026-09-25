package com.example.model.language.maths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class ProductCommand implements Command {
    private static final Pattern BOUNDED = Pattern.compile(
            "^prod_(?:\\(([^()]+)\\)|([\\p{L}\\p{N}]+))\\^(?:\\(([^()]+)\\)|([\\p{L}\\p{N}]+))\\((.+)\\)$");
    private static final Pattern SIMPLE = Pattern.compile("^prod\\((.+)\\)$");

    @Override
    public boolean matches(String raw) {
        return BOUNDED.matcher(raw).matches() || SIMPLE.matcher(raw).matches();
    }

    @Override
    public CommandResult apply(String raw) {
        Matcher b = BOUNDED.matcher(raw);
        if (b.matches()) {
            String lower = b.group(1) != null ? b.group(1) : b.group(2);
            String upper = b.group(3) != null ? b.group(3) : b.group(4);
            String rawData = lower + "|" + upper + "|" + b.group(5);
            return CommandResult.ofMathObject(new MathObject(MathObject.Type.PRODUCT, rawData));
        }
        Matcher s = SIMPLE.matcher(raw);
        s.matches();
        return CommandResult.ofMathObject(new MathObject(MathObject.Type.PRODUCT, "|" + "|" + s.group(1)));
    }
}
