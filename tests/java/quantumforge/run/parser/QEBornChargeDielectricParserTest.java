package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEBornChargeDielectricParserTest {

    @Test
    void testParserHandlesDielectricAndBornChargesWithAcsrPass() throws IOException {
        File tempFile = File.createTempFile("ph-born-charges", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Dielectric constant tensor\n");
            writer.write("          3.1000    0.0000    0.0000\n");
            writer.write("          0.0000    3.1000    0.0000\n");
            writer.write("          0.0000    0.0000    3.1000\n\n");
            writer.write("     Effective charges (espresso units) atom    1\n");
            writer.write("          2.2000    0.0000    0.0000\n");
            writer.write("          0.0000    2.2000    0.0000\n");
            writer.write("          0.0000    0.0000    2.2000\n");
            writer.write("     Effective charges (espresso units) atom    2\n");
            writer.write("         -2.2000    0.0000    0.0000\n");
            writer.write("          0.0000   -2.2000    0.0000\n");
            writer.write("          0.0000    0.0000   -2.2000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEBornChargeDielectricParser parser = new QEBornChargeDielectricParser(property);
        parser.parse(tempFile);

        assertEquals(3.1000, parser.getDielectricTensor()[0][0], 1e-6);
        assertEquals(2, parser.getBornCharges().size());
        assertEquals(2.2000, parser.getBornCharges().get(0).getTensor()[0][0], 1e-6);
        assertEquals(-2.2000, parser.getBornCharges().get(1).getTensor()[0][0], 1e-6);

        assertTrue(parser.isAcsrPassed(), "Acoustic Sum Rule must pass since sum (2.2 - 2.2 = 0) is well within 0.05 threshold");
        assertTrue(parser.getDiagnostics().get(0).contains("passed"));
    }

    @Test
    void testParserDetectsAcsrViolationForInconsistentCharges() throws IOException {
        File tempFile = File.createTempFile("ph-born-charges-fail", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Effective charges (espresso units) atom    1\n");
            writer.write("          2.5000    0.0000    0.0000\n");
            writer.write("          0.0000    2.5000    0.0000\n");
            writer.write("          0.0000    0.0000    2.5000\n");
            writer.write("     Effective charges (espresso units) atom    2\n");
            writer.write("         -2.2000    0.0000    0.0000\n");
            writer.write("          0.0000   -2.2000    0.0000\n");
            writer.write("          0.0000    0.0000   -2.2000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEBornChargeDielectricParser parser = new QEBornChargeDielectricParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isAcsrPassed(), "ACSR must fail since sum (2.5 - 2.2 = 0.3) exceeds 0.05 threshold");
        assertTrue(parser.getDiagnostics().get(0).contains("VIOLATION"));
    }
}
