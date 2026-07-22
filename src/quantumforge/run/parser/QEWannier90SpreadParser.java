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
 * Parses cycle-by-cycle Wannier function quadratic spreads and total spread (Omega)
 * from Wannier90 (.wout) output logs, diagnosing localization convergence (Roadmap #69).
 */
public final class QEWannier90SpreadParser extends LogParser {

    public static final class WannierSpreadFrame {
        private final int cycle;
        private final List<Double> individualSpreads = new ArrayList<>();
        private final double totalSpreadAng2;

        public WannierSpreadFrame(int cycle, List<Double> individual, double total) {
            this.cycle = cycle;
            if (individual != null) {
                this.individualSpreads.addAll(individual);
            }
            this.totalSpreadAng2 = total;
        }

        public int getCycle() { return this.cycle; }
        public List<Double> getIndividualSpreads() { return List.copyOf(this.individualSpreads); }
        public double getTotalSpreadAng2() { return this.totalSpreadAng2; }
    }

    private final List<WannierSpreadFrame> convergenceHistory = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean converged = false;

    public QEWannier90SpreadParser(ProjectProperty property) {
        super(property);
    }

    public List<WannierSpreadFrame> getConvergenceHistory() { return List.copyOf(this.convergenceHistory); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isConverged() { return this.converged; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.convergenceHistory.clear();
        this.diagnostics.clear();
        this.converged = false;

        // Wannier90 spread lines look like:
        // CYCLE     10  Spreads: (  1.24125,  0.81240,  0.51230 ) Total:    2.56595
        // We use regex to extract the cycle number, the list of spreads, and the total spread.
        Pattern pCycle = Pattern.compile("CYCLE\\s+(\\d+)\\s+Spreads:\\s*\\(([^\\)]+)\\)\\s+Total:\\s*([-\\d.]+)", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mCycle = pCycle.matcher(trim);
                if (mCycle.find()) {
                    try {
                        int cycle = Integer.parseInt(mCycle.group(1));
                        String spreadsBlock = mCycle.group(2);
                        double total = Double.parseDouble(mCycle.group(3));

                        List<Double> individual = new ArrayList<>();
                        for (String token : spreadsBlock.split(",")) {
                            String tokTrim = token.trim();
                            if (!tokTrim.isEmpty()) {
                                individual.add(Double.parseDouble(tokTrim));
                            }
                        }

                        this.convergenceHistory.add(new WannierSpreadFrame(cycle, individual, total));
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }

        performSpreadConvergenceChecks();
    }

    /**
     * Assesses the Wannier localization optimization:
     * - Total spread delta must decrease monotonically or stabilize below a strict convergence tolerance.
     */
    private void performSpreadConvergenceChecks() {
        if (this.convergenceHistory.size() < 2) {
            this.converged = false;
            this.diagnostics.add("Convergence assessment skipped: Mismatched/insufficient Wannier90 cycle history.");
            return;
        }

        int N = this.convergenceHistory.size();
        double lastTotal = this.convergenceHistory.get(N - 1).totalSpreadAng2;
        double secondLastTotal = this.convergenceHistory.get(N - 2).totalSpreadAng2;
        double diff = Math.abs(lastTotal - secondLastTotal);

        // Convergence threshold used by this audit: |delta total spread| <= 1e-5 Angstrom^2
        double threshold = 1.0e-5;
        if (diff <= threshold) {
            this.converged = true;
            this.diagnostics.add(String.format("Wannier spread optimization converged. Final total quadratic spread: %.5f Ang^2.", lastTotal));
            this.diagnostics.add(String.format("Last cycle delta: %.2e Ang^2 (Within %s tolerance).", diff, threshold));
        } else {
            this.converged = false;
            this.diagnostics.add(String.format("Warning: Wannier spread has not converged. Last cycle delta: %.2e Ang^2 (Exceeds %s tolerance).", diff, threshold));
            this.diagnostics.add("Localization is still optimizing or stuck in a local minimum. Consider raising your NUM_ITER steps, adjusting energy windows, or refining initial projection guesses.");
        }
    }
}
