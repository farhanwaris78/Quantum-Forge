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
 * Parses electronic entropy corrections (-TS / demet) from smearing SCF outputs,
 * validating degauss thresholds against unphysical force bias and metallic convergence
 * limits (Roadmap #38).
 */
public final class QESmearingConvergenceAnalyzer extends LogParser {

    private double entropyRy = 0.0;
    private double totalEnergyRy = 0.0;
    private double freeEnergyRy = 0.0;
    private boolean smearingFound = false;
    private final List<String> diagnostics = new ArrayList<>();

    public QESmearingConvergenceAnalyzer(ProjectProperty property) {
        super(property);
    }

    public double getEntropyRy() { return this.entropyRy; }
    public double getTotalEnergyRy() { return this.totalEnergyRy; }
    public double getFreeEnergyRy() { return this.freeEnergyRy; }
    public boolean isSmearingFound() { return this.smearingFound; }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.entropyRy = 0.0;
        this.totalEnergyRy = 0.0;
        this.freeEnergyRy = 0.0;
        this.smearingFound = false;
        this.diagnostics.clear();

        Pattern pTS = Pattern.compile("smearing\\s+contrib\\.\\s+\\(-TS\\)\\s*=\\s*([-\\d.]+)\\s*Ry", Pattern.CASE_INSENSITIVE);
        Pattern pDemet = Pattern.compile("demet\\s*=\\s*([-\\d.]+)\\s*Ry", Pattern.CASE_INSENSITIVE);
        Pattern pTot = Pattern.compile("total\\s+energy\\s*=\\s*([-\\d.]+)\\s*Ry", Pattern.CASE_INSENSITIVE);
        Pattern pFree = Pattern.compile("total\\s+free\\s+energy\\s*=\\s*([-\\d.]+)\\s*Ry", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mTS = pTS.matcher(trim);
                if (mTS.find()) {
                    this.entropyRy = Double.parseDouble(mTS.group(1));
                    this.smearingFound = true;
                }

                Matcher mDemet = pDemet.matcher(trim);
                if (mDemet.find()) {
                    this.entropyRy = Double.parseDouble(mDemet.group(1));
                    this.smearingFound = true;
                }

                Matcher mTot = pTot.matcher(trim);
                if (mTot.find()) {
                    this.totalEnergyRy = Double.parseDouble(mTot.group(1));
                }

                Matcher mFree = pFree.matcher(trim);
                if (mFree.find()) {
                    this.freeEnergyRy = Double.parseDouble(mFree.group(1));
                }
            }
        }
    }

    /**
     * Verifies if the smearing parameter 'degauss' is balanced for the system:
     * - If | -TS | / natoms > 0.001 Ry (~13 meV/atom): unphysical force bias, degauss is too large!
     * - If | -TS | / natoms < 1e-6 Ry and system is metallic: degauss is too small, risking SCF oscillation!
     */
    public boolean verifySmearingSafe(int natoms, boolean isMetal) {
        if (!this.smearingFound) {
            this.diagnostics.add("Smearing checks skipped: occupations is not set to smearing.");
            return true;
        }

        int N = natoms <= 0 ? 1 : natoms;
        double entropyPerAtom = Math.abs(this.entropyRy) / N;

        boolean safe = true;

        if (entropyPerAtom > 0.001) {
            safe = false;
            this.diagnostics.add(String.format("Warning: Smearing entropy correction (-TS/atom) is %.5f Ry, exceeding 0.001 Ry safety limit.", entropyPerAtom));
            this.diagnostics.add("Unphysical force bias detected. Consider decreasing your SYSTEM.degauss smearing width.");
        } else if (entropyPerAtom < 1.0e-6 && isMetal) {
            safe = false;
            this.diagnostics.add("Warning: Smearing entropy correction is near zero for a metallic system.");
            this.diagnostics.add("This indicates degauss is too small, which can cause severe SCF convergence oscillations. Consider raising degauss.");
        } else {
            this.diagnostics.add(String.format("Smearing convergence optimized. Electronic entropy contribution: %.6f Ry per atom.", entropyPerAtom));
        }

        return safe;
    }
}
