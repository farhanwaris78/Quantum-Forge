package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BandGapParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesDocumentedSummaryLines() throws Exception {
        Path output = temporaryDirectory.resolve("bands.out");
        Files.writeString(output,
                "     the Fermi energy is    5.4321 ev\n"
                + "     highest occupied level (ev): 4.9\n"
                + "Direct band gap = 1.2500 eV\n");

        BandGapParser parser = new BandGapParser(output.toString());
        assertTrue(parser.parse());
        assertEquals(5.4321, parser.getFermiEnergy(), 1.0e-12);
        assertEquals(1.25, parser.getBandGap(), 1.0e-12);
        assertTrue(parser.isInsulator());
        assertTrue(parser.isDirect());
    }

    @Test
    void rejectsMissingOrUnrecognizedOutput() throws Exception {
        assertFalse(new BandGapParser(temporaryDirectory.resolve("missing.out").toString()).parse());
        Path output = temporaryDirectory.resolve("metal.out");
        Files.writeString(output, "convergence has been achieved\n");
        assertFalse(new BandGapParser(output.toString()).parse());
    }
}
