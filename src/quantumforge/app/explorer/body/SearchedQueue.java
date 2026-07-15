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

import java.io.File;
import java.util.List;

import quantumforge.matapi.MaterialsAPIQueue;
import quantumforge.project.Project;

public class SearchedQueue extends FileQueue {

    private MaterialsAPIQueue matApiQueue;

    private List<Project> shownProjects;

    protected SearchedQueue(MaterialsAPIQueue matApiQueue, List<Project> shownProjects) {
        if (matApiQueue == null) {
            throw new IllegalArgumentException("matApiQueue is null.");
        }

        this.matApiQueue = matApiQueue;
        this.shownProjects = shownProjects;
    }

    @Override
    public FileElement pollFileElement() {
        File file = this.matApiQueue.pollCIFFile();

        String path = null;
        if (file != null) {
            path = file.getPath();
        }

        return this.createFileElement(path);
    }

    @Override
    public FileElement peekFileElement() {
        File file = this.matApiQueue.peekCIFFile();

        String path = null;
        if (file != null) {
            path = file.getPath();
        }

        return this.createFileElement(path);
    }

    private FileElement createFileElement(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        FileElement fileElement = new FileElement(path);

        if (this.shownProjects != null && (!this.shownProjects.isEmpty())) {
            for (Project project : this.shownProjects) {
                if (project != null && project.isRelatedFile(path)) {
                    fileElement.setProject(project);
                    break;
                }
            }
        }

        return fileElement;
    }

    @Override
    public void addFileElement(FileElement fileElement) {
        throw new RuntimeException("cannot call SearchedQueue#addFileElement.");
    }

    @Override
    public boolean hasFileElements() {
        return this.matApiQueue.hasCIFFiles();
    }

    @Override
    public void stopFileElements() {
        this.matApiQueue.stopCIFFiles();
    }
}
