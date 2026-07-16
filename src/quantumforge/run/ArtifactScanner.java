/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Detects on-disk QE artifacts so a command DAG can skip completed stages.
 *
 * <p>Presence is evidence of prior work, not scientific validity. Callers must
 * still re-run stages when inputs changed.</p>
 */
public final class ArtifactScanner {

    private ArtifactScanner() {
        // Utility.
    }

    public static Set<String> scan(Path projectDirectory, String prefix) {
        Set<String> tags = new LinkedHashSet<>();
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            return tags;
        }
        String p = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        Path save = projectDirectory.resolve(p + ".save");
        if (Files.isDirectory(save)) {
            if (existsContaining(save, "charge-density")
                    || Files.isRegularFile(save.resolve("charge-density.dat"))) {
                tags.add("charge-density");
            }
            if (existsContaining(save, "wfc") || existsContaining(save, "wave")) {
                tags.add("wavefunctions");
            }
            if (Files.isRegularFile(save.resolve("data-file-schema.xml"))
                    || Files.isRegularFile(save.resolve("data-file.xml"))) {
                // data-file presence alone is not enough for restart safety, but
                // indicates a prior SCF-like stage completed far enough to write XML.
                tags.add("charge-density");
            }
        }

        // Log heuristics: successful stage logs often end with JOB DONE.
        scanLog(projectDirectory, p, "scf", tags, "charge-density", "wavefunctions");
        scanLog(projectDirectory, p, "nscf", tags, "nscf-wavefunctions");
        scanLog(projectDirectory, p, "bands", tags, "band-wavefunctions");
        scanLog(projectDirectory, p, "band", tags, "band-wavefunctions");
        scanLog(projectDirectory, p, "dos", tags, "dos-data");
        scanLog(projectDirectory, p, "pdos", tags, "pdos-data");
        scanLog(projectDirectory, p, "opt", tags, "charge-density", "final-geometry");
        scanLog(projectDirectory, p, "md", tags, "trajectory");

        // Common data files
        if (fileExists(projectDirectory, p + ".dos") || fileExists(projectDirectory, "espresso.dos")) {
            tags.add("dos-data");
        }
        if (existsContaining(projectDirectory, "pdos") || existsContaining(projectDirectory, "projwfc")) {
            tags.add("pdos-data");
        }
        if (fileExists(projectDirectory, "bands.dat") || fileExists(projectDirectory, p + ".band")) {
            tags.add("bands-dat");
        }
        return tags;
    }

    private static void scanLog(Path dir, String prefix, String suffix, Set<String> tags,
                                String... produced) {
        Path log = dir.resolve(prefix + ".log." + suffix);
        if (!Files.isRegularFile(log)) {
            log = dir.resolve("espresso.log." + suffix);
        }
        if (!Files.isRegularFile(log)) {
            return;
        }
        try {
            String text = Files.readString(log);
            if (text.toLowerCase(Locale.ROOT).contains("job done")) {
                for (String tag : produced) {
                    tags.add(tag);
                }
            }
        } catch (IOException ignored) {
            // Ignore unreadable logs.
        }
    }

    private static boolean fileExists(Path dir, String name) {
        return Files.isRegularFile(dir.resolve(name));
    }

    private static boolean existsContaining(Path dir, String needle) {
        String n = needle.toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(path ->
                    path.getFileName().toString().toLowerCase(Locale.ROOT).contains(n));
        } catch (IOException ex) {
            return false;
        }
    }
}
