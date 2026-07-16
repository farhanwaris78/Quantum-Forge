/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.util.Locale;
import java.util.Objects;

/**
 * Canonical remote path validation for SFTP staging.
 *
 * <p>Rejects absolute escapes outside a configured staging root, parent
 * traversal, and empty/null paths.</p>
 */
public final class RemotePathGuard {

    private RemotePathGuard() {
        // Utility.
    }

    public static String normalizeStagingRoot(String root) {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("staging root is empty");
        }
        String normalized = root.trim().replace('\\', '/');
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("/")) {
            throw new IllegalArgumentException("staging root must be absolute: " + root);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("staging root must not contain '..': " + root);
        }
        return normalized;
    }

    public static String resolveUnderRoot(String stagingRoot, String relative) {
        String root = normalizeStagingRoot(stagingRoot);
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("relative path is empty");
        }
        String rel = relative.trim().replace('\\', '/');
        if (rel.startsWith("/")) {
            throw new IllegalArgumentException("relative path must not be absolute: " + relative);
        }
        if (rel.contains("..") || rel.startsWith("./..") || rel.contains("/../")) {
            throw new IllegalArgumentException("path traversal rejected: " + relative);
        }
        String combined = root + "/" + rel.replaceAll("^\\./", "");
        // Collapse duplicate slashes
        combined = combined.replaceAll("/{2,}", "/");
        if (!combined.equals(root) && !combined.startsWith(root + "/")) {
            throw new IllegalArgumentException("resolved path escaped staging root: " + combined);
        }
        return combined;
    }

    public static String uniqueJobDirectory(String stagingRoot, String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        String safe = jobId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("jobId is empty after sanitization");
        }
        return resolveUnderRoot(stagingRoot, "jobs/" + safe);
    }
}
