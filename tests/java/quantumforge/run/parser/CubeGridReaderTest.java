package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import quantumforge.operation.OperationResult;

class CubeGridReaderTest {
    @Test
    void readsBoundedCubeAndConvertsBohrAxes() throws Exception {
        Path file = Files.createTempFile("qf", ".cube");
        Files.writeString(file, "comment\ncomment\n1 0 0 0\n2 1 0 0\n1 0 1 0\n1 0 0 1\n1 0 0 0 0\n1.0 2.0\n");
        OperationResult<QEGridDensityDifference.Grid3D> result = CubeGridReader.read(file, 10);
        assertTrue(result.isSuccess(), result.toString());
        QEGridDensityDifference.Grid3D grid = result.getValue().orElseThrow();
        assertEquals(2, grid.getNx());
        assertEquals(1.0, grid.getValues()[0][0][0], 1e-12);
        assertEquals(2.0, grid.getValues()[1][0][0], 1e-12);
        assertEquals(2.0 * 0.529177210903, grid.getLattice()[0][0], 1e-12);
    }

    @Test
    void rejectsTruncatedAndOversizedCube() throws Exception {
        Path file = Files.createTempFile("qf-short", ".cube");
        Files.writeString(file, "a\nb\n0 0 0 0\n2 1 0 0\n1 0 1 0\n1 0 0 1\n1\n");
        assertFalse(CubeGridReader.read(file, 10).isSuccess());
        assertFalse(CubeGridReader.read(file, 1).isSuccess());
    }
}
