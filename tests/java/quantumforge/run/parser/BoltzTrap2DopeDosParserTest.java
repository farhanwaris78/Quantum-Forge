/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.BoltzTrap2DopeDosParser.DopeDosKind;
import quantumforge.run.parser.BoltzTrap2DopeDosParser.DopeDosTable;

/**
 * Pins {@link BoltzTrap2DopeDosParser} against the yiwang62 fork's
 * parse_dope writers, verbatim: {@code fp.write('{} {}\n'.format(
 * (e-fermi)/Volt, dos[i]*Volt))} for .dope.dos and the 4-column
 * vvdos-diagonal variant for .dope.vvdos (branch 20210126,
 * BoltzTraP2/interface.py + the dosremesh epilogue os.rename lines).
 */
final class BoltzTrap2DopeDosParserTest {

    private static String dosRows() {
        // (E-Ef) ca. eV rel Fermi, dos*Volt - the writer's own print shape
        return "-0.2 1.5\n-0.1 2.75\n0 3.5\n0.1 4.25\n0.2 5.0\n";
    }

    @Test
    void twoColumnTablesParseWithVerbatimProvenance() {
        OperationResult<DopeDosTable> result =
                BoltzTrap2DopeDosParser.parseText(dosRows(), "si.dope.dos");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("BOLTZTRAP2_DOPEDOS_OK", result.getCode());
        DopeDosTable table = result.getValue().orElseThrow();
        assertEquals(DopeDosKind.DOS, table.getKind());
        assertEquals(5, table.getRows());
        assertEquals(0, table.getPartialTailHeld());
        assertEquals(-0.1, table.getEnergy()[1], 1.0e-12);
        assertEquals(2.75, table.getChannels().get(0)[1], 1.0e-12);
        assertEquals(1, table.getChannels().size());
        assertTrue(table.getNotes().stream().anyMatch(n -> n.contains("2 columns")));
        assertTrue(table.getNotes().stream().anyMatch(n -> n.contains("(e-fermi)/Volt")));
        assertTrue(table.getNotes().stream().anyMatch(n
                -> n.contains("no physics conversion")));
    }

    @Test
    void fourColumnTablesParseAsVvdos() {
        String text = "-0.2 1.0 2.0 3.0\n-0.1 1.5 2.5 3.5\n0 2.0 3.0 4.0\n";
        OperationResult<DopeDosTable> result =
                BoltzTrap2DopeDosParser.parseText(text, "si.dope.vvdos");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        DopeDosTable table = result.getValue().orElseThrow();
        assertEquals(DopeDosKind.VVDOS, table.getKind());
        assertEquals(3, table.getRows());
        assertEquals(3, table.getChannels().size());
        assertEquals(2.5, table.getChannels().get(1)[1], 1.0e-12);
        assertEquals(3.5, table.getChannels().get(2)[1], 1.0e-12);
        assertTrue(table.getNotes().stream().anyMatch(n -> n.contains("vvdos")));
    }

    @Test
    void nameShapeMismatchesAreRefusedNeverRelabeled() {
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText(dosRows(), "si.dope.vvdos").getCode(),
                "a 2-column table named .dope.vvdos is refused, not re-labeled");
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText(
                        "0 1 2 3\n", "si.dope.dos").getCode(),
                "a 4-column table named .dope.dos is refused, not re-labeled");
        // nameless callers (clipboard preview) get the column-count family
        assertTrue(BoltzTrap2DopeDosParser.parseText("0 1 2 3\n", null).isSuccess());
        // _raw tables keep the name check AND carry the rename provenance
        OperationResult<DopeDosTable> raw =
                BoltzTrap2DopeDosParser.parseText(dosRows(), "si.dope.dos_raw");
        assertTrue(raw.isSuccess(), () -> raw.getMessage());
        assertTrue(raw.getValue().orElseThrow().getNotes().stream()
                .anyMatch(n -> n.contains("os.rename")));
    }

    @Test
    void raggedGarbageAndEmptyAreLoudRefusals() {
        assertEquals("BOLTZTRAP2_DOPEDOS_EMPTY",
                BoltzTrap2DopeDosParser.parseText("", "x").getCode());
        assertEquals("BOLTZTRAP2_DOPEDOS_EMPTY",
                BoltzTrap2DopeDosParser.parseText("\n  \n", "x").getCode());
        assertEquals("BOLTZTRAP2_DOPEDOS_INPUT",
                BoltzTrap2DopeDosParser.parseText(null, "x").getCode());
        // mid-file raggedness: a 3-cell row between 2-cell rows
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText(
                        "0 1\n1 2 3\n2 3\n", "x").getCode());
        // a width CHANGE mid-file
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText(
                        "0 1 2 3\n1 2\n", "x").getCode());
        // garbage cell
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText("0 junk\n", "x").getCode());
    }

    @Test
    void onlyTheLastLineMayBeAPartialAppend() {
        OperationResult<DopeDosTable> result = BoltzTrap2DopeDosParser.parseText(
                dosRows() + "0.3", "live.dope.dos");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        DopeDosTable table = result.getValue().orElseThrow();
        assertEquals(5, table.getRows());
        assertEquals(1, table.getPartialTailHeld());
        assertTrue(result.getMessage().contains("partial"));
        assertTrue(table.getNotes().stream().anyMatch(n -> n.contains("held back")));
        // two ragged lines in a row: the second is mid-file corruption
        assertEquals("BOLTZTRAP2_DOPEDOS_SHAPE",
                BoltzTrap2DopeDosParser.parseText(
                        "0 1\n1\n2", "x").getCode());
    }
}
