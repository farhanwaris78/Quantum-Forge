package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import quantumforge.project.property.ProjectProperty;

class QEDynmatModesParserTest {

    private static final String TWO_MODE_FILE =
            "     Diagonalizing the dynamical matrix\n"
            + "     q = (    0.000000000   0.000000000   0.000000000 )\n"
            + " **************************************************************************\n"
            + "     omega(  1) =       0.394099 [THz] =      13.143245 [cm-1]\n"
            + " **************************************************************************\n"
            + "     (  0.707107  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
            + "     (  0.707106  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
            + " **************************************************************************\n"
            + "     omega(  2) =      -1.234567 [THz] =     -41.185683 [cm-1]\n"
            + " **************************************************************************\n"
            + "     ( -0.707107  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
            + "     (  0.707106  0.000000  0.000000  0.000000  0.000000  0.000000 )\n";

    @Test
    void parsesModesAndAuditsOrthonormality() throws IOException {
        File tempFile = File.createTempFile("dynmat-modes", ".out");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(TWO_MODE_FILE);
        }
        QEDynmatModesParser parser = new QEDynmatModesParser(new ProjectProperty());
        parser.parse(tempFile);

        assertEquals(2, parser.getModes().size());
        assertEquals(2, parser.getAtomCount());
        QEDynmatModesParser.VibrationalMode first = parser.getModes().get(0);
        assertEquals(1, first.getIndex());
        assertEquals(13.143245, first.getOmegaCm1(), 1.0e-6);
        assertFalse(first.isImaginary());
        assertEquals(0.707107, first.getDisplacements()[0][0], 1.0e-6);
        assertTrue(first.getNormDeviation() < 1.0e-3, "2*(0.707107)^2 ~ 1");
        assertTrue(parser.getModes().get(1).isImaginary(),
                "A negative frequency is an imaginary mode");
        assertTrue(parser.isNormalizationConsistent(QEDynmatModesParser.DEFAULT_NORM_TOLERANCE));
        assertTrue(parser.getDiagnostics().stream().anyMatch(d -> d.contains("imaginary")),
                "The imaginary-mode diagnostic must be emitted");
    }

    @Test
    void rejectsInconsistentAtomCounts() throws IOException {
        File tempFile = File.createTempFile("dynmat-inconsistent", ".out");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     omega(  1) =       1.000000 [THz] =      33.356000 [cm-1]\n"
                    + "     (  0.707107  0.0  0.0  0.0  0.0  0.0 )\n"
                    + "     (  0.707106  0.0  0.0  0.0  0.0  0.0 )\n"
                    + "     omega(  2) =       2.000000 [THz] =      66.712000 [cm-1]\n"
                    + "     (  1.000000  0.0  0.0  0.0  0.0  0.0 )\n");
        }
        QEDynmatModesParser parser = new QEDynmatModesParser(new ProjectProperty());
        parser.parse(tempFile);
        assertTrue(parser.getModes().isEmpty(),
                "Modes with different atom counts invalidate the whole file");
        assertTrue(parser.getDiagnostics().stream().anyMatch(d -> d.contains("inconsistent")),
                parser.getDiagnostics().toString());
    }

    @Test
    void flagsBadNormalizationAndMissingData() throws IOException {
        File tempFile = File.createTempFile("dynmat-unnorm", ".out");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     omega(  1) =       1.000000 [THz] =      33.356000 [cm-1]\n"
                    + "     (  1.000000  0.0  0.0  0.0  0.0  0.0 )\n"
                    + "     (  1.000000  0.0  0.0  0.0  0.0  0.0 )\n");
        }
        QEDynmatModesParser parser = new QEDynmatModesParser(new ProjectProperty());
        parser.parse(tempFile);
        assertFalse(parser.isNormalizationConsistent(QEDynmatModesParser.DEFAULT_NORM_TOLERANCE),
                "norm sqrt(2) must fail the 1% audit");
        assertEquals(Math.sqrt(2.0) - 1.0, parser.getMaxNormDeviation(), 1.0e-9);
        assertTrue(parser.getDiagnostics().stream().anyMatch(d -> d.contains("audit failed")),
                parser.getDiagnostics().toString());

        parser.parse(new File(tempFile.getParentFile(), "does-not-exist.out"));
        assertTrue(parser.getModes().isEmpty(), "A missing file resets the parser state");
    }
}
