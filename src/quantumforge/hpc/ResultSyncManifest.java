/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import quantumforge.run.RunningType;

/**
 * Declares which remote files are required/optional after a workflow.
 *
 * <p>Prevents bulk "download everything / delete everything" remote operations.</p>
 */
public final class ResultSyncManifest {

    public enum Priority {
        REQUIRED,
        OPTIONAL,
        LARGE_OPTIONAL
    }

    public static final class Entry {
        private final String relativePath;
        private final Priority priority;

        public Entry(String relativePath, Priority priority) {
            this.relativePath = Objects.requireNonNull(relativePath).replace('\\', '/');
            if (this.relativePath.startsWith("/") || this.relativePath.contains("..")) {
                throw new IllegalArgumentException("unsafe relative path: " + relativePath);
            }
            this.priority = Objects.requireNonNull(priority);
        }

        public String getRelativePath() { return this.relativePath; }
        public Priority getPriority() { return this.priority; }
    }

    private final List<Entry> entries;

    public ResultSyncManifest(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(
                entries == null ? List.of() : entries));
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public List<String> requiredPaths() {
        List<String> out = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (entry.getPriority() == Priority.REQUIRED) {
                out.add(entry.getRelativePath());
            }
        }
        return out;
    }

    public List<String> allPaths(boolean includeLarge) {
        List<String> out = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (entry.getPriority() == Priority.LARGE_OPTIONAL && !includeLarge) {
                continue;
            }
            out.add(entry.getRelativePath());
        }
        return out;
    }

    public static ResultSyncManifest forWorkflow(RunningType type, String prefix) {
        String p = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        Set<Entry> entries = new LinkedHashSet<>();
        // Always useful
        entries.add(new Entry(p + ".log", Priority.REQUIRED));
        entries.add(new Entry(p + ".err", Priority.OPTIONAL));
        entries.add(new Entry(".quantumforge.run-manifest.jsonl", Priority.OPTIONAL));

        RunningType t = type == null ? RunningType.SCF : type;
        switch (t) {
        case SCF:
            entries.add(new Entry(p + ".log.scf", Priority.REQUIRED));
            entries.add(new Entry(p + ".err.scf", Priority.OPTIONAL));
            entries.add(new Entry(p + ".save/data-file-schema.xml", Priority.OPTIONAL));
            entries.add(new Entry(p + ".save/charge-density.dat", Priority.LARGE_OPTIONAL));
            break;
        case OPTIMIZ:
            entries.add(new Entry(p + ".log.opt", Priority.REQUIRED));
            entries.add(new Entry(p + ".err.opt", Priority.OPTIONAL));
            entries.add(new Entry(p + ".save/data-file-schema.xml", Priority.OPTIONAL));
            break;
        case MD:
            entries.add(new Entry(p + ".log.md", Priority.REQUIRED));
            entries.add(new Entry(p + ".err.md", Priority.OPTIONAL));
            break;
        case DOS:
            entries.add(new Entry(p + ".log.scf", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.nscf", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.dos", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.pdos", Priority.OPTIONAL));
            entries.add(new Entry(p + ".dos", Priority.REQUIRED));
            entries.add(new Entry(p + ".pdos_tot", Priority.OPTIONAL));
            break;
        case BAND:
            entries.add(new Entry(p + ".log.scf", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.bands", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.band.up", Priority.OPTIONAL));
            entries.add(new Entry(p + ".log.band.down", Priority.OPTIONAL));
            entries.add(new Entry("bands.dat", Priority.REQUIRED));
            break;
        case NEB:
            entries.add(new Entry(p + ".log.scf", Priority.OPTIONAL));
            entries.add(new Entry(p + ".log.neb", Priority.REQUIRED));
            entries.add(new Entry(p + ".err.neb", Priority.OPTIONAL));
            entries.add(new Entry("neb.dat", Priority.OPTIONAL));
            entries.add(new Entry("prefix.path", Priority.OPTIONAL));
            break;
        case PHONON:
            entries.add(new Entry(p + ".log.scf", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.ph", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.q2r", Priority.REQUIRED));
            entries.add(new Entry(p + ".log.matdyn", Priority.REQUIRED));
            entries.add(new Entry("matdyn.modes", Priority.OPTIONAL));
            entries.add(new Entry("matdyn.freq", Priority.OPTIONAL));
            entries.add(new Entry("matdyn.dos", Priority.OPTIONAL));
            entries.add(new Entry(p + ".dyn0", Priority.LARGE_OPTIONAL));
            break;
        default:
            entries.add(new Entry(p + ".log.scf", Priority.OPTIONAL));
            break;
        }
        return new ResultSyncManifest(new ArrayList<>(entries));
    }

    public static ResultSyncManifest of(String... relativePaths) {
        List<Entry> list = new ArrayList<>();
        if (relativePaths != null) {
            for (String path : relativePaths) {
                list.add(new Entry(path, Priority.REQUIRED));
            }
        }
        return new ResultSyncManifest(list);
    }

    @Override
    public String toString() {
        return "ResultSyncManifest entries=" + this.entries.size();
    }
}
