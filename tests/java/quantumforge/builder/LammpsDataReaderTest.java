/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.builder.LammpsDataReader.AtomStyle;
import quantumforge.builder.LammpsDataReader.LammpsData;
import quantumforge.operation.OperationResult;

class LammpsDataReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void parsesAtomicStyleWithHeaderMassesAndBox() throws IOException {
        Path file = write("data.lj",
                "LAMMPS lj fluid data\n"
                + "2 atoms\n"
                + "1 atom types\n"
                + "0.0 20.0 xlo xhi\n"
                + "0.0 20.0 ylo yhi\n"
                + "0.0 20.0 zlo zhi\n"
                + "\n"
                + "Masses\n"
                + "\n"
                + "1 39.948\n"
                + "\n"
                + "Atoms\n"
                + "\n"
                + "1 1 2.5 2.5 2.5\n"
                + "2 1 17.5 17.5 17.5\n");
        OperationResult<LammpsData> result = LammpsDataReader.parse(file, AtomStyle.ATOMIC);
        assertTrue(result.isSuccess(), result.toString());
        LammpsData data = result.getValue().orElseThrow();
        assertEquals(2, data.getAtomCount());
        assertEquals(1, data.getTypeCount());
        assertEquals(Double.valueOf(39.948), data.getMasses()[0]);
        assertEquals(20.0, data.getBoxLengths()[2], 0.0);
        assertEquals(0L, data.getOutsideBoxCount());
        assertEquals(2.5, data.getAtoms().get(0).getX(), 0.0);
        assertEquals(1, data.getAtoms().get(1).getType());
        assertTrue(data.getSkippedSections().isEmpty());
    }

    @Test
    void parsesChargeStyleAndSkipsVelocitiesByName() throws IOException {
        Path file = write("charge.data",
                "silica charge example\n"
                + "4 atoms\n"
                + "1 atom types\n"
                + "0.0 10.0 xlo xhi\n"
                + "0.0 10.0 ylo yhi\n"
                + "0.0 10.0 zlo zhi\n"
                + "\n"
                + "Masses\n"
                + "1 28.0855\n"
                + "\n"
                + "Atoms # charge\n"
                + "1 1 0.5 1.0 1.0 1.0\n"
                + "2 1 -0.5 2.0 2.0 2.0\n"
                + "3 1 0.5 3.0 3.0 3.0\n"
                + "4 1 -0.5 4.0 4.0 4.0\n"
                + "\n"
                + "Velocities\n"
                + "1 0.0 0.0 0.0\n"
                + "2 0.0 0.0 0.0\n"
                + "3 0.0 0.0 0.0\n"
                + "4 0.0 0.0 0.0\n");
        OperationResult<LammpsData> result = LammpsDataReader.parse(file, AtomStyle.CHARGE);
        assertTrue(result.isSuccess(), result.toString());
        LammpsData data = result.getValue().orElseThrow();
        assertEquals(4, data.getAtoms().size());
        assertEquals(-0.5, data.getAtoms().get(1).getCharge(), 0.0);
        assertEquals(1, data.getSkippedSections().size());
        assertEquals("Velocities", data.getSkippedSections().get(0));
        double charge = 0.0;
        for (int i = 0; i < 4; i++) {
            charge += data.getAtoms().get(i).getCharge();
        }
        assertEquals(0.0, charge, 1e-12, "the review can total the charges honestly");
    }

    @Test
    void outOfBoxAtomsAreCountedNotMoved() throws IOException {
        Path file = write("outside.data",
                "one atom outside\n"
                + "1 atoms\n"
                + "1 atom types\n"
                + "0.0 20.0 xlo xhi\n"
                + "0.0 20.0 ylo yhi\n"
                + "0.0 20.0 zlo zhi\n"
                + "Masses\n"
                + "1 1.0\n"
                + "Atoms\n"
                + "1 1 25.0 1.0 1.0\n");
        OperationResult<LammpsData> result = LammpsDataReader.parse(file, AtomStyle.ATOMIC);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals(1L, result.getValue().orElseThrow().getOutsideBoxCount(),
                "an out-of-box atom is reported, never wrapped");
    }

    @Test
    void malformedFilesFailClosedWithCodes() throws IOException {
        assertEquals("LAMMPS_STYLE", LammpsDataReader.parse(
                write("gjf.data", "x\n1 atoms\n1 atom types\n"
                        + "0 1 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 1 0.5 0.1 0.1 0.1\n"),
                AtomStyle.ATOMIC).getCode(), "6-column rows with ATOMIC style refuse");
        assertEquals("LAMMPS_STYLE", LammpsDataReader.parse(
                this.tempDir.resolve("nope.data"), null).getCode(),
                "the style is mandatory - the file never records it");
        assertEquals("LAMMPS_COUNT", LammpsDataReader.parse(
                write("mismatch.data", "title\n3 atoms\n1 atom types\n"
                        + "0 1 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 1 0.1 0.1 0.1\n2 1 0.2 0.2 0.2\n"),
                AtomStyle.ATOMIC).getCode(), "declared vs listed atom counts must agree");
        assertEquals("LAMMPS_VALUE", LammpsDataReader.parse(
                write("dup.data", "title\n2 atoms\n1 atom types\n"
                        + "0 1 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 1 0.1 0.1 0.1\n1 1 0.2 0.2 0.2\n"),
                AtomStyle.ATOMIC).getCode(), "duplicate ids refuse");
        assertEquals("LAMMPS_VALUE", LammpsDataReader.parse(
                write("type.data", "title\n1 atoms\n1 atom types\n"
                        + "0 1 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 3 0.1 0.1 0.1\n"),
                AtomStyle.ATOMIC).getCode(), "type 3 with 1 declared type refuses");
        assertEquals("LAMMPS_COUNT", LammpsDataReader.parse(
                write("mass.data", "title\n1 atoms\n2 atom types\n"
                        + "0 1 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 1 0.1 0.1 0.1\n"),
                AtomStyle.ATOMIC).getCode(), "Masses must cover all declared types");
        assertEquals("LAMMPS_EMPTY", LammpsDataReader.parse(
                write("none.data", "title\nAtoms\n1 1 0.1 0.1 0.1\n"),
                AtomStyle.ATOMIC).getCode());
        assertEquals("LAMMPS_VALUE", LammpsDataReader.parse(
                write("box.data", "title\n1 atoms\n1 atom types\n"
                        + "1 0 xlo xhi\n0 1 ylo yhi\n0 1 zlo zhi\nMasses\n1 1.0\nAtoms\n"
                        + "1 1 0.1 0.1 0.1\n"),
                AtomStyle.ATOMIC).getCode(), "xhi <= xlo refuses");
        assertEquals("LAMMPS_IO", LammpsDataReader.parse(
                this.tempDir.resolve("missing.data"), AtomStyle.ATOMIC).getCode());
    }
}
