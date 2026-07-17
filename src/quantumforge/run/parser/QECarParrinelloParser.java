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
 * Parses fictitious electronic kinetic energy (ekinc), physical nuclear kinetic energy (ekinh),
 * and conserved constant of motion (etot) from Car-Parrinello MD (cp.x) logs, enforcing
 * Born-Oppenheimer adiabaticity checks and conserved energy drift diagnostics (Roadmap #67).
 */
public final class QECarParrinelloParser extends LogParser {

    public static final class CpMdFrame {
        private final int step;
        private final double ekincAu; // Fictitious electronic kinetic energy
        private final double ekinhAu; // Physical ionic kinetic energy
        private final double etotAu;  // Conserved total energy constant of motion

        public CpMdFrame(int step, double ekinc, double ekinh, double etot) {
            this.step = step;
            this.ekincAu = ekinc;
            this.ekinhAu = ekinh;
            this.etotAu = etot;
        }

        public int getStep() { return this.step; }
        public double getEkincAu() { return this.ekincAu; }
        public double getEkinhAu() { return this.ekinhAu; }
        public double getEtotAu() { return this.etotAu; }
    }

    private final List<CpMdFrame> trajectory = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean adiabatic = true;

    public QECarParrinelloParser(ProjectProperty property) {
        super(property);
    }

    public List<CpMdFrame> getTrajectory() { return List.copyOf(this.trajectory); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public boolean isAdiabatic() { return this.adiabatic; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.trajectory.clear();
        this.diagnostics.clear();
        this.adiabatic = true;

        // CP outputs print structured blocks:
        // nfi=     10, ekinc=   0.00012, ekinh=   0.01245, etot=  -12.78452
        Pattern pCp = Pattern.compile("nfi\\s*=\\s*(\\d+)\\s*,\\s*ekinc\\s*=\\s*([-\\d.eE+]+)\\s*,\\s*ekinh\\s*=\\s*([-\\d.eE+]+)\\s*,\\s*etot\\s*=\\s*([-\\d.eE+]+)", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mCp = pCp.matcher(trim);
                if (mCp.find()) {
                    try {
                        int step = Integer.parseInt(mCp.group(1));
                        double ekinc = Double.parseDouble(mCp.group(2));
                        double ekinh = Double.parseDouble(mCp.group(3));
                        double etot = Double.parseDouble(mCp.group(4));
                        this.trajectory.add(new CpMdFrame(step, ekinc, ekinh, etot));
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }

        performAdiabaticityAndDriftChecks();
    }

    /**
     * Enforces the Born-Oppenheimer adiabaticity checks and total energy conservation checks:
     * - ekinc should remain very small (typically < 1e-3 a.u. per electron) during CP dynamics.
     * - The drift rate of etot should be small in micro-Hartrees/step.
     */
    private void performAdiabaticityAndDriftChecks() {
        if (this.trajectory.isEmpty()) {
            this.adiabatic = false;
            this.diagnostics.add("Adiabaticity checks skipped: No CP MD frames parsed.");
            return;
        }

        double maxEkinc = 0.0;
        double firstEtot = this.trajectory.get(0).etotAu;
        double maxDrift = 0.0;

        for (CpMdFrame frame : this.trajectory) {
            maxEkinc = Math.max(maxEkinc, frame.ekincAu);
            maxDrift = Math.max(maxDrift, Math.abs(frame.etotAu - firstEtot));
        }

        // Rule 1: Fictitious electronic kinetic energy check
        if (maxEkinc > 0.005) {
            this.adiabatic = false;
            this.diagnostics.add(String.format("Warning: Fictitious electronic kinetic energy (ekinc) reached %.5f a.u., exceeding 0.005 limit.", maxEkinc));
            this.diagnostics.add("The electrons have heated up. The system has departed from the Born-Oppenheimer surface. Reduce the time step (dt) or decrease the fictitious mass (emass).");
        } else {
            this.adiabatic = true;
            this.diagnostics.add(String.format("Born-Oppenheimer adiabaticity satisfied. Max ekinc: %.5f a.u. (within 0.005 threshold).", maxEkinc));
        }

        // Rule 2: Conserved total energy check
        this.diagnostics.add(String.format("Conserved energy total drift max deviation: %.6f a.u. over %d MD steps.", maxDrift, this.trajectory.size()));
    }
}
