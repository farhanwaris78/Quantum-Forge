/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.file;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import quantumforge.app.QEFXMain;

public class FileProgress {

    private static final long SLEEP_TIME = 500L;

    private String message;

    private boolean result;

    private boolean inProgress;

    private Alert alert;

    public FileProgress(String message) {
        this.message = message;
        this.result = false;
        this.inProgress = false;
        this.alert = null;
    }

    public void runFileOperation(FileOperation fileOperation) {
        synchronized (this) {
            this.inProgress = true;
        }

        Thread thread = new Thread(() -> {
            boolean result_ = false;
            if (fileOperation != null) {
                result_ = fileOperation.operate();
            }

            synchronized (this) {
                this.result = result_;
                this.inProgress = false;
                this.notifyAll();
            }
        });

        thread.start();
    }

    public synchronized boolean waitProgress() {
        while (this.inProgress) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (this.alert != null) {
            this.alert.getButtonTypes().add(ButtonType.CLOSE);
            this.alert.hide();
        }

        return this.result;
    }

    public synchronized void showProgress() {
        if (!this.inProgress) {
            return;
        }

        try {
            this.wait(SLEEP_TIME);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!this.inProgress) {
            return;
        }

        this.alert = new Alert(AlertType.INFORMATION);
        QEFXMain.initializeDialogOwner(this.alert);
        this.alert.setHeaderText(this.message == null ? "" : this.message);
        this.alert.getButtonTypes().clear();
        this.alert.show();
    }
}
