package com.example.model.language.maths;

import java.util.HashMap;
import java.util.Map;

import com.example.model.language.Command;
import com.example.model.language.CommandResult;

public class SymbolCommand implements Command {

    private static final Map<String, String> SYMBOLS = new HashMap<>();

    static {
        SYMBOLS.put("pi", "\u03C0");
        SYMBOLS.put("alpha", "\u03B1");
        SYMBOLS.put("beta", "\u03B2");
        SYMBOLS.put("theta", "\u03B8");
        SYMBOLS.put("lambda", "\u03BB");
        SYMBOLS.put("phi", "\u03C6");
        SYMBOLS.put("psi", "\u03C8");
        SYMBOLS.put("delta", "\u03B4");
        SYMBOLS.put("<=", "\u2264");
        SYMBOLS.put(">=", "\u2265");
        SYMBOLS.put("~=", "\u2248");
        SYMBOLS.put("!=", "\u2260");
        SYMBOLS.put("in", "\u2208");
        SYMBOLS.put("infinity", "\u221E");
        SYMBOLS.put("inf", "\u221E");
        SYMBOLS.put("pm", "\u00B1");
        SYMBOLS.put("times", "\u00D7");
        SYMBOLS.put("->", "\u2192");
        SYMBOLS.put("=>", "\u21D2");
        SYMBOLS.put("<=>", "\u21D4");
    }

    @Override
    public boolean matches(String raw) {
        return SYMBOLS.containsKey(raw);
    }

    @Override
    public CommandResult apply(String raw) {
        return new CommandResult(SYMBOLS.get(raw));
    }

}
