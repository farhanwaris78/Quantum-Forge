/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project;

import java.util.HashMap;
import java.util.Map;

import quantumforge.project.Project;

public abstract class ProjectActions<T> {

    protected Project project;

    protected QEFXProjectController controller;

    protected Map<T, ProjectAction> actions;

    public ProjectActions(Project project, QEFXProjectController controller) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }

        this.project = project;
        this.controller = controller;
        this.actions = new HashMap<T, ProjectAction>();
    }

    public abstract void actionInitially();
}
