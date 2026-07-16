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

class QEGipawNmrParserTest {

    @Test
    void testParserProcessesGipawMagneticShieldingTensors() throws IOException {
        File tempFile = File.createTempFile("gipaw-nmr-out", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Magnetic shielding tensors:\n");
            // Carbon-13 shielding (isotropic 54.2 ppm -> shift relative to 184.2 TMS is 130.0 ppm)
            writer.write("     Atom      1  C  Isotropic:       54.2000   Anisotropy:       35.1000   Asymmetry:        0.1200\n");
            // Silicon-29 shielding (isotropic 350.1 ppm -> shift relative to 320.1 TMS is -30.0 ppm)
            writer.write("     Atom      2  Si Isotropic:      350.1000   Anisotropy:      120.0000   Asymmetry:        0.0500\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEGipawNmrParser parser = new QEGipawNmrParser(property);
        parser.parse(tempFile);

        List<QEGipawNmrParser.NmrShielding> shieldings = parser.getShieldings();
        assertNotNull(shieldings);
        assertEquals(2, shieldings.size(), "Should parse exactly 2 shielding records");

        // Carbon assertions
        assertEquals(1, shieldings.get(0).getAtomIndex());
        assertEquals("C", shieldings.get(0).getElement());
        assertEquals(54.2000, shieldings.get(0).getIsotropicPpm(), 1e-6);
        assertEquals(35.1000, shieldings.get(0).getAnisotropyPpm(), 1e-6);
        assertEquals(0.1200, shieldings.get(0).getAsymmetry(), 1e-6);

        // Standard shift: TMS reference (184.2) - Isotropic (54.2) = 130.0 ppm
        assertEquals(130.0, shieldings.get(0).getStandardChemicalShift(), 1e-6);

        // Silicon assertions
        assertEquals(2, shieldings.get(1).getAtomIndex());
        assertEquals("Si", shieldings.get(1).getElement());
        // Standard shift: TMS reference (320.1) - Isotropic (350.1) = -30.0 ppm
        assertEquals(-30.0, shieldings.get(1).getStandardChemicalShift(), 1e-6);
    }
}
