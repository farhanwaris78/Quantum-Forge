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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.log.AppLog;
import quantumforge.project.ProjectSchema;
import quantumforge.ver.Version;

public class ProjectProperty {

    private static final String FILE_NAME_STATUS = ".quantumforge.status";
    private static final String FILE_NAME_SCF = ".quantumforge.elec";
    private static final String FILE_NAME_FERMI = ".quantumforge.fermi";
    private static final String FILE_NAME_OPT = ".quantumforge.opt";
    private static final String FILE_NAME_MD = ".quantumforge.md";
    private static final String FILE_NAME_PATH = ".quantumforge.path";

    public static boolean hasStatus(String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            return false;
        }

        try {
            File file = new File(directoryPath, FILE_NAME_STATUS);
            if (file.isFile()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private String directoryPath;

    private String prefixName;

    private ProjectStatus status;

    private ProjectEnergies scfEnergies;

    private ProjectEnergies fermiEnergies;

    private ProjectGeometryList optList;

    private ProjectGeometryList mdList;

    private ProjectDosFactory dosFactory;

    private ProjectBandPaths bandPaths;

    private ProjectBandFactory bandFactory;

    public ProjectProperty(String directoryPath, String prefixName) {
        if (directoryPath == null) {
            throw new IllegalArgumentException("directoryPath is null.");
        }

        if (prefixName == null) {
            throw new IllegalArgumentException("prefixName is null.");
        }

        this.directoryPath = directoryPath;
        this.prefixName = prefixName;

        this.status = null;
        this.scfEnergies = null;
        this.fermiEnergies = null;
        this.optList = null;
        this.mdList = null;
        this.dosFactory = new ProjectDosFactory();
        this.dosFactory.setPath(this.directoryPath, this.prefixName);
        this.bandPaths = null;
        this.bandFactory = new ProjectBandFactory();
        this.bandFactory.setPath(this.directoryPath, this.prefixName);
    }

    public synchronized void copyProperty(ProjectProperty property) {
        if (property == null) {
            return;
        }

        this.status = property.getStatus();
        this.scfEnergies = property.getScfEnergies();
        this.fermiEnergies = property.getFermiEnergies();
        this.optList = property.getOptList();
        this.mdList = property.getMdList();
        this.dosFactory = property.getDosFactory();
        this.dosFactory.setPath(this.directoryPath, this.prefixName);
        this.bandPaths = property.getBandPaths();
        this.bandFactory = property.getBandFactory();
        this.bandFactory.setPath(this.directoryPath, this.prefixName);
    }

    public void saveProperty() {
        this.saveStatus();
        this.saveScfEnergies();
        this.saveFermiEnergies();
        this.saveOptList();
        this.saveMdList();
        this.saveBandPaths();
    }

    public synchronized ProjectStatus getStatus() {
        if (this.status == null) {
            this.createStatus();
        }

        return this.status;
    }

    public synchronized ProjectEnergies getScfEnergies() {
        if (this.scfEnergies == null) {
            this.createScfEnergies();
        }

        return this.scfEnergies;
    }

    public synchronized ProjectEnergies getFermiEnergies() {
        if (this.fermiEnergies == null) {
            this.createFermiEnergies();
        }

        return this.fermiEnergies;
    }

    public synchronized ProjectGeometryList getOptList() {
        if (this.optList == null) {
            this.createOptList();
        }

        return this.optList;
    }

    public synchronized ProjectGeometryList getMdList() {
        if (this.mdList == null) {
            this.createMdList();
        }

        return this.mdList;
    }

    public synchronized ProjectDosFactory getDosFactory() {
        return this.dosFactory;
    }

    public synchronized ProjectDos getDos() {
        return this.dosFactory == null ? null : this.dosFactory.getProjectDos();
    }

    public synchronized ProjectBandPaths getBandPaths() {
        if (this.bandPaths == null) {
            this.createBandPaths();
        }

        return this.bandPaths;
    }

    public synchronized ProjectBandFactory getBandFactory() {
        return this.bandFactory;
    }

    public synchronized ProjectBand getBand() {
        return this.bandFactory == null ? null : this.bandFactory.getProjectBand();
    }

    private void createStatus() {
        try {
            this.status = this.<ProjectStatus> readFile(FILE_NAME_STATUS, ProjectStatus.class);
        } catch (IOException e) {
            AppLog.warn("project", "Could not read project status: " + e.getMessage());
            this.status = null;
        }

        if (this.status == null) {
            this.status = new ProjectStatus();
        } else {
            try {
                this.status.ensureSchemaMetadata();
            } catch (IllegalStateException ex) {
                AppLog.error("project", "Unsupported project schema in " + this.directoryPath, ex);
                throw ex;
            }
        }
    }

    private void createScfEnergies() {
        try {
            this.scfEnergies = this.<ProjectEnergies> readFile(FILE_NAME_SCF, ProjectEnergies.class);
        } catch (IOException e) {
            this.scfEnergies = null;
        }

        if (this.scfEnergies == null) {
            this.scfEnergies = new ProjectEnergies();
        }
    }

    private void createFermiEnergies() {
        try {
            this.fermiEnergies = this.<ProjectEnergies> readFile(FILE_NAME_FERMI, ProjectEnergies.class);
        } catch (IOException e) {
            this.fermiEnergies = null;
        }

        if (this.fermiEnergies == null) {
            this.fermiEnergies = new ProjectEnergies();
        }
    }

    private void createOptList() {
        try {
            this.optList = this.<ProjectGeometryList> readFile(FILE_NAME_OPT, ProjectGeometryList.class);
        } catch (IOException e) {
            this.optList = null;
        }

        if (this.optList == null) {
            this.optList = new ProjectGeometryList();
        }
    }

    private void createMdList() {
        try {
            this.mdList = this.<ProjectGeometryList> readFile(FILE_NAME_MD, ProjectGeometryList.class);
        } catch (IOException e) {
            this.mdList = null;
        }

        if (this.mdList == null) {
            this.mdList = new ProjectGeometryList();
        }
    }

    private void createBandPaths() {
        try {
            this.bandPaths = this.<ProjectBandPaths> readFile(FILE_NAME_PATH, ProjectBandPaths.class);
        } catch (IOException e) {
            this.bandPaths = null;
        }

        if (this.bandPaths == null) {
            this.bandPaths = new ProjectBandPaths();
        }
    }

    public synchronized void saveStatus() {
        if (this.status == null) {
            this.createStatus();
        }
        this.status.ensureSchemaMetadata();
        this.status.setSchemaVersion(ProjectSchema.CURRENT_VERSION);
        this.status.setQuantumforgeVersion(Version.VERSION);

        try {
            this.<ProjectStatus> writeFile(FILE_NAME_STATUS, this.status);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save project status", e);
        }
    }

    public synchronized void saveScfEnergies() {
        try {
            this.<ProjectEnergies> writeFile(FILE_NAME_SCF, this.scfEnergies);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save SCF energies", e);
        }
    }

    public synchronized void saveFermiEnergies() {
        try {
            this.<ProjectEnergies> writeFile(FILE_NAME_FERMI, this.fermiEnergies);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save Fermi energies", e);
        }
    }

    public synchronized void saveOptList() {
        try {
            this.<ProjectGeometryList> writeFile(FILE_NAME_OPT, this.optList);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save optimization geometry list", e);
        }
    }

    public synchronized void saveMdList() {
        try {
            this.<ProjectGeometryList> writeFile(FILE_NAME_MD, this.mdList);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save MD geometry list", e);
        }
    }

    public synchronized void saveBandPaths() {
        try {
            this.<ProjectBandPaths> writeFile(FILE_NAME_PATH, this.bandPaths);
        } catch (IOException e) {
            AppLog.error("project", "Failed to save band paths", e);
        }
    }

    private <T> T readFile(String fileName, Class<T> classT) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        Path path = Path.of(this.directoryPath, fileName);
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(reader, classT);
        } catch (FileNotFoundException e1) {
            throw e1;
        } catch (IOException e1) {
            throw e1;
        } catch (Exception e2) {
            throw new IOException(e2);
        }
    }

    private <T> void writeFile(String fileName, T objT) throws IOException {
        if (fileName == null || fileName.isEmpty() || objT == null) {
            return;
        }

        Path path = Path.of(this.directoryPath, fileName);
        try {
            // Keep last-known-good copy before replacement.
            if (Files.isRegularFile(path)) {
                Path backup = path.resolveSibling(fileName + ".bak");
                Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            AtomicFileWriter.writeUtf8(path, gson.toJson(objT));
        } catch (IOException e1) {
            throw e1;
        } catch (Exception e2) {
            throw new IOException(e2);
        }
    }
}
