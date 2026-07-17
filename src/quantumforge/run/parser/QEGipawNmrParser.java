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
 * Parses Gauge-Including Projector Augmented Wave (GIPAW) nuclear magnetic resonance (NMR)
 * shielding tensors and electric field gradients (EFG) from gipaw.x outputs, converting
 * raw isotropic shieldings to chemical shifts relative to standard references (Roadmap #66).
 */
public final class QEGipawNmrParser extends LogParser {

    public static final class NmrShielding {
        private final int atomIndex;
        private final String element;
        private final double isotropicPpm;
        private final double anisotropyPpm;
        private final double asymmetry;

        public NmrShielding(int atomIndex, String element, double isotropic, double anisotropy, double asymmetry) {
            this.atomIndex = atomIndex;
            this.element = element == null ? "" : element.trim();
            this.isotropicPpm = isotropic;
            this.anisotropyPpm = anisotropy;
            this.asymmetry = asymmetry;
        }

        public int getAtomIndex() { return this.atomIndex; }
        public String getElement() { return this.element; }
        public double getIsotropicPpm() { return this.isotropicPpm; }
        public double getAnisotropyPpm() { return this.anisotropyPpm; }
        public double getAsymmetry() { return this.asymmetry; }

        /**
         * Converts raw isotropic shielding to chemical shift (delta) relative to standard references:
         * delta = sigma_ref - sigma_iso
         */
        public double getChemicalShift(double referenceShieldingPpm) {
            return referenceShieldingPpm - this.isotropicPpm;
        }

        /**
         * Guesses the standard TMS reference based on element and computes the shift:
         * - C: TMS ~184.2 ppm
         * - Si: TMS ~320.1 ppm
         * - H: TMS ~31.0 ppm
         */
        public double getStandardChemicalShift() {
            double ref = 0.0;
            if ("C".equalsIgnoreCase(element)) {
                ref = 184.2; // Standard Carbon-13 TMS reference shielding
            } else if ("Si".equalsIgnoreCase(element)) {
                ref = 320.1; // Standard Silicon-29 TMS reference shielding
            } else if ("H".equalsIgnoreCase(element)) {
                ref = 31.0;  // Standard Proton TMS reference shielding
            }
            return ref - this.isotropicPpm;
        }
    }

    private final List<NmrShielding> shieldings = new ArrayList<>();

    public QEGipawNmrParser(ProjectProperty property) {
        super(property);
    }

    public List<NmrShielding> getShieldings() { return List.copyOf(this.shieldings); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.shieldings.clear();

        // Regex to parse shielding outputs:
        // e.g. "Atom      1  C  Isotropic:      124.5200   Anisotropy:       35.1200   Asymmetry:        0.1200"
        Pattern pNmr = Pattern.compile("Atom\\s+(\\d+)\\s+(\\w+)\\s+Isotropic:\\s*([-\\d.]+)\\s+Anisotropy:\\s*([-\\d.]+)\\s+Asymmetry:\\s*([-\\d.]+)", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mNmr = pNmr.matcher(trim);
                if (mNmr.find()) {
                    try {
                        int idx = Integer.parseInt(mNmr.group(1));
                        String element = mNmr.group(2);
                        double iso = Double.parseDouble(mNmr.group(3));
                        double ani = Double.parseDouble(mNmr.group(4));
                        double asy = Double.parseDouble(mNmr.group(5));
                        this.shieldings.add(new NmrShielding(idx, element, iso, ani, asy));
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }
    }
}
