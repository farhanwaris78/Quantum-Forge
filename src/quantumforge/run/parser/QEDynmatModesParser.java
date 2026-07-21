/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses dynmat.x vibrational modes: the {@code omega( k ) = ... [THz] = ... [cm-1]}
 * records together with the per-atom displacement rows
 * {@code ( ux_re uy_re uz_re ux_im uy_im uz_im )} that follow each record
 * (Roadmap #52 data path).
 *
 * <p>Every parsed mode is audited against the orthonormal-eigenvector
 * normalization (squared norm 1 within the stated tolerance), and all modes in
 * one file must share the same atom count, otherwise the file is rejected:
 * physics-relevant consistency is enforced here rather than re-checked by any
 * later animation layer. An overall per-mode phase/sign is gauge freedom and
 * is intentionally not normalized.</p>
 */
public final class QEDynmatModesParser extends LogParser {

    /** Default squared-norm deviation tolerance for the orthonormality audit. */
    public static final double DEFAULT_NORM_TOLERANCE = 1.0e-2;

    private static final Pattern OMEGA = Pattern.compile(
            "omega\\s*\\(\\s*(\\d+)\\s*\\)\\s*=\\s*([-+0-9.DdEe]+)\\s*\\[THz\\]\\s*=\\s*"
                    + "([-+0-9.DdEe]+)\\s*\\[cm-1\\]", Pattern.CASE_INSENSITIVE);

    private static final Pattern DISPLACEMENT_ROW = Pattern.compile(
            "^\\s*\\(\\s*([-+0-9.DdEe\\s]+?)\\s*\\)\\s*$");

    /** One vibrational mode: frequency plus validated per-atom displacements. */
    public static final class VibrationalMode {
        private final int index;
        private final double omegaThz;
        private final double omegaCm1;
        private final double[][] displacements;
        private final double normDeviation;

        VibrationalMode(int index, double omegaThz, double omegaCm1,
                double[][] displacements) {
            this.index = index;
            this.omegaThz = omegaThz;
            this.omegaCm1 = omegaCm1;
            this.displacements = new double[displacements.length][6];
            for (int i = 0; i < displacements.length; i++) {
                System.arraycopy(displacements[i], 0, this.displacements[i], 0, 6);
            }
            double norm2 = 0.0;
            for (double[] row : displacements) {
                for (double component : row) {
                    norm2 += component * component;
                }
            }
            this.normDeviation = Math.abs(Math.sqrt(norm2) - 1.0);
        }

        public int getIndex() { return this.index; }
        public double getOmegaThz() { return this.omegaThz; }
        public double getOmegaCm1() { return this.omegaCm1; }
        public boolean isImaginary() { return this.omegaCm1 < 0.0; }
        public int getAtomCount() { return this.displacements.length; }

        /** Per-atom rows of (ux_re, uy_re, uz_re, ux_im, uy_im, uz_im). */
        public double[][] getDisplacements() {
            double[][] copy = new double[this.displacements.length][6];
            for (int i = 0; i < this.displacements.length; i++) {
                System.arraycopy(this.displacements[i], 0, copy[i], 0, 6);
            }
            return copy;
        }

        /** |norm - 1| of the whole orthonormal eigenvector. */
        public double getNormDeviation() { return this.normDeviation; }
    }

    private final List<VibrationalMode> modes = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private int atomCount = 0;
    private double maxNormDeviation = 0.0;

    public QEDynmatModesParser(ProjectProperty property) {
        super(property);
    }

    public List<VibrationalMode> getModes() { return List.copyOf(this.modes); }
    public int getAtomCount() { return this.atomCount; }
    public double getMaxNormDeviation() { return this.maxNormDeviation; }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    /** True when every parsed mode passes the orthonormality audit at the tolerance. */
    public boolean isNormalizationConsistent(double tolerance) {
        double tol = tolerance > 0.0 ? tolerance : DEFAULT_NORM_TOLERANCE;
        return !this.modes.isEmpty() && this.maxNormDeviation <= tol;
    }

    @Override
    public void parse(File file) throws IOException {
        this.modes.clear();
        this.diagnostics.clear();
        this.atomCount = 0;
        this.maxNormDeviation = 0.0;
        if (file == null || !file.isFile()) {
            this.diagnostics.add("dynmat output file is missing.");
            return;
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int cursor = 0;
        while (cursor < lines.size()) {
            Matcher omega = OMEGA.matcher(lines.get(cursor));
            if (!omega.find()) {
                cursor++;
                continue;
            }
            int index;
            double thz;
            double cm1;
            try {
                index = Integer.parseInt(omega.group(1));
                thz = parseFortranDouble(omega.group(2));
                cm1 = parseFortranDouble(omega.group(3));
            } catch (NumberFormatException ex) {
                this.diagnostics.add("Skipped an omega record with unparseable numbers: "
                        + lines.get(cursor).trim());
                cursor++;
                continue;
            }
            List<double[]> rows = new ArrayList<>();
            int look = cursor + 1;
            while (look < lines.size()) {
                String candidate = lines.get(look);
                // Decoration lines (asterisk banners / blanks) carry no data:
                // skipping them never invents or alters a displacement value.
                if (candidate.trim().isEmpty() || candidate.trim().matches("\\*+")) {
                    look++;
                    continue;
                }
                Matcher row = DISPLACEMENT_ROW.matcher(candidate);
                if (!row.matches()) {
                    break;
                }
                String[] tokens = row.group(1).trim().split("\\s+");
                if (tokens.length == 6) {
                    try {
                        double[] displacement = new double[6];
                        for (int i = 0; i < 6; i++) {
                            displacement[i] = parseFortranDouble(tokens[i]);
                        }
                        boolean finite = true;
                        for (double component : displacement) {
                            if (!Double.isFinite(component)) {
                                finite = false;
                                break;
                            }
                        }
                        if (finite) {
                            rows.add(displacement);
                        }
                    } catch (NumberFormatException ex) {
                        // A malformed displacement entry ends this mode's block.
                        break;
                    }
                }
                look++;
            }
            if (rows.isEmpty()) {
                this.diagnostics.add("Mode " + index + " has no readable displacement rows; "
                        + "the record was skipped instead of being guessed.");
            } else {
                VibrationalMode mode = new VibrationalMode(index, thz, cm1,
                        rows.toArray(new double[0][6]));
                if (this.atomCount == 0) {
                    this.atomCount = mode.getAtomCount();
                } else if (mode.getAtomCount() != this.atomCount) {
                    this.diagnostics.add(String.format(Locale.ROOT,
                            "Mode %d has %d displacement rows but earlier modes have %d; "
                            + "the file is internally inconsistent and was rejected.",
                            index, mode.getAtomCount(), this.atomCount));
                    this.modes.clear();
                    this.atomCount = 0;
                    this.maxNormDeviation = 0.0;
                    return;
                }
                this.maxNormDeviation = Math.max(this.maxNormDeviation,
                        mode.getNormDeviation());
                this.modes.add(mode);
            }
            cursor = Math.max(look, cursor + 1);
        }

        if (this.modes.isEmpty()) {
            this.diagnostics.add("No omega( k ) mode records with displacement rows were "
                    + "found; a dynmat.x output file is required.");
            return;
        }
        long imaginary = this.modes.stream().filter(VibrationalMode::isImaginary).count();
        if (imaginary > 0) {
            this.diagnostics.add(imaginary + " mode(s) carry negative frequencies (imaginary "
                    + "modes, printed negative by dynmat.x).");
        }
        if (!isNormalizationConsistent(DEFAULT_NORM_TOLERANCE)) {
            this.diagnostics.add(String.format(Locale.ROOT,
                    "Orthonormality audit failed: max |norm-1| = %.6f exceeds %.4f.",
                    this.maxNormDeviation, DEFAULT_NORM_TOLERANCE));
        } else {
            this.diagnostics.add(String.format(Locale.ROOT,
                    "Orthonormality audit passed: max |norm-1| = %.6f (tolerance %.4f).",
                    this.maxNormDeviation, DEFAULT_NORM_TOLERANCE));
        }
    }

    private static double parseFortranDouble(String token) {
        return Double.parseDouble(token.replace('D', 'E').replace('d', 'e'));
    }
}
