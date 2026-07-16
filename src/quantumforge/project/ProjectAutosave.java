/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.log.AppLog;

/**
 * Debounced immutable project snapshots.
 *
 * <p>Snapshots never overwrite the deliberate project directory. Recovery is
 * explicit: the user (or a future recovery picker) copies a snapshot back.</p>
 */
public final class ProjectAutosave implements AutoCloseable {

    public static final String SNAPSHOT_DIR = ".quantumforge.autosave";
    private static final int DEFAULT_LIMIT = 10;
    private static final long DEFAULT_DELAY_MS = 5_000L;

    private final Project project;
    private final Path snapshotRoot;
    private final int limit;
    private final long delayMs;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pending;

    public ProjectAutosave(Project project) {
        this(project, DEFAULT_LIMIT, DEFAULT_DELAY_MS);
    }

    public ProjectAutosave(Project project, int limit, long delayMs) {
        this.project = Objects.requireNonNull(project, "project");
        String directory = project.getDirectoryPath();
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("project has no directory path");
        }
        this.snapshotRoot = Path.of(directory, SNAPSHOT_DIR);
        this.limit = Math.max(1, limit);
        this.delayMs = Math.max(500L, delayMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "quantumforge-autosave");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void requestSnapshot() {
        if (this.pending != null) {
            this.pending.cancel(false);
        }
        this.pending = this.scheduler.schedule(this::safeSnapshot, this.delayMs, TimeUnit.MILLISECONDS);
    }

    public Path snapshotNow() throws IOException {
        Files.createDirectories(this.snapshotRoot);
        String stamp = Instant.now().toString().replace(':', '-');
        Path target = this.snapshotRoot.resolve("snapshot-" + stamp);
        Files.createDirectories(target);
        // Reuse existing project save into a dedicated snapshot directory.
        this.project.saveQEInputs(target.toString());
        AtomicFileWriter.writeUtf8(target.resolve("AUTOSAVE.txt"),
                "QuantumForge autosave snapshot\ncreated=" + Instant.now() + "\n");
        pruneOldSnapshots();
        AppLog.debug("autosave", "Wrote snapshot " + target);
        return target;
    }

    private void safeSnapshot() {
        try {
            snapshotNow();
        } catch (Exception ex) {
            AppLog.warn("autosave", "Snapshot failed: " + ex.getMessage());
        }
    }

    private void pruneOldSnapshots() throws IOException {
        if (!Files.isDirectory(this.snapshotRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.list(this.snapshotRoot)) {
            Path[] snapshots = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toArray(Path[]::new);
            for (int i = this.limit; i < snapshots.length; i++) {
                deleteRecursively(snapshots[i]);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            });
        }
    }

    @Override
    public void close() {
        if (this.pending != null) {
            this.pending.cancel(false);
        }
        this.scheduler.shutdownNow();
    }
}
