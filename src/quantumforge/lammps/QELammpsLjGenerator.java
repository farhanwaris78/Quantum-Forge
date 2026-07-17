/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.lammps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Generates mathematically consistent Lennard-Jones pair potential parameters 
 * for multi-component systems, implementing standard Lorentz-Berthelot mixing rules 
 * for classical MD simulations (Roadmap #113).
 */
public final class QELammpsLjGenerator {

    public static final class LjParams {
        private final double epsilonEv; // Well depth in eV
        private final double sigmaAng;   // Collision diameter in Angstrom

        public LjParams(double epsilonEv, double sigmaAng) {
            this.epsilonEv = epsilonEv;
            this.sigmaAng = sigmaAng;
        }

        public double getEpsilonEv() { return this.epsilonEv; }
        public double getSigmaAng() { return this.sigmaAng; }
    }

    // Database of standard Lennard-Jones parameters for noble gases / common elements (in eV and Angstroms)
    private static final Map<String, LjParams> LJ_DATABASE = new HashMap<>();
    static {
        LJ_DATABASE.put("HE", new LjParams(0.00088, 2.56));
        LJ_DATABASE.put("NE", new LjParams(0.00310, 2.75));
        LJ_DATABASE.put("AR", new LjParams(0.01040, 3.40));
        LJ_DATABASE.put("KR", new LjParams(0.01470, 3.65));
        LJ_DATABASE.put("XE", new LjParams(0.01990, 3.98));
    }

    private QELammpsLjGenerator() {
        // Utility
    }

    /**
     * Looks up Lennard-Jones parameters for a single element.
     */
    public static LjParams getParams(String element) {
        if (element == null) return null;
        return LJ_DATABASE.get(element.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Applies Lorentz-Berthelot mixing rules to calculate cross-interaction parameters:
     * - Sigma_ij = (Sigma_i + Sigma_j) / 2
     * - Epsilon_ij = sqrt(Epsilon_i * Epsilon_j)
     */
    public static LjParams mixLorentzBerthelot(LjParams p1, LjParams p2) {
        if (p1 == null || p2 == null) {
            return null;
        }
        double sigmaMixed = (p1.getSigmaAng() + p2.getSigmaAng()) / 2.0;
        double epsilonMixed = Math.sqrt(p1.getEpsilonEv() * p2.getEpsilonEv());
        return new LjParams(epsilonMixed, sigmaMixed);
    }

    /**
     * Generates the complete set of LAMMPS pair coefficients for a multi-element system:
     * e.g.
     * pair_style lj/cut 10.0
     * pair_coeff 1 1 0.0104 3.40
     * pair_coeff 1 2 0.0123 3.52
     */
    public static String generatePairCoefficients(List<String> elements, double cutoffAngstrom) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "pair_style      lj/cut %.2f\n", cutoffAngstrom));

        int n = elements.size();
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                String el1 = elements.get(i);
                String el2 = elements.get(j);

                LjParams p1 = getParams(el1);
                LjParams p2 = getParams(el2);

                if (p1 == null || p2 == null) {
                    sb.append(String.format(Locale.ROOT, "# Warning: Missing LJ parameters for %s or %s\n", el1, el2));
                    continue;
                }

                LjParams mixed = mixLorentzBerthelot(p1, p2);
                if (mixed != null) {
                    // LAMMPS atom types are 1-based indices
                    sb.append(String.format(Locale.ROOT, "pair_coeff      %d %d %10.6f %10.4f\n", 
                        i + 1, j + 1, mixed.getEpsilonEv(), mixed.getSigmaAng()));
                }
            }
        }

        return sb.toString();
    }
}
