/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.project.property;

public class ProjectDosFactory {

    private String path;

    private String prefix;

    private ProjectDos dos;

    public ProjectDosFactory() {
        this.path = null;
        this.prefix = null;
        this.dos = null;
    }

    protected void setPath(String path, String prefix) {
        this.path = path;
        this.prefix = prefix;
    }

    public ProjectDos getProjectDos() {
        if (this.path == null || this.path.isEmpty() || this.prefix == null || this.prefix.isEmpty()) {
            this.dos = null;

        } else if (this.dos != null && this.path.equals(this.dos.getPath()) && this.prefix.equals(this.dos.getPrefix())) {
            this.dos.reload();

        } else {
            this.dos = new ProjectDos(this.path, this.prefix);
        }

        return this.dos;
    }
}
