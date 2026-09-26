package com.example.model.language.maths;

import com.example.model.TextStyle;
import java.util.Optional;

import org.fxmisc.richtext.model.SegmentOpsBase;

public class MathObjectSegmentOps extends SegmentOpsBase<MathObject,TextStyle> {

    public MathObjectSegmentOps() {
        super(MathObject.EMPTY);
    }

    @Override
    public int length(MathObject seg) {
        return seg == MathObject.EMPTY ? 0 : 1;
    }

    @Override
    public char realCharAt(MathObject seg, int index) {
        return '\uFFFC';
    }

    @Override
    public String realGetText(MathObject seg) {
        return "\uFFFC";
    }

    @Override
    public MathObject realSubSequence(MathObject seg, int start, int end) {
        return seg;
    }

    @Override
    public Optional<MathObject> joinSeg(MathObject currentSeg, MathObject nextSeg) {
        return Optional.empty();
    }
}
