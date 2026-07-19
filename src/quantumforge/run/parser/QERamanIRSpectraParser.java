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
 * Parses vibrational mode frequencies, IR intensities, and Raman activities
 * from ph.x or dynmat.x spectroscopic logs, implementing powder-averaged 
 * Lorentzian line-shape broadening (Roadmap #53).
 */
public final class QERamanIRSpectraParser extends LogParser {

    public static final class SpectroMode {
        private final int modeIndex;
        private final double frequencyCm1;
        private final double irIntensity;
        private final double ramanActivity;

        public SpectroMode(int modeIndex, double frequencyCm1, double irIntensity, double ramanActivity) {
            this.modeIndex = modeIndex;
            this.frequencyCm1 = frequencyCm1;
            this.irIntensity = irIntensity;
            this.ramanActivity = ramanActivity;
        }

        public int getModeIndex() { return this.modeIndex; }
        public double getFrequencyCm1() { return this.frequencyCm1; }
        public double getIrIntensity() { return this.irIntensity; }
        public double getRamanActivity() { return this.ramanActivity; }
    }

    public static final class SpectrumPoint {
        public double frequency;
        public double intensity;

        public SpectrumPoint(double frequency, double intensity) {
            this.frequency = frequency;
            this.intensity = intensity;
        }
    }

    private final List<SpectroMode> modes = new ArrayList<>();

    public QERamanIRSpectraParser(ProjectProperty property) {
        super(property);
    }

    public List<SpectroMode> getModes() { return List.copyOf(this.modes); }

    @Override
    public void parse(File file) throws IOException {
        this.modes.clear();
        if (file == null || !file.isFile()) {
            return;
        }

        // Regex to parse dynmat.x/ph.x Raman/IR table rows, e.g.:
        //   1      120.45  (cm-1)     0.0000 (D2/A2-amu)      1.2412 (A4/amu)
        // or standard ph.x columns:
        //      mode   1   freq    120.45 cm-1   IR intensity    0.0000   Raman activity    1.2412
        Pattern p1 = Pattern.compile("^\\s*(\\d+)\\s+([-\\d.DdEe+]+)\\s+.*\\s+([-\\d.DdEe+]+)\\s+([-\\d.DdEe+]+)$");
        Pattern p2 = Pattern.compile("mode\\s+(\\d+)\\s+freq\\s+([-\\d.DdEe+]+)\\s*cm-1\\s+IR\\s+intensity\\s+([-\\d.DdEe+]+)\\s+Raman\\s+activity\\s+([-\\d.DdEe+]+)", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inTable = false;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                if (trim.contains("Mode") && trim.contains("Frequency") && (trim.contains("IR") || trim.contains("Raman"))) {
                    inTable = true;
                    continue;
                }

                if (inTable && trim.isEmpty()) {
                    inTable = false;
                    continue;
                }

                if (inTable) {
                    Matcher m = p1.matcher(line);
                    if (m.find()) {
                        try {
                            int idx = Integer.parseInt(m.group(1));
                            double freq = ScfConvergenceAnalyzer.parseFortranDouble(m.group(2));
                            double ir = ScfConvergenceAnalyzer.parseFortranDouble(m.group(3));
                            double raman = ScfConvergenceAnalyzer.parseFortranDouble(m.group(4));
                            this.modes.add(new SpectroMode(idx, freq, ir, raman));
                        } catch (NumberFormatException e) {
                            // Ignored
                        }
                    }
                } else {
                    Matcher m = p2.matcher(line);
                    if (m.find()) {
                        try {
                            int idx = Integer.parseInt(m.group(1));
                            double freq = ScfConvergenceAnalyzer.parseFortranDouble(m.group(2));
                            double ir = ScfConvergenceAnalyzer.parseFortranDouble(m.group(3));
                            double raman = ScfConvergenceAnalyzer.parseFortranDouble(m.group(4));
                            this.modes.add(new SpectroMode(idx, freq, ir, raman));
                        } catch (NumberFormatException e) {
                            // Ignored
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates a broadened powder spectrum (IR or Raman) using Lorentzian profiles:
     * L(w) = Sum_i (A_i / pi) * (G/2) / ((w - w_i)^2 + (G/2)^2)
     * where G is the Full-Width at Half-Maximum (FWHM).
     */
    public List<SpectrumPoint> computePowderSpectra(double minFreq, double maxFreq, double step, double fwhm, boolean isRaman) {
        List<SpectrumPoint> spectrum = new ArrayList<>();
        if (this.modes.isEmpty() || !Double.isFinite(minFreq) || !Double.isFinite(maxFreq)
                || !Double.isFinite(step) || !Double.isFinite(fwhm) || minFreq >= maxFreq
                || step <= 0.0 || fwhm <= 0.0 || (maxFreq - minFreq) / step > 1_000_000) {
            return spectrum;
        }

        double gammaHalf = fwhm / 2.0;

        for (double w = minFreq; w <= maxFreq; w += step) {
            double intensity = 0.0;
            for (SpectroMode mode : this.modes) {
                double w0 = mode.getFrequencyCm1();
                if (w0 <= 0.0) {
                    continue; // Skip imaginary (unstable) modes in IR/Raman peaks
                }
                double activity = isRaman ? mode.getRamanActivity() : mode.getIrIntensity();
                if (activity <= 0.0) {
                    continue;
                }

                // Lorentzian term
                double denom = (w - w0) * (w - w0) + gammaHalf * gammaHalf;
                intensity += (activity / Math.PI) * (gammaHalf / denom);
            }
            spectrum.add(new SpectrumPoint(w, intensity));
        }

        return spectrum;
    }
}
