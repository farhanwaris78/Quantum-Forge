package quantumforge.app.project.viewer.result.convergence;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.convergence.QEFXConvergenceEditor;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

/** Result launcher adapted to the current graph viewer/editor contract. */
public final class QEFXConvergenceButton extends QEFXResultButton<QEFXConvergenceViewer, QEFXGraphEditor> {
    private final Project project;

    public static QEFXResultButtonWrapper<QEFXConvergenceButton> getWrapper(
            QEFXProjectController projectController, Project project) {
        return () -> new QEFXConvergenceButton(projectController, project);
    }

    private QEFXConvergenceButton(QEFXProjectController projectController, Project project) {
        super(projectController, "Convergence", null);
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        this.project = project;
    }

    @Override
    protected QEFXConvergenceViewer createResultViewer() throws IOException {
        return new QEFXConvergenceViewer(this.projectController, this.project);
    }

    @Override
    protected QEFXGraphEditor createResultEditor(QEFXConvergenceViewer resultViewer) throws IOException {
        return new QEFXConvergenceEditor(this.projectController, resultViewer);
    }
}
