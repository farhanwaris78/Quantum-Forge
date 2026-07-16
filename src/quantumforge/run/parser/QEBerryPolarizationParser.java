/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.atoms.model.Cell;
import quantumforge.project.property.ProjectProperty;

/**
 * Parses electronic and ionic polarization from Modern Theory of Polarization (lberry=.true.)
 * pw.x calculations, calculating polarization quanta and performing branch unwrapping (Roadmap #60).
 */
public final class QEBerryPolarizationParser extends LogParser {

    private static final double BOHR_TO_ANGSTROM = 0.5291772109;
    private static final double CHARGE_E_SI = 1.602176634e-19; // Coulomb

    private double ionicPolarizationBohr = 0.0;
    private double electronicPolarizationBohr = 0.0;
    private double totalPolarizationBohr = 0.0;

    public QEBerryPolarizationParser(ProjectProperty property) {
        super(property);
    }

    public double getIonicPolarizationBohr() { return this.ionicPolarizationBohr; }
    public double getElectronicPolarizationBohr() { return this.electronicPolarizationBohr; }
    public double getTotalPolarizationBohr() { return this.totalPolarizationBohr; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.ionicPolarizationBohr = 0.0;
        this.electronicPolarizationBohr = 0.0;
        this.totalPolarizationBohr = 0.0;

        Pattern pIonic = Pattern.compile("Ionic\\s+Polarization\\s*=\\s*([-\\d.]+)\\s*electrons\\s*\\*\\s*bohr", Pattern.CASE_INSENSITIVE);
        Pattern pElec = Pattern.compile("Electronic\\s+Polarization\\s*=\\s*([-\\d.]+)\\s*electrons\\s*\\*\\s*bohr", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mIonic = pIonic.matcher(trim);
                if (mIonic.find()) {
                    this.ionicPolarizationBohr = Double.parseDouble(mIonic.group(1));
                }

                Matcher mElec = pElec.matcher(trim);
                if (mElec.find()) {
                    this.electronicPolarizationBohr = Double.parseDouble(mElec.group(1));
                }
            }
        }

        this.totalPolarizationBohr = this.ionicPolarizationBohr + this.electronicPolarizationBohr;
    }

    /**
     * Calculates the polarization quantum P0 along a specified lattice vector direction:
     * P0 = e * |a_i| / Omega (in C/m^2)
     */
    public double calculatePolarizationQuantumSI(Cell cell, int directionIndex) {
        if (cell == null || directionIndex < 0 || directionIndex >= 3) {
            return 0.0;
        }

        double[][] lattice = cell.copyLattice(); // in Angstroms
        if (lattice == null) {
            return 0.0;
        }

        double volume = Math.abs(quantumforge.com.math.Matrix3D.determinant(lattice)); // in Angstrom^3
        if (volume <= 0.0) {
            return 0.0;
        }

        double[] vec = lattice[directionIndex];
        double length = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]); // in Angstroms

        // P0 = (e * length_Angstrom * 1e-10) / (volume_Angstrom^3 * 1e-30)
        // P0 = (e * length / volume) * 1e20 C/m^2
        return (CHARGE_E_SI * length / volume) * 1.0e20;
    }

    /**
     * Unwraps the branch modulo ambiguity by finding the integer 'n' that minimizes
     * the physical polarization change: |(pNew + n * pQuantum) - pOld|
     */
    public static double unwrapPolarization(double pOld, double pNew, double pQuantum) {
        if (pQuantum <= 0.0) {
            return pNew;
        }

        double diff = pNew - pOld;
        double nDouble = -diff / pQuantum;
        long nMin = Math.round(nDouble);

        return pNew + nMin * pQuantum;
    }
}
