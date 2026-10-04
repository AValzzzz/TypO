package com.example.model.language;

import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;
import com.example.model.language.shapes.ShapeType;

import java.util.function.UnaryOperator;

public class CommandResult {
    private final String text;
    private final int styleStart;
    private final int styleEnd;
    private final UnaryOperator<TextStyle> styleChange;
    private MathObject mathObject;
    private ShapeType shape;
    private boolean arrow = false;
    private boolean table = false;

    public CommandResult(String text) {
        this(text, 0, 0, null, null);
    }

    public CommandResult(String text, int styleStart, int styleEnd, UnaryOperator<TextStyle> styleChange,
            MathObject mathObject) {
        this.text = text;
        this.styleStart = styleStart;
        this.styleEnd = styleEnd;
        this.styleChange = styleChange;
        this.mathObject = mathObject;
    }

    public static CommandResult ofMathObject(MathObject obj) {
        CommandResult r = new CommandResult("");
        r.mathObject = obj;
        return r;
    }

    public static CommandResult ofShape(ShapeType type) {
        CommandResult r = new CommandResult("");
        r.shape = type;
        return r;
    }

    public static CommandResult ofArrow() {
        CommandResult r = new CommandResult("");
        r.arrow = true;
        return r;
    }

    public static CommandResult ofTable() {
        CommandResult r = new CommandResult("");
        r.table = true;
        return r;
    }

    public boolean isArrow() {
        return arrow;
    }

    public boolean isTable() {
        return table;
    }

    public String getText() {
        return text;
    }

    public boolean hasStyle() {
        return styleChange != null;
    }

    public int getStyleStart() {
        return styleStart;
    }

    public int getStyleEnd() {
        return styleEnd;
    }

    public boolean isMathObject() {
        return mathObject != null;
    }

    public MathObject getMathObject() {
        return mathObject;
    }

    public boolean isShape() {
        return shape != null;
    }

    public ShapeType getShape() {
        return shape;
    }

    public UnaryOperator<TextStyle> getStyleChange() {
        return styleChange;
    }
}
