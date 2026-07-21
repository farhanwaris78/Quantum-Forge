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

class QEAcousticSumRuleValidatorTest {

    @Test
    void testParserProcessesGammaPointAndPassesAsrForSmallDrift() throws IOException {
        File tempFile = File.createTempFile("matdyn-asr", ".freq");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            // First line represents Gamma point (q=0), then 5 mode frequencies
            writer.write("   0.0000000       2.5000       4.2000      6.1000     150.0000   240.0000\n");
            writer.write("   0.1000000      12.0000      14.2000     16.1000     152.0000   241.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEAcousticSumRuleValidator validator = new QEAcousticSumRuleValidator(property);
        validator.parse(tempFile);

        List<Double> freqs = validator.getGammaFrequenciesCm1();
        assertNotNull(freqs);
        assertEquals(5, freqs.size());
        assertEquals(2.5, freqs.get(0), 1e-6);

        assertTrue(validator.isAsrCompliant());
        assertTrue(validator.getDiagnostics().get(0).contains("verified"));
    }

    @Test
    void testParserProcessesGammaPointAndFailsAsrForLargeDrift() throws IOException {
        File tempFile = File.createTempFile("matdyn-asr-fail", ".freq");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            // Unphysical: third acoustic mode drifts to 25.1 cm-1 (> 20.0 limit)
            writer.write("   0.0000000       2.5000       4.2000     25.1000     150.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEAcousticSumRuleValidator validator = new QEAcousticSumRuleValidator(property);
        validator.parse(tempFile);

        assertFalse(validator.isAsrCompliant(), "ASR must fail since acoustic mode 3 drifts above 20.0 cm-1");
        assertTrue(validator.getDiagnostics().get(0).contains("VIOLATION"));
        assertTrue(validator.getDiagnostics().get(1).contains("grid-force leakage"));
    }
}
