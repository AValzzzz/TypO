package com.example.model.syntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Language {
    record StringDef(String open, String close, boolean multiline, boolean escapes) {
    }

    final String name;
    final Set<String> keywords = new HashSet<>();
    final Set<String> types = new HashSet<>();
    final Set<String> literals = new HashSet<>();
    final List<String> lineComments = new ArrayList<>();
    final List<String[]> blockComments = new ArrayList<>();
    final List<StringDef> strings = new ArrayList<>();

    boolean ignoreCase;
    boolean functions = true;
    boolean capitalizedTypes;
    boolean nestedComments;
    boolean propertyStrings;
    boolean atWords;
    boolean directives;
    boolean dollarVars;
    boolean primes;
    boolean markup;
    boolean css;

    Language(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    Language ignoreCase() {
        ignoreCase = true;
        return this;
    }

    Language keywords(String words) {
        addWords(keywords, words);
        return this;
    }

    Language types(String words) {
        addWords(types, words);
        return this;
    }

    Language literals(String words) {
        addWords(literals, words);
        return this;
    }

    Language lineComment(String... prefixes) {
        lineComments.addAll(List.of(prefixes));
        return this;
    }

    Language blockComment(String open, String close) {
        blockComments.add(new String[] { open, close });
        return this;
    }

    Language string(String open, String close, boolean multiline, boolean escapes) {
        strings.add(new StringDef(open, close, multiline, escapes));
        strings.sort(Comparator.comparingInt((StringDef d) -> d.open().length()).reversed());
        return this;
    }

    Language noFunctions() {
        functions = false;
        return this;
    }

    Language capitalizedTypes() {
        capitalizedTypes = true;
        return this;
    }

    Language nestedComments() {
        nestedComments = true;
        return this;
    }

    Language propertyStrings() {
        propertyStrings = true;
        return this;
    }

    Language atWords() {
        atWords = true;
        return this;
    }

    Language directives() {
        directives = true;
        return this;
    }

    Language dollarVars() {
        dollarVars = true;
        return this;
    }

    Language primes() {
        primes = true;
        return this;
    }

    Language markup() {
        markup = true;
        return this;
    }

    Language css() {
        css = true;
        return this;
    }

    private void addWords(Set<String> target, String words) {
        if (words == null || words.isBlank())
            return;
        for (String w : words.trim().split("\\s+"))
            target.add(ignoreCase ? w.toLowerCase(Locale.ROOT) : w);
    }
}