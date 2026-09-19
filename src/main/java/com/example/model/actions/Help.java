package com.example.model.actions;

public class Help implements AppAction {
    @Override
    public void execute() {
        System.out.println("Help triggered");
        // TODO: open help dialog/window
    }
}