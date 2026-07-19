/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.operation.OperationResult;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Static ESM/slab readiness audit (Roadmap #54 data layer): reads the live
 * pw.x SYSTEM namelist and the live cell, then states - verbatim - whether the
 * setup is an Effective Screening Medium calculation usable for work-function
 * work. The auditor checks the three facts QE actually enforces or documents:
 * assume_isolated='esm', esm_bc in {pbc, bc1, bc2, bc3} (QE default pbc),
 * and a z-oriented cell (a1_z = a2_z = 0 is REQUIRED by the ESM
 * implementation; the vacuum region lives along z). The vacuum estimate is an
 * honest geometric one: |c| minus the Cartesian z extent of the atoms, valid
 * only under the z orientation. Nothing is modified, and values NEVER leave
 * the namelist: unrecognized entries are reported as read.
 */
public final class QEEsmAuditor {

    /** Advisory vacuum floor in Angstrom; below this screening is questionable. */
    public static final double RECOMMENDED_VACUUM_ANG = 10.0;
    /** Relative tolerance for the a1_z = a2_z = 0 z-orientation requirement. */
    public static final double Z_ALIGNMENT_TOLERANCE = 1.0e-8;

    /** Keyword verdict of the audit. */
    public enum Verdict {
        /** assume_isolated='esm' with a documented non-periodic esm_bc. */
        READY,
        /** ESM active but periodic along z: no open circuit, no work function. */
        ACTIVE_BUT_PERIODIC,
        /** Not an ESM calculation at all (or assume_isolated unset). */
        NOT_ESM
    }

    /** Immutable audit outcome. */
    public static final class EsmAudit {
        private final String assumeIsolated; // verbatim (quotes stripped) or null
        private final String esmBc;          // verbatim or null (QE default is 'pbc')
        private final String esmW;           // verbatim or null
        private final String esmEfield;      // verbatim or null
        private final boolean zPerpendicular;
        private final double cLengthAng;
        private final double slabExtentAng;
        private final double vacuumGapAng;
        private final Verdict verdict;

        EsmAudit(String assumeIsolated, String esmBc, String esmW, String esmEfield,
                boolean zPerpendicular, double cLengthAng, double slabExtentAng,
                double vacuumGapAng, Verdict verdict) {
            this.assumeIsolated = assumeIsolated;
            this.esmBc = esmBc;
            this.esmW = esmW;
            this.esmEfield = esmEfield;
            this.zPerpendicular = zPerpendicular;
            this.cLengthAng = cLengthAng;
            this.slabExtentAng = slabExtentAng;
            this.vacuumGapAng = vacuumGapAng;
            this.verdict = verdict;
        }

        public String getAssumeIsolated() { return this.assumeIsolated; }
        public String getEsmBc() { return this.esmBc; }
        public String getEsmW() { return this.esmW; }
        public String getEsmEfield() { return this.esmEfield; }
        public boolean isZPerpendicular() { return this.zPerpendicular; }
        public double getCLengthAng() { return this.cLengthAng; }
        public double getSlabExtentAng() { return this.slabExtentAng; }
        public double getVacuumGapAng() { return this.vacuumGapAng; }
        public Verdict getVerdict() { return this.verdict; }
    }

    private QEEsmAuditor() { }

    /**
     * Runs the audit. Codes: ESM_INPUT (missing input), ESM_CELL
     * (missing/degenerate/empty cell - the geometry gate cannot run).
     */
    public static OperationResult<EsmAudit> audit(QEInput input, Cell cell) {
        if (input == null) {
            return OperationResult.failed("ESM_INPUT",
                    "The project has no resolvable current input to audit.", null);
        }
        if (cell == null) {
            return OperationResult.failed("ESM_CELL",
                    "The project has no atomic cell; the z-orientation and vacuum "
                            + "gates cannot run.",
                    null);
        }
        double[][] lattice = cell.copyLattice();
        if (lattice == null || lattice.length != 3) {
            return OperationResult.failed("ESM_CELL",
                    "The cell lattice is unavailable or not 3x3.", null);
        }
        Atom[] atoms = cell.listAtoms();
        if (atoms == null || atoms.length == 0) {
            return OperationResult.failed("ESM_CELL",
                    "The cell holds no atoms; a vacuum estimate is meaningless.", null);
        }

        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        String assumeIsolated = verbatim(system, "assume_isolated");
        String esmBc = verbatim(system, "esm_bc");
        String esmW = verbatim(system, "esm_w");
        String esmEfield = verbatim(system, "esm_efield");

        double a1z = Math.abs(lattice[0][2]);
        double a2z = Math.abs(lattice[1][2]);
        double norm1 = Math.max(1.0, Math.abs(lattice[0][0])
                + Math.abs(lattice[0][1]) + a1z);
        double norm2 = Math.max(1.0, Math.abs(lattice[1][0])
                + Math.abs(lattice[1][1]) + a2z);
        boolean zPerpendicular = a1z <= Z_ALIGNMENT_TOLERANCE * norm1
                && a2z <= Z_ALIGNMENT_TOLERANCE * norm2;
        double cLength = Math.sqrt(lattice[2][0] * lattice[2][0]
                + lattice[2][1] * lattice[2][1] + lattice[2][2] * lattice[2][2]);

        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        for (Atom atom : atoms) {
            zMin = Math.min(zMin, atom.getZ());
            zMax = Math.max(zMax, atom.getZ());
        }
        double extent = Math.max(0.0, zMax - zMin);
        double gap = cLength - extent;

        Verdict verdict;
        if (assumeIsolated == null || !assumeIsolated.equalsIgnoreCase("esm")) {
            verdict = Verdict.NOT_ESM;
        } else if (esmBc == null || esmBc.equalsIgnoreCase("pbc")) {
            verdict = Verdict.ACTIVE_BUT_PERIODIC;
        } else if (esmBc.equalsIgnoreCase("bc1") || esmBc.equalsIgnoreCase("bc2")
                || esmBc.equalsIgnoreCase("bc3")) {
            verdict = Verdict.READY;
        } else {
            // Recognized assume_isolated but an esm_bc outside the documented set
            // cannot be classified; honesty is refusing to call it ready.
            verdict = Verdict.ACTIVE_BUT_PERIODIC;
        }
        return OperationResult.success("ESM_AUDIT_OK", "Audit complete.",
                new EsmAudit(assumeIsolated, esmBc, esmW, esmEfield, zPerpendicular,
                        cLength, extent, gap, verdict));
    }

    /** Verbatim, quote-stripped value of one SYSTEM keyword; null when absent. */
    private static String verbatim(QENamelist system, String keyword) {
        if (system == null) {
            return null;
        }
        QEValue value = system.getValue(keyword);
        if (value == null) {
            return null;
        }
        String raw;
        try {
            raw = value.getCharacterValue();
        } catch (RuntimeException ex) {
            raw = null;
        }
        if (raw == null || raw.isBlank()) {
            raw = value.toString();
        }
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                        || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
