package com.example.view;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.ContextMenuEvent;

final class MenuSlot {
    private ContextMenu open;

    void show(ContextMenu menu, Node owner, ContextMenuEvent e) {
        if (isShowing())
            open.hide();
        open = menu;
        menu.show(owner, e.getScreenX(), e.getScreenY());
    }

    boolean isShowing() {
        return open != null && open.isShowing();
    }
}
