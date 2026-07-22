package quantumforge.app.project.viewer.result.elastic;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.elastic.QEFXElasticEditor;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

/** Result launcher adapted to the current graph viewer/editor contract. */
public final class QEFXElasticButton extends QEFXResultButton<QEFXElasticViewer, QEFXGraphEditor> {
    private final Project project;

    public static QEFXResultButtonWrapper<QEFXElasticButton> getWrapper(
            QEFXProjectController projectController, Project project) {
        return () -> new QEFXElasticButton(projectController, project);
    }

    private QEFXElasticButton(QEFXProjectController projectController, Project project) {
        super(projectController, "Elasticity", null);
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        this.project = project;
    }

    @Override
    protected QEFXElasticViewer createResultViewer() throws IOException {
        return new QEFXElasticViewer(this.projectController, this.project);
    }

    @Override
    protected QEFXGraphEditor createResultEditor(QEFXElasticViewer resultViewer) throws IOException {
        return new QEFXElasticEditor(this.projectController, resultViewer);
    }
}
