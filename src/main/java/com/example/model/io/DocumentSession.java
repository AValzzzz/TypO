package com.example.model.io;

import java.nio.file.Path;

public class DocumentSession {
    private Path currentFile;
    
    public Path getCurrentFile() {
        return currentFile;
    }    

    public void setCurrentFile(Path currentFile) {
        this.currentFile = currentFile;
    }
}
