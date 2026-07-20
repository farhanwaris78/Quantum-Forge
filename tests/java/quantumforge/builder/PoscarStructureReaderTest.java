/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.builder.PoscarStructureReader.PoscarStructure;
import quantumforge.operation.OperationResult;

class PoscarStructureReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void parsesVasp5DirectWithExactCartesianPositions() throws IOException {
        Path file = write("POSCAR_SiO2",
                "SiO2 quartz cell\n"
                + "1.0\n"
                + "4.9 0.0 0.0\n"
                + "-2.45 4.24352 0.0\n"
                + "0.0 0.0 5.4\n"
                + "Si O\n"
                + "1 2\n"
                + "Direct\n"
                + "0.0 0.0 0.0\n"
                + "0.33 0.33 0.33\n"
                + "0.5 0.5 0.0\n");
        OperationResult<PoscarStructure> result = PoscarStructureReader.parse(file);
        assertTrue(result.isSuccess(), result.toString());
        PoscarStructure structure = result.getValue().orElseThrow();
        assertEquals("SiO2 quartz cell", structure.getComment());
        assertEquals(2, structure.getSpecies().size());
        assertEquals("Si=1,O=2", structure.composition());
        assertEquals(3, structure.getTotalAtoms());
        assertTrue(structure.isDirectFrame());
        assertEquals(1.0, structure.getScaleApplied(), 0.0);
        double[][] positions = structure.getPositionsAng();
        assertEquals(0.0, positions[0][0], 1e-15);
        // 0.33 * (4.9 - 2.45) = 0.8085 ; 0.33 * 4.24352 = 1.4003616 ; 0.33 * 5.4 = 1.782
        assertEquals(0.8085, positions[1][0], 1e-12);
        assertEquals(1.4003616, positions[1][1], 1e-12);
        assertEquals(1.782, positions[1][2], 1e-12);
        assertEquals(0, structure.getOutOfCellCount());
        assertFalse(structure.isSelectiveDynamics());
        assertTrue(structure.getTrailingNote() == null,
                "nothing trails the coordinate block");
    }

    @Test
    void negativeScaleResolvesThroughVolume() throws IOException {
        Path file = write("POSCAR_Fe",
                "bcc Fe volume-scaled\n"
                + "-27.0\n"
                + "3.0 0.0 0.0\n"
                + "0.0 3.0 0.0\n"
                + "0.0 0.0 3.0\n"
                + "2\n"
                + "Cartesian\n"
                + "1.0 1.0 1.0\n"
                + "2.0 2.0 2.0\n");
        OperationResult<PoscarStructure> result = PoscarStructureReader.parse(file);
        assertTrue(result.isSuccess(), result.toString());
        PoscarStructure structure = result.getValue().orElseThrow();
        assertTrue(structure.isVolumeScaled());
        assertEquals(1.0, structure.getScaleApplied(), 1e-12, "cbrt(27/27) == 1 exactly");
        assertEquals(3.0, structure.getLattice()[0][0], 1e-12);
        assertTrue(structure.getSpecies().isEmpty(), "VASP 4 carries no species names");
        assertEquals("#1=2", structure.composition());
        assertFalse(structure.isDirectFrame());
        double[][] positions = structure.getPositionsAng();
        assertEquals(1.0, positions[0][0], 1e-12);
        assertEquals(2.0, positions[1][2], 1e-12);
    }

    @Test
    void selectiveDynamicsCountsFullyFixedAtomsAndOutOfCellFractions() throws IOException {
        Path file = write("POSCAR_sel",
                "selective slab\n"
                + "1.0\n"
                + "10.0 0.0 0.0\n"
                + "0.0 10.0 0.0\n"
                + "0.0 0.0 30.0\n"
                + "Cu\n"
                + "2\n"
                + "Selective dynamics\n"
                + "Direct\n"
                + "0.5 0.5 0.45 T T F\n"
                + "0.25 0.25 0.25 F F F\n");
        OperationResult<PoscarStructure> result = PoscarStructureReader.parse(file);
        assertTrue(result.isSuccess(), result.toString());
        PoscarStructure structure = result.getValue().orElseThrow();
        assertTrue(structure.isSelectiveDynamics());
        boolean[] fixed = structure.getFullyFixed();
        assertFalse(fixed[0], "T T F is not fully fixed");
        assertTrue(fixed[1], "F F F is fully fixed");
        assertEquals(0, structure.getOutOfCellCount());
        // z = 0.45 * 30.0 = 13.5 Angstrom
        assertEquals(13.5, structure.getPositionsAng()[0][2], 1e-12);

        Path wrapped = write("POSCAR_wrapped",
                "out of cell\n"
                + "1.0\n"
                + "10.0 0.0 0.0\n"
                + "0.0 10.0 0.0\n"
                + "0.0 0.0 10.0\n"
                + "H\n"
                + "1\n"
                + "Direct\n"
                + "1.2 0.0 0.0\n");
        OperationResult<PoscarStructure> out = PoscarStructureReader.parse(wrapped);
        assertTrue(out.isSuccess(), out.toString());
        assertEquals(1, out.getValue().orElseThrow().getOutOfCellCount(),
                "fraction 1.2 is outside the cell - reported, never wrapped");
    }

    @Test
    void trailingVelocityBlockIsReportedNotParsed() throws IOException {
        Path file = write("POSCAR_vel",
                "with velocities\n"
                + "1.0\n"
                + "10.0 0.0 0.0\n"
                + "0.0 10.0 0.0\n"
                + "0.0 0.0 10.0\n"
                + "H\n"
                + "1\n"
                + "Direct\n"
                + "0.0 0.0 0.0\n"
                + "0.1 0.1 0.1\n");
        OperationResult<PoscarStructure> result = PoscarStructureReader.parse(file);
        assertTrue(result.isSuccess(), result.toString());
        String note = result.getValue().orElseThrow().getTrailingNote();
        assertTrue(note != null && note.contains("NOT parsed"),
                "trailing content must be surfaced: " + note);
    }

    @Test
    void malformedInputsFailClosedWithCodes() throws IOException {
        assertEquals("POSCAR_SYNTAX", PoscarStructureReader.parse(
                write("POSCAR_short", "only\n4\nlines\nhere\n")).getCode());
        assertEquals("POSCAR_SCALE", PoscarStructureReader.parse(
                write("POSCAR_zero", "c\n0.0\n1 0 0\n0 1 0\n0 0 1\nH\n1\nDirect\n0 0 0\n"))
                .getCode());
        assertEquals("POSCAR_SCALE", PoscarStructureReader.parse(
                write("POSCAR_words", "c\nabc\n1 0 0\n0 1 0\n0 0 1\nH\n1\nDirect\n0 0 0\n"))
                .getCode());
        assertEquals("POSCAR_COUNT", PoscarStructureReader.parse(
                write("POSCAR_mm",
                        "c\n1.0\n1 0 0\n0 1 0\n0 0 1\nSi O\n1\nDirect\n0 0 0\n"))
                .getCode(), "2 species but 1 count must fail");
        assertEquals("POSCAR_FRAME", PoscarStructureReader.parse(
                write("POSCAR_frame",
                        "c\n1.0\n1 0 0\n0 1 0\n0 0 1\nH\n1\nAngstrom\n0 0 0\n"))
                .getCode());
        assertEquals("POSCAR_VALUE", PoscarStructureReader.parse(
                write("POSCAR_nan",
                        "c\n1.0\n1 0 0\n0 1 0\n0 0 1\nH\n1\nDirect\n0 x 0\n"))
                .getCode());
        assertEquals("POSCAR_SYNTAX", PoscarStructureReader.parse(
                write("POSCAR_partial",
                        "c\n1.0\n1 0 0\n0 1 0\n0 0 1\nH\n1\nSelective dynamics\nDirect\n"
                                + "0 0 0 T T\n"))
                .getCode(), "partial selective flags fail closed");
        assertEquals("POSCAR_IO", PoscarStructureReader.parse(
                this.tempDir.resolve("missing.POSCAR")).getCode());
    }
}
