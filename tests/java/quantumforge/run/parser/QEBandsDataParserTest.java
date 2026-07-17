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

class QEBandsDataParserTest {

    @Test
    void testParserGroupsBandsAndShiftsEnergiesByFermiReference() throws IOException {
        File tempFile = File.createTempFile("qe-bands-gnu", ".dat");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            // First Band
            writer.write("  0.0000000   -4.5000\n");
            writer.write("  0.1000000   -4.2000\n");
            writer.write("  0.2000000   -4.0000\n");
            writer.write("\n"); // Blank line separates bands
            // Second Band
            writer.write("  0.0000000    1.5000\n");
            writer.write("  0.1000000    1.8000\n");
            writer.write("  0.2000000    2.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEBandsDataParser parser = new QEBandsDataParser(property);
        
        // Let's set Fermi Energy to -1.0 eV.
        // First band shifted: -4.5 - (-1.0) = -3.5 eV
        // Second band shifted: 1.5 - (-1.0) = 2.5 eV
        parser.parseWithFermi(tempFile, -1.0);

        List<QEBandsDataParser.Band> bands = parser.getBands();
        assertNotNull(bands);
        assertEquals(2, bands.size(), "Should parse exactly 2 bands");

        // First Band assertions
        assertEquals(3, bands.get(0).size());
        assertEquals(0.1, bands.get(0).getKDistance()[1], 1e-6);
        assertEquals(-3.5, bands.get(0).getEnergyEv()[0], 1e-6);

        // Second Band assertions
        assertEquals(3, bands.get(1).size());
        assertEquals(2.5, bands.get(1).getEnergyEv()[0], 1e-6);
    }
}
