/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.movie;

import java.io.File;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public class MovieProgress {

    private File file;

    private QEFXMovieProgressDialog dialog;

    protected MovieProgress(File file) {
        this.file = file;
        this.dialog = null;
    }

    protected void setProgress(double value) {
        if (this.dialog != null) {
            this.dialog.setProgress(value);
        }
    }

    protected void showProgress() {
        this.showProgress(null);
    }

    protected void showProgress(EventHandler<ActionEvent> handler) {
        if (this.dialog != null) {
            return;
        }

        if (this.file != null) {
            this.dialog = new QEFXMovieProgressDialog(this.file);
            this.dialog.setOnStopAction(handler);
            this.dialog.showProgress();
        }
    }

    protected void hideProgress() {
        if (this.dialog == null) {
            return;
        }

        this.dialog.hideProgress();
        this.dialog = null;
    }
}
