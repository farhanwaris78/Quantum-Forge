package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class SpglibProtocolV2Test {

    @Test
    void buildRequestIncludesOpAndProtocol2() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        assertTrue(cell.addAtom(new Atom("Si", 0, 0, 0)));
        String json = SpglibService.buildRequest(cell, 1.0e-5, "standardize_primitive");
        assertTrue(json.contains("\"protocol\":\"2\"") || json.contains("\"protocol\": \"2\"")
                || json.contains("\"protocol\":\"2\"") || json.contains("protocol\":\"2"));
        assertTrue(json.contains("standardize_primitive"));
        assertTrue(json.contains("\"lattice\""));
    }

    @Test
    void unavailableServiceFailsClosedForStandardizeAndSeekpath() throws Exception {
        SpglibService service = new SpglibService(null, java.nio.file.Path.of("missing.py"));
        Cell cell = new Cell(Matrix3D.unit(5.0));
        assertTrue(cell.addAtom(new Atom("Si", 0, 0, 0)));
        OperationResult<StandardizedCell> std = service.standardize(cell, 1.0e-5, true);
        assertFalse(std.isSuccess());
        OperationResult<SeekPathResult> path = service.seekPath(cell, 1.0e-5);
        assertFalse(path.isSuccess());
    }
}
