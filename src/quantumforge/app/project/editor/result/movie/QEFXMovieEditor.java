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

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.app.project.viewer.result.movie.QEFXMovieViewer;
import quantumforge.project.Project;

public class QEFXMovieEditor extends QEFXResultEditor<QEFXMovieEditorController> {

    public QEFXMovieEditor(QEFXProjectController projectController, Project project, QEFXMovieViewer viewer) throws IOException {
        super("QEFXMovieEditor.fxml",
                new QEFXMovieEditorController(projectController, viewer == null ? null : viewer.getController(), project));
    }

}
