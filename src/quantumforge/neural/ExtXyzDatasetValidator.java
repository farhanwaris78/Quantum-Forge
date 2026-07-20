/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.neural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed validator for extXYZ machine-learning datasets (Roadmap #143
 * data layer). Reads a bounded dataset frame-by-frame and reports structure,
 * label presence, lattice/property schema, species coverage, per-frame energy
 * statistics, and exact-byte duplicate frames. Units follow the ASE extXYZ
 * convention (positions in Angstrom, energies in eV where the labels do);
 * nothing is converted or interpreted beyond the schema.
 */
public final class ExtXyzDatasetValidator {

    /** GUI-bounded read size; larger datasets belong to offline tooling. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_FRAMES = 50_000;
    public static final int MAX_ATOMS_PER_FRAME = 1_000_000;
    public static final int MAX_LISTED_DUPLICATES = 20;
    public static final int MAX_SPECIES_LIST = 128;

    private static final Pattern SPECIES = Pattern.compile("[A-Z][a-z]?");

    /** Validation outcome; always complete or absent - never partial. */
    public static final class DatasetReport {
        private final int frameCount;
        private final int minAtoms;
        private final int maxAtoms;
        private final int framesWithEnergy;
        private final double minEnergyEv;
        private final double maxEnergyEv;
        private final Set<String> species;
        private final boolean speciesListComplete;
        private final int framesWithLattice;
        private final String propertiesSchema;
        private final List<int[]> duplicatePairs;
        private final int duplicateCount;
        private final List<String> warnings;

        private DatasetReport(Builder builder) {
            this.frameCount = builder.frameCount;
            this.minAtoms = builder.minAtoms;
            this.maxAtoms = builder.maxAtoms;
            this.framesWithEnergy = builder.framesWithEnergy;
            this.minEnergyEv = builder.minEnergyEv;
            this.maxEnergyEv = builder.maxEnergyEv;
            this.species = Set.copyOf(builder.species);
            this.speciesListComplete = builder.speciesListComplete;
            this.framesWithLattice = builder.framesWithLattice;
            this.propertiesSchema = builder.propertiesSchema;
            this.duplicatePairs = List.copyOf(builder.duplicatePairs);
            this.duplicateCount = builder.duplicateCount;
            this.warnings = List.copyOf(builder.warnings);
        }

        public int getFrameCount() { return this.frameCount; }
        public int getMinAtoms() { return this.minAtoms; }
        public int getMaxAtoms() { return this.maxAtoms; }
        /** Frames carrying a parseable energy/free_energy label. */
        public int getFramesWithEnergy() { return this.framesWithEnergy; }
        /** NaN when no frame carried a parseable energy. */
        public double getMinEnergyEv() { return this.minEnergyEv; }
        public double getMaxEnergyEv() { return this.maxEnergyEv; }
        public Set<String> getSpecies() { return this.species; }
        /** False when the species list hit its display cap. */
        public boolean isSpeciesListComplete() { return this.speciesListComplete; }
        public int getFramesWithLattice() { return this.framesWithLattice; }
        /** First-frame Properties schema verbatim, or empty. */
        public String getPropertiesSchema() { return this.propertiesSchema; }
        /** Duplicate frame index pairs (1-based), capped for display. */
        public List<int[]> getDuplicatePairs() { return this.duplicatePairs; }
        public int getDuplicateCount() { return this.duplicateCount; }
        public List<String> getWarnings() { return this.warnings; }
    }

    private static final class Builder {
        private int frameCount = 0;
        private int minAtoms = Integer.MAX_VALUE;
        private int maxAtoms = 0;
        private int framesWithEnergy = 0;
        private double minEnergyEv = Double.POSITIVE_INFINITY;
        private double maxEnergyEv = Double.NEGATIVE_INFINITY;
        private final Set<String> species = new LinkedHashSet<>();
        private boolean speciesListComplete = true;
        private int framesWithLattice = 0;
        private String propertiesSchema = "";
        private final List<int[]> duplicatePairs = new ArrayList<>();
        private int duplicateCount = 0;
        private final List<String> warnings = new ArrayList<>();
    }

    private ExtXyzDatasetValidator() {
        // Utility
    }

    /**
     * Validates an extXYZ dataset. Structural problems (bad atom-count lines,
     * truncated frames, non-finite coordinates, malformed species tokens) are
     * blocking failures with the frame number; missing labels/lattices are
     * warnings, because a syntactically valid dataset may still be malformed
     * for training.
     */
    public static OperationResult<DatasetReport> validate(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("EXTXYZ_IO",
                    "Could not stat " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("EXTXYZ_TOO_LARGE",
                    file.getFileName() + " is " + size + " bytes; this bounded GUI "
                            + "validator reads at most " + MAX_FILE_BYTES
                            + " bytes. Index or validate large datasets offline.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("EXTXYZ_IO",
                    "Could not read " + file.getFileName() + " as UTF-8: " + ex.getMessage(),
                    ex instanceof IOException ? (IOException) ex : null);
        }
        Builder report = new Builder();
        Map<String, Integer> seenHashes = new LinkedHashMap<>();
        int lineNo = 0;
        while (true) {
            while (lineNo < lines.size() && lines.get(lineNo).trim().isEmpty()) {
                lineNo++;
            }
            if (lineNo >= lines.size()) {
                break;
            }
            int frame = report.frameCount + 1;
            if (frame > MAX_FRAMES) {
                return OperationResult.failed("EXTXYZ_TOO_LARGE",
                        "More than " + MAX_FRAMES + " frames; the bounded validator stops "
                                + "here rather than pretending coverage.", null);
            }
            int startLine = lineNo;
            String countText = lines.get(lineNo).trim();
            lineNo++;
            int atoms;
            try {
                atoms = Integer.parseInt(countText);
            } catch (NumberFormatException ex) {
                return OperationResult.failed("EXTXYZ_SYNTAX",
                        "Frame " + frame + " (line " + (startLine + 1) + ") does not start "
                                + "with an atom count: \"" + countText + "\"", null);
            }
            if (atoms <= 0 || atoms > MAX_ATOMS_PER_FRAME) {
                return OperationResult.failed("EXTXYZ_RANGE",
                        "Frame " + frame + " declares " + atoms + " atoms; expected 1.."
                                + MAX_ATOMS_PER_FRAME + ".", null);
            }
            if (lineNo >= lines.size()) {
                return OperationResult.failed("EXTXYZ_TRUNCATED",
                        "Frame " + frame + " ends before its comment line.", null);
            }
            String comment = lines.get(lineNo).trim();
            lineNo++;
            StringBuilder frameText = new StringBuilder(countText).append('\n')
                    .append(comment).append('\n');
            for (int atom = 0; atom < atoms; atom++) {
                if (lineNo >= lines.size()) {
                    return OperationResult.failed("EXTXYZ_TRUNCATED",
                            "Frame " + frame + " declares " + atoms + " atoms but only "
                                    + atom + " atom rows are present.", null);
                }
                String row = lines.get(lineNo).trim();
                lineNo++;
                frameText.append(row).append('\n');
                String[] tokens = row.split("\\s+");
                if (tokens.length < 4) {
                    return OperationResult.failed("EXTXYZ_SYNTAX",
                            "Frame " + frame + " atom row " + (atom + 1)
                                    + " must hold species + 3 coordinates: \"" + row + "\"",
                            null);
                }
                if (!SPECIES.matcher(tokens[0]).matches()) {
                    return OperationResult.failed("EXTXYZ_SYNTAX",
                            "Frame " + frame + " atom row " + (atom + 1)
                                    + " has a malformed species token: \"" + tokens[0] + "\"",
                            null);
                }
                for (int axis = 1; axis <= 3; axis++) {
                    double value;
                    try {
                        value = Double.parseDouble(
                                tokens[axis].replace('D', 'E').replace('d', 'e'));
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("EXTXYZ_SYNTAX",
                                "Frame " + frame + " atom row " + (atom + 1) + " has a "
                                        + "non-numeric coordinate: \"" + tokens[axis] + "\"",
                                null);
                    }
                    if (!Double.isFinite(value)) {
                        return OperationResult.failed("EXTXYZ_VALUE",
                                "Frame " + frame + " atom row " + (atom + 1) + " has a "
                                        + "non-finite coordinate: \"" + tokens[axis] + "\"",
                                null);
                    }
                }
                report.species.add(tokens[0]);
                if (report.species.size() > MAX_SPECIES_LIST) {
                    report.speciesListComplete = false;
                }
            }
            // Header schema.
            String lattice = extractQuoted(comment, "Lattice");
            if (lattice != null) {
                String[] parts = lattice.split("\\s+");
                if (parts.length == 9 && allFinite(parts)) {
                    report.framesWithLattice++;
                } else {
                    report.warnings.add("Frame " + frame + ": Lattice is present but does "
                            + "not hold 9 finite numbers; periodicity metadata is unusable "
                            + "for this frame.");
                }
            }
            if (report.propertiesSchema.isEmpty()) {
                String props = extractQuoted(comment, "Properties");
                if (props != null) {
                    report.propertiesSchema = props;
                }
            }
            String energyText = extractBare(comment, "energy");
            if (energyText == null) {
                energyText = extractBare(comment, "free_energy");
            }
            if (energyText != null) {
                try {
                    double energy = Double.parseDouble(
                            energyText.replace('D', 'E').replace('d', 'e'));
                    if (Double.isFinite(energy)) {
                        report.framesWithEnergy++;
                        report.minEnergyEv = Math.min(report.minEnergyEv, energy);
                        report.maxEnergyEv = Math.max(report.maxEnergyEv, energy);
                    } else {
                        report.warnings.add("Frame " + frame + ": non-finite energy label \""
                                + energyText + "\".");
                    }
                } catch (NumberFormatException ex) {
                    report.warnings.add("Frame " + frame + ": energy label does not parse: \""
                            + energyText + "\".");
                }
            }
            // Exact-byte duplicate detection via SHA-256 of the frame block.
            String hash = sha256Hex(frameText.toString());
            Integer previous = seenHashes.putIfAbsent(hash, frame);
            if (previous != null) {
                report.duplicateCount++;
                if (report.duplicatePairs.size() < MAX_LISTED_DUPLICATES) {
                    report.duplicatePairs.add(new int[] {previous, frame});
                }
            }
            report.frameCount++;
            report.minAtoms = Math.min(report.minAtoms, atoms);
            report.maxAtoms = Math.max(report.maxAtoms, atoms);
        }
        if (report.frameCount == 0) {
            return OperationResult.failed("EXTXYZ_EMPTY",
                    "No complete frame was found in " + file.getFileName() + ".", null);
        }
        if (report.framesWithEnergy == 0) {
            report.warnings.add("No frame carries a parseable energy/free_energy label - "
                    + "without labels this file is geometry, not a training set.");
        }
        if (report.framesWithLattice == 0) {
            report.warnings.add("No frame carries a usable 9-number Lattice - periodic "
                    + "supervision cannot be validated from this file.");
        }
        if (report.duplicateCount > 0) {
            report.warnings.add(report.duplicateCount + " exact duplicate frame(s) found "
                    + "(leakage risk across splits).");
        }
        DatasetReport built = new DatasetReport(report);
        return OperationResult.success("EXTXYZ_OK",
                "Validated " + built.getFrameCount() + " frame(s), "
                        + built.getFramesWithEnergy() + " with energy labels, "
                        + built.getDuplicateCount() + " exact duplicate(s), "
                        + built.getWarnings().size() + " warning(s).", built);
    }

    private static boolean allFinite(String[] parts) {
        try {
            for (String part : parts) {
                if (!Double.isFinite(
                        Double.parseDouble(part.replace('D', 'E').replace('d', 'e')))) {
                    return false;
                }
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    /** Extracts key="value" from an extXYZ comment line; null when absent. */
    private static String extractQuoted(String comment, String key) {
        String needle = key + "=\"";
        int start = comment.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int end = comment.indexOf('"', valueStart);
        return end < 0 ? null : comment.substring(valueStart, end);
    }

    /** Extracts an unquoted key=value token; null when absent or quoted. */
    private static String extractBare(String comment, String key) {
        for (String token : comment.split("\\s+")) {
            if (token.startsWith(key + "=") && !token.substring(key.length() + 1)
                    .startsWith("\"")) {
                return token.substring(key.length() + 1);
            }
        }
        return null;
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }
}
