package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class QEKpointMeshAdvisorTest {

    private static final double[][] CUBIC_10 = {{10.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 10.0}};

    @Test
    void cubicCellSpacingMatchesExactReciprocalDensity() {
        OperationResult<QEKpointMeshAdvisor.MeshReport> result =
                QEKpointMeshAdvisor.assess(CUBIC_10, new int[] {4, 4, 4}, new int[] {0, 0, 0});
        assertTrue(result.isSuccess(), result.toString());
        QEKpointMeshAdvisor.MeshReport report = result.getValue().orElseThrow();

        // |b_i| = 2*pi/10 = 0.6283185 Ang^-1; spacing = |b_i|/4 = 0.1570796 Ang^-1.
        QEKpointMeshAdvisor.DirectionReport direction = report.getDirections().get(0);
        assertEquals(0.6283185, direction.getReciprocalNormInvAng(), 1.0e-6);
        assertEquals(Math.PI / 20.0, direction.getSpacingInvAng(), 1.0e-9);
        assertEquals(20.0 / Math.PI, direction.getRangeAng(), 1.0e-9);
        assertEquals(QEKpointMeshAdvisor.MeshQuality.COARSE, direction.getQuality());
        assertEquals(QEKpointMeshAdvisor.MeshQuality.COARSE, report.getOverallQuality());
        assertEquals(64, report.getTotalGridPoints());
        assertTrue(report.getNotes().get(0).contains("Gamma-centred"));
    }

    @Test
    void classifiedRecommendedAndAccurateAtQeSchoolHeuristics() {
        // n=8: range = 8*10/(2*pi) = 12.73 Ang -> RECOMMENDED (>= 12).
        QEKpointMeshAdvisor.MeshReport eight = QEKpointMeshAdvisor
                .assess(CUBIC_10, new int[] {8, 8, 8}, new int[] {0, 0, 0}).getValue().orElseThrow();
        assertEquals(QEKpointMeshAdvisor.MeshQuality.RECOMMENDED, eight.getOverallQuality());

        // n=20: range = 31.83 Ang -> ACCURATE (>= 24).
        QEKpointMeshAdvisor.MeshReport twenty = QEKpointMeshAdvisor
                .assess(CUBIC_10, new int[] {20, 20, 20}, new int[] {0, 0, 0}).getValue().orElseThrow();
        assertEquals(QEKpointMeshAdvisor.MeshQuality.ACCURATE, twenty.getOverallQuality());
    }

    @Test
    void orthorhombicPerDirectionSpacings() {
        double[][] lattice = {{8.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 12.0}};
        OperationResult<QEKpointMeshAdvisor.MeshReport> result =
                QEKpointMeshAdvisor.assess(lattice, new int[] {4, 5, 6}, new int[] {0, 1, 0});
        assertTrue(result.isSuccess(), result.toString());
        QEKpointMeshAdvisor.MeshReport report = result.getValue().orElseThrow();

        assertEquals((2.0 * Math.PI / 8.0) / 4.0, report.getDirections().get(0).getSpacingInvAng(),
                1.0e-9);
        assertEquals((2.0 * Math.PI / 10.0) / 5.0, report.getDirections().get(1).getSpacingInvAng(),
                1.0e-9);
        assertEquals((2.0 * Math.PI / 12.0) / 6.0, report.getDirections().get(2).getSpacingInvAng(),
                1.0e-9);
        assertEquals(120, report.getTotalGridPoints());
        assertEquals(QEKpointMeshAdvisor.MeshQuality.COARSE, report.getOverallQuality());
        assertTrue(report.getNotes().get(0).contains("shifted"),
                "Offset 0 1 0 must be reported as a shifted mesh");
    }

    @Test
    void failsClosedOnDegenerateInputs() {
        assertFalse(QEKpointMeshAdvisor.assess(CUBIC_10, new int[] {0, 4, 4}, new int[] {0, 0, 0})
                .isSuccess(), "A zero division must be rejected");
        assertFalse(QEKpointMeshAdvisor.assess(CUBIC_10, new int[] {4, 4, 4}, new int[] {2, 0, 0})
                .isSuccess(), "QE shifts are exactly 0 or 1");
        double[][] degenerate = {{10.0, 0.0, 0.0}, {10.0, 0.0, 0.0}, {0.0, 0.0, 10.0}};
        assertFalse(QEKpointMeshAdvisor.assess(degenerate, new int[] {4, 4, 4}, new int[] {0, 0, 0})
                .isSuccess(), "A zero-volume lattice must be rejected");
        double[][] nan = {{Double.NaN, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 10.0}};
        assertFalse(QEKpointMeshAdvisor.assess(nan, new int[] {4, 4, 4}, new int[] {0, 0, 0})
                .isSuccess(), "Non-finite lattice components must be rejected");
        assertFalse(QEKpointMeshAdvisor.assess(null, new int[] {4, 4, 4}, new int[] {0, 0, 0})
                .isSuccess());
    }
}
