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
 * Parses Zone-Center (Gamma-point, q=0) acoustic phonon frequencies and enforces
 * Acoustic Sum Rule (ASR) physical validations, detecting unphysical grid-force 
 * leakage and recommending cutoff/threshold adjustments (Roadmap #51).
 */
public final class QEAcousticSumRuleValidator extends LogParser {

    private final List<Double> gammaFrequenciesCm1 = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean asrCompliant = true;

    public QEAcousticSumRuleValidator(ProjectProperty property) {
        super(property);
    }

    public List<Double> getGammaFrequenciesCm1() { return List.copyOf(this.gammaFrequenciesCm1); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isAsrCompliant() { return this.asrCompliant; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.gammaFrequenciesCm1.clear();
        this.diagnostics.clear();
        this.asrCompliant = true;

        // In matdyn.freq, Gamma-point is typically the first line (q_distance = 0.0)
        // e.g. "   0.0000000       4.1245       6.1524      12.2412 ..."
        Pattern pGamma = Pattern.compile("^\\s*0\\.0000000\\s+(.*)$");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                Matcher m = pGamma.matcher(trim);
                if (m.find()) {
                    String[] tokens = m.group(1).trim().split("\\s+");
                    for (String tok : tokens) {
                        try {
                            this.gammaFrequenciesCm1.add(Double.parseDouble(tok));
                        } catch (NumberFormatException e) {
                            // Skip malformed tokens
                        }
                    }
                    break; // Only need the Gamma point
                }
            }
        }

        performAsrVerification();
    }

    /**
     * Verifies if the three acoustic phonon modes are close to 0 cm-1.
     * Due to grid-force leakage, raw uncorrected frequencies can drift.
     * If deviation > 20 cm-1, it flags an ASR violation.
     */
    private void performAsrVerification() {
        if (this.gammaFrequenciesCm1.size() < 3) {
            this.asrCompliant = false;
            this.diagnostics.add("Acoustic Sum Rule check skipped: Insufficient phonon modes parsed at Gamma.");
            return;
        }

        // The first three modes are the acoustic modes
        double w1 = this.gammaFrequenciesCm1.get(0);
        double w2 = this.gammaFrequenciesCm1.get(1);
        double w3 = this.gammaFrequenciesCm1.get(2);

        double maxDeviation = Math.max(Math.abs(w1), Math.max(Math.abs(w2), Math.abs(w3)));

        if (maxDeviation > 20.0) {
            this.asrCompliant = false;
            this.diagnostics.add(String.format("Acoustic Sum Rule VIOLATION: Raw acoustic mode frequencies at Gamma drift by max %.2f cm-1 (limit 20.0 cm-1).", maxDeviation));
            this.diagnostics.add("This indicates unphysical grid-force leakage. Increase your plane-wave charge cutoff (ecutrho) or tighten the SCF convergence threshold.");
        } else {
            this.asrCompliant = true;
            this.diagnostics.add(String.format("Acoustic Sum Rule verified. Raw acoustic drift at Gamma: %.2f cm-1 (Well within 20.0 cm-1 limit).", maxDeviation));
        }
    }
}
