/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.run;

import javafx.application.Platform;
import quantumforge.input.QEInput;
import quantumforge.project.Project;

public class FXQEInputFactory {

    private RunningType type;

    private QEInput qeInput;

    private boolean hasQEInput;

    protected FXQEInputFactory(RunningType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null.");
        }

        this.type = type;
        this.qeInput = null;
        this.hasQEInput = false;
    }

    protected QEInput getQEInput(Project project) {
        if (project == null) {
            return null;
        }

        this.hasQEInput = false;

        Platform.runLater(() -> {
            project.resolveQEInputs();
            this.qeInput = this.type.getQEInput(project);

            synchronized (this) {
                this.hasQEInput = true;
                this.notifyAll();
            }
        });

        synchronized (this) {
            while (!this.hasQEInput) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        this.hasQEInput = false;

        return this.qeInput;
    }
}
