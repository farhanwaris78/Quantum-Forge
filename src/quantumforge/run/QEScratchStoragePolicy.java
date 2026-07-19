/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Cell;
import quantumforge.com.log.AppLog;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Implements scratch storage routing, local high-speed SSD allocation,
 * retention/clean-up rules, and predictive disk-space quota validation (Roadmap #34).
 */
public final class QEScratchStoragePolicy {

    public enum RetentionPolicy {
        KEEP_ALL,
        CLEAN_WFC_ONLY,
        CLEAN_ALL_TEMPS
    }

    private final Path scratchRoot;
    private final RetentionPolicy retentionPolicy;
    private final long maxQuotaBytes;

    public QEScratchStoragePolicy() {
        this(getDefaultScratchPath(), RetentionPolicy.CLEAN_WFC_ONLY, 100L * 1024L * 1024L * 1024L); // 100 GB default quota
    }

    public QEScratchStoragePolicy(Path scratchRoot, RetentionPolicy retentionPolicy, long maxQuotaBytes) {
        Path configured = scratchRoot != null ? scratchRoot : getDefaultScratchPath();
        this.scratchRoot = configured.toAbsolutePath().normalize();
        this.retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.CLEAN_WFC_ONLY;
        if (maxQuotaBytes <= 0L) {
            throw new IllegalArgumentException("maxQuotaBytes must be positive");
        }
        this.maxQuotaBytes = maxQuotaBytes;
    }

    public Path getScratchRoot() { return this.scratchRoot; }
    public RetentionPolicy getRetentionPolicy() { return this.retentionPolicy; }
    public long getMaxQuotaBytes() { return this.maxQuotaBytes; }

    /**
     * Estimates maximum scratch size (in bytes) of wavefunctions produced by the run:
     * Size = NumKpoints * NumBands * NumPlaneWaves * 16 bytes (Double Complex)
     */
    public long estimateScratchSize(Cell cell, QEInput input) {
        if (cell == null || input == null) {
            return 0L;
        }

        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        if (system == null) {
            return 0L;
        }

        QEValue ecutVal = system.getValue("ecutwfc");
        double ecutwfc = ecutVal != null ? ecutVal.getRealValue() : 25.0;

        QEValue nbndVal = system.getValue("nbnd");
        int nbnd = nbndVal != null ? nbndVal.getIntegerValue() : 0;
        if (nbnd <= 0) {
            // Without pseudo valence metadata an exact value is impossible.
            // Use a deliberately conservative floor rather than a fabricated
            // element-specific electron count.
            nbnd = Math.max(20, Math.max(1, cell.numAtoms()) * 4);
        }
        if (!(ecutwfc > 0.0) || !Double.isFinite(ecutwfc)) {
            return 0L;
        }

        // Cell volume in Bohr^3
        double volume = Math.abs(quantumforge.com.math.Matrix3D.determinant(cell.copyLattice()));
        if (!(volume > 0.0) || !Double.isFinite(volume)) {
            return 0L;
        }

        // N_pw = (4*pi/3) * Vol * Ecut^(1.5) / (8 * pi^3)
        // In Ry/Bohr units, standard scaling:
        double npw = (4.0 * Math.PI / 3.0) * volume * Math.pow(ecutwfc, 1.5) / (8.0 * Math.PI * Math.PI * Math.PI);
        if (npw <= 0.0) {
            npw = 1000.0;
        }

        int nkpoints = 1; // Default
        quantumforge.input.card.QEKPoints kPoints = input.getCard(quantumforge.input.card.QEKPoints.class);
        if (kPoints != null) {
            if (kPoints.isAutomatic()) {
                int[] grid = kPoints.getKGrid();
                if (grid == null || grid.length < 3 || grid[0] <= 0 || grid[1] <= 0 || grid[2] <= 0) {
                    return 0L;
                }
                // Scratch can contain wavefunctions for the full mesh; do not
                // halve it by guessing symmetry reduction.
                long product = (long) grid[0] * (long) grid[1] * (long) grid[2];
                nkpoints = product > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
            } else {
                nkpoints = Math.max(1, kPoints.numKPoints());
            }
        }

        // Complex coefficients alone are 16 bytes. Apply a factor two for
        // buffers/restart metadata, then clamp instead of overflowing long.
        double rawBytes = nkpoints * (double) nbnd * npw * 32.0;
        if (!Double.isFinite(rawBytes) || rawBytes >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1024L * 1024L, (long) Math.ceil(rawBytes));
    }

    /**
     * Checks if target filesystem has enough quota/usable space for the expected run.
     */
    public boolean verifySpace(Path path, long estimatedSizeBytes, List<String> warnings) {
        List<String> messages = warnings == null ? new ArrayList<>() : warnings;
        if (path == null || estimatedSizeBytes <= 0L) {
            messages.add("Scratch-space estimate is unavailable; refusing to treat storage as verified.");
            return false;
        }
        try {
            Files.createDirectories(path);
            long usableSpace = Files.getFileStore(path).getUsableSpace();
            long required = estimatedSizeBytes;
            if (required > this.maxQuotaBytes) {
                messages.add(String.format("Estimated scratch requirement %.2f MB exceeds configured quota %.2f MB.",
                        estimatedSizeBytes / (1024.0 * 1024.0), this.maxQuotaBytes / (1024.0 * 1024.0)));
                return false;
            }
            if (usableSpace < required) {
                messages.add(String.format("Insufficient disk space on %s. Estimated required: %.2f MB, available: %.2f MB",
                    path, required / (1024.0 * 1024.0), usableSpace / (1024.0 * 1024.0)));
                return false;
            }
            if (usableSpace - required < 500L * 1024L * 1024L) {
                messages.add(String.format("Warning: scratch allocation would leave less than 500 MB free on %s.", path));
            }
            return true;
        } catch (IOException | SecurityException e) {
            messages.add("Disk space query failed on " + path + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleans up transient scratch files (.wfc, .igk) according to the active policy,
     * while preserving important restart logs and manifests.
     */
    public int performCleanup(Path runDir) {
        if (runDir == null || !Files.isDirectory(runDir) || retentionPolicy == RetentionPolicy.KEEP_ALL) {
            return 0;
        }

        Path normalizedRunDir = runDir.toAbsolutePath().normalize();
        if (!normalizedRunDir.startsWith(this.scratchRoot)) {
            AppLog.warn("scratch", "Refused cleanup outside configured scratch root: " + normalizedRunDir);
            return 0;
        }
        int deletedCount = 0;
        try {
            List<Path> filesToDelete = new ArrayList<>();
            try (java.util.stream.Stream<Path> paths = Files.walk(normalizedRunDir)) {
                paths.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (retentionPolicy == RetentionPolicy.CLEAN_WFC_ONLY) {
                        if (name.endsWith(".wfc") || name.endsWith(".igk")) {
                            filesToDelete.add(p);
                        }
                    } else if (retentionPolicy == RetentionPolicy.CLEAN_ALL_TEMPS) {
                        if (name.endsWith(".wfc") || name.endsWith(".igk") || name.endsWith(".mix") || name.endsWith(".grid")) {
                            filesToDelete.add(p);
                        }
                    }
                });
            }
            for (Path p : filesToDelete) {
                Files.deleteIfExists(p);
                deletedCount++;
            }
        } catch (IOException | SecurityException e) {
            AppLog.warn("scratch", "Cleanup failed: " + e.getMessage());
        }
        return deletedCount;
    }

    private static Path getDefaultScratchPath() {
        String custom = System.getenv("QUANTUMFORGE_SCRATCH");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".quantumforge", "scratch");
    }
}
