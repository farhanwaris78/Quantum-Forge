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

import quantumforge.project.property.ProjectProperty;

/**
 * Parses phonon dispersion frequency files (matdyn.freq / *.freq.gp) from ph.x / matdyn.x,
 * mapping q-resolved vibrational branches and identifying unstable imaginary modes (Roadmap #51).
 */
public final class QEPhononFreqParser extends LogParser {

    public static final class PhononBranch {
        private final List<Double> qDistance = new ArrayList<>();
        private final List<Double> frequencyCm1 = new ArrayList<>();

        public void addPoint(double q, double freq) {
            this.qDistance.add(q);
            this.frequencyCm1.add(freq);
        }

        public double[] getQDistance() {
            return qDistance.stream().mapToDouble(Double::doubleValue).toArray();
        }

        public double[] getFrequencyCm1() {
            return frequencyCm1.stream().mapToDouble(Double::doubleValue).toArray();
        }

        public int size() {
            return qDistance.size();
        }
    }

    private final List<PhononBranch> branches = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean latticeStable = true;

    public QEPhononFreqParser(ProjectProperty property) {
        super(property);
    }

    public List<PhononBranch> getBranches() { return List.copyOf(this.branches); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isLatticeStable() { return this.latticeStable; }

    @Override
    public void parse(File file) throws IOException {
        this.branches.clear();
        this.diagnostics.clear();
        this.latticeStable = false;
        if (file == null || !file.isFile()) {
            this.diagnostics.add("Phonon frequency file is missing or not a regular file.");
            return;
        }

        List<double[]> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                if (trim.isEmpty() || trim.startsWith("#") || trim.startsWith("@")) {
                    continue; // Skip comments
                }

                String[] tokens = trim.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        double[] row = new double[tokens.length];
                        for (int i = 0; i < tokens.length; i++) {
                            row[i] = ScfConvergenceAnalyzer.parseFortranDouble(tokens[i]);
                            if (!Double.isFinite(row[i])) {
                                throw new NumberFormatException("non-finite frequency row");
                            }
                        }
                        if (!rows.isEmpty() && row.length != rows.get(0).length) {
                            this.diagnostics.add("Skipped phonon row with inconsistent branch count: " + trim);
                            continue;
                        }
                        rows.add(row);
                    } catch (NumberFormatException e) {
                        this.diagnostics.add("Skipped malformed phonon row: " + trim);
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            this.diagnostics.add("No valid q-path/frequency rows were found.");
            return;
        }

        // Reconstruct branches
        // Row format: [q_distance, freq_1, freq_2, ..., freq_3N]
        int numModes = rows.get(0).length - 1;
        for (int m = 0; m < numModes; m++) {
            this.branches.add(new PhononBranch());
        }

        int imaginaryModesCount = 0;
        double mostImaginaryCm1 = 0.0;

        for (double[] row : rows) {
            double q = row[0];
            for (int m = 0; m < numModes; m++) {
                double freq = row[m + 1];
                
                // QE prints imaginary unstable modes as negative frequencies (e.g. -50.0 cm-1)
                if (freq < -0.1) {
                    imaginaryModesCount++;
                    mostImaginaryCm1 = Math.min(mostImaginaryCm1, freq);
                }

                this.branches.get(m).addPoint(q, freq);
            }
        }

        if (imaginaryModesCount > 0) {
            this.latticeStable = false;
            this.diagnostics.add(String.format("Significant imaginary phonon frequencies: %d sampled q-path values below -0.1 cm-1.", imaginaryModesCount));
            this.diagnostics.add(String.format("Most negative sampled frequency: %.2f cm-1. Check relaxation, acoustic sum rule, q-path/grid, and convergence before assigning a physical instability.", mostImaginaryCm1));
        } else {
            this.latticeStable = true;
            this.diagnostics.add("No significant imaginary frequencies were found on the sampled q-path (tolerance 0.1 cm-1).");
            this.diagnostics.add("This is not a full Brillouin-zone stability proof; q-grid, ASR, and convergence still require review.");
        }
    }
}
