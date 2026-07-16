/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

/**
 * One electronic SCF iteration extracted from a pw.x log.
 */
public final class ScfIterationRecord {

    private final int iteration;
    private final double totalEnergyRy;
    private final Double estimatedAccuracyRy;
    private final boolean finalConvergedLine;
    private final String sourceLine;

    public ScfIterationRecord(int iteration, double totalEnergyRy, Double estimatedAccuracyRy,
                              boolean finalConvergedLine, String sourceLine) {
        this.iteration = iteration;
        this.totalEnergyRy = totalEnergyRy;
        this.estimatedAccuracyRy = estimatedAccuracyRy;
        this.finalConvergedLine = finalConvergedLine;
        this.sourceLine = sourceLine == null ? "" : sourceLine;
    }

    public int getIteration() {
        return this.iteration;
    }

    public double getTotalEnergyRy() {
        return this.totalEnergyRy;
    }

    public Double getEstimatedAccuracyRy() {
        return this.estimatedAccuracyRy;
    }

    public boolean isFinalConvergedLine() {
        return this.finalConvergedLine;
    }

    public String getSourceLine() {
        return this.sourceLine;
    }
}
