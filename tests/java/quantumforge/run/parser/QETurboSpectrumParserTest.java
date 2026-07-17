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

class QETurboSpectrumParserTest {

    @Test
    void testParserProcessesComplexPolarizabilitiesAndCalculatesAbsorption() throws IOException {
        File tempFile = File.createTempFile("turbo-spectrum", ".dat");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("# energy(eV)    Re(xx)      Im(xx)      Re(yy)      Im(yy)      Re(zz)      Im(zz)\n");
            // Resonance at 1.5 eV
            writer.write("   1.500000     0.10000     1.50000     0.10000     1.50000     0.10000     1.50000\n");
            // Off-resonance at 2.0 eV
            writer.write("   2.000000     0.05000     0.20000     0.05000     0.20000     0.05000     0.20000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QETurboSpectrumParser parser = new QETurboSpectrumParser(property);
        parser.parse(tempFile);

        List<QETurboSpectrumParser.SpectrumPoint> points = parser.getSpectrumPoints();
        assertNotNull(points);
        assertEquals(2, points.size(), "Should parse exactly 2 spectrum points");

        // First Point assertions
        QETurboSpectrumParser.SpectrumPoint pt1 = points.get(0);
        assertEquals(1.5, pt1.getEnergyEv(), 1e-6);
        assertEquals(0.1, pt1.getReAlphaXx(), 1e-6);
        assertEquals(1.5, pt1.getImAlphaXx(), 1e-6);

        // Isotropic Absorption = Energy (1.5) * ImAvg (1.5) = 2.25
        assertEquals(2.25, pt1.getIsotropicAbsorption(), 1e-6);

        // Second Point assertions
        QETurboSpectrumParser.SpectrumPoint pt2 = points.get(1);
        assertEquals(2.0, pt2.getEnergyEv(), 1e-6);
        // Isotropic Absorption = Energy (2.0) * ImAvg (0.2) = 0.40
        assertEquals(0.40, pt2.getIsotropicAbsorption(), 1e-6);
    }
}
