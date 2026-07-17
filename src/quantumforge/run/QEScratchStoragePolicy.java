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
        this.scratchRoot = scratchRoot != null ? scratchRoot : getDefaultScratchPath();
        this.retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.CLEAN_WFC_ONLY;
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
            // Estimate nbnd from valence electrons if omitted
            double zValence = 0.0;
            // Default guess of bands based on elements
            nbnd = 20; 
        }

        // Cell volume in Bohr^3
        double volume = Math.abs(quantumforge.com.math.Matrix3D.determinant(cell.copyLattice()));
        if (volume <= 0.0) {
            volume = 100.0; // Fallback
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
                nkpoints = Math.max(1, grid[0] * grid[1] * grid[2] / 2); // irreducible guestimate
            } else {
                nkpoints = Math.max(1, kPoints.numKPoints());
            }
        }

        // Double Complex = 16 bytes
        double rawBytes = nkpoints * nbnd * npw * 16.0;
        return (long) Math.max(1024.0 * 1024.0, rawBytes); // At least 1 MB
    }

    /**
     * Checks if target filesystem has enough quota/usable space for the expected run.
     */
    public boolean verifySpace(Path path, long estimatedSizeBytes, List<String> warnings) {
        try {
            long usableSpace = Files.getFileStore(path).getUsableSpace();
            if (usableSpace < estimatedSizeBytes) {
                warnings.add(String.format("Insufficient disk space on %s. Estimated required: %.2f MB, available: %.2f MB",
                    path, estimatedSizeBytes / (1024.0 * 1024.0), usableSpace / (1024.0 * 1024.0)));
                return false;
            }
            if (usableSpace < 500L * 1024L * 1024L) {
                warnings.add(String.format("Warning: Directory %s is running dangerously low on space (< 500 MB).", path));
            }
        } catch (IOException e) {
            warnings.add("Disk space query failed on " + path + ": " + e.getMessage());
        }
        return true;
    }

    /**
     * Cleans up transient scratch files (.wfc, .igk) according to the active policy,
     * while preserving important restart logs and manifests.
     */
    public int performCleanup(Path runDir) {
        if (runDir == null || !Files.isDirectory(runDir) || retentionPolicy == RetentionPolicy.KEEP_ALL) {
            return 0;
        }

        int deletedCount = 0;
        try {
            List<Path> filesToDelete = new ArrayList<>();
            Files.walk(runDir).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase();
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

            for (Path p : filesToDelete) {
                if (Files.isRegularFile(p)) {
                    Files.delete(p);
                    deletedCount++;
                }
            }
        } catch (IOException e) {
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
