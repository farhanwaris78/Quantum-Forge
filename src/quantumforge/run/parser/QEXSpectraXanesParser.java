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
 * Parses X-ray Absorption Near Edge Structure (XANES) spectra calculated by the
 * xspectra.x Lanczos engine, supporting custom core-hole lifetime broadening (Roadmap #65).
 */
public final class QEXSpectraXanesParser extends LogParser {

    public static final class XanesPoint {
        private final double energyEv;
        private final double crossSectionMb; // Absorption cross section in Megabarns

        public XanesPoint(double energy, double sigma) {
            this.energyEv = energy;
            this.crossSectionMb = sigma;
        }

        public double getEnergyEv() { return this.energyEv; }
        public double getCrossSectionMb() { return this.crossSectionMb; }
    }

    private final List<XanesPoint> spectrum = new ArrayList<>();

    public QEXSpectraXanesParser(ProjectProperty property) {
        super(property);
    }

    public List<XanesPoint> getSpectrum() { return List.copyOf(this.spectrum); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.spectrum.clear();

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
                        double energy = Double.parseDouble(tokens[0]);
                        double sigma = Double.parseDouble(tokens[1]);
                        if (Double.isFinite(energy) && Double.isFinite(sigma)) {
                            this.spectrum.add(new XanesPoint(energy, sigma));
                        }
                    } catch (NumberFormatException e) {
                        // Skip malformed data rows
                    }
                }
            }
        }
    }

    /**
     * Applies Lorentzian broadening to model the core-hole lifetime broadening:
     * I(E) = Sum_i (sigma_i * gammaHalf / (pi * ((E - E_i)^2 + gammaHalf^2)))
     * 
     * @param minEnergy minimum energy for the broadened range (eV)
     * @param maxEnergy maximum energy for the broadened range (eV)
     * @param step energy grid step (eV)
     * @param fwhm Full-Width at Half-Maximum representing core-hole lifetime width (eV)
     */
    public List<XanesPoint> computeBroadenedSpectrum(double minEnergy, double maxEnergy, double step, double fwhm) {
        List<XanesPoint> broadened = new ArrayList<>();
        if (this.spectrum.isEmpty() || minEnergy >= maxEnergy || step <= 0.0 || fwhm <= 0.0) {
            return broadened;
        }

        double gammaHalf = fwhm / 2.0;

        for (double e = minEnergy; e <= maxEnergy; e += step) {
            double intensity = 0.0;
            for (XanesPoint pt : this.spectrum) {
                double e0 = pt.getEnergyEv();
                double sigma = pt.getCrossSectionMb();
                if (sigma <= 0.0) {
                    continue;
                }

                double denom = (e - e0) * (e - e0) + gammaHalf * gammaHalf;
                intensity += (sigma * gammaHalf) / (Math.PI * denom);
            }
            broadened.add(new XanesPoint(e, intensity));
        }

        return broadened;
    }
}
