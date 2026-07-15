/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.fx;

import javafx.application.Platform;

public class FXThread<R> {

    private boolean inFX;

    private boolean doneFX;

    private R result;

    private FXRunnable<? extends R> runnable;

    public FXThread(FXRunnable<? extends R> runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("runnable is null.");
        }

        this.inFX = false;
        this.doneFX = false;
        this.result = null;
        this.runnable = runnable;
    }

    public R getResult() {
        this.runAndWait();
        return this.result;
    }

    public void runAndWait() {
        if (this.doneFX) {
            return;
        }

        this.inFX = true;

        Platform.runLater(() -> {
            this.result = this.runnable.run();

            synchronized (this) {
                this.inFX = false;
                this.notifyAll();
            }
        });

        synchronized (this) {
            while (this.inFX) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        this.doneFX = true;
    }
}
