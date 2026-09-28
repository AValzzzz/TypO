package com.example.model.actions;

import com.example.view.SettingsMenu;

import javafx.geometry.Side;
import javafx.scene.Node;

public class Settings implements AppAction {
    private final Node anchor;

    public Settings(Node anchor) {
        this.anchor = anchor;
    }

    @Override
    public void execute() {
        new SettingsMenu().show(anchor, Side.BOTTOM, 0, 0);
    }
}