package com.example.model.actions;

import com.example.model.Page;

public class InsertArrow implements AppAction {
    private final Page page;

    public InsertArrow(Page page) {
        this.page = page;
    }

    @Override
    public void execute() {
        double cx = page.getPane().getWidth() / 2;
        double cy = page.getPane().getHeight() / 2;

        double startX = cx - 60, startY = cy + 40;
        double endX = cx + 60, endY = cy - 40;
        double controlX = cx, controlY = cy; 

        page.addArrowOverlay(startX, startY, endX, endY, controlX, controlY);
    }
}