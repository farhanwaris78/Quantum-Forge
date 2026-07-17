/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run;

import java.util.ArrayList;
import java.util.List;

import quantumforge.input.QEInput;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Recommends optimal Quantum ESPRESSO parallelization topology flags
 * (-nk, -nb, -nt, -nd) based on MPI ranks, k-points, and bands (Roadmap #102).
 */
public final class QEMpiTopologyAdvisor {

    public static final class TopologyRecommendation {
        private final int numPools;        // -nk / -npool
        private final int numBandGroups;   // -nb / -nband
        private final int numTaskGroups;   // -nt / -ntg
        private final int numDiagGroups;   // -nd / -ndiag
        private final List<String> warnings = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();

        public TopologyRecommendation(int pools, int bands, int taskGroups, int diag) {
            this.numPools = pools;
            this.numBandGroups = bands;
            this.numTaskGroups = taskGroups;
            this.numDiagGroups = diag;
        }

        public int getNumPools() { return this.numPools; }
        public int getNumBandGroups() { return this.numBandGroups; }
        public int getNumTaskGroups() { return this.numTaskGroups; }
        public int getNumDiagGroups() { return this.numDiagGroups; }
        public List<String> getWarnings() { return List.copyOf(this.warnings); }
        public List<String> getNotes() { return List.copyOf(this.notes); }

        public void addWarning(String warning) { this.warnings.add(warning); }
        public void addNote(String note) { this.notes.add(note); }

        public String getCmdArguments() {
            StringBuilder sb = new StringBuilder();
            if (numPools > 1) sb.append(" -nk ").append(numPools);
            if (numBandGroups > 1) sb.append(" -nb ").append(numBandGroups);
            if (numTaskGroups > 1) sb.append(" -nt ").append(numTaskGroups);
            if (numDiagGroups > 1) sb.append(" -nd ").append(numDiagGroups);
            return sb.toString().trim();
        }
    }

    private QEMpiTopologyAdvisor() {
        // Utility
    }

    /**
     * Recommends optimal MPI topology flags for the given run configurations.
     * 
     * @param input the parsed QEInput configuration
     * @param totalRanks the total number of MPI processes/ranks allocated
     */
    public static TopologyRecommendation advise(QEInput input, int totalRanks) {
        if (totalRanks <= 0) {
            totalRanks = 1;
        }

        int nkpoints = 1;
        if (input != null) {
            QEKPoints kPoints = input.getCard(QEKPoints.class);
            if (kPoints != null) {
                if (kPoints.isAutomatic()) {
                    int[] grid = kPoints.getKGrid();
                    nkpoints = Math.max(1, grid[0] * grid[1] * grid[2] / 2); // estimate irreducible
                } else {
                    nkpoints = Math.max(1, kPoints.numKPoints());
                }
            }
        }

        int nbnds = 20; // default guess
        if (input != null) {
            QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
            if (system != null) {
                QEValue nbndVal = system.getValue("nbnd");
                if (nbndVal != null && nbndVal.getIntegerValue() > 0) {
                    nbnds = nbndVal.getIntegerValue();
                }
            }
        }

        // Rule 1: k-point pools (-nk / -npool)
        // Optimal: npools divides totalRanks, npools divides nkpoints, and totalRanks/npools is a positive integer.
        int optimalPools = 1;
        for (int p = Math.min(totalRanks, nkpoints); p >= 1; p--) {
            if (totalRanks % p == 0 && nkpoints % p == 0) {
                optimalPools = p;
                break;
            }
        }

        int ranksPerPool = totalRanks / optimalPools;

        // Rule 2: Band groups (-nb / -nband)
        // Optimal: nbnds divides ranksPerPool, and nbnds is relatively small (typically 2, 4, 8)
        int optimalBands = 1;
        if (ranksPerPool > 1) {
            for (int b : new int[]{8, 4, 2}) {
                if (ranksPerPool % b == 0 && nbnds % b == 0) {
                    optimalBands = b;
                    break;
                }
            }
        }

        // Rule 3: Task groups (-nt / -ntg)
        int optimalTaskGroups = 1;

        // Rule 4: Diagonal groups (-nd / -ndiag)
        int optimalDiag = 1;

        TopologyRecommendation rec = new TopologyRecommendation(optimalPools, optimalBands, optimalTaskGroups, optimalDiag);

        // Verification checks
        if (totalRanks % rec.numPools != 0) {
            rec.addWarning("Fatal parallelization conflict: Total MPI ranks (" + totalRanks 
                + ") is not divisible by the number of pools (" + rec.numPools + "). QE will crash immediately.");
        } else {
            rec.addNote("MPI parallelization optimized: " + rec.numPools + " pools created with " 
                + ranksPerPool + " ranks per pool.");
        }

        if (nkpoints % rec.numPools != 0) {
            rec.addWarning("Warning: The number of k-points (" + nkpoints 
                + ") is not divisible by the number of pools (" + rec.numPools + "). Load balance will be imperfect.");
        } else {
            rec.addNote("Perfect k-point load balance: " + (nkpoints / rec.numPools) + " k-points per pool.");
        }

        return rec;
    }
}
