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

class QECastepLogParserTest {

    @Test
    void testParserProcessesCastepLogSuccessfully() throws IOException {
        File tempFile = File.createTempFile("castep-run", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     -------------------------------------------------------------------------\n");
            writer.write("     Final energy, E             =  -1234.567890 eV\n");
            writer.write("     Fermi energy                =   4.321000 eV\n");
            writer.write("     Geometry optimization completed successfully\n");
            writer.write("     -------------------------------------------------------------------------\n");
        }

        ProjectProperty property = new ProjectProperty();
        QECastepLogParser parser = new QECastepLogParser(property);
        parser.parse(tempFile);

        assertEquals(-1234.567890, parser.getFinalEnergyEv(), 1e-6);
        assertEquals(4.321000, parser.getFermiEnergyEv(), 1e-6);
        assertTrue(parser.isGeometryConverged());

        List<String> diagnostics = parser.getDiagnostics();
        assertNotNull(diagnostics);
        assertTrue(diagnostics.size() >= 3);
        assertTrue(diagnostics.get(0).contains("Final energy"));
        assertTrue(diagnostics.get(2).contains("completed successfully"));
    }
}
