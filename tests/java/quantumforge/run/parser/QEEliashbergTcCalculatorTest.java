package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEEliashbergTcCalculatorTest {

    @Test
    void testParserProcessesEliashbergSpectralFunctionAndComputesTc() throws IOException {
        File tempFile = File.createTempFile("epw-a2F", ".dat");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("# frequency (cm-1)   alpha2F(w)\n");
            writer.write("   50.0000000         0.2000000\n");
            writer.write("  100.0000000         0.5000000\n");
            writer.write("  150.0000000         0.8000000\n");
            writer.write("  200.0000000         0.4000000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEEliashbergTcCalculator parser = new QEEliashbergTcCalculator(property);
        parser.parse(tempFile);

        assertEquals(4, parser.getSpectralFunction().size());
        assertEquals(50.0, parser.getSpectralFunction().get(0).frequencyCm1, 1e-6);
        assertEquals(0.8, parser.getSpectralFunction().get(2).alpha2F, 1e-6);

        // Perform Allen-Dynes Tc integration with Coulomb pseudopotential mu* = 0.10
        QEEliashbergTcCalculator.TcResult result = parser.calculateTc(0.10);
        assertNotNull(result);

        // Assert that lambda is physically computed and positive
        assertTrue(result.getLambda() > 0.5, "Lambda should be integrated and positive");
        assertTrue(result.getOmegaLogCm1() > 50.0 && result.getOmegaLogCm1() < 200.0, "w_log must lie within the physical frequency spectrum bounds");
        assertTrue(result.getTcKelvin() > 0.0, "Superconducting transition temperature Tc must be physically positive");

        String report = result.getSummary();
        assertNotNull(report);
        assertTrue(report.contains("Superconductivity"));
    }
}
