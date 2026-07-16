/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;

/**
 * Determines whether a geometry optimization may honestly be labelled "optimized".
 *
 * <p>Requires evidence of BFGS / ionic convergence markers and optional force/pressure
 * thresholds. Absence of evidence yields NOT_OPTIMIZED rather than a silent success.</p>
 */
public final class GeometryConvergenceValidator {

    public enum Status {
        OPTIMIZED,
        NOT_OPTIMIZED,
        INCOMPLETE,
        UNKNOWN
    }

    public static final class Result {
        private final Status status;
        private final boolean bfgsEndMarker;
        private final boolean forcesConvergedMarker;
        private final boolean scfAlwaysConverged;
        private final Double finalTotalForce;
        private final Double finalPressureKbar;
        private final List<String> diagnostics;

        public Result(Status status, boolean bfgsEndMarker, boolean forcesConvergedMarker,
                      boolean scfAlwaysConverged, Double finalTotalForce, Double finalPressureKbar,
                      List<String> diagnostics) {
            this.status = status;
            this.bfgsEndMarker = bfgsEndMarker;
            this.forcesConvergedMarker = forcesConvergedMarker;
            this.scfAlwaysConverged = scfAlwaysConverged;
            this.finalTotalForce = finalTotalForce;
            this.finalPressureKbar = finalPressureKbar;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }

        public Status getStatus() { return this.status; }
        public boolean isBfgsEndMarker() { return this.bfgsEndMarker; }
        public boolean isForcesConvergedMarker() { return this.forcesConvergedMarker; }
        public boolean isScfAlwaysConverged() { return this.scfAlwaysConverged; }
        public Double getFinalTotalForce() { return this.finalTotalForce; }
        public Double getFinalPressureKbar() { return this.finalPressureKbar; }
        public List<String> getDiagnostics() { return this.diagnostics; }
        public boolean isOptimized() { return this.status == Status.OPTIMIZED; }
    }

    private static final Pattern BFGS_END = Pattern.compile(
            "bfgs converged in\\s+\\d+\\s+scf cycles|End final coordinates|Final estimate of structures",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORCES_CONV = Pattern.compile(
            "The total force is below the threshold|Forces convergence threshold",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_FORCE = Pattern.compile(
            "Total force\\s*=\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESSURE = Pattern.compile(
            "total\\s+stress.*P=\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private GeometryConvergenceValidator() {
        // Utility.
    }

    public static Result validate(String logText, ProjectGeometryList geometries,
                                  Double forceThresholdRyBohr, Double pressureThresholdKbar) {
        List<String> diagnostics = new ArrayList<>();
        boolean bfgs = logText != null && BFGS_END.matcher(logText).find();
        boolean forcesMarker = logText != null && FORCES_CONV.matcher(logText).find();
        Double force = lastMatch(logText, TOTAL_FORCE);
        Double pressure = lastMatch(logText, PRESSURE);

        boolean listConverged = geometries != null && geometries.isConverged();
        boolean hasGeometry = geometries != null && geometries.numGeometries() > 0;
        boolean scfOk = true;
        if (geometries != null && geometries.numGeometries() > 0) {
            ProjectGeometry last = geometries.getGeometry(geometries.numGeometries() - 1);
            if (last != null && force == null && last.getTotalForce() > 0.0) {
                force = last.getTotalForce();
            }
        }

        if (logText != null && logText.toLowerCase(Locale.ROOT).contains("convergence not achieved")) {
            scfOk = false;
            diagnostics.add("Electronic SCF reported convergence NOT achieved.");
        }

        if (!hasGeometry && (logText == null || logText.isBlank())) {
            diagnostics.add("No geometry list and no log text were supplied.");
            return new Result(Status.UNKNOWN, bfgs, forcesMarker, scfOk, force, pressure, diagnostics);
        }
        if (!hasGeometry) {
            diagnostics.add("No ionic steps were parsed into the geometry list.");
            return new Result(Status.INCOMPLETE, bfgs, forcesMarker, scfOk, force, pressure, diagnostics);
        }

        boolean forceOk = forceThresholdRyBohr == null
                || (force != null && force <= forceThresholdRyBohr);
        boolean pressureOk = pressureThresholdKbar == null
                || (pressure != null && Math.abs(pressure) <= pressureThresholdKbar);

        if (forceThresholdRyBohr != null && force == null) {
            diagnostics.add("Force threshold requested but no Total force value was found.");
            forceOk = false;
        }
        if (pressureThresholdKbar != null && pressure == null) {
            diagnostics.add("Pressure threshold requested but no pressure value was found.");
            pressureOk = false;
        }

        boolean markers = bfgs || forcesMarker || listConverged;
        if (!markers) {
            diagnostics.add("No BFGS/end-final-coordinates / forces-threshold / list-converged marker.");
        }
        if (!scfOk) {
            markers = false;
        }
        if (!forceOk) {
            diagnostics.add(String.format(Locale.ROOT,
                    "Final force %s exceeds threshold %s Ry/bohr.",
                    force, forceThresholdRyBohr));
        }
        if (!pressureOk) {
            diagnostics.add(String.format(Locale.ROOT,
                    "Final pressure %s exceeds threshold %s kbar.",
                    pressure, pressureThresholdKbar));
        }

        Status status = (markers && forceOk && pressureOk && scfOk)
                ? Status.OPTIMIZED : Status.NOT_OPTIMIZED;
        if (status == Status.OPTIMIZED) {
            diagnostics.add("Geometry meets declared convergence evidence.");
        }
        return new Result(status, bfgs, forcesMarker, scfOk, force, pressure, diagnostics);
    }

    private static Double lastMatch(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        Double value = null;
        while (matcher.find()) {
            try {
                value = ScfConvergenceAnalyzer.parseFortranDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // keep previous
            }
        }
        return value;
    }
}
