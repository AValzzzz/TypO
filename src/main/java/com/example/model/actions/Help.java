package com.example.model.actions;

import com.example.view.HelpWindow;

import javafx.stage.Window;

public class Help implements AppAction {
    private final Window owner;

    public Help() {
        this(null);
    }

    public Help(Window owner) {
        this.owner = owner;
    }

    @Override
    public void execute() {
        HelpWindow.open(owner);
    }
}