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

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;

public class ProjectAnsatz {

    private List<Node> viewerPaneNodes;

    private Node viewerMenuGraphic;

    private Node editorPaneNode;

    private Node editorMenuGraphic;

    private boolean editorMenuDisable;

    private String editorMenuText;

    protected ProjectAnsatz() {
        this.viewerPaneNodes = null;
        this.viewerMenuGraphic = null;
        this.editorPaneNode = null;
        this.editorMenuGraphic = null;
        this.editorMenuDisable = false;
        this.editorMenuText = null;
    }

    protected List<Node> getViewerPaneNodes() {
        return this.viewerPaneNodes;
    }

    protected void setViewerPaneNodes(List<Node> viewerPaneNodes) {
        if (viewerPaneNodes == null || viewerPaneNodes.isEmpty()) {
            this.viewerPaneNodes = null;

        } else {
            this.viewerPaneNodes = new ArrayList<Node>();
            this.viewerPaneNodes.addAll(viewerPaneNodes);
        }
    }

    protected Node getViewerMenuGraphic() {
        return this.viewerMenuGraphic;
    }

    protected void setViewerMenuGraphic(Node viewerMenuGraphic) {
        this.viewerMenuGraphic = viewerMenuGraphic;
    }

    protected Node getEditorPaneNode() {
        return this.editorPaneNode;
    }

    protected void setEditorPaneNode(Node editorPaneNode) {
        this.editorPaneNode = editorPaneNode;
    }

    protected Node getEditorMenuGraphic() {
        return this.editorMenuGraphic;
    }

    protected void setEditorMenuGraphic(Node editorMenuGraphic) {
        this.editorMenuGraphic = editorMenuGraphic;
    }

    protected boolean isEditorMenuDisable() {
        return this.editorMenuDisable;
    }

    protected void setEditorMenuDisable(boolean editorMenuDisable) {
        this.editorMenuDisable = editorMenuDisable;
    }

    protected String getEditorMenuText() {
        return this.editorMenuText;
    }

    protected void setEditorMenuText(String editorMenuText) {
        this.editorMenuText = editorMenuText;
    }
}
