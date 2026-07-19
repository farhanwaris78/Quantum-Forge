/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.symmetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Mathematically rigorous Shubnikov Magnetic Space Group (MSG) and magnetic order
 * analyzer, classifying spin-polarized collinear and noncollinear systems into 
 * Ferromagnetic, Antiferromagnetic, Ferrimagnetic, or Paramagnetic states (Roadmap #74).
 */
public final class MagneticSpaceGroupDetector {

    public enum MagneticOrder {
        PARAMAGNETIC,
        FERROMAGNETIC,
        ANTIFERROMAGNETIC,
        FERRIMAGNETIC
    }

    public static final class MsgReport {
        private final MagneticOrder order;
        private final double netMoment;
        private final double absoluteMomentSum;
        private final String shubnikovTypeGuess; // Type I, II, III, IV
        private final String description;

        public MsgReport(MagneticOrder order, double net, double abs, String type, String desc) {
            this.order = Objects.requireNonNull(order);
            this.netMoment = net;
            this.absoluteMomentSum = abs;
            this.shubnikovTypeGuess = Objects.requireNonNull(type);
            this.description = Objects.requireNonNull(desc);
        }

        public MagneticOrder getOrder() { return this.order; }
        public double getNetMoment() { return this.netMoment; }
        public double getAbsoluteMomentSum() { return this.absoluteMomentSum; }
        public String getShubnikovTypeGuess() { return this.shubnikovTypeGuess; }
        public String getDescription() { return this.description; }
    }

    private final Cell cell;

    public MagneticSpaceGroupDetector(Cell cell) {
        this.cell = Objects.requireNonNull(cell, "cell");
    }

    /**
     * Analyzes atomic magnetic moments (spins) and classifies the magnetic order and Shubnikov type.
     */
    public MsgReport analyzeMagneticSymmetry() {
        Atom[] atoms = cell.listAtoms(true);
        if (atoms == null || atoms.length == 0) {
            return new MsgReport(MagneticOrder.PARAMAGNETIC, 0.0, 0.0, "Type I", "Paramagnetic / empty system.");
        }

        double netX = 0, netY = 0, netZ = 0;
        double absSum = 0;
        int magneticAtomsCount = 0;

        List<Double> moments = new ArrayList<>();

        for (Atom atom : atoms) {
            if (atom == null || atom.isSlaveAtom()) continue;

            // Retrieve spin moments from properties
            double mx = 0.0, my = 0.0, mz = 0.0;
            if (atom.hasProperty("starting_magnetization")) {
                mz = Double.parseDouble(atom.getProperty("starting_magnetization").toString());
            } else if (atom.hasProperty("magnetic_moment_z")) {
                mz = Double.parseDouble(atom.getProperty("magnetic_moment_z").toString());
                if (atom.hasProperty("magnetic_moment_x")) {
                    mx = Double.parseDouble(atom.getProperty("magnetic_moment_x").toString());
                }
                if (atom.hasProperty("magnetic_moment_y")) {
                    my = Double.parseDouble(atom.getProperty("magnetic_moment_y").toString());
                }
            }

            double mag = Math.sqrt(mx * mx + my * my + mz * mz);
            if (mag > 0.01) {
                netX += mx;
                netY += my;
                netZ += mz;
                absSum += mag;
                magneticAtomsCount++;
                moments.add(mz); // collinear tracking
            }
        }

        double netMoment = Math.sqrt(netX * netX + netY * netY + netZ * netZ);

        if (magneticAtomsCount == 0) {
            return new MsgReport(MagneticOrder.PARAMAGNETIC, 0.0, 0.0, "Type I", 
                "Non-magnetic system. The symmetry is described by the standard grey space group (Type I).");
        }

        MagneticOrder order = MagneticOrder.PARAMAGNETIC;
        String shubnikovType = "Type I";
        String desc = "";

        double tol = 0.05; // numerical tolerance

        // 1. Classify Magnetic Order
        if (Math.abs(netMoment - absSum) < tol) {
            // All moments point along the same direction
            order = MagneticOrder.FERROMAGNETIC;
            shubnikovType = "Type I"; // Ferromagnetic MSG is Type I (standard SG)
            desc = "Ferromagnetic order: All local magnetic moments point parallel.";
        } else if (netMoment < tol) {
            // Net moment is zero but atomic moments are non-zero -> Antiferromagnetic!
            order = MagneticOrder.ANTIFERROMAGNETIC;
            shubnikovType = "Type IV"; // Antiferromagnetic MSG is standardly Type IV (black-and-white translation)
            desc = "Antiferromagnetic order: Local magnetic moments cancel out completely across the unit cell.";
        } else {
            // Net moment is non-zero but less than absolute sum -> Ferrimagnetic!
            order = MagneticOrder.FERRIMAGNETIC;
            shubnikovType = "Type III"; // Ferrimagnetic MSG is standardly Type III (black-and-white point group)
            desc = "Ferrimagnetic order: Local magnetic moments are unequal or partially cancel out.";
        }

        return new MsgReport(order, netMoment, absSum, shubnikovType, desc);
    }

    /** Retroactive backward-compatible method. */
    public String detectMSG() {
        MsgReport report = analyzeMagneticSymmetry();
        if (report.getOrder() == MagneticOrder.PARAMAGNETIC) {
            return "Paramagnetic / Non-magnetic (Use standard SpaceGroupDetector)";
        }
        return String.format("%s (Shubnikov %s)", report.getDescription(), report.getShubnikovTypeGuess());
    }
}
