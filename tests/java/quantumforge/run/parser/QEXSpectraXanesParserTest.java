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

class QEXSpectraXanesParserTest {

    @Test
    void testParserProcessesLanczosXanesSpectrumAndConvolutes() throws IOException {
        File tempFile = File.createTempFile("xspectra-xanes", ".dat");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("# energy (eV)     cross section (Mb)\n");
            writer.write("   285.00000         1.50000000\n");
            writer.write("   286.50000         3.00000000\n");
            writer.write("   288.00000         0.50000000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEXSpectraXanesParser parser = new QEXSpectraXanesParser(property);
        parser.parse(tempFile);

        assertEquals(3, parser.getSpectrum().size(), "Should parse exactly 3 spectrum points");
        assertEquals(285.0, parser.getSpectrum().get(0).getEnergyEv(), 1e-6);
        assertEquals(3.0, parser.getSpectrum().get(1).getCrossSectionMb(), 1e-6);

        // Broaden spectrum from 280 to 295 eV with a core-hole lifetime FWHM of 0.2 eV (Carbon K-edge standard)
        List<QEXSpectraXanesParser.XanesPoint> broadened = parser.computeBroadenedSpectrum(280.0, 295.0, 0.1, 0.2);
        assertNotNull(broadened);
        assertTrue(broadened.size() >= 140);

        // Find peak corresponding to the second, largest resonance peak (E = 286.5 eV)
        int peakIndex = -1;
        double maxIntensity = -1.0;
        for (int i = 0; i < broadened.size(); i++) {
            double e = broadened.get(i).getEnergyEv();
            double val = broadened.get(i).getCrossSectionMb();
            if (val > maxIntensity) {
                maxIntensity = val;
                peakIndex = i;
            }
        }

        // Resonant peak should occur precisely at E = 286.5 eV
        assertEquals(286.5, broadened.get(peakIndex).getEnergyEv(), 0.05);

        // Peak value should approximate the Lorentzian limit: A / (pi * (FWHM/2))
        // For A = 3.0, FWHM = 0.2 -> peak = 3.0 / (3.14159 * 0.1) = 9.5492
        assertEquals(3.0 / (Math.PI * 0.1), maxIntensity, 0.5, "Resonant peak must match Lorentzian lifetime convolution");
    }
}
