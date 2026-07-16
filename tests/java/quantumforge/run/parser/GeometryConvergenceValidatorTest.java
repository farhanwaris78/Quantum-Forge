package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;

class GeometryConvergenceValidatorTest {

    @Test
    void optimisedWhenBfgsMarkerPresent() throws Exception {
        String log = Files.readString(
                Path.of("tests/fixtures/qe/relax_converged.log"), StandardCharsets.UTF_8);
        ProjectGeometryList list = new ProjectGeometryList();
        ProjectGeometry geometry = new ProjectGeometry();
        geometry.setTotalForce(0.0008);
        geometry.setConverged(true);
        list.addGeometry(geometry);
        list.setConverged(true);

        GeometryConvergenceValidator.Result result =
                GeometryConvergenceValidator.validate(log, list, 1.0e-3, null);
        assertTrue(result.isOptimized());
        assertTrue(result.isBfgsEndMarker());
        assertEquals(GeometryConvergenceValidator.Status.OPTIMIZED, result.getStatus());
    }

    @Test
    void notOptimisedWithoutMarkers() {
        ProjectGeometryList list = new ProjectGeometryList();
        ProjectGeometry geometry = new ProjectGeometry();
        geometry.setTotalForce(0.05);
        list.addGeometry(geometry);

        GeometryConvergenceValidator.Result result =
                GeometryConvergenceValidator.validate("still running", list, 1.0e-3, null);
        assertFalse(result.isOptimized());
        assertEquals(GeometryConvergenceValidator.Status.NOT_OPTIMIZED, result.getStatus());
    }
}
