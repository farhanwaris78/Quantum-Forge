/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer.body;

import quantumforge.project.Project;
import quantumforge.run.RunningNode;

public class FileElement {

    private String name;

    private int position;

    private boolean swapping;

    private Project project;

    private RunningNode runningNode;

    protected FileElement(String name) {
        this(name, -1);
    }

    protected FileElement(String name, int position) {
        this(name, position, false);
    }

    protected FileElement(String name, int position, boolean swapping) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is empty.");
        }

        this.name = name;
        this.position = position;
        this.swapping = swapping;
        this.project = null;
        this.runningNode = null;
    }

    protected String getName() {
        return this.name;
    }

    protected int getPosition() {
        return this.position;
    }

    protected boolean isSwapping() {
        return this.swapping;
    }

    protected void setProject(Project project) {
        this.project = project;
    }

    protected Project getProject() {
        return this.project;
    }

    protected void setRunningNode(RunningNode runningNode) {
        this.runningNode = runningNode;
    }

    protected RunningNode getRunningNode() {
        return this.runningNode;
    }
}
