package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.symmetry.QEBrillouinZoneGeometry.BzReport;

/**
 * Batch-45 coverage for the reciprocal Wigner-Seitz zone construction
 * (Roadmap #126). Counts reference the analytic zone of each lattice family.
 */
class QEBrillouinZoneGeometryTest {

    private static final double TWO_PI = 2.0 * Math.PI;

    @Test
    void testCubicZoneIsACube() {
        OperationResult<BzReport> result = QEBrillouinZoneGeometry.compute(
                new double[][] {{10, 0, 0}, {0, 10, 0}, {0, 0, 10}});
        assertTrue(result.isSuccess(), result.getMessage());
        BzReport report = result.getValue().orElseThrow();
        assertEquals(8, report.getVertexCount(), "A cubic zone is a cube");
        assertEquals(6, report.getFaceCount());
        assertEquals(12, report.getEdgeCount());
        assertEquals(Math.pow(TWO_PI, 3.0) / 1000.0, report.getVolumeInvAng3(), 1.0e-9);
        assertEquals(Math.pow(TWO_PI / 10.0, 3.0), report.getVolumeInvAng3(), 1.0e-9,
                "The cube edge is pi/5 Ang^-1");
        assertTrue(report.getVolumeRelativeDeviation() < 1.0e-9,
                "Volume identity deviation " + report.getVolumeRelativeDeviation());
        assertTrue(report.isConsistent());
    }

    @Test
    void testTetragonalZoneIsARectangularBox() {
        OperationResult<BzReport> result = QEBrillouinZoneGeometry.compute(
                new double[][] {{10, 0, 0}, {0, 10, 0}, {0, 0, 15}});
        assertTrue(result.isSuccess(), result.getMessage());
        BzReport report = result.getValue().orElseThrow();
        assertEquals(8, report.getVertexCount());
        assertEquals(6, report.getFaceCount());
        assertEquals(12, report.getEdgeCount());
        assertEquals(Math.pow(TWO_PI, 3.0) / 1500.0, report.getVolumeInvAng3(), 1.0e-9);
        assertTrue(report.isConsistent());
    }

    @Test
    void testFccZoneIsATruncatedOctahedron() {
        // Conventional FCC cell (a=4): the zone is the bcc Wigner-Seitz cell.
        OperationResult<BzReport> result = QEBrillouinZoneGeometry.compute(
                new double[][] {{2, 2, 0}, {2, 0, 2}, {0, 2, 2}});
        assertTrue(result.isSuccess(), result.getMessage());
        BzReport report = result.getValue().orElseThrow();
        assertEquals(24, report.getVertexCount(), "Truncated octahedron vertex count");
        assertEquals(14, report.getFaceCount(), "6 square + 8 hexagon faces");
        assertEquals(36, report.getEdgeCount());
        assertEquals(Math.pow(TWO_PI, 3.0) / 16.0, report.getVolumeInvAng3(), 1.0e-8);
        assertTrue(report.getVolumeRelativeDeviation() < 1.0e-9);
        assertTrue(report.isConsistent());
    }

    @Test
    void testTriclinicZonePassesAllChecks() {
        OperationResult<BzReport> result = QEBrillouinZoneGeometry.compute(
                new double[][] {{6, 0, 0}, {1, 7, 0}, {0.5, 1.5, 8}});
        assertTrue(result.isSuccess(), result.getMessage());
        BzReport report = result.getValue().orElseThrow();
        assertEquals(24, report.getVertexCount());
        assertEquals(14, report.getFaceCount());
        assertEquals(36, report.getEdgeCount());
        assertEquals(Math.pow(TWO_PI, 3.0) / (6.0 * 7.0 * 8.0 - 0.0 * 0.0),
                report.getExpectedVolumeInvAng3(), 1.0e-9);
        assertTrue(report.isConsistent(), report.getNotes().toString());
    }

    @Test
    void testDegenerateLatticeFailsClosed() {
        OperationResult<BzReport> collinear = QEBrillouinZoneGeometry.compute(
                new double[][] {{1, 0, 0}, {2, 0, 0}, {0, 0, 5}});
        assertFalse(collinear.isSuccess());
        assertEquals("BZ_LATTICE_DEGENERATE", collinear.getCode());

        OperationResult<BzReport> nan = QEBrillouinZoneGeometry.compute(
                new double[][] {{1, 0, 0}, {0, Double.NaN, 0}, {0, 0, 5}});
        assertFalse(nan.isSuccess());
        assertEquals("BZ_LATTICE_NONFINITE", nan.getCode());

        OperationResult<BzReport> shape = QEBrillouinZoneGeometry.compute(
                new double[][] {{1, 0, 0}, {0, 1, 0}});
        assertFalse(shape.isSuccess());
        assertEquals("BZ_LATTICE_SHAPE", shape.getCode());
    }

    @Test
    void testVerticesAreSortedAndInsideZone() {
        OperationResult<BzReport> result = QEBrillouinZoneGeometry.compute(
                new double[][] {{10, 0, 0}, {0, 10, 0}, {0, 0, 10}});
        BzReport report = result.getValue().orElseThrow();
        double limit = Math.PI / 10.0 + 1.0e-9;
        double previous = Double.NEGATIVE_INFINITY;
        for (double[] vertex : report.getVertices()) {
            for (double component : vertex) {
                assertTrue(Math.abs(component) <= limit,
                        "Cube vertices lie within +/- pi/a halfplanes");
            }
            assertTrue(vertex[0] >= previous, "Vertices are sorted by x for deterministic CSV");
            previous = vertex[0];
        }
    }
}
