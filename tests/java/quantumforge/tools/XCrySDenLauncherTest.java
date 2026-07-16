package quantumforge.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class XCrySDenLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsValidXsfForSimpleCell() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        assertTrue(cell.addAtom(new Atom("Si", 0.0, 0.0, 0.0)));
        assertTrue(cell.addAtom(new Atom("Si", 1.25, 1.25, 1.25)));

        Path xsf = tempDir.resolve("si.xsf");
        OperationResult<Path> result = XCrySDenLauncher.exportXsf(cell, xsf);
        assertTrue(result.isSuccess(), result.toString());
        String text = Files.readString(xsf, StandardCharsets.UTF_8);
        assertTrue(text.contains("CRYSTAL"));
        assertTrue(text.contains("PRIMVEC"));
        assertTrue(text.contains("PRIMCOORD"));
        assertTrue(text.contains("14 ")); // Si atomic number
    }

    @Test
    void launchWithoutBinaryIsUnsupportedOrFailedCleanly() throws ZeroVolumCellException {
        Cell cell = new Cell(Matrix3D.unit());
        assertTrue(cell.addAtom(new Atom("C", 0, 0, 0)));
        OperationResult<Process> result = XCrySDenLauncher.launch(cell);
        // In CI/sandbox without xcrysden this must not pretend success.
        if (XCrySDenLauncher.findExecutable() == null) {
            assertFalse(result.isSuccess());
            assertTrue(result.getCode().contains("XCRYSDEN") || result.getMessage().toLowerCase().contains("xcrysden"));
        }
    }
}
