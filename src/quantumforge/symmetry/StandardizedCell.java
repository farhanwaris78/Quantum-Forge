/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.symmetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Standardized lattice/positions returned by spglib protocol v2.
 */
public final class StandardizedCell {

    public static final class AtomSite {
        private final int atomicNumber;
        private final double x;
        private final double y;
        private final double z;
        private final boolean fractional;

        public AtomSite(int atomicNumber, double x, double y, double z, boolean fractional) {
            this.atomicNumber = atomicNumber;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fractional = fractional;
        }

        public int getAtomicNumber() { return this.atomicNumber; }
        public double getX() { return this.x; }
        public double getY() { return this.y; }
        public double getZ() { return this.z; }
        public boolean isFractional() { return this.fractional; }
    }

    private final double[][] lattice;
    private final List<AtomSite> sites;
    private final String kind; // primitive | conventional
    private final int spaceGroupNumber;
    private final String internationalSymbol;
    private final double tolerance;
    private final String spglibVersion;

    public StandardizedCell(double[][] lattice, List<AtomSite> sites, String kind,
                            int spaceGroupNumber, String internationalSymbol,
                            double tolerance, String spglibVersion) {
        this.lattice = copy3(Objects.requireNonNull(lattice, "lattice"));
        this.sites = Collections.unmodifiableList(new ArrayList<>(
                sites == null ? List.of() : sites));
        this.kind = kind == null ? "" : kind;
        this.spaceGroupNumber = spaceGroupNumber;
        this.internationalSymbol = internationalSymbol == null ? "" : internationalSymbol;
        this.tolerance = tolerance;
        this.spglibVersion = spglibVersion == null ? "" : spglibVersion;
    }

    public double[][] getLattice() { return copy3(this.lattice); }
    public List<AtomSite> getSites() { return this.sites; }
    public String getKind() { return this.kind; }
    public int getSpaceGroupNumber() { return this.spaceGroupNumber; }
    public String getInternationalSymbol() { return this.internationalSymbol; }
    public double getTolerance() { return this.tolerance; }
    public String getSpglibVersion() { return this.spglibVersion; }

    private static double[][] copy3(double[][] src) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(src[i], 0, out[i], 0, 3);
        }
        return out;
    }
}
