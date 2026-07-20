/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.neural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #147 (first slice): physics-informed compositional BASELINE for an
 * extXYZ training dataset. Fits the deliberately simple linear model
 *
 * <pre>E(frame) = intercept + sum_species c_s * n_s(frame)</pre>
 *
 * by least squares over the energy-labeled frames and reports the per-frame
 * residuals. This is EXACTLY the isolated-atom/composition reference family
 * the roadmap asks ML predictions to be compared against BEFORE use: a frame
 * whose DFT label sits far from the compositional baseline (while its
 * neighbours sit close) is a candidate mislabel, mixed-method contamination
 * or unit slip - caught here, cheaply, before any model is trusted.
 *
 * <p>Load-bearing honesty points:</p>
 * <ul>
 *   <li>this is a HEURISTIC SCREEN, not a physics validation: a small
 *       residual does NOT prove a label right, and a large residual is a
 *       flag for the user, never an accusation the tool acts on;</li>
 *   <li>frames without a parseable energy/free_energy label are EXCLUDED
 *       and counted - their energies are never guessed or interpolated;</li>
 *   <li>the fit needs at least as many labeled frames as columns
 *       (1 + species count) - otherwise BASELINE_UNDERDETERMINED - and the
 *       compositions must vary independently of each other and the constant
 *       column, otherwise BASELINE_DEGENERATE (e.g. every frame is the same
 *       composition: there is NO compositional information to fit);</li>
 *   <li>coefficients are eV per atom of a fitted species WITH the intercept
 *       printed alongside - they are regression outputs of THIS dataset,
 *       not atomization or formation energies.</li>
 * </ul>
 *
 * <p>Refusal codes: BASELINE_IO, BASELINE_TOO_LARGE, BASELINE_SYNTAX,
 * BASELINE_EMPTY, BASELINE_ENERGY, BASELINE_WIDE, BASELINE_UNDERDETERMINED,
 * BASELINE_DEGENERATE.</p>
 */
public final class CompositionalBaselineMath {

    /** Bounds mirrored from {@link ExtXyzDatasetValidator}. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_FRAMES = 50_000;
    public static final int MAX_ATOMS_PER_FRAME = 1_000_000;
    public static final int MAX_SPECIES = 128;

    /** Largest residual outliers rendered/returned. */
    public static final int MAX_OUTLIERS = 5;

    private static final Pattern SPECIES_TOKEN = Pattern.compile("[A-Z][a-z]?");

    /** One flagged frame: 1-based frame index and its residual. */
    public static final class ResidualOutlier {
        private final int frame;
        private final double residualEv;
        private final double fittedEv;
        private final double actualEv;

        ResidualOutlier(int frame, double residualEv, double fittedEv, double actualEv) {
            this.frame = frame;
            this.residualEv = residualEv;
            this.fittedEv = fittedEv;
            this.actualEv = actualEv;
        }

        public int getFrame() { return this.frame; }
        public double getResidualEv() { return this.residualEv; }
        public double getFittedEv() { return this.fittedEv; }
        public double getActualEv() { return this.actualEv; }
    }

    /** The fitted baseline plus screening statistics. */
    public static final class BaselineReport {
        private final List<String> species;
        private final List<Double> coefficientsEv;
        private final double interceptEv;
        private final int framesUsed;
        private final int framesSkippedNoEnergy;
        private final double rmsEv;
        private final double meanAbsEv;
        private final double maxAbsEv;
        private final List<ResidualOutlier> outliers;

        BaselineReport(List<String> species, List<Double> coefficientsEv,
                double interceptEv, int framesUsed, int framesSkippedNoEnergy,
                double rmsEv, double meanAbsEv, double maxAbsEv,
                List<ResidualOutlier> outliers) {
            this.species = new ArrayList<>(species);
            this.coefficientsEv = new ArrayList<>(coefficientsEv);
            this.interceptEv = interceptEv;
            this.framesUsed = framesUsed;
            this.framesSkippedNoEnergy = framesSkippedNoEnergy;
            this.rmsEv = rmsEv;
            this.meanAbsEv = meanAbsEv;
            this.maxAbsEv = maxAbsEv;
            this.outliers = new ArrayList<>(outliers);
        }

        /** Sorted species of the design matrix columns (after intercept). */
        public List<String> getSpecies() { return List.copyOf(this.species); }
        /** eV per atom of each species, aligned with {@link #getSpecies()}. */
        public List<Double> getCoefficientsEv() { return List.copyOf(this.coefficientsEv); }
        /** Constant column of the fit, eV. */
        public double getInterceptEv() { return this.interceptEv; }
        /** Energy-labeled frames the fit used. */
        public int getFramesUsed() { return this.framesUsed; }
        /** Frames EXCLUDED for a missing/unparseable energy label. */
        public int getFramesSkippedNoEnergy() { return this.framesSkippedNoEnergy; }
        public double getRmsEv() { return this.rmsEv; }
        public double getMeanAbsEv() { return this.meanAbsEv; }
        public double getMaxAbsEv() { return this.maxAbsEv; }
        /** Up to {@link #MAX_OUTLIERS} largest-|residual| frames. */
        public List<ResidualOutlier> getOutliers() { return List.copyOf(this.outliers); }
    }

    /** Scanned design-matrix content (package-visible seam for tests). */
    static final class ScanResult {
        final List<String> species;
        final List<int[]> compositions;
        final List<Double> energies;
        final List<Integer> frameNumbers;
        final int skippedNoEnergy;
        final int totalFrames;

        ScanResult(List<String> species, List<int[]> compositions,
                List<Double> energies, List<Integer> frameNumbers,
                int skippedNoEnergy, int totalFrames) {
            this.species = species;
            this.compositions = compositions;
            this.energies = energies;
            this.frameNumbers = frameNumbers;
            this.skippedNoEnergy = skippedNoEnergy;
            this.totalFrames = totalFrames;
        }
    }

    private CompositionalBaselineMath() {
    }

    /**
     * Bounded file entry point; same codes as the class javadoc.
     */
    public static OperationResult<BaselineReport> evaluate(Path file) {
        if (file == null || !Files.exists(file)) {
            return OperationResult.failed("BASELINE_IO",
                    "The extXYZ dataset file does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("BASELINE_IO",
                    "Could not stat the dataset file: " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("BASELINE_TOO_LARGE",
                    "The dataset is " + size + " bytes; the baseline cap is "
                            + MAX_FILE_BYTES + " bytes.",
                    null);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("BASELINE_IO",
                    "Reading the dataset failed: " + ex.getMessage(), null);
        }
        OperationResult<ScanResult> scanned = scanText(text);
        if (!scanned.isSuccess() || scanned.getValue().isEmpty()) {
            return OperationResult.failed(scanned.getCode(), scanned.getMessage(), null);
        }
        return fit(scanned.getValue().get());
    }

    /**
     * Scans extXYZ frames (validator-mirrored grammar) into design-matrix
     * rows. Codes: BASELINE_SYNTAX, BASELINE_EMPTY, BASELINE_ENERGY,
     * BASELINE_WIDE.
     */
    static OperationResult<ScanResult> scanText(String text) {
        if (text == null || text.isBlank()) {
            return OperationResult.failed("BASELINE_EMPTY",
                    "The dataset file is empty.", null);
        }
        String[] lines = text.split("\n", -1);
        List<List<String>> frameSpecies = new ArrayList<>();
        List<Double> energies = new ArrayList<>();
        List<Integer> frameNumbers = new ArrayList<>();
        Set<String> speciesSeen = new TreeSet<>();
        int skippedNoEnergy = 0;
        int totalFrames = 0;
        int cursor = 0;
        while (cursor < lines.length) {
            String head = lines[cursor].trim();
            if (head.isEmpty()) {
                cursor += 1;
                continue;
            }
            if (totalFrames >= MAX_FRAMES) {
                return OperationResult.failed("BASELINE_SYNTAX",
                        "More than " + MAX_FRAMES + " frames; refusing to scan on.",
                        null);
            }
            int atoms;
            try {
                atoms = Integer.parseInt(head);
            } catch (NumberFormatException ex) {
                return OperationResult.failed("BASELINE_SYNTAX",
                        "Frame " + (totalFrames + 1) + " (line " + (cursor + 1)
                                + ") does not start with an atom count: \"" + head
                                + "\"",
                        null);
            }
            if (atoms < 1 || atoms > MAX_ATOMS_PER_FRAME) {
                return OperationResult.failed("BASELINE_SYNTAX",
                        "Frame " + (totalFrames + 1) + " declares " + atoms
                                + " atoms; expected 1.." + MAX_ATOMS_PER_FRAME + ".",
                        null);
            }
            if (cursor + 1 >= lines.length) {
                return OperationResult.failed("BASELINE_SYNTAX",
                        "Frame " + (totalFrames + 1) + " ends before its comment "
                                + "line.",
                        null);
            }
            String comment = lines[cursor + 1];
            if (cursor + 2 + atoms > lines.length) {
                return OperationResult.failed("BASELINE_SYNTAX",
                        "Frame " + (totalFrames + 1) + " declares " + atoms
                                + " atoms but only " + (lines.length - cursor - 2)
                                + " row(s) remain.",
                        null);
            }
            List<String> frameSpeciesRow = new ArrayList<>();
            for (int atom = 0; atom < atoms; atom += 1) {
                String row = lines[cursor + 2 + atom].trim();
                String[] tokens = row.split("\\s+");
                if (tokens.length < 4) {
                    return OperationResult.failed("BASELINE_SYNTAX",
                            "Frame " + (totalFrames + 1) + " atom row " + (atom + 1)
                                    + " must hold species + 3 coordinates: \"" + row
                                    + "\"",
                            null);
                }
                if (!SPECIES_TOKEN.matcher(tokens[0]).matches()) {
                    return OperationResult.failed("BASELINE_SYNTAX",
                            "Frame " + (totalFrames + 1) + " atom row " + (atom + 1)
                                    + " has a malformed species token: \"" + tokens[0]
                                    + "\"",
                            null);
                }
                for (int axis = 1; axis <= 3; axis += 1) {
                    try {
                        double value = Double.parseDouble(
                                tokens[axis].replace('D', 'E').replace('d', 'e'));
                        if (!Double.isFinite(value)) {
                            return OperationResult.failed("BASELINE_SYNTAX",
                                    "Frame " + (totalFrames + 1) + " atom row "
                                            + (atom + 1) + " has a non-finite "
                                            + "coordinate: \"" + tokens[axis] + "\"",
                                    null);
                        }
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("BASELINE_SYNTAX",
                                "Frame " + (totalFrames + 1) + " atom row " + (atom + 1)
                                        + " has a non-numeric coordinate: \""
                                        + tokens[axis] + "\"",
                                null);
                    }
                }
                frameSpeciesRow.add(tokens[0]);
                speciesSeen.add(tokens[0]);
            }
            totalFrames += 1;
            String energyText = extractBare(comment, "energy");
            if (energyText == null) {
                energyText = extractBare(comment, "free_energy");
            }
            Double energy = null;
            if (energyText != null) {
                try {
                    double parsed = Double.parseDouble(
                            energyText.replace('D', 'E').replace('d', 'e'));
                    if (Double.isFinite(parsed)) {
                        energy = parsed;
                    }
                } catch (NumberFormatException ex) {
                    energy = null;
                }
            }
            if (energy == null) {
                skippedNoEnergy += 1;
            } else {
                frameSpecies.add(new ArrayList<>(frameSpeciesRow));
                energies.add(energy);
                frameNumbers.add(totalFrames);
            }
            cursor += 2 + atoms;
        }
        if (totalFrames == 0) {
            return OperationResult.failed("BASELINE_EMPTY",
                    "No frame was found in the dataset.", null);
        }
        if (speciesSeen.size() > MAX_SPECIES) {
            return OperationResult.failed("BASELINE_WIDE",
                    "The dataset holds " + speciesSeen.size() + " species; the "
                            + "baseline design-matrix cap is " + MAX_SPECIES + ".",
                    null);
        }
        if (energies.isEmpty()) {
            return OperationResult.failed("BASELINE_ENERGY",
                    "No frame carries a parseable energy/free_energy label - there "
                            + "is nothing to fit and nothing is invented.",
                    null);
        }
        List<String> species = new ArrayList<>(speciesSeen);
        int speciesCount = species.size();
        List<int[]> compositions = new ArrayList<>();
        for (List<String> rowSpecies : frameSpecies) {
            int[] counts = new int[speciesCount];
            for (String token : rowSpecies) {
                counts[species.indexOf(token)] += 1;
            }
            compositions.add(counts);
        }
        return OperationResult.success("BASELINE_SCAN_OK",
                "Scanned " + totalFrames + " frame(s).",
                new ScanResult(species, compositions, energies, frameNumbers,
                        skippedNoEnergy, totalFrames));
    }

    /**
     * Least-squares compositional fit (normal equations on column-scaled
     * design matrix, Gaussian elimination with partial pivoting). Codes:
     * BASELINE_UNDERDETERMINED, BASELINE_DEGENERATE.
     */
    static OperationResult<BaselineReport> fit(ScanResult scan) {
        int rows = scan.energies.size();
        int columns = 1 + scan.species.size();
        if (rows < columns) {
            return OperationResult.failed("BASELINE_UNDERDETERMINED",
                    "The fit needs at least 1 + " + scan.species.size() + " = "
                            + columns + " energy-labeled frames for "
                            + scan.species.size() + " species plus the intercept, "
                            + "but only " + rows + " labeled frame(s) exist.",
                    null);
        }
        double[][] design = new double[rows][columns];
        double[] rhs = new double[rows];
        for (int i = 0; i < rows; i += 1) {
            design[i][0] = 1.0;
            for (int s = 0; s < scan.species.size(); s += 1) {
                design[i][1 + s] = scan.compositions.get(i)[s];
            }
            rhs[i] = scan.energies.get(i);
        }
        double[] scales = new double[columns];
        for (int j = 0; j < columns; j += 1) {
            double norm = 0.0;
            for (int i = 0; i < rows; i += 1) {
                norm += design[i][j] * design[i][j];
            }
            norm = Math.sqrt(norm);
            scales[j] = norm > 0.0 ? norm : 1.0;
        }
        double[][] normal = new double[columns][columns + 1];
        for (int j = 0; j < columns; j += 1) {
            for (int k = 0; k < columns; k += 1) {
                double sum = 0.0;
                for (int i = 0; i < rows; i += 1) {
                    sum += (design[i][j] / scales[j]) * (design[i][k] / scales[k]);
                }
                normal[j][k] = sum;
            }
            double sum = 0.0;
            for (int i = 0; i < rows; i += 1) {
                sum += (design[i][j] / scales[j]) * rhs[i];
            }
            normal[j][columns] = sum;
        }
        for (int k = 0; k < columns; k += 1) {
            int pivotRow = k;
            double pivotAbs = Math.abs(normal[k][k]);
            for (int i = k + 1; i < columns; i += 1) {
                double candidate = Math.abs(normal[i][k]);
                if (candidate > pivotAbs) {
                    pivotAbs = candidate;
                    pivotRow = i;
                }
            }
            if (pivotAbs < 1e-10) {
                return OperationResult.failed("BASELINE_DEGENERATE",
                        "The compositions do not vary independently of each other "
                                + "and the constant column (pivot " + pivotAbs
                                + " at column " + (k + 1) + "): there is NO "
                                + "compositional information to fit - e.g. every "
                                + "labeled frame has the same composition.",
                        null);
            }
            if (pivotRow != k) {
                double[] swap = normal[k];
                normal[k] = normal[pivotRow];
                normal[pivotRow] = swap;
            }
            double pivot = normal[k][k];
            for (int i = k + 1; i < columns; i += 1) {
                double factor = normal[i][k] / pivot;
                for (int j = k; j <= columns; j += 1) {
                    normal[i][j] -= factor * normal[k][j];
                }
            }
        }
        double[] solution = new double[columns];
        for (int i = columns - 1; i >= 0; i -= 1) {
            double sum = normal[i][columns];
            for (int j = i + 1; j < columns; j += 1) {
                sum -= normal[i][j] * solution[j];
            }
            solution[i] = sum / normal[i][i];
        }
        for (int j = 0; j < columns; j += 1) {
            solution[j] /= scales[j];
        }
        double rms = 0.0;
        double meanAbs = 0.0;
        double maxAbs = 0.0;
        List<ResidualOutlier> ranked = new ArrayList<>();
        for (int i = 0; i < rows; i += 1) {
            double fitted = 0.0;
            for (int j = 0; j < columns; j += 1) {
                fitted += design[i][j] * solution[j];
            }
            double residual = rhs[i] - fitted;
            rms += residual * residual;
            meanAbs += Math.abs(residual);
            maxAbs = Math.max(maxAbs, Math.abs(residual));
            ranked.add(new ResidualOutlier(scan.frameNumbers.get(i), residual, fitted,
                    rhs[i]));
        }
        rms = Math.sqrt(rms / rows);
        meanAbs /= rows;
        ranked.sort((left, right) -> {
            int byMagnitude = Double.compare(Math.abs(right.getResidualEv()),
                    Math.abs(left.getResidualEv()));
            if (byMagnitude != 0) {
                return byMagnitude;
            }
            return Integer.compare(left.getFrame(), right.getFrame());
        });
        List<ResidualOutlier> outliers = new ArrayList<>();
        for (int idx = 0; idx < ranked.size() && idx < MAX_OUTLIERS; idx += 1) {
            outliers.add(ranked.get(idx));
        }
        List<Double> coefficients = new ArrayList<>();
        for (int s = 0; s < scan.species.size(); s += 1) {
            coefficients.add(solution[1 + s]);
        }
        return OperationResult.success("BASELINE_OK",
                "Fitted " + rows + " frame(s).",
                new BaselineReport(scan.species, coefficients, solution[0], rows,
                        scan.skippedNoEnergy, rms, meanAbs, maxAbs, outliers));
    }

    private static String extractBare(String comment, String key) {
        for (String token : comment.split("\\s+")) {
            if (token.startsWith(key + "=") && !token.substring(key.length() + 1)
                    .startsWith("\"")) {
                return token.substring(key.length() + 1);
            }
        }
        return null;
    }
}
