/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class ExtXyzCellExporterTest {

    @Test
    void exportsLosslessExtXyzDocument() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.0, 0.0);
        OperationResult<String> result = ExtXyzCellExporter.export(cell);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("XXYZ_OK", result.getCode());
        String document = result.getValue().orElseThrow();
        String[] lines = document.split("\n");
        assertEquals(4, lines.length);
        assertEquals("2", lines[0]);
        assertTrue(lines[1].contains(
                "Lattice=\"10.0 0.0 0.0 0.0 10.0 0.0 0.0 0.0 10.0\""), lines[1]);
        assertTrue(lines[1].contains("Properties=species:S:1:pos:R:3"), lines[1]);
        assertTrue(lines[1].contains("pbc=\"T T T\""), lines[1]);
        assertEquals("Si 0.0 0.0 0.0", lines[2]);
        assertEquals("Si 1.5 0.0 0.0", lines[3]);
        assertTrue(result.getMessage().contains("1000.0 Angstrom^3"), result.getMessage());
    }

    @Test
    void failsClosedOnEmptyOrDegenerateCells() throws Exception {
        assertEquals("XXYZ_EMPTY", ExtXyzCellExporter.export(null).getCode());
        Cell empty = new Cell(Matrix3D.unit(10.0));
        assertEquals("XXYZ_EMPTY", ExtXyzCellExporter.export(empty).getCode());

        double[][] flat = {{10.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 0.0}};
        Cell degenerate = new Cell(flat);
        degenerate.addAtom("Si", 0.0, 0.0, 0.0);
        OperationResult<String> refused = ExtXyzCellExporter.export(degenerate);
        assertFalse(refused.isSuccess());
        assertEquals("XXYZ_LATTICE", refused.getCode());
    }
}
