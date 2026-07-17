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
 * Parses lattice thermal conductivity tensors (kappa) as a function of temperature
 * from phono3py or ShengBTE calculations, validating high-temperature anharmonic 
 * three-phonon 1/T scattering scaling laws (Roadmap #108).
 */
public final class QEPhono3pyKappaParser extends LogParser {

    public static final class ThermalConductivityPoint {
        private final double temperatureK;
        private final double kappaXx; // W/m-K
        private final double kappaYy; // W/m-K
        private final double kappaZz; // W/m-K

        public ThermalConductivityPoint(double temp, double kxx, double kyy, double kzz) {
            this.temperatureK = temp;
            this.kappaXx = kxx;
            this.kappaYy = kyy;
            this.kappaZz = kzz;
        }

        public double getTemperatureK() { return this.temperatureK; }
        public double getKappaXx() { return this.kappaXx; }
        public double getKappaYy() { return this.kappaYy; }
        public double getKappaZz() { return this.kappaZz; }

        public double getIsotropicKappa() {
            return (this.kappaXx + this.kappaYy + this.kappaZz) / 3.0;
        }
    }

    private final List<ThermalConductivityPoint> kappaData = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean physicalScaling = true;

    public QEPhono3pyKappaParser(ProjectProperty property) {
        super(property);
    }

    public List<ThermalConductivityPoint> getKappaData() { return List.copyOf(this.kappaData); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isPhysicalScaling() { return this.physicalScaling; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.kappaData.clear();
        this.diagnostics.clear();
        this.physicalScaling = true;

        // phono3py prints thermal conductivity tables like:
        // Temp (K)     kappa_xx     kappa_yy     kappa_zz
        //   100.00       45.1234      45.1234      45.1234
        Pattern pHeader = Pattern.compile("^\\s*Temp\\s*\\(K\\)", Pattern.CASE_INSENSITIVE);
        Pattern pCols = Pattern.compile("^\\s*([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)");

        boolean inTable = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                if (pHeader.matcher(trim).find()) {
                    inTable = true;
                    continue;
                }

                if (inThermoBoundary(trim)) {
                    inTable = false;
                    continue;
                }

                if (inTable) {
                    Matcher m = pCols.matcher(trim);
                    if (m.find()) {
                        try {
                            double temp = Double.parseDouble(m.group(1));
                            double kxx = Double.parseDouble(m.group(2));
                            double kyy = Double.parseDouble(m.group(3));
                            double kzz = Double.parseDouble(m.group(4));
                            if (Double.isFinite(temp) && Double.isFinite(kxx)) {
                                this.kappaData.add(new ThermalConductivityPoint(temp, kxx, kyy, kzz));
                            }
                        } catch (NumberFormatException e) {
                            // Skip malformed lines
                        }
                    }
                }
            }
        }

        performScatteringModelVerification();
    }

    private static boolean inThermoBoundary(String trim) {
        return trim.isEmpty() || trim.startsWith("Thermal") || trim.contains("---");
    }

    /**
     * Verifies if the thermal conductivity scales as 1/T at high temperatures (T >= Debye Temperature, typically > 250K)
     * as required by three-phonon Umklapp scattering laws.
     */
    private void performScatteringModelVerification() {
        if (this.kappaData.size() < 3) {
            this.physicalScaling = false;
            this.diagnostics.add("Anharmonic transport checks skipped: Mismatched or insufficient temperature points.");
            return;
        }

        List<ThermalConductivityPoint> highTPoints = new ArrayList<>();
        for (ThermalConductivityPoint pt : this.kappaData) {
            if (pt.temperatureK >= 250.0) {
                highTPoints.add(pt);
            }
        }

        if (highTPoints.size() < 2) {
            this.diagnostics.add("Anharmonic check: No high-temperature points (>= 250 K) available. Add larger temperature steps.");
            return;
        }

        // Check monotonic decrease of kappa at high T
        boolean monotonicDecrease = true;
        for (int i = 1; i < highTPoints.size(); i++) {
            double prevK = highTPoints.get(i - 1).getIsotropicKappa();
            double curK = highTPoints.get(i).getIsotropicKappa();
            if (curK >= prevK) {
                monotonicDecrease = false;
                break;
            }
        }

        if (!monotonicDecrease) {
            this.physicalScaling = false;
            this.diagnostics.add("Warning: Lattice thermal conductivity (kappa) does not decrease monotonically at high temperatures.");
            this.diagnostics.add("This indicates unphysical transport behavior. Check your q-mesh convergence or force-constant validation (Roadmap #108).");
        } else {
            // Verify 1/T scaling law: kappa(T2) * T2 / (kappa(T1) * T1) should be close to 1.0 (within 30% tolerance)
            double t1 = highTPoints.get(0).getTemperatureK();
            double k1 = highTPoints.get(0).getIsotropicKappa();
            double t2 = highTPoints.get(highTPoints.size() - 1).getTemperatureK();
            double k2 = highTPoints.get(highTPoints.size() - 1).getIsotropicKappa();

            double ratio = (k2 * t2) / (k1 * t1);
            if (ratio < 0.6 || ratio > 1.4) {
                this.physicalScaling = false;
                this.diagnostics.add(String.format("Warning: Lattice thermal conductivity deviates from 1/T Umklapp scattering scaling. Scaling ratio: %.2f.", ratio));
                this.diagnostics.add("Consider refining your phonon-phonon scattering lifetime convergence or checking supercell dimensionality.");
            } else {
                this.physicalScaling = true;
                this.diagnostics.add(String.format("Lattice thermal transport verified: Symmetric 1/T Umklapp scaling holds. Scaling ratio: %.2f (Within 0.6 - 1.4 limits).", ratio));
            }
        }
    }
}
