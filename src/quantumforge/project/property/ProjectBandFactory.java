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

public class ProjectBandFactory {

    private String path;

    private String prefix;

    private ProjectBand band;

    public ProjectBandFactory() {
        this.path = null;
        this.prefix = null;
        this.band = null;
    }

    protected void setPath(String path, String prefix) {
        this.path = path;
        this.prefix = prefix;
    }

    public ProjectBand getProjectBand() {
        if (this.path == null || this.path.isEmpty() || this.prefix == null || this.prefix.isEmpty()) {
            this.band = null;

        } else if (this.band != null && this.path.equals(this.band.getPath()) && this.prefix.equals(this.band.getPrefix())) {
            this.band.reload();

        } else {
            this.band = new ProjectBand(this.path, this.prefix);
        }

        return this.band;
    }
}
