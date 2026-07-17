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

class QEMagneticMomentParserTest {

    @Test
    void testParserProcessesCollinearMagnetization() throws IOException {
        File tempFile = File.createTempFile("pw-collinear-mag", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     total magnetization          =     1.2400 Bohr mag/cell\n");
            writer.write("     absolute magnetization       =     1.3400 Bohr mag/cell\n");
            writer.write("     atom    1 local magnetic moment:     1.8400\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEMagneticMomentParser parser = new QEMagneticMomentParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isNoncollinear());
        assertEquals(1.2400, parser.getTotalMagnetizationBohr(), 1e-6);
        assertEquals(1.3400, parser.getAbsoluteMagnetizationBohr(), 1e-6);
        
        List<QEMagneticMomentParser.AtomicMoment> moments = parser.getAtomicMoments();
        assertNotNull(moments);
        assertEquals(1, moments.size());
        assertEquals(1, moments.get(0).getAtomIndex());
        assertEquals(0.0, moments.get(0).getMx());
        assertEquals(1.8400, moments.get(0).getMz());
    }

    @Test
    void testParserProcessesNoncollinearMagnetization() throws IOException {
        File tempFile = File.createTempFile("pw-noncollinear-mag", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     total magnetization          = (   0.5000,   0.0000,   1.2000) Bohr mag/cell\n");
            writer.write("     atom    1 local magnetic moment:     0.1000    0.0000    1.5000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEMagneticMomentParser parser = new QEMagneticMomentParser(property);
        parser.parse(tempFile);

        assertTrue(parser.isNoncollinear());
        assertEquals(0.5000, parser.getTotalMagnetizationVector()[0], 1e-6);
        assertEquals(1.2000, parser.getTotalMagnetizationVector()[2], 1e-6);
        assertEquals(Math.sqrt(0.5*0.5 + 1.2*1.2), parser.getTotalMagnetizationBohr(), 1e-6);

        List<QEMagneticMomentParser.AtomicMoment> moments = parser.getAtomicMoments();
        assertNotNull(moments);
        assertEquals(1, moments.size());
        assertEquals(0.1000, moments.get(0).getMx());
        assertEquals(1.5000, moments.get(0).getMz());
        assertEquals(Math.sqrt(0.1*0.1 + 1.5*1.5), moments.get(0).getMagnitude(), 1e-6);
    }
}
