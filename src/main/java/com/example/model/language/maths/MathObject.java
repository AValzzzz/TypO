package com.example.model.language.maths;

import java.util.Objects;

public class MathObject {
    public enum Type {
        FRACTION, EXPONENT, SUBSCRIPT, SQRT, MATRIX,
        SUM("\u03A3"), INTEGRAL("\u222B"), PRODUCT("\u03A0"),
        LIMIT, IMAGE;
        private final String symbol;
        Type() { this(null); }
        Type(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }
    }

    private final Type type;
    private final String raw;
    public static final MathObject EMPTY = new MathObject(null, "");

    public MathObject(Type type, String raw) {
        this.type = type;
        this.raw = raw;
    }

    public String getRaw() {
        return raw;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MathObject))
            return false;

        MathObject m = (MathObject) obj;
        return type == m.type && raw.equals(m.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, raw);
    }
}
