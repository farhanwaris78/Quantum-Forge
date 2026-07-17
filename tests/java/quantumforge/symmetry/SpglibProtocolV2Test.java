package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void testParseStandardizedSuccessfulResponse() throws Exception {
        String response = "{\n" +
                "  \"protocol\": \"2\",\n" +
                "  \"op\": \"standardize_primitive\",\n" +
                "  \"kind\": \"primitive\",\n" +
                "  \"lattice\": [[5.0, 0.0, 0.0], [0.0, 5.0, 0.0], [0.0, 0.0, 5.0]],\n" +
                "  \"positions\": [[0.0, 0.0, 0.0], [0.25, 0.25, 0.25]],\n" +
                "  \"numbers\": [14, 14],\n" +
                "  \"number\": 227,\n" +
                "  \"international\": \"Fd-3m\",\n" +
                "  \"spglib_version\": \"2.1.0\",\n" +
                "  \"tolerance\": 1e-5\n" +
                "}";

        OperationResult<StandardizedCell> result = SpglibService.parseStandardized(response, 1.0e-5, "primitive");
        assertTrue(result.isSuccess());
        StandardizedCell cell = result.getResult();
        assertNotNull(cell);
        assertEquals("primitive", cell.getKind());
        assertEquals(227, cell.getSpaceGroupNumber());
        assertEquals("Fd-3m", cell.getInternationalSymbol());
        assertEquals(2, cell.getSites().size());
        assertEquals(14, cell.getSites().get(0).getAtomicNumber());
        assertEquals(0.0, cell.getSites().get(0).getX());
        assertEquals(0.25, cell.getSites().get(1).getX());
        assertEquals(5.0, cell.getLattice()[0][0]);
    }

    @Test
    void testParseSeekPathSuccessfulResponse() throws Exception {
        String response = "{\n" +
                "  \"protocol\": \"2\",\n" +
                "  \"op\": \"seekpath\",\n" +
                "  \"path\": [[\"G\", [0.0, 0.0, 0.0]], [\"X\", [0.5, 0.0, 0.5]]],\n" +
                "  \"number\": 227,\n" +
                "  \"international\": \"Fd-3m\",\n" +
                "  \"seekpath_version\": \"2.0.1\",\n" +
                "  \"spglib_version\": \"2.1.0\",\n" +
                "  \"tolerance\": 1e-5\n" +
                "}";

        OperationResult<SeekPathResult> result = SpglibService.parseSeekPath(response, 1.0e-5);
        assertTrue(result.isSuccess());
        SeekPathResult seekPath = result.getResult();
        assertNotNull(seekPath);
        assertEquals(227, seekPath.getSpaceGroupNumber());
        assertEquals("Fd-3m", seekPath.getSpaceGroupInternational());
        assertEquals(2, seekPath.getPath().size());
        assertEquals("G", seekPath.getPath().get(0).getLabel());
        assertEquals(0.5, seekPath.getPath().get(1).getKx());
        assertEquals(0.0, seekPath.getPath().get(1).getKy());
        assertEquals(0.5, seekPath.getPath().get(1).getKz());
    }
}
