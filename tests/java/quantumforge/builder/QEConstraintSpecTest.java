package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/** Batch-54 coverage for the if_pos constraint-specification parser. */
class QEConstraintSpecTest {

    @Test
    void validSpecParsesRangesAndFlags() {
        OperationResult<QEConstraintSpec> result =
                QEConstraintSpec.parse("1:000; 3:111; 5-7:010", 7);
        assertTrue(result.isSuccess(), result.toString());
        QEConstraintSpec spec = result.getValue().orElseThrow();
        assertEquals(5, spec.getEntries().size());
        QEConstraintSpec.Entry first = spec.getEntries().get(0);
        assertEquals(0, first.getAtomIndex0());
        assertEquals(0, first.getIfX());
        assertEquals(0, first.getIfY());
        assertEquals(0, first.getIfZ());
        QEConstraintSpec.Entry rangeFirst = spec.getEntries().get(2);
        assertEquals(4, rangeFirst.getAtomIndex0());
        assertEquals(0, rangeFirst.getIfX());
        assertEquals(1, rangeFirst.getIfY());
        assertEquals(0, rangeFirst.getIfZ());
        assertEquals(6, spec.getEntries().get(4).getAtomIndex0());
        // atom 1 fully frozen and atoms 5-7 partially frozen => 4 frozen of 5.
        assertEquals(4, spec.frozenCount());
    }

    @Test
    void rejectsBadFlagsAndSyntax() {
        OperationResult<QEConstraintSpec> digits = QEConstraintSpec.parse("1:002", 4);
        assertFalse(digits.isSuccess());
        assertEquals("CONSTRAINT_SPEC_SYNTAX", digits.getCode());
        assertFalse(QEConstraintSpec.parse("1", 4).isSuccess());
        assertFalse(QEConstraintSpec.parse("abc:111", 4).isSuccess());
        assertFalse(QEConstraintSpec.parse("1:11", 4).isSuccess());
        assertFalse(QEConstraintSpec.parse("", 4).isSuccess());
    }

    @Test
    void rejectsRangeViolationsAndDuplicates() {
        assertEquals("CONSTRAINT_SPEC_RANGE",
                QEConstraintSpec.parse("5:111", 4).getCode());
        assertEquals("CONSTRAINT_SPEC_RANGE",
                QEConstraintSpec.parse("3-2:111", 4).getCode());
        assertEquals("CONSTRAINT_SPEC_RANGE",
                QEConstraintSpec.parse("0-2:111", 4).getCode());
        assertEquals("CONSTRAINT_SPEC_DUPLICATE",
                QEConstraintSpec.parse("1:000; 1:111", 4).getCode());
        assertEquals("CONSTRAINT_SPEC_DUPLICATE",
                QEConstraintSpec.parse("1-3:010; 2-4:011", 4).getCode());
    }

    @Test
    void rejectsEmptyCell() {
        OperationResult<QEConstraintSpec> result = QEConstraintSpec.parse("1:000", 0);
        assertFalse(result.isSuccess());
        assertEquals("CONSPEC_ATOMS", result.getCode());
    }
}
