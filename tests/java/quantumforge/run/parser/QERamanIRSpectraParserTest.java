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

class QERamanIRSpectraParserTest {

    @Test
    void testParserProcessesSpectroscopyTableRows() throws IOException {
        File tempFile = File.createTempFile("ph-raman-table", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Mode    Frequency (cm-1)    IR intensity (D2/A2-amu)    Raman activity (A4/amu)\n");
            writer.write("       1          120.50                  1.5000                     4.5000\n");
            writer.write("       2          240.00                  0.0000                     0.0000\n");
            writer.write("       3          360.25                  3.0000                     8.0000\n");
            writer.write("\n"); // Table boundary
        }

        ProjectProperty property = new ProjectProperty();
        QERamanIRSpectraParser parser = new QERamanIRSpectraParser(property);
        parser.parse(tempFile);

        List<QERamanIRSpectraParser.SpectroMode> modes = parser.getModes();
        assertNotNull(modes);
        assertEquals(3, modes.size());

        assertEquals(1, modes.get(0).getModeIndex());
        assertEquals(120.50, modes.get(0).getFrequencyCm1(), 1e-6);
        assertEquals(1.5000, modes.get(0).getIrIntensity(), 1e-6);
        assertEquals(4.5000, modes.get(0).getRamanActivity(), 1e-6);

        assertEquals(360.25, modes.get(2).getFrequencyCm1(), 1e-6);
    }

    @Test
    void acceptsFortranExponentModesAndClearsStaleResults() throws IOException {
        File tempFile = File.createTempFile("ph-raman-d", ".log");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("mode 1 freq 2.0D+02 cm-1 IR intensity 1.0D+01 Raman activity 5.0D+00\\n");
        }
        QERamanIRSpectraParser parser = new QERamanIRSpectraParser(new ProjectProperty());
        parser.parse(tempFile);
        assertEquals(1, parser.getModes().size());
        parser.parse(new File(tempFile.getParentFile(), "missing.log"));
        assertTrue(parser.getModes().isEmpty());
    }

    @Test
    void testLorentzianBroadeningGeneratesPeakAtModeFrequency() throws IOException {
        File tempFile = File.createTempFile("ph-spectra-peaks", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("mode   1   freq    200.00 cm-1   IR intensity    10.0000   Raman activity    5.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QERamanIRSpectraParser parser = new QERamanIRSpectraParser(property);
        parser.parse(tempFile);

        // Compute spectra with 10 cm-1 FWHM, stepped from 180 to 220 cm-1
        List<QERamanIRSpectraParser.SpectrumPoint> irSpectra = parser.computePowderSpectra(180.0, 220.0, 1.0, 10.0, false);
        assertNotNull(irSpectra);
        assertTrue(irSpectra.size() >= 40);

        // Find the index representing the resonance peak (w = 200 cm-1)
        int peakIndex = -1;
        double maxIntensity = -1.0;
        for (int i = 0; i < irSpectra.size(); i++) {
            double w = irSpectra.get(i).frequency;
            double val = irSpectra.get(i).intensity;
            if (val > maxIntensity) {
                maxIntensity = val;
                peakIndex = i;
            }
        }

        // The maximum intensity MUST reside exactly at the resonant frequency w = 200 cm-1
        assertEquals(200.0, irSpectra.get(peakIndex).frequency, 0.1);

        // Analytic peak value of a single Lorentzian is A / (pi * (FWHM/2))
        // For A = 10, FWHM = 10 -> peak = 10 / (3.14159 * 5) = 0.636619
        assertEquals(10.0 / (Math.PI * 5.0), maxIntensity, 1e-4);
    }
}
