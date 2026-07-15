/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer.body;

public abstract class FileQueue {

    private FileElementDeleted onFileElementDeleted;

    protected FileQueue() {
        this.onFileElementDeleted = null;
    }

    public abstract FileElement pollFileElement();

    public abstract FileElement peekFileElement();

    public abstract void addFileElement(FileElement fileElement);

    public abstract boolean hasFileElements();

    public abstract void stopFileElements();

    public void setOnFileElementDeleted(FileElementDeleted onFileElementDeleted) {
        this.onFileElementDeleted = onFileElementDeleted;
    }

    protected void actionOnFileElementDeleted(FileElement fileElement) {
        if (this.onFileElementDeleted != null) {
            this.onFileElementDeleted.onFileElementDeleted(fileElement);
        }
    }
}
