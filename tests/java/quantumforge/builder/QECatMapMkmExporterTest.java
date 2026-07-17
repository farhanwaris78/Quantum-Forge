package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

class QECatMapMkmExporterTest {

    @Test
    void testMkmExporterComputesThermodynamicsAndGeneratesScript() throws IOException {
        QECatMapMkmExporter exporter = new QECatMapMkmExporter();

        // Step 1: Add elementary reactions
        exporter.addReactionStep("CO* + O* <-> CO2* + *", 0.85);

        // Step 2: Add adsorbates with vibrational frequencies
        // CO adsorbed on Pt, DFT energy = -1.50 eV
        // Vibrational modes (cm-1)
        double[] coFreqs = {2000.0, 400.0, 100.0};
        exporter.addIntermediate("CO", -1.50, coFreqs);

        assertEquals(1, exporter.getSteps().size());
        assertEquals(1, exporter.getSpeciesList().size());

        // 3. Verify thermodynamic free energy calculations (at 300 K)
        QECatMapMkmExporter.IntermediateSpecies coSp = exporter.getSpeciesList().get(0);
        double g300 = exporter.calculateFreeEnergyEv(coSp, 300.0);

        // ZPE alone = 0.5 * h * nu_sum
        // h * nu = 2500 cm-1 * 1.23984e-4 eV = 0.30996 eV -> ZPE = 0.15498 eV
        // G(300K) should be around -1.50 + ZPE + corrections ~ -1.35 eV
        assertTrue(g300 > -1.50, "Free energy must exceed electronic energy due to zero-point energy contribution");
        assertEquals(-1.38, g300, 0.10); // check general bound

        // 4. Verify script generation
        String script = exporter.generateMkmScript(300.0, 1.0);
        assertNotNull(script);
        assertTrue(script.contains("rxn_expressions = ["));
        assertTrue(script.contains("CO* + O* <-> CO2* + *"));
        assertTrue(script.contains("species_definitions['CO*']"));
        assertTrue(script.contains("dft_energy': -1.50"));

        File tempFile = File.createTempFile("model", ".mkm");
        tempFile.deleteOnExit();

        exporter.writeMkmFile(tempFile, 300.0, 1.0);
        assertTrue(tempFile.exists());
        assertTrue(Files.size(tempFile.toPath()) > 100);
    }
}
