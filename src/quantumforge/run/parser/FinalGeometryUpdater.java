/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;

/**
 * Transactional preview for applying the last complete optimised geometry.
 *
 * <p>Does not mutate QE inputs until a future implementation can rebuild
 * ATOMIC_POSITIONS/CELL_PARAMETERS from a validated {@link ProjectGeometry}
 * snapshot with rollback. Callers receive a typed preview result.</p>
 */
public final class FinalGeometryUpdater {

    public static final class GeometryPreview {
        private final int stepIndex;
        private final double energyRy;
        private final double totalForce;
        private final boolean converged;
        private final int atomCount;
        private final List<String> notes;

        public GeometryPreview(int stepIndex, double energyRy, double totalForce,
                               boolean converged, int atomCount, List<String> notes) {
            this.stepIndex = stepIndex;
            this.energyRy = energyRy;
            this.totalForce = totalForce;
            this.converged = converged;
            this.atomCount = atomCount;
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        }

        public int getStepIndex() { return this.stepIndex; }
        public double getEnergyRy() { return this.energyRy; }
        public double getTotalForce() { return this.totalForce; }
        public boolean isConverged() { return this.converged; }
        public int getAtomCount() { return this.atomCount; }
        public List<String> getNotes() { return this.notes; }
    }

    private FinalGeometryUpdater() {
        // Utility.
    }

    public static OperationResult<GeometryPreview> preview(Project project) {
        if (project == null) {
            return OperationResult.failed("PROJECT_MISSING", "No project was supplied.", null);
        }
        ProjectProperty property = project.getProperty();
        if (property == null) {
            return OperationResult.failed("PROPERTY_MISSING", "Project has no property store.", null);
        }
        ProjectGeometryList list = property.getOptList();
        if (list == null || list.numGeometries() == 0) {
            return OperationResult.failed("GEOMETRY_MISSING",
                    "No optimised geometry steps are available to preview.", null);
        }
        int last = list.numGeometries() - 1;
        ProjectGeometry geometry = list.getGeometry(last);
        if (geometry == null) {
            return OperationResult.failed("GEOMETRY_NULL", "Last geometry entry is null.", null);
        }

        List<String> notes = new ArrayList<>();
        GeometryConvergenceValidator.Result validation =
                GeometryConvergenceValidator.validate(null, list, null, null);
        if (!validation.isOptimized() && !geometry.isConverged() && !list.isConverged()) {
            notes.add("Geometry list is not marked converged; applying it would be unsafe.");
            return OperationResult.failed("GEOMETRY_NOT_CONVERGED",
                    "Refusing preview of a non-converged optimisation.", null);
        }
        notes.add("Preview only: input cards are not modified by this call.");
        notes.add("Validation: " + validation.getStatus());
        for (String diagnostic : validation.getDiagnostics()) {
            notes.add(diagnostic);
        }

        int atomCount = 0;
        try {
            // ProjectGeometry stores atoms in a private list; count via toString size is unsafe.
            // Use energy/force presence as the structural signal for now.
            atomCount = -1;
        } catch (RuntimeException ignored) {
            atomCount = -1;
        }

        GeometryPreview preview = new GeometryPreview(last, geometry.getEnergy(),
                geometry.getTotalForce(), geometry.isConverged() || list.isConverged(),
                atomCount, notes);
        return OperationResult.success("GEOMETRY_PREVIEW",
                "Final geometry preview ready (no mutation).", preview);
    }

    public static OperationResult<Void> apply(Project project) {
        OperationResult<GeometryPreview> preview = preview(project);
        if (!preview.isSuccess()) {
            return OperationResult.failed(preview.getCode(), preview.getMessage(), null);
        }
        // Still fail-closed for mutation: rebuilding QE cards transactionally is roadmap #40 body.
        return OperationResult.unsupported("FINAL_GEOMETRY_APPLY_UNAVAILABLE",
                "Transactional write-back of final geometry into QE inputs is not yet implemented; "
                        + "preview succeeded without modifying the project.");
    }
}
