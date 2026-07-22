package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QESmearingConvergenceAnalyzerTest {

    @Test
    void testParserProcessesEntropyAndTotalEnergy() throws IOException {
        File tempFile = File.createTempFile("pw-smearing-out", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     total energy              =   -12.703512 Ry\n");
            writer.write("     smearing contrib. (-TS)   =    -0.004500 Ry\n");
            writer.write("     total free energy         =   -12.708012 Ry\n");
        }

        ProjectProperty property = new ProjectProperty();
        QESmearingConvergenceAnalyzer analyzer = new QESmearingConvergenceAnalyzer(property);
        analyzer.parse(tempFile);

        assertTrue(analyzer.isSmearingFound());
        assertEquals(-0.0045, analyzer.getEntropyRy(), 1e-6);
        assertEquals(-12.703512, analyzer.getTotalEnergyRy(), 1e-6);
        assertEquals(-12.708012, analyzer.getFreeEnergyRy(), 1e-6);

        // System with 2 atoms -> entropy/atom = 0.00225 Ry (> 0.001 Ry limit)
        // Should trigger unphysical force bias warning!
        boolean safe = analyzer.verifySmearingSafe(2, true);
        assertFalse(safe);
        assertTrue(analyzer.getDiagnostics().get(0).contains("exceeding"));

        // System with 10 atoms -> entropy/atom = 0.00045 Ry (< 0.001 Ry limit)
        // Should be marked as safe
        boolean safe10 = analyzer.verifySmearingSafe(10, true);
        assertTrue(safe10);
        assertTrue(analyzer.getDiagnostics().stream().anyMatch(d -> d.contains("optimized")),
                "the safe verdict lands in the cumulative diagnostics: "
                        + analyzer.getDiagnostics());
    }

    @Test
    void testMetalConvergenceWarningForZeroDegauss() throws IOException {
        File tempFile = File.createTempFile("pw-smearing-out-zero", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     total energy              =   -12.703512 Ry\n");
            writer.write("     demet                     =    -0.000000 Ry\n");
        }

        ProjectProperty property = new ProjectProperty();
        QESmearingConvergenceAnalyzer analyzer = new QESmearingConvergenceAnalyzer(property);
        analyzer.parse(tempFile);

        assertTrue(analyzer.isSmearingFound());
        assertEquals(0.0, analyzer.getEntropyRy(), 1e-6);

        // Metallic system with 1 atom and zero entropy correction should trigger a warning
        // that degauss is too small, risking convergence failure!
        boolean safe = analyzer.verifySmearingSafe(1, true);
        assertFalse(safe);
        assertTrue(analyzer.getDiagnostics().get(0).contains("near zero"));
    }
}
