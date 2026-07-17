package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEPhono3pyKappaParserTest {

    @Test
    void testParserProcessesCommensurateThermalConductivities() throws IOException {
        File tempFile = File.createTempFile("phono3py-kappa", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("  Lattice Thermal Conductivity Tensors:\n");
            writer.write("  Temp (K)     kappa_xx     kappa_yy     kappa_zz\n");
            // Stable 1/T scaling:
            // 300 K -> kappa = 30.0 W/m-K (300 * 30 = 9000)
            writer.write("   300.00       30.0000      30.0000      30.0000\n");
            // 600 K -> kappa = 15.0 W/m-K (600 * 15 = 9000) -> perfect 1/T scaling!
            writer.write("   600.00       15.0000      15.0000      15.0000\n");
            writer.write("   900.00       10.0000      10.0000      10.0000\n");
            writer.write("\n"); // End of table
        }

        ProjectProperty property = new ProjectProperty();
        QEPhono3pyKappaParser parser = new QEPhono3pyKappaParser(property);
        parser.parse(tempFile);

        List<QEPhono3pyKappaParser.ThermalConductivityPoint> data = parser.getKappaData();
        assertNotNull(data);
        assertEquals(3, data.size(), "Should parse exactly 3 data points");

        // First Point assertions
        assertEquals(300.0, data.get(0).getTemperatureK(), 1e-6);
        assertEquals(30.0000, data.get(0).getKappaXx(), 1e-6);
        assertEquals(30.0000, data.get(0).getIsotropicKappa(), 1e-6);

        // Third Point assertions
        assertEquals(900.0, data.get(2).getTemperatureK(), 1e-6);

        // Verification checks
        assertTrue(parser.isPhysicalScaling(), "Perfect 1/T scaling (300*30 = 600*15) must pass anharmonic transport checks");
        assertTrue(parser.getDiagnostics().get(0).contains("completed"));
        assertTrue(parser.getDiagnostics().get(2).contains("verified"));
    }

    @Test
    void testParserDetectsAnharmonicViolationForUnstableTransport() throws IOException {
        File tempFile = File.createTempFile("phono3py-kappa-fail", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("  Temp (K)     kappa_xx     kappa_yy     kappa_zz\n");
            writer.write("   300.00       30.0000      30.0000      30.0000\n");
            // Unstable: thermal conductivity increases with temperature (unphysical!)
            writer.write("   600.00       50.0000      50.0000      50.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEPhono3pyKappaParser parser = new QEPhono3pyKappaParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isPhysicalScaling(), "Increasing thermal conductivity with temperature must fail stability checks");
        assertTrue(parser.getDiagnostics().get(1).contains("Warning"));
    }
}
