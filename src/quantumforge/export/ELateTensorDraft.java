/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed 6x6 elastic-tensor reader plus an ELATE-convention draft writer
 * (Roadmap #119). The reader targets the thermo_pw "Elastic Constant Matrix"
 * block and refuses anything that is not exactly six rows of six finite
 * values; the lenient legacy {@code ElasticParser} path is deliberately NOT
 * reused here because a zero-padded tensor exported for directional analysis
 * would silently produce wrong physics.
 *
 * <p>The draft document carries the Voigt convention (1=xx 2=yy 3=zz 4=yz
 * 5=xz 6=xy), the verbatim values (NO unit conversion is ever applied
 * silently - thermo_pw prints kbar while ELATE expects GPa, so the conversion
 * is an explicit REQUIRED-EDIT flag), the source provenance and the Born
 * stability verdict reported by the calling analysis. It is emitted only
 * through the GUI's explicit save action.</p>
 */
public final class ELateTensorDraft {

    /** Parse bound identical to the other bounded text readers. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /**
     * Accepted max |C_ij - C_ji| relative to the largest |C_ij|: print-rounded
     * rows pass, structurally non-Voigt input is refused instead of averaged.
     */
    public static final double SYMMETRY_REL_TOLERANCE = 1.0e-4;

    /** Parsed, symmetrized tensor plus the asymmetry audit that allowed it. */
    public static final class TensorBlock {
        private final double[][] cij;
        private final double maxAsymmetry;
        private final double maxAbs;

        TensorBlock(double[][] cij, double maxAsymmetry, double maxAbs) {
            this.cij = cij;
            this.maxAsymmetry = maxAsymmetry;
            this.maxAbs = maxAbs;
        }

        public double[][] getCij() {
            double[][] copy = new double[6][6];
            for (int i = 0; i < 6; i++) {
                System.arraycopy(this.cij[i], 0, copy[i], 0, 6);
            }
            return copy;
        }

        public double getMaxAsymmetry() { return this.maxAsymmetry; }
        public double getMaxAbs() { return this.maxAbs; }
    }

    private ELateTensorDraft() { }

    /**
     * Reads the tensor. Codes: ELATE_IO, ELATE_TOO_LARGE, ELATE_BLOCK,
     * ELATE_SYNTAX, ELATE_VALUE, ELATE_ASYMMETRY.
     */
    public static OperationResult<TensorBlock> readTensor(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("ELATE_IO", "The tensor file does not exist.",
                    null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("ELATE_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("ELATE_TOO_LARGE",
                    "The file exceeds the 64 MiB parse bound.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return OperationResult.failed("ELATE_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        int marker = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase(Locale.ROOT).contains("elastic constant matrix")) {
                marker = i;
                break;
            }
        }
        if (marker < 0) {
            return OperationResult.failed("ELATE_BLOCK",
                    "No 'Elastic Constant Matrix' block exists in this file; the draft "
                            + "needs the thermo_pw block header as provenance anchor.",
                    null);
        }
        double[][] raw = new double[6][6];
        int row = 0;
        int cursor = marker + 1;
        while (row < 6 && cursor < lines.size()) {
            String line = lines.get(cursor);
            cursor += 1;
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) {
                return OperationResult.failed("ELATE_SYNTAX",
                        "Tensor row " + (row + 1) + " has fewer than 6 values: '"
                                + line.trim() + "'.",
                        null);
            }
            for (int j = 0; j < 6; j++) {
                double value;
                try {
                    value = Double.parseDouble(parts[j]
                            .replace('D', 'E').replace('d', 'E'));
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("ELATE_VALUE",
                            "Tensor row " + (row + 1) + " column " + (j + 1)
                                    + " is not a finite number: '" + parts[j] + "'.",
                                    null);
                }
                if (!Double.isFinite(value)) {
                    return OperationResult.failed("ELATE_VALUE",
                            "Tensor row " + (row + 1) + " column " + (j + 1)
                                    + " is not finite: '" + parts[j] + "'.",
                                    null);
                }
                raw[row][j] = value;
            }
            row += 1;
        }
        if (row < 6) {
            return OperationResult.failed("ELATE_BLOCK",
                    "Only " + row + " tensor rows follow the matrix header; all 6 are "
                            + "required (no zero padding).",
                    null);
        }
        double maxAbs = 0.0;
        double maxAsym = 0.0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                maxAbs = Math.max(maxAbs, Math.abs(raw[i][j]));
                if (j > i) {
                    maxAsym = Math.max(maxAsym, Math.abs(raw[i][j] - raw[j][i]));
                }
            }
        }
        if (maxAbs == 0.0) {
            return OperationResult.failed("ELATE_VALUE",
                    "The parsed tensor is identically zero - a placeholder, not data.",
                    null);
        }
        if (maxAsym > SYMMETRY_REL_TOLERANCE * maxAbs) {
            return OperationResult.failed("ELATE_ASYMMETRY",
                    "max |C_ij - C_ji| = " + String.format(Locale.ROOT, "%.6g", maxAsym)
                            + " exceeds the " + SYMMETRY_REL_TOLERANCE
                            + " relative symmetry tolerance against max |C| = "
                            + String.format(Locale.ROOT, "%.6g", maxAbs)
                            + "; the file is not a Voigt tensor within print precision.",
                            null);
        }
        double[][] sym = new double[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                sym[i][j] = 0.5 * (raw[i][j] + raw[j][i]);
            }
        }
        return OperationResult.success("ELATE_TENSOR_OK",
                "Parsed a symmetric 6x6 tensor.", new TensorBlock(sym, maxAsym, maxAbs));
    }

    /**
     * Renders the ELATE-convention draft. Verbatim symmetrized values; the
     * kbar->GPa conversion thermo_pw users need is an explicit REQUIRED-EDIT
     * flag, never a silent division.
     */
    public static String draft(TensorBlock block, String sourceName, boolean stable,
            String stabilitySummary) {
        StringBuilder text = new StringBuilder();
        text.append("#!/usr/bin/env python3\n");
        text.append("# ELATE elastic-tensor draft - QuantumForge Roadmap #119\n");
        text.append("# Convention: Voigt stiffness order 1=xx 2=yy 3=zz 4=yz 5=xz 6=xy.\n");
        text.append("# Source: ").append(sourceName)
                .append(" (values verbatim, SYMMETRIZED (C+C^T)/2, max |C-C^T| = ")
                .append(String.format(Locale.ROOT, "%.6g", block.getMaxAsymmetry()))
                .append(")\n");
        text.append("# Born stability (Sylvester leading minors): ")
                .append(stable ? "STABLE - " : "UNSTABLE - ")
                .append(stabilitySummary).append('\n');
        if (!stable) {
            text.append("# REVIEW-REQUIRED: the tensor is mechanically UNSTABLE; ELATE "
                    + "directional properties of an unstable phase are not physical "
                    + "equilibrium properties.\n");
        }
        text.append("# Units: AS PARSED (thermo_pw prints kbar; ELATE expects GPa: "
                + "divide every value by 10).\n");
        text.append("# NO unit conversion was applied here; enabling it is your explicit\n");
        text.append("# edit. Reference: Gaillac, Coudert, JPCM 28 (2016) 275201;\n");
        text.append("# http://progs.coudert.name/elate\n");
        text.append("CONVERT_TO_GPA = False  # REQUIRED-EDIT: set True ONLY for kbar input\n\n");
        text.append("TENSOR = [\n");
        double[][] cij = block.getCij();
        for (int i = 0; i < 6; i++) {
            text.append("    [");
            for (int j = 0; j < 6; j++) {
                if (j > 0) {
                    text.append(", ");
                }
                text.append(Double.toString(cij[i][j]));
            }
            text.append("],\n");
        }
        text.append("]\n\n");
        text.append("if CONVERT_TO_GPA:\n");
        text.append("    TENSOR = [[c / 10.0 for c in row] for row in TENSOR]\n");
        text.append("# Paste/feed TENSOR to a local ELATE instance or the official site;\n");
        text.append("# QuantumForge does not send this tensor anywhere.\n");
        return text.toString();
    }
}
