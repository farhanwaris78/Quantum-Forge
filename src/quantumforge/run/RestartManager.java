/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import quantumforge.operation.OperationResult;

/**
 * Validates Quantum ESPRESSO restart artifacts before suggesting restart_mode.
 *
 * <p>Does not invent restart safety: incomplete {@code .save} directories or
 * mismatched prefix/outdir yield an explicit failure rather than a silent
 * {@code restart_mode='restart'}.</p>
 */
public final class RestartManager {

    public static final class RestartAssessment {
        private final boolean restartSafe;
        private final String recommendedRestartMode;
        private final Path saveDirectory;
        private final List<String> diagnostics;

        public RestartAssessment(boolean restartSafe, String recommendedRestartMode,
                                 Path saveDirectory, List<String> diagnostics) {
            this.restartSafe = restartSafe;
            this.recommendedRestartMode = recommendedRestartMode;
            this.saveDirectory = saveDirectory;
            this.diagnostics = List.copyOf(diagnostics);
        }

        public boolean isRestartSafe() { return this.restartSafe; }
        public String getRecommendedRestartMode() { return this.recommendedRestartMode; }
        public Path getSaveDirectory() { return this.saveDirectory; }
        public List<String> getDiagnostics() { return this.diagnostics; }
    }

    private RestartManager() {
        // Utility.
    }

    public static OperationResult<RestartAssessment> assess(Path projectDirectory, String prefix) {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            return OperationResult.failed("PROJECT_DIR_MISSING",
                    "Project directory is missing or not a directory.", null);
        }
        String prefix2 = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        Path saveDir = projectDirectory.resolve(prefix2 + ".save");
        List<String> diagnostics = new ArrayList<>();

        if (!Files.isDirectory(saveDir)) {
            diagnostics.add("No " + saveDir.getFileName() + " directory; only from_scratch is safe.");
            return OperationResult.success("RESTART_FROM_SCRATCH",
                    "No restart directory; use from_scratch.",
                    new RestartAssessment(false, "from_scratch", saveDir, diagnostics));
        }

        boolean hasDataFile = Files.isRegularFile(saveDir.resolve("data-file-schema.xml"))
                || Files.isRegularFile(saveDir.resolve("data-file.xml"));
        boolean hasCharge = existsMatching(saveDir, "charge-density");
        boolean hasWave = existsMatching(saveDir, "wfc") || existsMatching(saveDir, "wfcole");

        if (!hasDataFile) {
            diagnostics.add("Missing data-file-schema.xml / data-file.xml in " + saveDir.getFileName());
        }
        if (!hasCharge) {
            diagnostics.add("No charge-density artifact found under " + saveDir.getFileName());
        }
        if (!hasWave) {
            diagnostics.add("No wavefunction artifacts found (may still allow charge-only restart).");
        }

        boolean safe = hasDataFile && hasCharge;
        if (safe) {
            diagnostics.add("Restart artifacts look complete enough for restart_mode='restart'.");
            return OperationResult.success("RESTART_SAFE",
                    "Restart directory appears complete.",
                    new RestartAssessment(true, "restart", saveDir, diagnostics));
        }
        diagnostics.add("Refusing automatic restart; prefer from_scratch or repair the .save directory.");
        return OperationResult.success("RESTART_UNSAFE",
                "Restart directory is incomplete.",
                new RestartAssessment(false, "from_scratch", saveDir, diagnostics));
    }

    public static String namelistSnippet(RestartAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        return "restart_mode = '" + assessment.getRecommendedRestartMode() + "'";
    }

    private static boolean existsMatching(Path dir, String needle) {
        String n = needle.toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(path ->
                    path.getFileName().toString().toLowerCase(Locale.ROOT).contains(n));
        } catch (IOException ex) {
            return false;
        }
    }
}
