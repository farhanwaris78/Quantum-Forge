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
 * Parses Eliashberg spectral function (alpha2F) output data files from EPW/ph.x,
 * executing rigorous trapezoidal integrations to extract the electron-phonon coupling 
 * constant lambda, logarithmic average frequency omega_log, and superconducting 
 * transition temperature Tc via the Allen-Dynes formula (Roadmap #62).
 */
public final class QEEliashbergTcCalculator extends LogParser {

    public static final class EliashbergPoint {
        public final double frequencyCm1;
        public final double alpha2F;

        public EliashbergPoint(double freq, double val) {
            this.frequencyCm1 = freq;
            this.alpha2F = val;
        }
    }

    public static final class TcResult {
        private final double lambda;
        private final double omegaLogCm1;
        private final double tcKelvin;
        private final double muStar;
        private final String notes;

        public TcResult(double lambda, double omegaLog, double tc, double muStar, String notes) {
            this.lambda = lambda;
            this.omegaLogCm1 = omegaLog;
            this.tcKelvin = tc;
            this.muStar = muStar;
            this.notes = notes == null ? "" : notes;
        }

        public double getLambda() { return this.lambda; }
        public double getOmegaLogCm1() { return this.omegaLogCm1; }
        public double getTcKelvin() { return this.tcKelvin; }
        public double getMuStar() { return this.muStar; }
        public String getNotes() { return this.notes; }

        public String getSummary() {
            return String.format(java.util.Locale.ROOT,
                "Superconductivity Eliashberg Analysis:\n" +
                " - Electron-phonon coupling (lambda): %.4f\n" +
                " - Logarithmic average frequency (w_log): %.2f cm-1\n" +
                " - Coulomb pseudopotential (mu*): %.2f\n" +
                " - Predicted Tc (Allen-Dynes): %.2f K\n" +
                " - Note: %s",
                lambda, omegaLogCm1, muStar, tcKelvin, notes);
        }
    }

    private final List<EliashbergPoint> spectralFunction = new ArrayList<>();

    public QEEliashbergTcCalculator(ProjectProperty property) {
        super(property);
    }

    public List<EliashbergPoint> getSpectralFunction() { return List.copyOf(this.spectralFunction); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.spectralFunction.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                if (trim.isEmpty() || trim.startsWith("#") || trim.startsWith("@")) {
                    continue; // Skip comments/headers
                }

                String[] tokens = trim.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        double freq = Double.parseDouble(tokens[0]);
                        double val = Double.parseDouble(tokens[1]);
                        if (Double.isFinite(freq) && Double.isFinite(val)) {
                            this.spectralFunction.add(new EliashbergPoint(freq, val));
                        }
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }
    }

    /**
     * Integrates the spectral function to extract lambda, w_log, and predicts Tc using 
     * the standard McMillan/Allen-Dynes equation:
     * Tc = (w_log / 1.2) * exp( - 1.04*(1 + L) / (L - mu*(1 + 0.62*L)) )
     */
    public TcResult calculateTc(double muStar) {
        if (this.spectralFunction.size() < 2) {
            return new TcResult(0.0, 0.0, 0.0, muStar, "Incomplete spectral function data.");
        }

        // 1. Compute lambda via trapezoidal integration:
        // lambda = 2 * Integral( alpha2F(w) / w ) dw
        double integratedLambda = 0.0;
        double logSumIntegral = 0.0;

        for (int i = 1; i < this.spectralFunction.size(); i++) {
            EliashbergPoint pt1 = this.spectralFunction.get(i - 1);
            EliashbergPoint pt2 = this.spectralFunction.get(i);

            double w1 = pt1.frequencyCm1;
            double w2 = pt2.frequencyCm1;
            double f1 = pt1.alpha2F;
            double f2 = pt2.alpha2F;

            // Skip zero/negative frequencies to prevent singularities
            if (w1 <= 1.0e-5 || w2 <= 1.0e-5) {
                continue;
            }

            double dw = w2 - w1;
            if (dw <= 0.0) {
                continue;
            }

            // Trapezoidal contribution for lambda integration
            double term1 = f1 / w1;
            double term2 = f2 / w2;
            integratedLambda += 0.5 * dw * (term1 + term2);

            // Trapezoidal contribution for logarithmic frequency integration:
            // ln(w) * alpha2F(w) / w
            double logTerm1 = Math.log(w1) * f1 / w1;
            double logTerm2 = Math.log(w2) * f2 / w2;
            logSumIntegral += 0.5 * dw * (logTerm1 + logTerm2);
        }

        // Multiply by the factor of 2 in Eliashberg definition
        double lambda = 2.0 * integratedLambda;

        if (lambda <= 1.0e-4) {
            return new TcResult(0.0, 0.0, 0.0, muStar, "Electron-phonon coupling lambda is near zero.");
        }

        // ln(w_log) = (2 / lambda) * Integral( alpha2F(w) * ln(w) / w ) dw
        double lnOmegaLog = (2.0 / lambda) * logSumIntegral;
        double omegaLog = Math.exp(lnOmegaLog);

        // 2. Predict Tc via the Allen-Dynes modification of McMillan equation:
        double num = -1.04 * (1.0 + lambda);
        double denom = lambda - muStar * (1.0 + 0.62 * lambda);
        
        double tc = 0.0;
        String notes = "Superconducting transition temperature predicted successfully.";
        if (denom > 0.0) {
            // Conversion factor: 1 cm-1 of frequency is approx. 1.4388 Kelvin
            double CM1_TO_KELVIN = 1.4388;
            double omegaLogK = omegaLog * CM1_TO_KELVIN;
            tc = (omegaLogK / 1.2) * Math.exp(num / denom);
        } else {
            notes = "Superconductivity suppressed: Coulomb pseudopotential mu* dominates coupling lambda.";
        }

        return new TcResult(lambda, omegaLog, tc, muStar, notes);
    }
}
