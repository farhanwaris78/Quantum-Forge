/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.builder.SdfStructureReader.SdfStructure;
import quantumforge.operation.OperationResult;

class SdfStructureReaderTest {

    private static final String METHANE = """
            methane test
              QuantumForge

              5  4  0  0  0  0  0  0  0  0  0  0    V2000
                0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0
                0.6291    0.6291    0.6291 H   0  0  0  0  0  0  0  0  0  0  0  0
               -0.6291   -0.6291    0.6291 H   0  0  0  0  0  0  0  0  0  0  0  0
               -0.6291    0.6291   -0.6291 H   0  0  0  0  0  0  0  0  0  0  0  0
                0.6291   -0.6291   -0.6291 H   0  0  0  0  0  0  0  0  0  0  0  0
              1  2  1  0  0  0  0
              1  3  1  0  0  0  0
              1  4  1  0  0  0  0
              1  5  1  0  0  0  0
            M  END
            """;

    @Test
    void methaneReviewsWithExactSubsetCounts() {
        OperationResult<SdfStructure> result = SdfStructureReader.parseText(METHANE);
        assertTrue(result.isSuccess(), result.toString());
        SdfStructure structure = result.getValue().orElseThrow();
        assertEquals("methane test", structure.getTitle(), "line 1 verbatim");
        assertTrue(structure.hasVersionMarker());
        assertEquals(5, structure.getAtoms().size());
        assertEquals(4, structure.getBonds().size());
        assertEquals(2, structure.elementCounts().size());
        assertEquals(1, structure.elementCounts().get("C").intValue());
        assertEquals(4, structure.elementCounts().get("H").intValue());
        assertEquals(1, structure.bondTypeCounts().size());
        assertEquals(4, structure.bondTypeCounts().get(1).intValue(),
                "bond types echoed, NEVER aromaticity-perceived");
        assertEquals(0.0, structure.getAtoms().get(0).getX(), 1e-12);
        assertEquals(0.6291, structure.getAtoms().get(1).getZ(), 1e-12);
        assertEquals(-0.6291, structure.getAtoms().get(2).getX(), 1e-12);
        assertEquals("H", structure.getAtoms().get(4).getElement());
        assertEquals(0, structure.getPseudoAtomCount());
        assertEquals(0, structure.getChargedAtoms());
        assertEquals(0, structure.getChargeSum());
        assertEquals(1, structure.getPropertyLines(), "only M  END");
        assertEquals(0, structure.getDataFieldCount());
    }

    @Test
    void pseudoAtomsChargesAndDataFieldsAreCountedNotInterpreted() {
        String query = """
                query record
                  QF

                  3  0  0  0  0  0  0  0  0  0  0  0    V2000
                    0.0000    0.0000    0.0000 Q   0  0  0  0  0  0  0  0  0  0  0  0
                    1.0000    0.0000    0.0000 Na  0  0  0  0  0  0  0  0  0  0  0  0
                    2.0000    0.0000    0.0000 Cl  0  0  0  0  0  0  0  0  0  0  0  0
                M  CHG  2  2  1  3 -1
                M  END
                > <REGNO>
                123
                """;
        OperationResult<SdfStructure> result = SdfStructureReader.parseText(query);
        assertTrue(result.isSuccess(), result.toString());
        SdfStructure structure = result.getValue().orElseThrow();
        assertEquals(1, structure.getPseudoAtomCount(), "Q counted, never guessed");
        assertNull(structure.getAtoms().get(0).getElement(),
                "pseudo atom has NO element");
        assertEquals(2, structure.elementCounts().size(),
                "composition excludes the pseudo atom");
        assertEquals(1, structure.elementCounts().get("Cl").intValue());
        assertEquals(1, structure.elementCounts().get("Na").intValue());
        assertEquals(2, structure.getChargedAtoms());
        assertEquals(0, structure.getChargeSum(), "1 + (-1) = 0 declared-sum");
        assertEquals(2, structure.getPropertyLines(), "M  CHG plus M  END");
        assertEquals(1, structure.getDataFieldCount());
        assertFalse(structure.getAtoms().get(1).isPseudoAtom());
    }

    @Test
    void tokenLayoutFromNonPaddedWritersStillParses() {
        String minimal = "water\n"
                + "\n"
                + "\n"
                + "2 1    V2000\n"
                + "0.0 0.0 0.0 O\n"
                + "0.0 0.0 0.958 H\n"
                + "1 2 1\n"
                + "M  END\n";
        OperationResult<SdfStructure> result = SdfStructureReader.parseText(minimal);
        assertTrue(result.isSuccess(), result.toString());
        SdfStructure structure = result.getValue().orElseThrow();
        assertEquals(2, structure.getAtoms().size());
        assertEquals(1, structure.getBonds().size());
        assertEquals(1, structure.elementCounts().get("O").intValue());
        assertEquals(1, structure.elementCounts().get("H").intValue());
        assertEquals(0.958, structure.getAtoms().get(1).getZ(), 1e-12);
        assertEquals(1, structure.getBonds().get(0).getType());
    }

    @Test
    void multiRecordBundlesAreRefusedNotSampled() {
        OperationResult<SdfStructure> result = SdfStructureReader.parseText(
                "mol one\n A\n\n  1  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                        + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0\n"
                        + "M  END\n$$$$\nmol two\n");
        assertFalse(result.isSuccess());
        assertEquals("SDF_MULTIRECORD", result.getCode());
        assertTrue(result.getMessage().contains("1 '$$$$'"), result.getMessage());
    }

    @Test
    void refusedInputsFailClosed() {
        OperationResult<SdfStructure> v3000 = SdfStructureReader.parseText(
                "mol\n A\n\n  1  0  0  0  0  0  0  0  0  0  0  0    V3000\n");
        assertFalse(v3000.isSuccess());
        assertEquals("SDF_V3000", v3000.getCode());

        OperationResult<SdfStructure> shortFile = SdfStructureReader.parseText(
                "only\ntwo\nlines\n");
        assertFalse(shortFile.isSuccess());
        assertEquals("SDF_SYNTAX", shortFile.getCode());

        OperationResult<SdfStructure> badCounts = SdfStructureReader.parseText(
                "mol\n A\n\nxx yy\n");
        assertFalse(badCounts.isSuccess());
        assertEquals("SDF_COUNTS", badCounts.getCode());

        OperationResult<SdfStructure> noAtoms = SdfStructureReader.parseText(
                "mol\n A\n\n  0  0  0  0  0  0  0  0  0  0  0  0    V2000\n");
        assertFalse(noAtoms.isSuccess());
        assertEquals("SDF_EMPTY", noAtoms.getCode());

        OperationResult<SdfStructure> badNumber = SdfStructureReader.parseText(
                "mol\n A\n\n  1  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                        + "    not-a-nu    0.0000    0.0000 C   0  0  0  0  0  0\n"
                        + "M  END\n");
        assertFalse(badNumber.isSuccess());
        assertEquals("SDF_VALUE", badNumber.getCode());

        OperationResult<SdfStructure> badBond = SdfStructureReader.parseText(
                "mol\n A\n\n  1  1  0  0  0  0  0  0  0  0  0  0    V2000\n"
                        + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0\n"
                        + "  1 99  1  0  0  0  0\n"
                        + "M  END\n");
        assertFalse(badBond.isSuccess());
        assertEquals("SDF_BONDS", badBond.getCode());

        OperationResult<SdfStructure> noEnd = SdfStructureReader.parseText(
                "mol\n A\n\n  1  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                        + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0\n"
                        + "M  CHG  1  1  1\n");
        assertFalse(noEnd.isSuccess());
        assertEquals("SDF_SYNTAX", noEnd.getCode());

        OperationResult<SdfStructure> badSymbol = SdfStructureReader.parseText(
                "mol\n A\n\n  1  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                        + "    0.0000    0.0000    0.0000 X1  0  0  0  0  0  0\n"
                        + "M  END\n");
        assertFalse(badSymbol.isSuccess());
        assertEquals("SDF_VALUE", badSymbol.getCode(),
                "unknown symbols are refused, never guessed");

        OperationResult<SdfStructure> blank = SdfStructureReader.parseText("   \n  ");
        assertFalse(blank.isSuccess());
        assertEquals("SDF_EMPTY", blank.getCode());
    }
}
