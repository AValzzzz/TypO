package com.example.model.language;

import com.example.model.TextStyle;
import com.example.model.language.maths.MathObject;

import java.util.function.UnaryOperator;

public class CommandResult {
    private final String text;
    private final int styleStart;
    private final int styleEnd;
    private final UnaryOperator<TextStyle> styleChange;
    private MathObject mathObject;

    public CommandResult(String text) {
        this(text, 0, 0, null, null);
    }

    public CommandResult (String text, int styleStart, int styleEnd, UnaryOperator<TextStyle> styleChange, MathObject mathObject) {
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

    public boolean isMathObject() {return mathObject != null;}

    public MathObject getMathObject() {
        return mathObject;
    }

    public UnaryOperator<TextStyle> getStyleChange() {
        return styleChange;
    }
}
