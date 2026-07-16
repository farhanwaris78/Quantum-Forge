/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import quantumforge.com.log.AppLog;

/**
 * Cancels a process and its descendants with graceful then forced termination.
 *
 * <p>MPI launches often leave orphaned children if only the launcher process is
 * destroyed. This helper walks {@link ProcessHandle} descendants.</p>
 */
public final class ProcessTreeKiller {

    private ProcessTreeKiller() {
        // Utility class.
    }

    public static boolean stop(Process process, Duration grace, Duration forceAfter) {
        if (process == null || !process.isAlive()) {
            return true;
        }
        Objects.requireNonNull(grace, "grace");
        Objects.requireNonNull(forceAfter, "forceAfter");

        List<ProcessHandle> tree = collectTree(process.toHandle());
        AppLog.info("process", "Requesting graceful stop for " + tree.size() + " process handle(s)");
        for (ProcessHandle handle : tree) {
            try {
                handle.destroy();
            } catch (RuntimeException ex) {
                AppLog.warn("process", "destroy failed for pid " + handle.pid() + ": " + ex.getMessage());
            }
        }

        boolean stopped = waitUntilDead(process, grace);
        if (stopped) {
            AppLog.info("process", "Process tree stopped gracefully");
            return true;
        }

        AppLog.warn("process", "Forcing process-tree termination");
        for (ProcessHandle handle : tree) {
            try {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            } catch (RuntimeException ex) {
                AppLog.warn("process", "destroyForcibly failed for pid " + handle.pid() + ": " + ex.getMessage());
            }
        }
        try {
            process.destroyForcibly();
        } catch (RuntimeException ignored) {
            // Already reported above where possible.
        }
        return waitUntilDead(process, forceAfter);
    }

    static List<ProcessHandle> collectTree(ProcessHandle root) {
        List<ProcessHandle> handles = new ArrayList<>();
        if (root == null) {
            return handles;
        }
        root.descendants().forEach(handles::add);
        // Destroy children before parents when possible.
        handles.sort(Comparator.comparingLong(ProcessHandle::pid).reversed());
        handles.add(root);
        return handles;
    }

    private static boolean waitUntilDead(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || !process.isAlive();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }
}
