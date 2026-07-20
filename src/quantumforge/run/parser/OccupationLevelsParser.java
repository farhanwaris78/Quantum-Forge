/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #47 (occupation half): line-provenanced review of the pw.x
 * "highest occupied, lowest unoccupied level (ev)" and "highest occupied
 * level (ev)" statements in a pw log. Every occurrence is reported with its
 * 1-based line number - nothing is hidden and the LAST pair is NOT silently
 * crowned: this parser does not establish convergence, so whether the last
 * pair is the converged one remains the user's reading (stated plainly).
 *
 * <p>Load-bearing honesty:</p>
 * <ul>
 *   <li>single-value "highest occupied level" lines appear with
 *       metallic/smearing occupations - a gap is UNDEFINED there and the
 *       review says so instead of inventing a LUMO;</li>
 *   <li>these lines carry no spin-channel label: per-channel attribution is
 *       NOT derivable from this needle and is never guessed - the
 *       spin-polarized fixtures of full #47 depth handle channels
 *       explicitly;</li>
 *   <li>this line-level HOMO/LUMO gap complements, and does not replace,
 *       the k-resolved VBM/CBM/directness verdict of the curve half
 *       ({@link BandGapBandMath});</li>
 *   <li>an empty result means the needle is absent - which is NEITHER a
 *       convergence statement nor a run-quality certificate.</li>
 * </ul>
 *
 * <p>Refusal codes: OCCLEVEL_IO, OCCLEVEL_TOO_LARGE.</p>
 */
public final class OccupationLevelsParser {

    /** File cap mirrored from the log-review kinds. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private static final Pattern PAIR = Pattern.compile(
            "highest occupied, lowest unoccupied level \\(ev\\):"
                    + "\\s*(\\S+)\\s+(\\S+)");
    private static final Pattern SINGLE = Pattern.compile(
            "highest occupied level \\(ev\\):\\s*(\\S+)");

    /** One needle hit with line provenance. */
    public static final class OccupationLine {
        private final int lineNumber;
        private final String verbatim;
        private final double homoEv;
        private final double lumoEv;

        OccupationLine(int lineNumber, String verbatim, double homoEv, double lumoEv) {
            this.lineNumber = lineNumber;
            this.verbatim = verbatim;
            this.homoEv = homoEv;
            this.lumoEv = lumoEv;
        }

        public int getLineNumber() { return this.lineNumber; }
        /** The exact parsed line, trimmed, for display. */
        public String getVerbatim() { return this.verbatim; }
        public double getHomoEv() { return this.homoEv; }
        /** NaN for single-value (metallic/smearing) lines. */
        public double getLumoEv() { return this.lumoEv; }
        /** lumo - homo; NaN when no LUMO was printed. */
        public double getGapEv() {
            return Double.isNaN(this.lumoEv) ? Double.NaN : this.lumoEv - this.homoEv;
        }
    }

    /** The completed review. */
    public static final class OccupationReview {
        private final int linesScanned;
        private final List<OccupationLine> occurrences;

        OccupationReview(int linesScanned, List<OccupationLine> occurrences) {
            this.linesScanned = linesScanned;
            this.occurrences = new ArrayList<>(occurrences);
        }

        public int getLinesScanned() { return this.linesScanned; }
        public List<OccupationLine> getOccurrences() {
            return List.copyOf(this.occurrences);
        }
        /** Pair lines carrying both HOMO and LUMO. */
        public int getPairCount() {
            int count = 0;
            for (OccupationLine line : this.occurrences) {
                if (!Double.isNaN(line.getLumoEv())) {
                    count += 1;
                }
            }
            return count;
        }
        /** Single-value lines (metallic/smearing occupation print). */
        public int getSingleCount() {
            return this.occurrences.size() - getPairCount();
        }
    }

    private OccupationLevelsParser() {
    }

    /**
     * Scans a pw log for the occupation-level needles. Codes: OCCLEVEL_IO,
     * OCCLEVEL_TOO_LARGE.
     */
    public static OperationResult<OccupationReview> review(Path file) {
        if (file == null || !Files.exists(file)) {
            return OperationResult.failed("OCCLEVEL_IO",
                    "The log file does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("OCCLEVEL_IO",
                    "Could not stat the log: " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("OCCLEVEL_TOO_LARGE",
                    "The log is " + size + " bytes; the review cap is "
                            + MAX_FILE_BYTES + " bytes - refusing rather than "
                            + "sampling the middle and hiding statements.",
                    null);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("OCCLEVEL_IO",
                    "Reading the log failed: " + ex.getMessage(), null);
        }
        String[] lines = text.split("\n", -1);
        List<OccupationLine> occurrences = new ArrayList<>();
        for (int idx = 0; idx < lines.length; idx += 1) {
            String line = lines[idx];
            Matcher pair = PAIR.matcher(line);
            if (pair.find()) {
                Double homo = parseNumber(pair.group(1));
                Double lumo = parseNumber(pair.group(2));
                if (homo == null || lumo == null) {
                    return OperationResult.failed("OCCLEVEL_IO",
                            "Line " + (idx + 1) + " matches the occupation needle "
                                    + "but its numbers do not parse - refusing "
                                    + "rather than dropping a hit silently.",
                            null);
                }
                occurrences.add(new OccupationLine(idx + 1, line.trim(), homo, lumo));
                continue;
            }
            Matcher single = SINGLE.matcher(line);
            if (single.find()) {
                Double homo = parseNumber(single.group(1));
                if (homo == null) {
                    return OperationResult.failed("OCCLEVEL_IO",
                            "Line " + (idx + 1) + " matches the occupation needle "
                                    + "but its number does not parse - refusing "
                                    + "rather than dropping a hit silently.",
                            null);
                }
                occurrences.add(new OccupationLine(idx + 1, line.trim(), homo,
                        Double.NaN));
            }
        }
        return OperationResult.success("OCCLEVEL_OK",
                "Scanned " + lines.length + " lines.",
                new OccupationReview(lines.length, occurrences));
    }

    private static Double parseNumber(String token) {
        try {
            double value = Double.parseDouble(
                    token.replace('D', 'E').replace('d', 'e'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
