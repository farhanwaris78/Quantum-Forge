package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEVasprunXmlParserTest {

    @Test
    void testParserProcessesSecureVaspXmlFile() throws IOException {
        File tempFile = File.createTempFile("vasprun", ".xml");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            writer.write("<modeling>\n");
            writer.write("  <calculation>\n");
            writer.write("    <scstep>\n");
            writer.write("      <energy>\n");
            writer.write("        <i name=\"e_fr_energy\"> -12.43500000 </i>\n");
            writer.write("      </energy>\n");
            writer.write("    </scstep>\n");
            writer.write("    <dos>\n");
            writer.write("      <i name=\"efermi\"> 4.32100000 </i>\n");
            writer.write("    </dos>\n");
            writer.write("  </calculation>\n");
            writer.write("  <structure>\n");
            writer.write("    <crystal>\n");
            writer.write("      <varray name=\"basis\">\n");
            writer.write("        <v>   5.43000   0.00000   0.00000 </v>\n");
            writer.write("        <v>   0.00000   5.43000   0.00000 </v>\n");
            writer.write("        <v>   0.00000   0.00000   5.43000 </v>\n");
            writer.write("      </varray>\n");
            writer.write("    </crystal>\n");
            writer.write("    <varray name=\"positions\">\n");
            writer.write("      <v>   0.00000   0.00000   0.00000 </v>\n");
            writer.write("      <v>   0.25000   0.25000   0.25000 </v>\n");
            writer.write("    </varray>\n");
            writer.write("  </structure>\n");
            writer.write("</modeling>\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEVasprunXmlParser parser = new QEVasprunXmlParser(property);
        parser.parse(tempFile);

        QEVasprunXmlParser.VasprunResults results = parser.getResults();
        assertNotNull(results);

        assertEquals(-12.4350, results.getTotalEnergyEv(), 1e-6);
        assertEquals(4.3210, results.getFermiEnergyEv(), 1e-6);

        assertEquals(5.4300, results.getFinalLattice()[0][0], 1e-6);
        assertEquals(2, results.getFinalFractionalCoords().length);
        assertEquals(0.2500, results.getFinalFractionalCoords()[1][0], 1e-6);
    }

    @Test
    void refusesIncompleteOutputInsteadOfInventingZeroEnergyOrCoordinates() throws IOException {
        File tempFile = File.createTempFile("vasprun-incomplete", ".xml");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("<modeling><structure><varray name=\"basis\">"
                    + "<v>1 0 0</v><v>0 1 0</v><v>0 0 1</v>"
                    + "</varray></structure></modeling>");
        }
        QEVasprunXmlParser parser = new QEVasprunXmlParser(new ProjectProperty());
        assertThrows(IOException.class, () -> parser.parse(tempFile));
        assertTrue(parser.getResults() == null);
    }
}
