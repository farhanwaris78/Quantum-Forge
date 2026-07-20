/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Cell;
import quantumforge.input.QEEsmAuditor.EsmAudit;
import quantumforge.input.QEEsmAuditor.Verdict;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class QEEsmAuditorTest {

    private static Cell slabCell() {
        double[][] lattice = {{10.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 30.0}};
        Cell cell = new Cell(lattice);
        cell.addAtom("Cu", 0.5, 0.5, 0.4); // fractional z -> 12.0 Ang
        cell.addAtom("Cu", 0.5, 0.5, 0.6); // fractional z -> 18.0 Ang
        return cell;
    }

    private static QESCFInput input(String assumeIsolated, String esmBc) {
        QESCFInput input = new QESCFInput();
        if (assumeIsolated != null) {
            input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                    QEValueBase.getInstance("assume_isolated", assumeIsolated));
        }
        if (esmBc != null) {
            input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                    QEValueBase.getInstance("esm_bc", esmBc));
        }
        return input;
    }

    @Test
    void readyEsmSlabCarriesVerbatimKeywordsAndHonestGeometry() {
        QESCFInput input = input("'esm'", "'bc1'");
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("esm_w", "-0.2"));
        OperationResult<EsmAudit> result = QEEsmAuditor.audit(input, slabCell());
        assertTrue(result.isSuccess(), result.toString());
        EsmAudit audit = result.getValue().orElseThrow();
        assertEquals(Verdict.READY, audit.getVerdict());
        assertEquals("esm", audit.getAssumeIsolated(), "quotes are stripped verbatim");
        assertEquals("bc1", audit.getEsmBc());
        assertTrue(audit.getEsmW().contains("-2.0"), "esm_w stays verbatim: "
                + audit.getEsmW());
        assertEquals(null, audit.getEsmEfield(), "unset esm_efield stays null");
        assertTrue(audit.isZPerpendicular());
        assertEquals(30.0, audit.getCLengthAng(), 1e-12);
        assertEquals(6.0, audit.getSlabExtentAng(), 1e-12,
                "fractional z 0.4/0.6 of c=30 spans 6 Angstrom");
        assertEquals(24.0, audit.getVacuumGapAng(), 1e-12);
    }

    @Test
    void periodicAndNonEsmVerdicts() {
        OperationResult<EsmAudit> periodic = QEEsmAuditor.audit(input("'esm'", "'pbc'"),
                slabCell());
        assertTrue(periodic.isSuccess(), periodic.toString());
        assertEquals(Verdict.ACTIVE_BUT_PERIODIC, periodic.getValue().orElseThrow()
                .getVerdict(), "ESM with pbc has no open circuit");

        OperationResult<EsmAudit> missingBc = QEEsmAuditor.audit(input("'esm'", null),
                slabCell());
        assertEquals(Verdict.ACTIVE_BUT_PERIODIC, missingBc.getValue().orElseThrow()
                .getVerdict(), "absent esm_bc means the QE default pbc");

        OperationResult<EsmAudit> plain = QEEsmAuditor.audit(input(null, null), slabCell());
        assertEquals(Verdict.NOT_ESM, plain.getValue().orElseThrow().getVerdict());
        assertEquals(null, plain.getValue().orElseThrow().getAssumeIsolated());

        OperationResult<EsmAudit> other = QEEsmAuditor.audit(
                input("'martyna-tuckerman'", null), slabCell());
        assertEquals(Verdict.NOT_ESM, other.getValue().orElseThrow().getVerdict(),
                "martyna-tuckerman is not the ESM scheme");
    }

    @Test
    void skewedCellFlagsTheZOrientationGate() {
        double[][] skewed = {{10.0, 0.0, 0.5}, {0.0, 10.0, 0.0}, {0.0, 0.0, 30.0}};
        Cell cell = new Cell(skewed);
        cell.addAtom("Cu", 0.5, 0.5, 0.5);
        OperationResult<EsmAudit> result = QEEsmAuditor.audit(input("'esm'", "'bc1'"), cell);
        assertTrue(result.isSuccess(), result.toString());
        assertFalse(result.getValue().orElseThrow().isZPerpendicular(),
                "a1_z = 0.5 violates the ESM z-orientation requirement");
    }

    @Test
    void missingInputOrCellFailsClosed() {
        assertEquals("ESM_INPUT", QEEsmAuditor.audit(null, slabCell()).getCode());
        assertEquals("ESM_CELL", QEEsmAuditor.audit(input("'esm'", "'bc1'"), null)
                .getCode());
        double[][] lattice = {{10.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 30.0}};
        assertEquals("ESM_CELL", QEEsmAuditor.audit(input("'esm'", "'bc1'"),
                new Cell(lattice)).getCode(), "an empty cell cannot yield a vacuum gap");
    }
}
