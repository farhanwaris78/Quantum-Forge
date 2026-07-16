/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import quantumforge.com.log.AppLog;

/**
 * Lists and restores {@link ProjectAutosave} snapshots without destroying the
 * deliberate project tree until the user confirms a restore.
 */
public final class ProjectRecovery {

    public static final class SnapshotInfo {
        private final Path path;
        private final Instant modified;

        public SnapshotInfo(Path path, Instant modified) {
            this.path = path;
            this.modified = modified;
        }

        public Path getPath() { return this.path; }
        public Instant getModified() { return this.modified; }

        @Override
        public String toString() {
            return this.path.getFileName() + " (" + this.modified + ")";
        }
    }

    private ProjectRecovery() {
        // Utility.
    }

    public static Path snapshotRoot(Path projectDirectory) {
        return Objects.requireNonNull(projectDirectory, "projectDirectory")
                .resolve(ProjectAutosave.SNAPSHOT_DIR);
    }

    public static List<SnapshotInfo> listSnapshots(Path projectDirectory) throws IOException {
        Path root = snapshotRoot(projectDirectory);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> {
                        try {
                            return new SnapshotInfo(path, Files.getLastModifiedTime(path).toInstant());
                        } catch (IOException ex) {
                            return new SnapshotInfo(path, Instant.EPOCH);
                        }
                    })
                    .sorted(Comparator.comparing(SnapshotInfo::getModified).reversed())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Copy snapshot files into the project directory after writing a safety
     * backup of the current deliberate inputs under {@code .quantumforge.pre-restore/}.
     */
    public static void restoreSnapshot(Path projectDirectory, Path snapshotDirectory)
            throws IOException {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        if (!Files.isDirectory(snapshotDirectory)) {
            throw new IOException("Snapshot directory does not exist: " + snapshotDirectory);
        }
        Path backup = projectDirectory.resolve(".quantumforge.pre-restore-"
                + Instant.now().toString().replace(':', '-'));
        Files.createDirectories(backup);
        copyInputFiles(projectDirectory, backup);
        copyInputFiles(snapshotDirectory, projectDirectory);
        AppLog.info("recovery", "Restored snapshot " + snapshotDirectory.getFileName()
                + " into " + projectDirectory + " (pre-restore backup: " + backup.getFileName() + ")");
    }

    private static void copyInputFiles(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        String[] names = {
                "espresso.geom.in", "espresso.scf.in", "espresso.opt.in",
                "espresso.md.in", "espresso.dos.in", "espresso.band.in",
                ".quantumforge.status", "AUTOSAVE.txt"
        };
        List<String> copied = new ArrayList<>();
        for (String name : names) {
            Path src = from.resolve(name);
            if (Files.isRegularFile(src)) {
                Files.copy(src, to.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                copied.add(name);
            }
        }
        if (copied.isEmpty()) {
            // Copy any *.in present as a fallback.
            try (Stream<Path> stream = Files.list(from)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".in"))
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.copy(path, to.resolve(path.getFileName().toString()),
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ignored) {
                                // Best effort.
                            }
                        });
            }
        }
    }
}
