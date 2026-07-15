/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.path;

import java.io.File;

import quantumforge.com.env.Environments;

public final class QEPath {

    private static final String PROP_QE_PATH = "espresso_path";
    private static final String PROP_MPI_PATH = "mpi_path";

    private QEPath() {
        // NOP
    }

    public static String getPath() {
        return Environments.getProperty(PROP_QE_PATH);
    }

    public static String getMPIPath() {
        return Environments.getProperty(PROP_MPI_PATH);
    }

    public static void setPath(File file) {
        setPath(file == null ? null : file.getAbsolutePath());
    }

    public static void setPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            Environments.removeProperty(PROP_QE_PATH);
        } else {
            Environments.setProperty(PROP_QE_PATH, path.trim());
        }
    }

    public static void setMPIPath(File file) {
        setMPIPath(file == null ? null : file.getAbsolutePath());
    }

    public static void setMPIPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            Environments.removeProperty(PROP_MPI_PATH);
        } else {
            Environments.setProperty(PROP_MPI_PATH, path.trim());
        }
    }

}
