package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class SpglibServiceTest {

    @Test
    void unavailableSidecarDoesNotInventSpaceGroup() throws Exception {
        SpglibService service = new SpglibService(null, java.nio.file.Path.of("missing.py"));
        Cell cell = new Cell(Matrix3D.unit(5.0));
        assertTrue(cell.addAtom(new Atom("Si", 0, 0, 0)));
        OperationResult<SpglibService.Dataset> result = service.getDataset(cell, 1.0e-5);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().toLowerCase().contains("unavailable")
                || result.getCode().contains("UNAVAILABLE"));
    }

    @Test
    void requestJsonContainsLatticeAndNumbers() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        assertTrue(cell.addAtom(new Atom("Si", 0, 0, 0)));
        assertTrue(cell.addAtom(new Atom("Si", 1.25, 1.25, 1.25)));
        String json = SpglibService.buildRequest(cell, 1.0e-5);
        assertTrue(json.contains("\"op\":\"get_dataset\""));
        assertTrue(json.contains("\"lattice\""));
        assertTrue(json.contains("\"numbers\""));
        assertTrue(json.contains("14"));
    }
}
