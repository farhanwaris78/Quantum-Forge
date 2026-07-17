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

class QEHubbardHpParserTest {

    @Test
    void testParserProcessesCalculatedHubbardUParameters() throws IOException {
        File tempFile = File.createTempFile("hp-hubbard-out", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Hubbard U for atom    1 (Fe, d) =    4.3000 eV\n");
            // Multiple atoms of same element will be averaged
            writer.write("     Hubbard U for atom    2 (Fe, d) =    4.5000 eV\n");
            writer.write("     Hubbard U for atom    3 (O, p)  =    0.0000 eV\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEHubbardHpParser parser = new QEHubbardHpParser(property);
        parser.parse(tempFile);

        List<QEHubbardHpParser.ParsedHubbardU> params = parser.getParsedParameters();
        assertNotNull(params);
        assertEquals(3, params.size(), "Should parse exactly 3 parameters");

        assertEquals(1, params.get(0).getAtomIndex());
        assertEquals("Fe", params.get(0).getElement());
        assertEquals("d", params.get(0).getShell());
        assertEquals(4.3000, params.get(0).getUValueEv(), 1e-6);

        // Average of Fe-3d should be (4.3 + 4.5)/2 = 4.40 eV
        String card = parser.generateHubbardCard();
        assertNotNull(card);
        assertTrue(card.contains("HUBBARD {ortho-atomic}"));
        assertTrue(card.contains("U Fe-3d 4.4000"));
        assertTrue(card.contains("U O-p 0.0000"));
    }
}
