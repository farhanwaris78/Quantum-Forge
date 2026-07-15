/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result;

import quantumforge.app.QEFXAppController;
import quantumforge.app.project.QEFXProjectController;

public abstract class QEFXResultViewerController extends QEFXAppController {

    private static final long INTER_LOADING_TIME = 2500L;

    private boolean loading;
    private Object loadingLock;

    protected QEFXProjectController projectController;

    public QEFXResultViewerController(QEFXProjectController projectController) {
        super(projectController == null ? null : projectController.getMainController());

        if (projectController == null) {
            throw new IllegalArgumentException("projectController is null.");
        }

        this.loading = false;
        this.loadingLock = new Object();

        this.projectController = projectController;
    }

    public abstract void reload();

    public void reloadSafely() {
        synchronized (this.loadingLock) {
            if (this.loading) {
                return;
            }

            this.loading = true;
        }

        this.reload();

        Thread thread = new Thread(() -> {
            synchronized (this.loadingLock) {
                try {
                    this.loadingLock.wait(INTER_LOADING_TIME);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                this.loading = false;
            }
        });

        thread.start();
    }
}
