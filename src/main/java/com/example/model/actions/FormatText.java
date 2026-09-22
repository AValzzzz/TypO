package com.example.model.actions;

import java.util.function.UnaryOperator;

import com.example.model.Page;
import com.example.model.TextStyle;

public class FormatText implements AppAction {
    private final Page page;
    private final UnaryOperator<TextStyle> change;

    public FormatText(Page page, UnaryOperator<TextStyle> change) {
        this.page = page;
        this.change = change;
    }

    @Override
    public void execute() {
        page.getEditor().updateSelectionStyle(change);
        page.getEditor().requestLayout();
    }
}
