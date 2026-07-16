/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.project;

import java.util.Objects;

/**
 * Versioned project metadata contract.
 *
 * <p>Schema changes must bump {@link #CURRENT_VERSION} and provide a migration
 * path that leaves the original files intact on failure.</p>
 */
public final class ProjectSchema {

    /** Current on-disk project schema version written by QuantumForge 2.0.x. */
    public static final int CURRENT_VERSION = 1;

    public static final String STATUS_FILE = ".quantumforge.status";
    public static final String SCHEMA_FIELD = "schemaVersion";
    public static final String APP_VERSION_FIELD = "quantumforgeVersion";

    private ProjectSchema() {
        // Utility class.
    }

    public static int normalize(Integer version) {
        if (version == null || version < 1) {
            return 1;
        }
        return version;
    }

    public static boolean isSupported(int version) {
        return version >= 1 && version <= CURRENT_VERSION;
    }

    public static void requireSupported(int version) {
        if (!isSupported(version)) {
            throw new IllegalStateException("Unsupported project schema version: " + version
                    + " (this QuantumForge build supports 1.." + CURRENT_VERSION + ")");
        }
    }

    public static String describe(int version) {
        return "project-schema-v" + Objects.requireNonNullElse(version, CURRENT_VERSION);
    }
}
