/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #50 (review slice): a fail-closed GEOMETRIC audit of a multi-frame
 * XYZ NEB path artifact (the file a path editor or interpolation engine would
 * feed to neb.x). This is deliberately NOT the editor itself - it VERDICTS an
 * existing ladder: 2..64 frames (owned bound, stated), a shared atom count and
 * a shared element sequence across every frame (reordered atoms between frames
 * make per-atom displacement meaningless - refused, not averaged away), finite
 * Cartesian coordinates in Angstrom (stated convention).
 *
 * <ul>
 *   <li>per consecutive image pair: RMS displacement (root-mean-square of the
 *       per-atom 3D displacement) and the MAX single-atom displacement - both
 *       exact arithmetic over the file's own Angstrom coordinates;</li>
 *   <li>defects NAMED, never hidden: any near-zero-displacement consecutive
 *       pair (a duplicated image has no spring force and silently breaks path
 *       parameterization), a coincident first/last frame (reported, NOT
 *       refused - ring/closed paths are legitimate), and spacing uniformity
 *       reported as max/min RMSD ratio against the owned RULE-OF-THUMB bound
 *       1.5 (labeled ours, never a QE rule);</li>
 *   <li>scope honesty: Cartesian frame-only - NO minimum-image convention, no
 *       cell wrap, no spring-constant energy model, no climbing-image judge.
 *       Periodic paths, force projections and CI decisions are the #50
 *       cell-aware editor depth, stated as such on every report.</li>
 * </ul>
 *
 * <p>Codes: NEB_FILE (missing/oversized/unreadable), NEB_SHAPE (grammar/frame
 * consistency), NEB_VALUE (non-finite/duplicate geometry is reported, not a
 * code), NEB_OK.</p>
 */
public final class NebPathAudit {

    public static final int MIN_FRAMES = 2;
    public static final int MAX_FRAMES = 64;
    public static final int MAX_ATOMS = 100_000;
    public static final long MAX_FILE_BYTES = 8_000_000L;
    /** Owned RULE-OF-THUMB bound for max/min pair-RMSD ratio - stated as ours. */
    public static final double SPACING_RATIO_RULE = 1.5;
    /** Pair RMSD below this (Angstrom) means a duplicated image in the ladder. */
    public static final double DUPLICATE_PAIR_RMSD = 1.0e-8;
    /** First/last max displacement below this (Angstrom) means coincident endpoints. */
    public static final double COINCIDENT_ENDPOINT_DISP = 1.0e-6;

    /** One frame: element sequence + Cartesian rows (Angstrom). */
    public static final class Frame {
        private final String[] elements;
        private final double[][] coords;   // [atom][xyz]

        Frame(String[] elements, double[][] coords) {
            this.elements = elements;
            this.coords = coords;
        }

        public int size() { return this.elements.length; }
        public String element(int i) { return this.elements[i]; }
        public double[] coord(int i) { return this.coords[i]; }
    }

    /** Per-pair metrics. */
    public static final class PairMetrics {
        private final int from;        // 0-based frame index
        private final double rmsd;     // Angstrom
        private final double maxDisp;  // Angstrom (single atom)

        PairMetrics(int from, double rmsd, double maxDisp) {
            this.from = from;
            this.rmsd = rmsd;
            this.maxDisp = maxDisp;
        }

        public int getFrom() { return this.from; }
        public double getRmsd() { return this.rmsd; }
        public double getMaxDisp() { return this.maxDisp; }
    }

    /** Whole-audit value. */
    public static final class Audit {
        private final Path file;
        private final int frames;
        private final int atomsPerFrame;
        private final List<PairMetrics> pairs;
        private final double minRmsd;
        private final double maxRmsd;
        private final double totalLength;   // sum of pair RMSDs
        private final int worstPairFrom;    // pair index of maxRmsd
        private final int bestPairFrom;     // pair index of minRmsd
        private final List<Integer> duplicatePairs;  // from-indices below DUPLICATE_PAIR_RMSD
        private final boolean coincidentEndpoints;
        private final double endpointMaxDisp;

        Audit(Path file, int frames, int atomsPerFrame, List<PairMetrics> pairs,
              double minRmsd, double maxRmsd, double totalLength, int worstPairFrom,
              int bestPairFrom, List<Integer> duplicatePairs,
              boolean coincidentEndpoints, double endpointMaxDisp) {
            this.file = file;
            this.frames = frames;
            this.atomsPerFrame = atomsPerFrame;
            this.pairs = List.copyOf(pairs);
            this.minRmsd = minRmsd;
            this.maxRmsd = maxRmsd;
            this.totalLength = totalLength;
            this.worstPairFrom = worstPairFrom;
            this.bestPairFrom = bestPairFrom;
            this.duplicatePairs = List.copyOf(duplicatePairs);
            this.coincidentEndpoints = coincidentEndpoints;
            this.endpointMaxDisp = endpointMaxDisp;
        }

        public Path getFile() { return this.file; }
        public int getFrames() { return this.frames; }
        public int getAtomsPerFrame() { return this.atomsPerFrame; }
        public List<PairMetrics> getPairs() { return this.pairs; }
        public double getMinRmsd() { return this.minRmsd; }
        public double getMaxRmsd() { return this.maxRmsd; }
        public double getTotalLength() { return this.totalLength; }
        public int getWorstPairFrom() { return this.worstPairFrom; }
        public int getBestPairFrom() { return this.bestPairFrom; }
        public List<Integer> getDuplicatePairs() { return this.duplicatePairs; }
        public boolean hasCoincidentEndpoints() { return this.coincidentEndpoints; }
        public double getEndpointMaxDisp() { return this.endpointMaxDisp; }
        /** max/min RMSD ratio; +Infinity semantics never appear (min >= 0 duplicates caught). */
        public double spacingRatio() {
            return this.minRmsd <= 0.0 ? Double.POSITIVE_INFINITY
                    : this.maxRmsd / this.minRmsd;
        }
    }

    private NebPathAudit() {
        // Utility.
    }

    /** Parse + audit {@code file} as a multi-frame XYZ path. Read-only. */
    public static OperationResult<Audit> audit(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("NEB_FILE",
                    "No such path artifact - audit needs an existing multi-frame XYZ file.",
                    null);
        }
        final long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("NEB_FILE",
                    "Could not stat the path artifact: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("NEB_FILE",
                    "Path artifact is " + size + " bytes, above the bounded-read cap of "
                            + MAX_FILE_BYTES + ".", null);
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("NEB_FILE",
                    "Could not read the path artifact: " + ex.getMessage(), ex);
        }
        OperationResult<List<Frame>> parsed = parseFrames(lines);
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return OperationResult.failed(parsed.getCode(), parsed.getMessage(), null);
        }
        List<Frame> frames = parsed.getValue().get();
        return OperationResult.success("NEB_OK",
                "Audited " + frames.size() + " frame(s).", measure(file, frames));
    }

    /** Owned strict XYZ grammar: natoms line, comment line, natoms atom rows. */
    static OperationResult<List<Frame>> parseFrames(List<String> lines) {
        List<Frame> frames = new ArrayList<>();
        int pos = 0;
        int n = lines.size();
        while (pos < n) {
            while (pos < n && lines.get(pos).isBlank()) {
                pos++;
            }
            if (pos >= n) {
                break;
            }
            if (frames.size() >= MAX_FRAMES) {
                return OperationResult.failed("NEB_SHAPE",
                        "More than " + MAX_FRAMES + " frames (owned bound) - split the "
                                + "ladder or raise the bound deliberately.", null);
            }
            final int natoms;
            try {
                natoms = Integer.parseInt(lines.get(pos).trim());
            } catch (NumberFormatException ex) {
                return OperationResult.failed("NEB_SHAPE",
                        "Line " + (pos + 1) + ": expected an atom-count line to open a "
                                + "frame, got '" + truncate(lines.get(pos)) + "'.", null);
            }
            if (natoms <= 0 || natoms > MAX_ATOMS) {
                return OperationResult.failed("NEB_SHAPE",
                        "Line " + (pos + 1) + ": atom count " + natoms
                                + " outside owned band 1.." + MAX_ATOMS + ".", null);
            }
            pos++;  // comment line (content is the author's - never audited semantically)
            if (pos >= n) {
                return OperationResult.failed("NEB_SHAPE",
                        "Truncated artifact: comment line missing after atom count.", null);
            }
            pos++;
            if (pos + natoms > n) {
                return OperationResult.failed("NEB_SHAPE",
                        "Truncated artifact: frame wants " + natoms + " atom rows but only "
                                + (n - pos) + " line(s) remain.", null);
            }
            String[] elements = new String[natoms];
            double[][] coords = new double[natoms][3];
            for (int i = 0; i < natoms; i++) {
                String row = lines.get(pos + i).trim();
                String[] tokens = row.split("\\s+");
                if (tokens.length < 4) {
                    return OperationResult.failed("NEB_SHAPE",
                            "Line " + (pos + i + 1) + ": atom row needs at least "
                                    + "'el x y z', got " + tokens.length + " token(s): '"
                                    + truncate(row) + "'.", null);
                }
                String element = tokens[0];
                if (!element.matches("[A-Za-z]{1,3}")) {
                    return OperationResult.failed("NEB_SHAPE",
                            "Line " + (pos + i + 1) + ": element token '" + element
                                    + "' is not 1-3 letters.", null);
                }
                for (int axis = 0; axis < 3; axis++) {
                    try {
                        coords[i][axis] = Double.parseDouble(tokens[axis + 1]);
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("NEB_SHAPE",
                                "Line " + (pos + i + 1) + ": coordinate '" + tokens[axis + 1]
                                        + "' is not a number.", null);
                    }
                    if (!Double.isFinite(coords[i][axis])) {
                        return OperationResult.failed("NEB_SHAPE",
                                "Line " + (pos + i + 1) + ": coordinate " + coords[i][axis]
                                        + " is non-finite - refuse rather than propagate.",
                                null);
                    }
                }
                elements[i] = element;
            }
            pos += natoms;
            frames.add(new Frame(elements, coords));
        }
        if (frames.size() < MIN_FRAMES) {
            return OperationResult.failed("NEB_SHAPE",
                    "A path needs at least " + MIN_FRAMES + " frames (found "
                            + frames.size() + ") - a single frame is a structure, not a ladder.",
                    null);
        }
        Frame first = frames.get(0);
        for (int f = 1; f < frames.size(); f++) {
            Frame frame = frames.get(f);
            if (frame.size() != first.size()) {
                return OperationResult.failed("NEB_SHAPE",
                        "Frame " + (f + 1) + " has " + frame.size() + " atom(s) but frame 1 "
                                + "has " + first.size() + " - inconsistent ladders cannot be "
                                + "audited for displacement.", null);
            }
            for (int i = 0; i < first.size(); i++) {
                if (!frame.element(i).equals(first.element(i))) {
                    return OperationResult.failed("NEB_SHAPE",
                            "Frame " + (f + 1) + " atom " + (i + 1) + " is "
                                    + frame.element(i) + " but frame 1 has "
                                    + first.element(i) + " - reordered atoms between images "
                                    + "make per-atom displacement meaningless.", null);
                }
            }
        }
        return OperationResult.success("NEB_OK", "Parsed " + frames.size() + " frame(s).",
                frames);
    }

    /** Exact arithmetic over the parsed frames. */
    static Audit measure(Path file, List<Frame> frames) {
        List<PairMetrics> pairs = new ArrayList<>();
        double minRmsd = Double.POSITIVE_INFINITY;
        double maxRmsd = Double.NEGATIVE_INFINITY;
        double total = 0.0;
        int worst = 0;
        int best = 0;
        List<Integer> duplicates = new ArrayList<>();
        for (int f = 0; f < frames.size() - 1; f++) {
            Frame a = frames.get(f);
            Frame b = frames.get(f + 1);
            double sumSq = 0.0;
            double maxDisp = 0.0;
            for (int i = 0; i < a.size(); i++) {
                double dx = b.coord(i)[0] - a.coord(i)[0];
                double dy = b.coord(i)[1] - a.coord(i)[1];
                double dz = b.coord(i)[2] - a.coord(i)[2];
                double d2 = dx * dx + dy * dy + dz * dz;
                sumSq += d2;
                if (d2 > maxDisp * maxDisp) {
                    maxDisp = Math.sqrt(d2);
                }
            }
            double rmsd = Math.sqrt(sumSq / a.size());
            pairs.add(new PairMetrics(f, rmsd, maxDisp));
            total += rmsd;
            if (rmsd > maxRmsd) {
                maxRmsd = rmsd;
                worst = f;
            }
            if (rmsd < minRmsd) {
                minRmsd = rmsd;
                best = f;
            }
            if (rmsd < DUPLICATE_PAIR_RMSD) {
                duplicates.add(f);
            }
        }
        Frame first = frames.get(0);
        Frame last = frames.get(frames.size() - 1);
        double endpointMax = 0.0;
        for (int i = 0; i < first.size(); i++) {
            double dx = last.coord(i)[0] - first.coord(i)[0];
            double dy = last.coord(i)[1] - first.coord(i)[1];
            double dz = last.coord(i)[2] - first.coord(i)[2];
            double disp = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (disp > endpointMax) {
                endpointMax = disp;
            }
        }
        return new Audit(file, frames.size(), first.size(), pairs, minRmsd, maxRmsd,
                total, worst, best, duplicates,
                endpointMax < COINCIDENT_ENDPOINT_DISP, endpointMax);
    }

    private static String truncate(String line) {
        String trimmed = line.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40) + "...";
    }

    /** Format helper for report/CSV reuse: 6 decimals, Locale.ROOT. */
    public static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
