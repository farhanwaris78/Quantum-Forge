/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;

import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;

/**
 * Exact-exchange (hybrid/EXX) run guidance for pw.x (Roadmap #70).
 *
 * <p>The one hard structural rule this planner enforces is the QE EXX k/q-mesh
 * compatibility constraint: each Fock q-grid division {@code nq_i} must divide
 * the corresponding SCF k-grid division {@code nk_i} (q points must be a
 * sub-mesh of the k mesh). Richer guidance - {@code ecutfock} screening, ACE,
 * {@code x_gamma_extrapolation}, empirical memory models - is stated as
 * documentation, not invented numerically: the only quantitative output is the
 * finite pair count nk_total x nq_total with its pre-symmetry caveat, because
 * the actual cost of an EXX step is implementation- and hardware-specific.</p>
 */
public final class QEExxPlanner {

    /** Documentation anchor given to findings. */
    public static final String DOCS = "https://www.quantum-espresso.org/Doc/INPUT_PW.html"
            + " (namelist &SYSTEM: input_dft hybrid ids, exxdiv_treatment, ecutfock and "
            + "the nq1/nq2/nq3 entries)";

    private QEExxPlanner() {
        // Utility
    }

    /** Guidance outcome: the proposed grids, pair count, and typed findings. */
    public static final class ExxGuidance {
        private final int[] kGrid;
        private final int[] qGrid;
        private final long kqPairCount;
        private final List<ValidationIssue> issues;

        private ExxGuidance(int[] kGrid, int[] qGrid, long kqPairCount,
                            List<ValidationIssue> issues) {
            this.kGrid = kGrid.clone();
            this.qGrid = qGrid.clone();
            this.kqPairCount = kqPairCount;
            this.issues = List.copyOf(issues);
        }

        public int[] getKGrid() { return this.kGrid.clone(); }
        public int[] getQGrid() { return this.qGrid.clone(); }

        /**
         * Full k x q pair count without symmetry reduction. This is a count, not
         * a benchmark: EXX implementations reduce it with symmetry and screening.
         */
        public long getKqPairCount() { return this.kqPairCount; }

        public List<ValidationIssue> getIssues() { return this.issues; }

        public List<ValidationIssue> errors() {
            List<ValidationIssue> errors = new ArrayList<>();
            for (ValidationIssue issue : this.issues) {
                if (issue.getSeverity() == ValidationSeverity.ERROR) {
                    errors.add(issue);
                }
            }
            return errors;
        }

        public boolean isUsable() {
            return errors().isEmpty();
        }
    }

    /**
     * Validates the requested Fock q grid against the SCF k grid and reports the
     * pre-symmetry pair count. Grids are cloned on input; nothing is mutated.
     */
    public static ExxGuidance plan(int nk1, int nk2, int nk3, int nq1, int nq2, int nq3) {
        int[] kGrid = {nk1, nk2, nk3};
        int[] qGrid = {nq1, nq2, nq3};
        List<ValidationIssue> issues = new ArrayList<>();
        long pairs = 0L;
        boolean gridsValid = true;

        for (int axis = 0; axis < 3; axis++) {
            if (kGrid[axis] < 1) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "EXX_KGRID_INVALID",
                        "k-grid division nk" + (axis + 1) + " must be >= 1 (got "
                                + kGrid[axis] + ").", DOCS));
                gridsValid = false;
            }
            if (qGrid[axis] < 1) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "EXX_NQ_INVALID",
                        "nq" + (axis + 1) + " must be >= 1 (got " + qGrid[axis]
                                + "); EXX requires an explicit q grid.", DOCS));
                gridsValid = false;
            }
        }
        if (gridsValid) {
            for (int axis = 0; axis < 3; axis++) {
                if (qGrid[axis] > kGrid[axis]) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            "EXX_NQ_DENSER_THAN_K",
                            "nq" + (axis + 1) + "=" + qGrid[axis] + " exceeds nk" + (axis + 1)
                                    + "=" + kGrid[axis]
                                    + "; the Fock q grid cannot be denser than the SCF k grid.",
                            DOCS));
                } else if (kGrid[axis] % qGrid[axis] != 0) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            "EXX_NQ_NOT_DIVISOR",
                            "nq" + (axis + 1) + "=" + qGrid[axis] + " does not divide nk"
                                    + (axis + 1) + "=" + kGrid[axis]
                                    + "; QE requires the q mesh to be a sub-mesh of k.",
                            DOCS));
                }
            }
            long kTotal = 1L;
            long qTotal = 1L;
            for (int axis = 0; axis < 3; axis++) {
                kTotal *= kGrid[axis];
                qTotal *= qGrid[axis];
            }
            if (issues.stream().noneMatch(i -> i.getCode().startsWith("EXX_NQ"))) {
                pairs = kTotal * qTotal;
            }
            issues.add(new ValidationIssue(ValidationSeverity.INFO, "EXX_COST_COUNT_ONLY",
                    "nk_total*nq_total = " + kTotal + " x " + qTotal
                            + (pairs > 0L ? " = " + pairs : "")
                            + " pairs before symmetry reduction; real EXX cost scales with "
                            + "screening (ecutfock), band count and hardware, so benchmark "
                            + "this run rather than trusting any universal factor.",
                    DOCS));
            issues.add(new ValidationIssue(ValidationSeverity.INFO, "EXX_SETTINGS_MANUAL",
                    "input_dft (hybrid id), exxdiv_treatment, ecutfock and "
                            + "x_gamma_extrapolation are physics choices for your QE version "
                            + "and are intentionally not guessed here.",
                    DOCS));
        }
        // pairs stays 0 whenever any EXX_NQ* finding was emitted above.
        return new ExxGuidance(kGrid, qGrid, pairs, issues);
    }
}
