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

class QEPwcondConductanceParserTest {

    @Test
    void testParserProcessesBallisticTransmissionAndCalculatesSIConductance() throws IOException {
        File tempFile = File.createTempFile("pwcond-conductance", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Energy (eV)     Transmission (T)\n");
            writer.write("        -1.2000               1.5000\n");
            writer.write("        -0.5000               2.0000\n");
            writer.write("         0.0000               3.0000\n");
            writer.write("\n"); // End of section
        }

        ProjectProperty property = new ProjectProperty();
        QEPwcondConductanceParser parser = new QEPwcondConductanceParser(property);
        parser.parse(tempFile);

        List<QEPwcondConductanceParser.ConductancePoint> points = parser.getConductancePoints();
        assertNotNull(points);
        assertEquals(3, points.size(), "Should parse exactly 3 conductance points");

        // First Point assertions
        assertEquals(-1.2000, points.get(0).getEnergyEv(), 1e-6);
        assertEquals(1.5000, points.get(0).getTransmission(), 1e-6);
        assertEquals(1.5000, points.get(0).getConductanceInG0(), 1e-6);
        // SI: 1.5 * 7.74809172e-5 = 1.162213758e-4 S
        assertEquals(1.162213758e-4, points.get(0).getConductanceSI(), 1e-9);

        // Third Point assertions
        assertEquals(3.0000, points.get(2).getTransmission(), 1e-6);
        assertEquals(3.0000 * 7.74809172e-5, points.get(2).getConductanceSI(), 1e-9);
    }
}
