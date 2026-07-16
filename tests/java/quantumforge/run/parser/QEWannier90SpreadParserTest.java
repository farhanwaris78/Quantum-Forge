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

class QEWannier90SpreadParserTest {

    @Test
    void testParserProcessesConvergedWannierSpreads() throws IOException {
        File tempFile = File.createTempFile("wannier90-spread", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("  CYCLE      10    Spreads: (  1.24120,  0.81240,  0.51230 ) Total:    2.56590\n");
            writer.write("  CYCLE      20    Spreads: (  1.23120,  0.80120,  0.50110 ) Total:    2.53350\n");
            // Delta from cycle 20 to 30 is 2.53350 - 2.53349 = 0.00001 (<= 1e-5 threshold)
            writer.write("  CYCLE      30    Spreads: (  1.23119,  0.80119,  0.50111 ) Total:    2.53349\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEWannier90SpreadParser parser = new QEWannier90SpreadParser(property);
        parser.parse(tempFile);

        List<QEWannier90SpreadParser.WannierSpreadFrame> history = parser.getConvergenceHistory();
        assertNotNull(history);
        assertEquals(3, history.size(), "Should parse exactly 3 Wannier spread cycles");

        assertEquals(10, history.get(0).getCycle());
        assertEquals(1.24120, history.get(0).getIndividualSpreads().get(0), 1e-6);
        assertEquals(2.56590, history.get(0).getTotalSpreadAng2(), 1e-6);

        assertTrue(parser.isConverged(), "Run should satisfy convergence threshold");
        assertTrue(parser.getDiagnostics().get(0).contains("converged"));
    }

    @Test
    void testParserDetectsNonConvergenceForUnstableSpreads() throws IOException {
        File tempFile = File.createTempFile("wannier90-spread-fail", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("  CYCLE      10    Spreads: (  1.24120,  0.81240,  0.51230 ) Total:    2.56590\n");
            // Delta from cycle 10 to 20 is 2.56590 - 2.51210 = 0.05380 (> 1e-5 threshold)
            writer.write("  CYCLE      20    Spreads: (  1.20120,  0.80120,  0.50970 ) Total:    2.51210\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEWannier90SpreadParser parser = new QEWannier90SpreadParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isConverged(), "Unstable run should fail convergence thresholds");
        assertTrue(parser.getDiagnostics().get(0).contains("not converged"));
    }
}
