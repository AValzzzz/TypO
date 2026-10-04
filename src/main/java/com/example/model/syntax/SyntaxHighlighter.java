package com.example.model.syntax;

import java.util.Locale;

public final class SyntaxHighlighter {
    private SyntaxHighlighter() {
    }

    public static TokenType[] highlight(String text, Language lang) {
        int n = text.length();
        TokenType[] out = new TokenType[n];
        int depth = 0; 
        boolean inTag = false; 
        int i = 0;

        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            int cEnd = commentEnd(text, i, lang);
            if (cEnd > i) {
                fill(out, i, cEnd, TokenType.COMMENT);
                i = cEnd;
                continue;
            }

            if (lang.markup) {
                if (!inTag) {
                    if (c == '<') {
                        int j = i + 1;
                        if (j < n && "/!?".indexOf(text.charAt(j)) >= 0)
                            j++;
                        int k = j;
                        while (k < n && isTagChar(text.charAt(k)))
                            k++;
                        if (k > j && Character.isLetter(text.charAt(j))) {
                            fill(out, j, k, TokenType.KEYWORD);
                            inTag = true;
                            i = k;
                            continue;
                        }
                    }
                    i++;
                    continue;
                }
                if (c == '>') {
                    inTag = false;
                    i++;
                    continue;
                }
                int tagString = stringEnd(text, i, lang);
                if (tagString > 0) {
                    fill(out, i, tagString, TokenType.STRING);
                    i = tagString;
                    continue;
                }
                if (Character.isLetter(c) || c == '_' || c == ':' || c == '@') {
                    int k = i + 1;
                    while (k < n && isTagChar(text.charAt(k)))
                        k++;
                    fill(out, i, k, TokenType.PROPERTY);
                    i = k;
                    continue;
                }
                i++;
                continue;
            }

            if (lang.css) {
                if (c == '{') {
                    depth++;
                    i++;
                    continue;
                }
                if (c == '}') {
                    depth = Math.max(0, depth - 1);
                    i++;
                    continue;
                }
                if ((c == '#' || c == '.') && i + 1 < n) {
                    int j = i + 1;
                    while (j < n && isCssIdentChar(text.charAt(j)))
                        j++;
                    boolean selectorClass = c == '.' && depth == 0 && Character.isLetter(text.charAt(i + 1));
                    if (j > i + 1 && (c == '#' || selectorClass)) {
                        fill(out, i, j, (c == '#' && depth > 0) ? TokenType.NUMBER : TokenType.TYPE);
                        i = j;
                        continue;
                    }
                }
            }

            if (lang.atWords && c == '@' && i + 1 < n && Character.isLetter(text.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_'
                        || (lang.css && text.charAt(j) == '-')))
                    j++;
                fill(out, i, j, TokenType.KEYWORD);
                i = j;
                continue;
            }

            if (lang.dollarVars && c == '$' && i + 1 < n) {
                int j = dollarEnd(text, i);
                if (j > 0) {
                    fill(out, i, j, TokenType.PROPERTY);
                    i = j;
                    continue;
                }
            }

            if (lang.directives && c == '#' && atLineStart(text, i)) {
                int j = i + 1;
                while (j < n && (text.charAt(j) == ' ' || text.charAt(j) == '\t'))
                    j++;
                int k = j;
                while (k < n && Character.isLetter(text.charAt(k)))
                    k++;
                if (k > j) {
                    fill(out, i, k, TokenType.KEYWORD);
                    i = k;
                    continue;
                }
            }

            if (lang.primes && c == '\'') {
                int ce = charLiteralEnd(text, i);
                if (ce > 0) {
                    fill(out, i, ce, TokenType.STRING);
                    i = ce;
                    continue;
                }
                int j = i + 1;
                if (j < n && (Character.isLetter(text.charAt(j)) || text.charAt(j) == '_')) {
                    while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_'))
                        j++;
                    fill(out, i, j, TokenType.TYPE);
                    i = j;
                    continue;
                }
                i++;
                continue;
            }

            int sEnd = stringEnd(text, i, lang);
            if (sEnd > 0) {
                TokenType type = TokenType.STRING;
                if (lang.propertyStrings) {
                    int k = sEnd;
                    while (k < n && Character.isWhitespace(text.charAt(k)))
                        k++;
                    if (k < n && text.charAt(k) == ':')
                        type = TokenType.PROPERTY;
                }
                fill(out, i, sEnd, type);
                i = sEnd;
                continue;
            }

            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int j = numberEnd(text, i);
                fill(out, i, j, TokenType.NUMBER);
                i = j;
                continue;
            }

            if (isIdentStart(text, i, lang)) {
                int j = i + 1;
                while (j < n && isIdentPart(text.charAt(j), lang))
                    j++;
                TokenType type = classify(text, text.substring(i, j), j, depth, lang);
                if (type != null)
                    fill(out, i, j, type);
                i = j;
                continue;
            }

            i++;
        }
        return out;
    }

    private static TokenType classify(String text, String word, int end, int depth, Language lang) {
        String w = lang.ignoreCase ? word.toLowerCase(Locale.ROOT) : word;
        if (lang.keywords.contains(w))
            return TokenType.KEYWORD;
        if (lang.types.contains(w))
            return TokenType.TYPE;
        if (lang.literals.contains(w))
            return TokenType.NUMBER;

        int next = skipBlanks(text, end);
        char nc = next < text.length() ? text.charAt(next) : '\0';
        if (lang.css && depth > 0 && nc == ':' && !(next + 1 < text.length() && text.charAt(next + 1) == ':'))
            return TokenType.PROPERTY;
        if (lang.functions && nc == '(')
            return TokenType.FUNCTION;
        if (lang.capitalizedTypes && isCapitalized(word))
            return TokenType.TYPE;
        return null;
    }

    private static int commentEnd(String text, int i, Language lang) {
        int n = text.length();
        for (String prefix : lang.lineComments) {
            if (text.startsWith(prefix, i)) {
                int nl = text.indexOf('\n', i);
                return nl < 0 ? n : nl;
            }
        }
        for (String[] block : lang.blockComments) {
            String open = block[0], close = block[1];
            if (!text.startsWith(open, i))
                continue;
            int j = i + open.length();
            int level = 1;
            while (j < n) {
                if (lang.nestedComments && text.startsWith(open, j)) {
                    level++;
                    j += open.length();
                } else if (text.startsWith(close, j)) {
                    level--;
                    j += close.length();
                    if (level == 0)
                        return j;
                } else {
                    j++;
                }
            }
            return n;
        }
        return -1;
    }

    private static int stringEnd(String text, int i, Language lang) {
        int n = text.length();
        for (Language.StringDef d : lang.strings) {
            if (!text.startsWith(d.open(), i))
                continue;
            int j = i + d.open().length();
            while (j < n) {
                char ch = text.charAt(j);
                if (d.escapes() && ch == '\\' && j + 1 < n && (d.multiline() || text.charAt(j + 1) != '\n')) {
                    j += 2;
                    continue;
                }
                if (text.startsWith(d.close(), j))
                    return j + d.close().length();
                if (!d.multiline() && ch == '\n')
                    return j;
                j++;
            }
            return n;
        }
        return -1;
    }

    private static int numberEnd(String text, int i) {
        int n = text.length();
        int j = i + 1;
        boolean hex = text.charAt(i) == '0' && j < n && (text.charAt(j) == 'x' || text.charAt(j) == 'X');
        while (j < n) {
            char ch = text.charAt(j);
            if (Character.isLetterOrDigit(ch) || ch == '_')
                j++;
            else if (ch == '.' && j + 1 < n && Character.isDigit(text.charAt(j + 1)))
                j++;
            else if ((ch == '+' || ch == '-') && !hex && (text.charAt(j - 1) == 'e' || text.charAt(j - 1) == 'E'))
                j++;
            else
                break;
        }
        return j;
    }

    private static int charLiteralEnd(String text, int i) {
        int n = text.length();
        if (i + 2 >= n)
            return -1;
        char a = text.charAt(i + 1);
        if (a == '\\') {
            for (int j = i + 3; j < Math.min(n, i + 8); j++) {
                char ch = text.charAt(j);
                if (ch == '\n')
                    return -1;
                if (ch == '\'')
                    return j + 1;
            }
            return -1;
        }
        if (a != '\n' && a != '\'' && text.charAt(i + 2) == '\'')
            return i + 3;
        return -1;
    }

    private static int dollarEnd(String text, int i) {
        int n = text.length();
        char d = text.charAt(i + 1);
        if (d == '{') {
            int close = text.indexOf('}', i + 2);
            int nl = text.indexOf('\n', i);
            return (close > 0 && (nl < 0 || close < nl)) ? close + 1 : -1;
        }
        if (Character.isLetter(d) || d == '_') {
            int j = i + 2;
            while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_'))
                j++;
            return j;
        }
        if (Character.isDigit(d) || "?#@*!$-".indexOf(d) >= 0)
            return i + 2;
        return -1;
    }

    private static boolean atLineStart(String text, int i) {
        for (int k = i - 1; k >= 0; k--) {
            char ch = text.charAt(k);
            if (ch == '\n')
                return true;
            if (!Character.isWhitespace(ch))
                return false;
        }
        return true;
    }

    private static int skipBlanks(String text, int from) {
        int k = from;
        while (k < text.length() && (text.charAt(k) == ' ' || text.charAt(k) == '\t'))
            k++;
        return k;
    }

    private static boolean isIdentStart(String text, int i, Language lang) {
        char c = text.charAt(i);
        if (Character.isLetter(c) || c == '_')
            return true;
        if (i + 1 >= text.length())
            return false;
        char next = text.charAt(i + 1);
        if (c == '$')
            return Character.isLetter(next) || next == '_';
        return lang.css && c == '-' && (Character.isLetter(next) || next == '-');
    }

    private static boolean isIdentPart(char c, Language lang) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$'
                || (lang.css && c == '-') || (lang.primes && c == '\'');
    }

    private static boolean isTagChar(char c) {
        return Character.isLetterOrDigit(c) || "-_:.@".indexOf(c) >= 0;
    }

    private static boolean isCssIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static boolean isCapitalized(String word) {
        if (!Character.isUpperCase(word.charAt(0)))
            return false;
        for (int k = 1; k < word.length(); k++)
            if (Character.isLowerCase(word.charAt(k)))
                return true;
        return false;
    }

    private static void fill(TokenType[] out, int from, int to, TokenType type) {
        for (int k = from; k < to && k < out.length; k++)
            out[k] = type;
    }
}