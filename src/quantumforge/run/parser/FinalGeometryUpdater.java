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

        int atomCount = geometry.numAtoms();
        if (atomCount <= 0) {
            return OperationResult.failed("GEOMETRY_ATOMS_MISSING",
                    "Last converged geometry contains no atomic coordinates.", null);
        }
        if (project.getCell() != null && project.getCell().numAtoms() != atomCount) {
            return OperationResult.failed("GEOMETRY_ATOM_COUNT_MISMATCH",
                    "Last geometry has " + atomCount + " atoms but the current project has "
                            + project.getCell().numAtoms() + "; refusing unsafe preview.", null);
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
        // Roadmap #40 body landed: the transactional write-back lives in
        // FinalGeometryTransaction (staged copies, per-mode audit artifacts,
        // verified write-through, best-effort rollback). Delegate to it.
        OperationResult<FinalGeometryTransaction.Plan> applied =
                FinalGeometryTransaction.apply(project);
        if (!applied.isSuccess()) {
            return OperationResult.failed(applied.getCode(), applied.getMessage(),
                    applied.getCause().orElse(null));
        }
        return OperationResult.success("FINAL_GEOMETRY_APPLIED", applied.getMessage(), null);
    }
}
