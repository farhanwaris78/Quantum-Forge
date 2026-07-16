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

import java.util.Date;

import quantumforge.project.ProjectSchema;
import quantumforge.ver.Version;

public class ProjectStatus {

    private int schemaVersion;
    private String quantumforgeVersion;

    private String date;

    private String cellAxis;
    private boolean molecule;

    private int scfCount;
    private int optCount;
    private int mdCount;
    private int dosCount;
    private int bandCount;

    public ProjectStatus() {
        this.schemaVersion = ProjectSchema.CURRENT_VERSION;
        this.quantumforgeVersion = Version.VERSION;
        this.updateDate();

        this.cellAxis = null;
        this.molecule = false;

        this.scfCount = 0;
        this.optCount = 0;
        this.mdCount = 0;
        this.dosCount = 0;
        this.bandCount = 0;
    }

    public synchronized int getSchemaVersion() {
        return ProjectSchema.normalize(this.schemaVersion);
    }

    public synchronized void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public synchronized String getQuantumforgeVersion() {
        return this.quantumforgeVersion;
    }

    public synchronized void setQuantumforgeVersion(String quantumforgeVersion) {
        this.quantumforgeVersion = quantumforgeVersion;
    }

    /**
     * Ensure schema metadata is present before serialization.
     * Older projects without these fields are treated as schema v1.
     */
    public synchronized void ensureSchemaMetadata() {
        this.schemaVersion = ProjectSchema.normalize(this.schemaVersion);
        ProjectSchema.requireSupported(this.schemaVersion);
        if (this.quantumforgeVersion == null || this.quantumforgeVersion.isBlank()) {
            this.quantumforgeVersion = Version.VERSION;
        }
    }

    private void updateDate() {
        Date objDate = new Date();
        this.date = objDate.toString();
    }

    public synchronized String getDate() {
        return this.date;
    }

    public synchronized String getCellAxis() {
        return this.cellAxis;
    }

    public synchronized void setCellAxis(String cellAxis) {
        this.cellAxis = cellAxis;
    }

    public synchronized boolean isMolecule() {
        return this.molecule;
    }

    public synchronized void setMolecule(boolean molecule) {
        this.molecule = molecule;
    }

    public synchronized boolean isScfDone() {
        return this.scfCount > 0;
    }

    public synchronized int getScfCount() {
        return this.scfCount;
    }

    public synchronized void updateScfCount() {
        this.updateDate();
        this.scfCount++;
    }

    public synchronized boolean isOptDone() {
        return this.optCount > 0;
    }

    public synchronized int getOptCount() {
        return this.optCount;
    }

    public synchronized void updateOptDone() {
        this.updateDate();
        this.optCount++;
    }

    public synchronized boolean isMdDone() {
        return this.mdCount > 0;
    }

    public synchronized int getMdCount() {
        return this.mdCount;
    }

    public synchronized void updateMdCount() {
        this.updateDate();
        this.mdCount++;
    }

    public synchronized boolean isDosDone() {
        return this.dosCount > 0;
    }

    public synchronized int getDosCount() {
        return this.dosCount;
    }

    public synchronized void updateDosCount() {
        this.updateDate();
        this.dosCount++;
    }

    public synchronized boolean isBandDone() {
        return this.bandCount > 0;
    }

    public synchronized int getBandCount() {
        return this.bandCount;
    }

    public synchronized void updateBandDone() {
        this.updateDate();
        this.bandCount++;
    }
}
