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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses dielectric constant tensors and Born effective charge tensors from DFPT
 * (ph.x) calculations, enforcing the Acoustic Charge Sum Rule (ACSR) (Roadmap #61).
 */
public final class QEBornChargeDielectricParser extends LogParser {

    public static final class BornCharge {
        private final int atomIndex;
        private final double[][] tensor;

        public BornCharge(int atomIndex, double[][] tensor) {
            this.atomIndex = atomIndex;
            this.tensor = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(tensor[i], 0, this.tensor[i], 0, 3);
            }
        }

        public int getAtomIndex() { return this.atomIndex; }
        public double[][] getTensor() {
            double[][] out = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(this.tensor[i], 0, out[i], 0, 3);
            }
            return out;
        }
    }

    private double[][] dielectricTensor = new double[3][3];
    private final List<BornCharge> bornCharges = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean acsrPassed = true;

    public QEBornChargeDielectricParser(ProjectProperty property) {
        super(property);
    }

    public double[][] getDielectricTensor() {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(this.dielectricTensor[i], 0, out[i], 0, 3);
        }
        return out;
    }

    public List<BornCharge> getBornCharges() { return List.copyOf(this.bornCharges); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isAcsrPassed() { return this.acsrPassed; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.bornCharges.clear();
        this.diagnostics.clear();
        this.acsrPassed = true;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                // 1. Detect dielectric constant tensor block
                if (trim.contains("Dielectric constant tensor") || trim.contains("Dielectric tensor")) {
                    parseDielectricTensor(reader);
                }

                // 2. Detect Born effective charges block
                if (trim.contains("Effective charges (espresso units)") && trim.contains("atom")) {
                    Matcher m = Pattern.compile("atom\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(trim);
                    if (m.find()) {
                        int atomIdx = Integer.parseInt(m.group(1));
                        parseBornChargeTensor(reader, atomIdx);
                    }
                }
            }
        }

        performAcousticSumRuleCheck();
    }

    private void parseDielectricTensor(BufferedReader reader) throws IOException {
        double[][] mat = read3x3Matrix(reader);
        if (mat != null) {
            this.dielectricTensor = mat;
        }
    }

    private void parseBornChargeTensor(BufferedReader reader, int atomIdx) throws IOException {
        double[][] mat = read3x3Matrix(reader);
        if (mat != null) {
            this.bornCharges.add(new BornCharge(atomIdx, mat));
        }
    }

    private double[][] read3x3Matrix(BufferedReader reader) throws IOException {
        double[][] mat = new double[3][3];
        int count = 0;
        String line;
        while (count < 3 && (line = reader.readLine()) != null) {
            String trim = line.trim();
            if (trim.isEmpty()) {
                continue;
            }
            String[] tokens = trim.split("\\s+");
            if (tokens.length >= 3) {
                try {
                    mat[count][0] = Double.parseDouble(tokens[0]);
                    mat[count][1] = Double.parseDouble(tokens[1]);
                    mat[count][2] = Double.parseDouble(tokens[2]);
                    count++;
                } catch (NumberFormatException e) {
                    // Stop parsing if malformed
                    return null;
                }
            }
        }
        return count == 3 ? mat : null;
    }

    /**
     * Enforces the Acoustic Charge Sum Rule (ACSR): Sum of Born effective charges over all atoms
     * must equal 0 within numerical thresholds (charge neutrality constraint).
     */
    private void performAcousticSumRuleCheck() {
        if (this.bornCharges.isEmpty()) {
            this.acsrPassed = false;
            this.diagnostics.add("Acoustic Charge Sum Rule check skipped: No Born effective charges parsed.");
            return;
        }

        double[][] sum = new double[3][3];
        for (BornCharge charge : this.bornCharges) {
            double[][] tensor = charge.getTensor();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    sum[i][j] += tensor[i][j];
                }
            }
        }

        double maxDeviation = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                maxDeviation = Math.max(maxDeviation, Math.abs(sum[i][j]));
            }
        }

        if (maxDeviation > 0.05) {
            this.acsrPassed = false;
            this.diagnostics.add(String.format("Acoustic Charge Sum Rule VIOLATION: Sum of Born effective charges deviates by max %.4f espresso units.", maxDeviation));
            this.diagnostics.add("Warning: Dielectric or phonon results may be non-reproducible. Check your k-points and SCF convergence thresholds.");
        } else {
            this.acsrPassed = true;
            this.diagnostics.add(String.format("Acoustic Charge Sum Rule passed. Max sum deviation is %.4f (Within 0.05 threshold).", maxDeviation));
        }
    }
}
