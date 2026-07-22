package quantumforge.app.project.viewer.result.qc;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.qc.QEFXQCEditor;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

/** Result launcher adapted to the current graph viewer/editor contract. */
public final class QEFXQCButton extends QEFXResultButton<QEFXQCViewer, QEFXGraphEditor> {
    private final Project project;

    public static QEFXResultButtonWrapper<QEFXQCButton> getWrapper(
            QEFXProjectController projectController, Project project) {
        return () -> new QEFXQCButton(projectController, project);
    }

    private QEFXQCButton(QEFXProjectController projectController, Project project) {
        super(projectController, "Q-Capacitance", null);
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        this.project = project;
    }

    @Override
    protected QEFXQCViewer createResultViewer() throws IOException {
        return new QEFXQCViewer(this.projectController, this.project);
    }

    @Override
    protected QEFXGraphEditor createResultEditor(QEFXQCViewer resultViewer) throws IOException {
        return new QEFXQCEditor(this.projectController, resultViewer);
    }
}
