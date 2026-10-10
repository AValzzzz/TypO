package com.example.view;

import javafx.application.Platform;

public final class CoalescedTask {
    private final Runnable task;
    private boolean scheduled;

    public CoalescedTask(Runnable task) {
        this.task = task;
    }

    public void schedule() {
        if (scheduled)
            return;
        scheduled = true;
        Platform.runLater(() -> {
            scheduled = false;
            task.run();
        });
    }
}
