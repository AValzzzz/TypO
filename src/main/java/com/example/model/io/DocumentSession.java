package com.example.model.io;

import java.nio.file.Path;

public class DocumentSession {
    private Path currentFile;
    private String savedSnapshot;

    public Path getCurrentFile() {
        return currentFile;
    }    

    public void setCurrentFile(Path currentFile) {
        this.currentFile = currentFile;
    }

    public void markSaved(String snapshot) {
        this.savedSnapshot = snapshot;
    }

    public boolean isModified(String snapshot) {
        return !snapshot.equals(savedSnapshot);
    }
}
