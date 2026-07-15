/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.matapi;

import java.io.File;

public class MaterialsAPIQueue {

    private boolean alive;

    private int cifIndex;

    private MaterialsAPILoader loader;

    public MaterialsAPIQueue(MaterialsAPILoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader is null.");
        }

        this.alive = true;

        this.cifIndex = 0;

        this.loader = loader;
    }

    private synchronized void setToBeDead() {
        this.alive = false;
    }

    private synchronized boolean isAlive() {
        return this.alive;
    }

    public File pollCIFFile() {
        File cifFile = null;

        synchronized (this.loader) {
            while (this.isAlive() &&
                    (!this.loader.isFinishedNosync()) && (this.cifIndex >= this.loader.numCIFFilesNosync())) {
                try {
                    this.loader.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (this.isAlive() && this.cifIndex < this.loader.numCIFFilesNosync()) {
                cifFile = this.loader.getCIFFileNosync(this.cifIndex);
                this.cifIndex++;
            }
        }

        if (this.isAlive()) {
            return cifFile;
        } else {
            return null;
        }
    }

    public File peekCIFFile() {
        File cifFile = null;

        synchronized (this.loader) {
            while (this.isAlive() &&
                    (!this.loader.isFinishedNosync()) && (this.cifIndex >= this.loader.numCIFFilesNosync())) {
                try {
                    this.loader.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (this.isAlive() && this.cifIndex < this.loader.numCIFFilesNosync()) {
                cifFile = this.loader.getCIFFileNosync(this.cifIndex);
            }
        }

        if (this.isAlive()) {
            return cifFile;
        } else {
            return null;
        }
    }

    public boolean hasCIFFiles() {
        return this.cifIndex < this.loader.numCIFFiles();
    }

    public void stopCIFFiles() {
        this.setToBeDead();

        synchronized (this.loader) {
            this.loader.notifyAll();
        }
    }
}
