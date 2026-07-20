/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class SupercellMatrixValidatorTest {

    @Test
    void acceptsDiagonalAndNonDiagonalMatrices() {
        OperationResult<SupercellMatrixValidator.SupercellTransform> diag =
                SupercellMatrixValidator.validate("2 0 0; 0 2 0; 0 0 2");
        assertTrue(diag.isSuccess(), diag.getMessage());
        assertEquals(8L, diag.getValue().orElseThrow().getDeterminant());

        OperationResult<SupercellMatrixValidator.SupercellTransform> shear =
                SupercellMatrixValidator.validate("1 1 0; 0 1 0; 0 0 1");
        assertTrue(shear.isSuccess(), shear.getMessage());
        assertEquals(1L, shear.getValue().orElseThrow().getDeterminant());
        // New row 0 = a0 + a1 on the unit(10) lattice.
        double[][] lattice = shear.getValue().orElseThrow()
                .applyToLattice(Matrix3D.unit(10.0));
        assertEquals(10.0, lattice[0][0], 1e-12);
        assertEquals(10.0, lattice[0][1], 1e-12);
        assertEquals(0.0, lattice[0][2], 1e-12);
        assertEquals(0.0, lattice[1][0], 1e-12);
        assertEquals(10.0, lattice[1][1], 1e-12);

        // Comma separators and negative mixed entries within bounds are legal.
        OperationResult<SupercellMatrixValidator.SupercellTransform> mixed =
                SupercellMatrixValidator.validate("1,-1 0 ; 0, 1 0; 0 0 2");
        assertTrue(mixed.isSuccess(), mixed.getMessage());
        assertEquals(2L, mixed.getValue().orElseThrow().getDeterminant());
    }

    @Test
    void failsClosedOnSyntaxBoundsAndTopology() {
        assertEquals("SUPERCELL_SYNTAX",
                SupercellMatrixValidator.validate("").getCode());
        assertEquals("SUPERCELL_SYNTAX",
                SupercellMatrixValidator.validate("2 0 0; 0 2 0").getCode());
        assertEquals("SUPERCELL_SYNTAX",
                SupercellMatrixValidator.validate("2 0; 0 2 0; 0 0 1").getCode());
        assertEquals("SUPERCELL_SYNTAX",
                SupercellMatrixValidator.validate("2.5 0 0; 0 2 0; 0 0 1").getCode(),
                "Non-integer entries are not supercell matrices");
        assertEquals("SUPERCELL_BOUND",
                SupercellMatrixValidator.validate("9 0 0; 0 1 0; 0 0 1").getCode());
        assertEquals("SUPERCELL_SINGULAR",
                SupercellMatrixValidator.validate("1 1 0; 2 2 0; 0 0 1").getCode());
        assertEquals("SUPERCELL_HANDEDNESS",
                SupercellMatrixValidator.validate("-1 0 0; 0 1 0; 0 0 1").getCode(),
                "A handedness flip is refused, not silently accepted");
        assertEquals("SUPERCELL_DET",
                SupercellMatrixValidator.validate("5 0 0; 0 5 0; 0 0 3").getCode(),
                "Multiplicity beyond the preview bound is refused");
    }
}
