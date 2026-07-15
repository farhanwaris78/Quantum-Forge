/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor;

import quantumforge.app.project.ProjectAction;
import quantumforge.app.project.ProjectActions;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.project.Project;

public abstract class EditorActions extends ProjectActions<String> {

    public EditorActions(Project project, QEFXProjectController controller) {
        super(project, controller);

        this.setupOnEditorSelected();
    }

    public void attach() {
        this.setupOnEditorSelected();
    }

    public void detach() {
        this.controller.setOnEditorSelected(null);
    }

    private void setupOnEditorSelected() {
        if (this.controller == null) {
            return;
        }

        this.controller.setOnEditorSelected(text -> {
            if (text == null || text.isEmpty()) {
                return;
            }

            ProjectAction action = null;
            if (this.actions != null) {
                action = this.actions.get(text);
            }

            if (action != null && this.controller != null) {
                action.actionOnProject(this.controller);
            }
        });
    }
}
