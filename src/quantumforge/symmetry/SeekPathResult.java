/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.symmetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reciprocal-space path labels/points from seekpath (via sidecar).
 */
public final class SeekPathResult {

    public static final class Point {
        private final String label;
        private final double kx;
        private final double ky;
        private final double kz;

        public Point(String label, double kx, double ky, double kz) {
            this.label = label == null ? "" : label;
            this.kx = kx;
            this.ky = ky;
            this.kz = kz;
        }

        public String getLabel() { return this.label; }
        public double getKx() { return this.kx; }
        public double getKy() { return this.ky; }
        public double getKz() { return this.kz; }
    }

    private final List<Point> path;
    private final String spaceGroupInternational;
    private final int spaceGroupNumber;
    private final String seekpathVersion;
    private final double tolerance;

    public SeekPathResult(List<Point> path, String spaceGroupInternational, int spaceGroupNumber,
                          String seekpathVersion, double tolerance) {
        this.path = Collections.unmodifiableList(new ArrayList<>(
                path == null ? List.of() : path));
        this.spaceGroupInternational = spaceGroupInternational == null ? "" : spaceGroupInternational;
        this.spaceGroupNumber = spaceGroupNumber;
        this.seekpathVersion = seekpathVersion == null ? "" : seekpathVersion;
        this.tolerance = tolerance;
    }

    public List<Point> getPath() { return this.path; }
    public String getSpaceGroupInternational() { return this.spaceGroupInternational; }
    public int getSpaceGroupNumber() { return this.spaceGroupNumber; }
    public String getSeekpathVersion() { return this.seekpathVersion; }
    public double getTolerance() { return this.tolerance; }

    public String pathSummary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < this.path.size(); i++) {
            if (i > 0) {
                out.append(" -> ");
            }
            out.append(this.path.get(i).getLabel());
        }
        return out.toString();
    }
}
