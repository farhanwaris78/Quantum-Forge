package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QELammpsThermoParserTest {

    @Test
    void testParserProcessesLammpsThermodynamicBlocks() throws IOException {
        File tempFile = File.createTempFile("lammps-thermo", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("Memory usage per processor = 2.45 Mbytes\n");
            writer.write("Step Temp Press PotEng KinEng TotEng\n");
            writer.write("   0  300.0  1.0  -150.0  15.0  -135.0\n");
            writer.write(" 100  310.0  2.0  -152.0  16.0  -136.0\n");
            writer.write(" 200  290.0  0.0  -148.0  14.0  -134.0\n");
            writer.write("Loop time of 12.345s on 4 procs\n");
        }

        ProjectProperty property = new ProjectProperty();
        QELammpsThermoParser parser = new QELammpsThermoParser(property);
        parser.parse(tempFile);

        List<QELammpsThermoParser.ThermoStep> steps = parser.getSteps();
        assertNotNull(steps);
        assertEquals(3, steps.size(), "Should parse exactly 3 steps");

        // First step assertions
        assertEquals(0, steps.get(0).getStep());
        assertEquals(300.0, steps.get(0).getTemperatureK(), 1e-6);
        assertEquals(1.0, steps.get(0).getPressureBar(), 1e-6);
        assertEquals(-150.0, steps.get(0).getPotentialEnergyEv(), 1e-6);
        assertEquals(15.0, steps.get(0).getKineticEnergyEv(), 1e-6);
        assertEquals(-135.0, steps.get(0).getTotalEnergyEv(), 1e-6);

        // Third step assertions
        assertEquals(200, steps.get(2).getStep());
        assertEquals(290.0, steps.get(2).getTemperatureK(), 1e-6);

        // Statistical Diagnostics verification
        List<String> diagnostics = parser.getDiagnostics();
        assertNotNull(diagnostics);
        assertTrue(diagnostics.size() >= 3);

        // Averages: Temp = (300+310+290)/3 = 300 K
        assertTrue(diagnostics.get(1).contains("Temp = 300.00 K"));
        // Standard deviation of Temp = sqrt( ((300-300)^2 + (310-300)^2 + (290-300)^2)/3 ) = sqrt( (0 + 100 + 100)/3 ) = sqrt(66.6667) = 8.16 K
        assertTrue(diagnostics.get(2).contains("Temp = 8.16 K"));
    }
}
