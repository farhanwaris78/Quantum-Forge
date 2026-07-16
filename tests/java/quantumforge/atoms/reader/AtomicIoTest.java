package quantumforge.atoms.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Cell;
import quantumforge.export.AtomicExporter;

class AtomicIoTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsExtendedXyzAndExportsInteroperableFormats() throws Exception {
        Path xyz = temporaryDirectory.resolve("silicon.xyz");
        Files.writeString(xyz,
                "2\n"
                + "Lattice=\"5.43 0 0 0 5.43 0 0 0 5.43\"\n"
                + "Si 0.0 0.0 0.0\n"
                + "Si 1.3575 1.3575 1.3575\n");

        Cell cell;
        XYZReader reader = new XYZReader(xyz.toFile());
        try {
            cell = reader.readCell();
        } finally {
            reader.close();
        }

        assertNotNull(cell);
        assertEquals(2, cell.numAtoms(true));
        assertEquals(Math.pow(5.43, 3), cell.getVolume(), 1.0e-8);

        String exportedXyz = AtomicExporter.toXYZ(cell);
        String cif = AtomicExporter.toCIF(cell);
        String poscar = AtomicExporter.toPOSCAR(cell);
        String qe = AtomicExporter.toQEInput(cell);
        assertTrue(exportedXyz.startsWith("2\n"));
        assertTrue(cif.contains("_cell_length_a 5.430000"));
        assertTrue(poscar.contains("\n Si\n 2\nDirect\n"));
        assertTrue(qe.contains("ATOMIC_POSITIONS {angstrom}"));
        assertTrue(qe.contains("nat = 2"));
    }
}
